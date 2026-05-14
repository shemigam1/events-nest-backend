#!/usr/bin/env bash
#
# Nightly Postgres backup → S3 for the EC2-hosted deployment.
#
# Runs `pg_dump` against RDS, uploads the gzipped dump to S3 with a
# date-stamped key, and prunes anything older than RETENTION_DAYS.
#
# Designed to run unattended as a cron job on the EC2 host. Relies on:
#   * The IAM instance profile having s3:PutObject + s3:DeleteObject on
#     the backups bucket
#   * pg_dump 16+ installed on the host (`sudo apt-get install postgresql-client-16`)
#   * The same .env file the app uses, so we can pull SPRING_DATASOURCE_*
#     without duplicating credentials
#
# Configure via env vars (typically set in /etc/eventsnest-backup.env
# which the cron entry sources):
#
#   BACKUP_S3_BUCKET   — required, e.g. eventsnest-backups
#   BACKUP_S3_PREFIX   — optional, default "postgres/"
#   RETENTION_DAYS     — optional, default 30
#   APP_ENV_FILE       — optional, default /home/ubuntu/.env
#
# Recommended cron entry on the EC2 (run as ubuntu user):
#   0 3 * * *  /home/ubuntu/scripts/backup-postgres.sh >> /var/log/eventsnest-backup.log 2>&1

set -euo pipefail

# ─── Config ─────────────────────────────────────────────────────────────
BUCKET="${BACKUP_S3_BUCKET:?Set BACKUP_S3_BUCKET, e.g. eventsnest-backups}"
PREFIX="${BACKUP_S3_PREFIX:-postgres/}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
APP_ENV_FILE="${APP_ENV_FILE:-/home/ubuntu/.env}"

# Strip a trailing slash off prefix for cleaner concatenation later
PREFIX="${PREFIX%/}/"

# ─── Pull connection details from the app's .env ────────────────────────
# We expect SPRING_DATASOURCE_URL of the form:
#   jdbc:postgresql://<host>:<port>/<db>
# plus SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD.
if [ ! -f "$APP_ENV_FILE" ]; then
  echo "ERROR: app env file not found at $APP_ENV_FILE" >&2
  exit 1
fi

# Source the env file (use grep to skip comments/blank lines safely).
# shellcheck disable=SC1090
set -a
. <(grep -E '^[A-Z_][A-Z0-9_]*=' "$APP_ENV_FILE")
set +a

: "${SPRING_DATASOURCE_URL:?missing in $APP_ENV_FILE}"
: "${SPRING_DATASOURCE_USERNAME:?missing in $APP_ENV_FILE}"
: "${SPRING_DATASOURCE_PASSWORD:?missing in $APP_ENV_FILE}"

# Parse the JDBC URL into host/port/db
# jdbc:postgresql://HOST:PORT/DB[?params]
parsed="${SPRING_DATASOURCE_URL#jdbc:postgresql://}"
host_port="${parsed%%/*}"
db_with_params="${parsed#*/}"
PG_HOST="${host_port%%:*}"
PG_PORT="${host_port##*:}"
PG_DB="${db_with_params%%\?*}"

# pg_dump reads the password from PGPASSWORD env var
export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"

# ─── Take the dump ──────────────────────────────────────────────────────
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

DUMP_FILE="$TMP_DIR/eventsnest-$TIMESTAMP.sql.gz"

echo "[$(date -u +%FT%TZ)] Starting pg_dump from $PG_HOST/$PG_DB"

pg_dump \
  --host="$PG_HOST" \
  --port="$PG_PORT" \
  --username="$SPRING_DATASOURCE_USERNAME" \
  --dbname="$PG_DB" \
  --format=plain \
  --no-owner --no-privileges \
  --schema=public \
  | gzip > "$DUMP_FILE"

DUMP_BYTES=$(stat -c%s "$DUMP_FILE" 2>/dev/null || stat -f%z "$DUMP_FILE")
echo "[$(date -u +%FT%TZ)] Dump complete: $DUMP_BYTES bytes"

# Sanity check — a successful dump for a non-empty DB should be > 1KB,
# but on a fresh DB with just the baseline schema it can be small.
if [ "$DUMP_BYTES" -lt 100 ]; then
  echo "ERROR: dump suspiciously small ($DUMP_BYTES bytes), aborting upload" >&2
  exit 1
fi

# ─── Upload to S3 ───────────────────────────────────────────────────────
S3_KEY="${PREFIX}eventsnest-$TIMESTAMP.sql.gz"
echo "[$(date -u +%FT%TZ)] Uploading to s3://$BUCKET/$S3_KEY"

aws s3 cp "$DUMP_FILE" "s3://$BUCKET/$S3_KEY" \
  --storage-class STANDARD_IA

echo "[$(date -u +%FT%TZ)] Upload complete"

# ─── Prune old backups ──────────────────────────────────────────────────
echo "[$(date -u +%FT%TZ)] Pruning backups older than $RETENTION_DAYS days from s3://$BUCKET/$PREFIX"

# Find objects older than RETENTION_DAYS and delete them. We avoid lifecycle
# policies for now because the bucket may also hold non-backup objects.
CUTOFF=$(date -u -d "$RETENTION_DAYS days ago" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
  || date -u -v-"$RETENTION_DAYS"d +%Y-%m-%dT%H:%M:%SZ)

aws s3api list-objects-v2 \
  --bucket "$BUCKET" \
  --prefix "$PREFIX" \
  --query "Contents[?LastModified<='$CUTOFF'].Key" \
  --output text 2>/dev/null \
  | tr '\t' '\n' \
  | while read -r key; do
      [ -z "$key" ] && continue
      echo "  deleting s3://$BUCKET/$key"
      aws s3 rm "s3://$BUCKET/$key" >/dev/null
    done

echo "[$(date -u +%FT%TZ)] Done"
