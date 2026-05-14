# EventsNest — EC2 Deployment Guide (end-to-end)

Deploys the Spring Boot monolith to a single EC2 instance running Docker Compose,
with RDS Postgres for the database, CloudFront for TLS, and GitHub Actions for
CI/CD. Frontend stays on Vercel.

## Command location legend

Every command in this guide is prefixed with where to run it:

| Prefix | Where |
|---|---|
| 💻 **LOCAL** | Your laptop terminal (Mac / Linux / Git Bash on Windows) |
| ☁️ **AWS** | AWS Web Console |
| 🖥️ **EC2** | Inside an SSH session to the EC2 instance |
| 🐙 **GitHub** | GitHub Web UI |

---

## Phase 0 — pre-flight checks (💻 LOCAL)

Verify what survived the EKS tear-down. All of these should return success:

```bash
# 💻 LOCAL — verify AWS CLI is configured with admin credentials
aws sts get-caller-identity

# 💻 LOCAL — ECR repo + images
aws ecr describe-repositories --repository-names events-nest --region eu-north-1
aws ecr describe-images --repository-name events-nest --region eu-north-1 \
  --query 'imageDetails[*].imageTags' --output table

# 💻 LOCAL — CloudFront still exists
aws cloudfront list-distributions \
  --query 'DistributionList.Items[?Comment==`EventsNest API`].[Id,DomainName,Status]' \
  --output table
```

If ECR has no `latest` tag, no problem — the first GHA deploy will create it. But
make a note of the ECR registry URI now:

```bash
# 💻 LOCAL
export AWS_REGION=eu-north-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_REGISTRY=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
echo "ECR registry: $ECR_REGISTRY"
```

Keep this terminal open — `AWS_REGION` and `AWS_ACCOUNT_ID` are referenced in
later steps.

---

## Phase 1 — RDS Postgres (☁️ AWS, ~10 min)

### 1.1 — get default VPC info

```bash
# 💻 LOCAL
DEFAULT_VPC=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
  --region $AWS_REGION --query 'Vpcs[0].VpcId' --output text)
echo "Default VPC: $DEFAULT_VPC"
```

### 1.2 — create the RDS Postgres instance

☁️ **AWS Console → RDS → Create database** (use Full configuration):

| Field | Value |
|---|---|
| Engine | PostgreSQL 16.x |
| Template | **Free tier** (if eligible, else Dev/Test) |
| DB instance identifier | `events-nest-db-prod` |
| Master username | `eventsnest_admin` |
| Credentials management | **Self managed** |
| Master password | run `openssl rand -base64 32` on your laptop, paste the result |
| Instance class | `db.t4g.micro` |
| Storage | 20 GiB gp3 |
| VPC | the default VPC (the ID from §1.1) |
| Public access | **No** |
| VPC security group | **Create new** → name `eventsnest-rds-sg` |
| Database port | 5432 |
| Initial database name | `events_nest_db` |
| Backup retention | 7 days |
| Encryption | enabled |

**Create database.** Wait ~5–10 min until status = **Available**.

### 1.3 — note the endpoint

```bash
# 💻 LOCAL — capture the endpoint for later steps
export RDS_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier events-nest-db-prod \
  --region $AWS_REGION \
  --query 'DBInstances[0].Endpoint.Address' --output text)
echo "RDS endpoint: $RDS_ENDPOINT"
```

---

## Phase 2 — EC2 instance (💻 LOCAL, ~10 min)

### 2.1 — SSH key pair

```bash
# 💻 LOCAL
aws ec2 create-key-pair \
  --key-name eventsnest-ec2 \
  --region $AWS_REGION \
  --query 'KeyMaterial' \
  --output text > ~/.ssh/eventsnest-ec2.pem
chmod 400 ~/.ssh/eventsnest-ec2.pem
```

> **Save this file.** You can't re-download the private key. If lost, you'll have
> to make a new key pair and re-launch the instance.

### 2.2 — security group

```bash
# 💻 LOCAL
SG_ID=$(aws ec2 create-security-group \
  --group-name eventsnest-ec2-sg \
  --description "EventsNest EC2 SG" \
  --vpc-id $DEFAULT_VPC \
  --region $AWS_REGION \
  --query 'GroupId' --output text)
echo "EC2 SG: $SG_ID"

# Allow SSH from your laptop only
MY_IP=$(curl -s ifconfig.me)
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 22 --cidr $MY_IP/32 \
  --region $AWS_REGION

# Allow HTTP/HTTPS from anywhere (CloudFront connects to :80, browsers might too)
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 80 --cidr 0.0.0.0/0 \
  --region $AWS_REGION
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 443 --cidr 0.0.0.0/0 \
  --region $AWS_REGION
```

### 2.3 — IAM instance profile (so EC2 can pull from ECR + write backups)

```bash
# 💻 LOCAL
# Trust policy: only EC2 can assume this role
cat > /tmp/ec2-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ec2.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role \
  --role-name EventsNestEC2Role \
  --assume-role-policy-document file:///tmp/ec2-trust.json

# Inline policy: ECR read + S3 backup write
cat > /tmp/ec2-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": [
        "arn:aws:s3:::eventsnest-backups",
        "arn:aws:s3:::eventsnest-backups/*"
      ]
    }
  ]
}
EOF

aws iam put-role-policy \
  --role-name EventsNestEC2Role \
  --policy-name EventsNestEC2InlinePolicy \
  --policy-document file:///tmp/ec2-policy.json

# Bundle the role into an instance profile (EC2 attaches profiles, not roles
# directly — same name is fine)
aws iam create-instance-profile --instance-profile-name EventsNestEC2Role
aws iam add-role-to-instance-profile \
  --instance-profile-name EventsNestEC2Role \
  --role-name EventsNestEC2Role

# Give IAM a few seconds to propagate before launching the instance
sleep 10
```

### 2.4 — launch the instance

```bash
# 💻 LOCAL — get latest Ubuntu 24.04 AMI
AMI=$(aws ec2 describe-images --owners 099720109477 \
  --filters "Name=name,Values=ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*" \
            "Name=state,Values=available" \
  --region $AWS_REGION \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' --output text)
echo "AMI: $AMI"

# 💻 LOCAL — launch t3.medium with the instance profile attached
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id $AMI \
  --instance-type t3.medium \
  --key-name eventsnest-ec2 \
  --security-group-ids $SG_ID \
  --iam-instance-profile Name=EventsNestEC2Role \
  --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3}' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=eventsnest-app}]' \
  --region $AWS_REGION \
  --query 'Instances[0].InstanceId' --output text)
echo "Instance: $INSTANCE_ID"

# Wait for it to enter the running state
aws ec2 wait instance-running --instance-ids $INSTANCE_ID --region $AWS_REGION
```

### 2.5 — Elastic IP (stable public IP across reboots)

```bash
# 💻 LOCAL
ALLOC_ID=$(aws ec2 allocate-address --region $AWS_REGION \
  --query 'AllocationId' --output text)
aws ec2 associate-address \
  --instance-id $INSTANCE_ID \
  --allocation-id $ALLOC_ID \
  --region $AWS_REGION

export EC2_HOST=$(aws ec2 describe-addresses --allocation-ids $ALLOC_ID \
  --region $AWS_REGION \
  --query 'Addresses[0].PublicIp' --output text)
echo "EC2 host: $EC2_HOST"
```

---

## Phase 3 — wire RDS to accept EC2 connections (💻 LOCAL)

```bash
# 💻 LOCAL — find the RDS SG
RDS_SG=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=eventsnest-rds-sg" \
  --region $AWS_REGION \
  --query 'SecurityGroups[0].GroupId' --output text)

# Allow Postgres ingress from the EC2 SG only
aws ec2 authorize-security-group-ingress \
  --group-id $RDS_SG \
  --protocol tcp --port 5432 \
  --source-group $SG_ID \
  --region $AWS_REGION

# Verify
aws ec2 describe-security-groups --group-ids $RDS_SG \
  --region $AWS_REGION \
  --query 'SecurityGroups[0].IpPermissions' --output table
```

---

## Phase 4 — install Docker + tooling on the EC2 (🖥️ EC2)

### 4.1 — SSH into the box (💻 LOCAL)

```bash
# 💻 LOCAL — first connection prompts to accept the host key
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST
```

### 4.2 — install everything (🖥️ EC2)

```bash
# 🖥️ EC2 — system packages
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg jq postgresql-client-16

# Docker via the official Docker repo
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Let ubuntu user run docker without sudo
sudo usermod -aG docker ubuntu

# AWS CLI v2 — needed for ECR login + backup script
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
sudo apt-get install -y unzip
unzip -q awscliv2.zip
sudo ./aws/install
rm -rf awscliv2.zip aws

# Verify
docker --version
docker compose version
aws --version
pg_dump --version
```

### 4.3 — log out + back in so the docker group takes effect

```bash
# 🖥️ EC2
exit

# 💻 LOCAL
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST

# 🖥️ EC2 — should work without sudo now
docker ps
```

### 4.4 — confirm ECR auth via the instance profile

```bash
# 🖥️ EC2 — instance profile gives us creds automatically
aws sts get-caller-identity   # ARN should end with /EventsNestEC2Role/i-xxxxx

# Test ECR login
aws ecr get-login-password --region eu-north-1 \
  | docker login --username AWS --password-stdin \
    $(aws sts get-caller-identity --query Account --output text).dkr.ecr.eu-north-1.amazonaws.com

# Should print: Login Succeeded
exit
```

---

## Phase 5 — provision the prod `.env` (💻 LOCAL → 🖥️ EC2)

The EC2 reads its env vars from `~/.env` on the box. Build this file locally
first, then scp it over.

### 5.1 — write the prod env locally

```bash
# 💻 LOCAL — generate a strong JWT key
NEW_JWT=$(openssl rand -base64 48)

# 💻 LOCAL — write .env.prod (gitignored)
cat > .env.prod <<EOF
# Postgres on RDS
SPRING_DATASOURCE_URL=jdbc:postgresql://$RDS_ENDPOINT:5432/events_nest_db
SPRING_DATASOURCE_USERNAME=eventsnest_admin
SPRING_DATASOURCE_PASSWORD=<the-rds-master-password-from-§1.2>

# Admin bootstrap
ADMIN_EMAIL=admin@eventsnest.com
ADMIN_PASSWORD=$(openssl rand -base64 24)
ADMIN_FIRST_NAME=Platform
ADMIN_LAST_NAME=Admin

# JWT signing
JWT_SIGNING_KEY=$NEW_JWT

# Mail (Brevo HTTP API)
MAIL_PROVIDER=brevo
BREVO_API_KEY=<your-brevo-key>
# FROM_EMAIL=EventsNest <noreply@yourdomain.com>

# Frontend / CORS
FRONTEND_URL=https://eventsnest.vercel.app
APP_CORS_ALLOWED_ORIGINS=https://eventsnest.vercel.app,https://eventsnest-*.vercel.app

# Grafana admin (only used if you run the monitoring services on the EC2)
GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 16)

# ECR registry — referenced by compose.prod.yaml. The EC2 instance profile
# handles auth; this is just the URL.
ECR_REGISTRY=$ECR_REGISTRY
IMAGE_TAG=latest
EOF

echo "Wrote .env.prod — saved the ADMIN_PASSWORD and GRAFANA_ADMIN_PASSWORD?"
grep -E "^(ADMIN_PASSWORD|GRAFANA_ADMIN_PASSWORD|JWT_SIGNING_KEY)=" .env.prod
```

> **Save these values somewhere** before the file ships to the EC2 — `ADMIN_PASSWORD`
> is what you'll use to log in as the seeded admin.

### 5.2 — ship .env.prod + infra + compose to the EC2

```bash
# 💻 LOCAL
scp -i ~/.ssh/eventsnest-ec2.pem .env.prod ubuntu@$EC2_HOST:/home/ubuntu/.env

scp -i ~/.ssh/eventsnest-ec2.pem compose.prod.yaml ubuntu@$EC2_HOST:/home/ubuntu/compose.prod.yaml

# Infra directories (prometheus config + grafana provisioning)
scp -i ~/.ssh/eventsnest-ec2.pem -r infra/ ubuntu@$EC2_HOST:/home/ubuntu/

# Backup script
scp -i ~/.ssh/eventsnest-ec2.pem scripts/backup-postgres.sh \
  ubuntu@$EC2_HOST:/home/ubuntu/backup-postgres.sh
```

---

## Phase 6 — first manual deploy (🖥️ EC2)

We do one manual deploy to confirm everything's wired before CI takes over.

```bash
# 💻 LOCAL
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST

# 🖥️ EC2 — make sure prep dirs exist
mkdir -p logs
chmod +x backup-postgres.sh

# Authenticate Docker to ECR
aws ecr get-login-password --region eu-north-1 \
  | docker login --username AWS --password-stdin \
    $(aws sts get-caller-identity --query Account --output text).dkr.ecr.eu-north-1.amazonaws.com

# Source .env so $ECR_REGISTRY + $IMAGE_TAG are available to compose
export $(grep -E '^[A-Z_][A-Z0-9_]*=' .env | xargs)

# Start the stack
docker compose -f compose.prod.yaml up -d

# Watch the app come up — Spring Boot 4 takes ~30-45s, then Flyway V1 runs
docker compose -f compose.prod.yaml logs -f app
```

Look for these lines in the log:
```
Successfully validated 1 migration
Migrating schema "public" to version "1 - baseline schema"
Successfully applied 1 migration to schema "public"
Started EventsNestServerApplication in N seconds
Seeded default admin: admin@eventsnest.com
```

Ctrl+C to exit the log tail (the container keeps running).

```bash
# 🖥️ EC2 — confirm the app is healthy
docker inspect --format '{{.State.Health.Status}}' events-nest-server
# Should print: healthy
```

### 6.1 — smoke-test from the EC2 itself

```bash
# 🖥️ EC2
curl -s http://localhost/actuator/health | jq .status
# Should print: "UP"

exit
```

### 6.2 — smoke-test from your laptop

```bash
# 💻 LOCAL — hit the public IP
curl -s "http://$EC2_HOST/actuator/health" | jq .status
# Should print: "UP"
```

If both return `"UP"`, the EC2 is serving traffic. If the laptop call hangs or
returns nothing, your SG isn't allowing port 80 — recheck §2.2.

---

## Phase 7 — update CloudFront origin (💻 LOCAL, ~10 min for propagation)

CloudFront still points at the old EKS ALB. Swap it to the EC2 IP.

```bash
# 💻 LOCAL
DIST_ID=$(aws cloudfront list-distributions \
  --query 'DistributionList.Items[?Comment==`EventsNest API`].Id | [0]' \
  --output text)
echo "CloudFront distribution: $DIST_ID"

# Fetch current config + ETag
aws cloudfront get-distribution-config --id $DIST_ID > /tmp/cf-current.json
ETAG=$(jq -r '.ETag' /tmp/cf-current.json)

# Swap origin domain to the EC2 Elastic IP
jq --arg new "$EC2_HOST" \
  '.DistributionConfig.Origins.Items[0].DomainName = $new' \
  /tmp/cf-current.json > /tmp/cf-updated.json

jq '.DistributionConfig' /tmp/cf-updated.json > /tmp/cf-config-only.json

aws cloudfront update-distribution \
  --id $DIST_ID \
  --if-match $ETAG \
  --distribution-config file:///tmp/cf-config-only.json
```

Wait for propagation (~5–10 min):

```bash
# 💻 LOCAL
aws cloudfront get-distribution --id $DIST_ID \
  --query 'Distribution.Status' --output text
# Repeat until: Deployed
```

Test through CloudFront:

```bash
# 💻 LOCAL
CF_DNS=$(aws cloudfront get-distribution --id $DIST_ID \
  --query 'Distribution.DomainName' --output text)
echo "CloudFront: https://$CF_DNS"

curl -s "https://$CF_DNS/actuator/health" | jq .status
# Should print: "UP"
```

---

## Phase 8 — CI/CD wiring (🐙 GitHub)

### 8.1 — add the new repo secrets

🐙 **GitHub → repo Settings → Secrets and variables → Actions → New repository secret**

| Name | Value |
|---|---|
| `EC2_HOST` | The Elastic IP from §2.5 |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_PRIVATE_KEY` | Run `cat ~/.ssh/eventsnest-ec2.pem` locally and paste the full output (including `-----BEGIN...` and `-----END...` lines) |

Existing secrets carry over:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_ACCOUNT_ID`
- `AWS_REGION`

Delete `EKS_CLUSTER_NAME` if it's still there — no longer used.

### 8.2 — trigger the first pipeline run

```bash
# 💻 LOCAL
git add compose.prod.yaml .github/workflows/deploy.yml scripts/
git commit -m "feat[deploy]: EC2 + RDS Postgres pipeline"
git push origin main
```

Watch the workflow at 🐙 **GitHub → Actions tab**. Expected: ~5–7 min,
green check. The workflow:
1. Runs `mvn test`
2. Builds + pushes image to ECR
3. `scp`s `compose.prod.yaml` + `infra/` to the EC2
4. `ssh`s to the EC2, runs `docker compose pull && up -d --no-deps --force-recreate app`
5. Waits for the container to report healthy
6. Smoke-tests `http://$EC2_HOST/actuator/health` from the runner

Final logs should include `Health check attempt N: healthy` and the smoke
test JSON.

---

## Phase 9 — Frontend (🐙 Vercel)

Frontend stays on Vercel — your WebSocket goes browser → backend directly, so
Vercel's tier limits don't apply.

🐙 **Vercel → project Settings → Environment Variables → edit `VITE_API_BASE_URL`**:

```
https://<your-cloudfront-domain>/api/v1
```

Trigger a redeploy: 🐙 **Vercel → Deployments → click latest → Redeploy**.

Test from the deployed frontend:
1. Open the Vercel URL in a browser
2. Try logging in as `admin@eventsnest.com` with the `ADMIN_PASSWORD` you saved in §5.1
3. DevTools → Network tab — confirm requests go to `https://<cloudfront>/api/v1/...`

---

## Phase 10 — backups (🖥️ EC2)

### 10.1 — create the S3 backup bucket (💻 LOCAL)

```bash
# 💻 LOCAL
aws s3api create-bucket \
  --bucket eventsnest-backups \
  --region eu-north-1 \
  --create-bucket-configuration LocationConstraint=eu-north-1

# Block public access
aws s3api put-public-access-block \
  --bucket eventsnest-backups \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

### 10.2 — install the cron job on the EC2

```bash
# 💻 LOCAL
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST

# 🖥️ EC2 — script is already at /home/ubuntu/backup-postgres.sh (from §5.2)

# Set the config env file the script reads
sudo tee /etc/eventsnest-backup.env > /dev/null <<'EOF'
BACKUP_S3_BUCKET=eventsnest-backups
BACKUP_S3_PREFIX=postgres/
RETENTION_DAYS=30
APP_ENV_FILE=/home/ubuntu/.env
EOF

# Make sure the script is executable
chmod +x /home/ubuntu/backup-postgres.sh

# Test-run once to confirm it works end-to-end
. /etc/eventsnest-backup.env && /home/ubuntu/backup-postgres.sh

# Should print:
#   Starting pg_dump from <rds-host>/events_nest_db
#   Dump complete: N bytes
#   Uploading to s3://eventsnest-backups/postgres/eventsnest-YYYYMMDD...
#   Upload complete
#   Pruning backups older than 30 days...
#   Done

# Confirm in S3
aws s3 ls s3://eventsnest-backups/postgres/

# Schedule it daily at 03:00 UTC
(crontab -l 2>/dev/null; echo "0 3 * * * . /etc/eventsnest-backup.env && /home/ubuntu/backup-postgres.sh >> /var/log/eventsnest-backup.log 2>&1") | crontab -

# Verify the cron entry landed
crontab -l

# Create the log file with the right ownership
sudo touch /var/log/eventsnest-backup.log
sudo chown ubuntu:ubuntu /var/log/eventsnest-backup.log

exit
```

---

## Phase 11 — billing alarms (💻 LOCAL, 2 min — do this NOW)

```bash
# 💻 LOCAL
EMAIL=you@example.com
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws budgets create-budget \
  --account-id $ACCOUNT_ID \
  --budget "{
    \"BudgetName\": \"eventsnest-50\",
    \"BudgetLimit\": {\"Amount\": \"50\", \"Unit\": \"USD\"},
    \"TimeUnit\": \"MONTHLY\",
    \"BudgetType\": \"COST\"
  }" \
  --notifications-with-subscribers "[{
    \"Notification\": {
      \"NotificationType\": \"ACTUAL\",
      \"ComparisonOperator\": \"GREATER_THAN\",
      \"Threshold\": 100
    },
    \"Subscribers\": [{\"SubscriptionType\": \"EMAIL\", \"Address\": \"$EMAIL\"}]
  }]"
```

Repeat with name `eventsnest-80` at $80.

---

## Day-2 operations

### Tail app logs

```bash
# 💻 LOCAL
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST

# 🖥️ EC2
docker logs -f events-nest-server --tail=100
# Or all containers:
cd /home/ubuntu && docker compose -f compose.prod.yaml logs -f
```

### Access Grafana via SSH tunnel

Grafana is bound to `127.0.0.1:3000` on the EC2 — not reachable from the
internet on purpose. Tunnel it through SSH:

```bash
# 💻 LOCAL — forward Grafana :3000 and Prometheus :9090 to your laptop
ssh -i ~/.ssh/eventsnest-ec2.pem \
    -L 3000:localhost:3000 \
    -L 9090:localhost:9090 \
    ubuntu@$EC2_HOST
```

While the SSH session is open, on your laptop:
- Grafana → http://localhost:3000 — `admin` / the `GRAFANA_ADMIN_PASSWORD` from `.env`
- Prometheus → http://localhost:9090

Close the SSH session to drop the tunnel.

### Manual redeploy without pushing code

```bash
# 💻 LOCAL
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST

# 🖥️ EC2
cd /home/ubuntu
docker compose -f compose.prod.yaml pull app
docker compose -f compose.prod.yaml up -d --no-deps --force-recreate app
```

### Connect to RDS from the EC2 (debug a query)

```bash
# 💻 LOCAL
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST

# 🖥️ EC2
source /home/ubuntu/.env
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
  -h $(echo $SPRING_DATASOURCE_URL | sed 's|jdbc:postgresql://||' | cut -d: -f1) \
  -U $SPRING_DATASOURCE_USERNAME \
  -d events_nest_db
```

### Rotate a secret (e.g. Brevo key)

```bash
# 💻 LOCAL — edit .env.prod, then re-ship
nano .env.prod

scp -i ~/.ssh/eventsnest-ec2.pem .env.prod ubuntu@$EC2_HOST:/home/ubuntu/.env

# 💻 LOCAL — restart app to pick up new env
ssh -i ~/.ssh/eventsnest-ec2.pem ubuntu@$EC2_HOST \
  "cd /home/ubuntu && docker compose -f compose.prod.yaml up -d --no-deps --force-recreate app"
```

### Stop the EC2 to pause spend (without losing anything)

```bash
# 💻 LOCAL
aws ec2 stop-instances --instance-ids $INSTANCE_ID --region eu-north-1
aws rds stop-db-instance --db-instance-identifier events-nest-db-prod --region eu-north-1
```

**Saves:** ~$30/mo (EC2) + ~$13/mo (RDS) = ~$43/mo
**Still pays:** Elastic IP if instance is stopped (~$3.60/mo — yes, AWS charges for *unattached or attached-to-stopped-instance* EIPs), EBS volumes (~$3/mo), CloudFront (~free)

To resume:
```bash
# 💻 LOCAL
aws ec2 start-instances --instance-ids $INSTANCE_ID --region eu-north-1
aws rds start-db-instance --db-instance-identifier events-nest-db-prod --region eu-north-1

# Wait, then re-fetch the public IP — Elastic IP keeps the same address ✓
aws ec2 describe-instances --instance-ids $INSTANCE_ID --region eu-north-1 \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text
```

---

## Verification checklist (post-deploy)

| Check | Command | Where |
|---|---|---|
| EC2 reachable | `curl http://$EC2_HOST/actuator/health` | 💻 LOCAL |
| CloudFront reachable | `curl https://$CF_DNS/actuator/health` | 💻 LOCAL |
| Login works | `curl -X POST https://$CF_DNS/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@eventsnest.com","password":"<ADMIN_PASSWORD>"}'` | 💻 LOCAL |
| CORS preflight OK | `curl -i -X OPTIONS -H "Origin: https://eventsnest.vercel.app" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: content-type, authorization" https://$CF_DNS/api/v1/auth/login \| grep -i access-control` | 💻 LOCAL |
| GHA pipeline green | Push an empty commit, watch Actions tab | 🐙 GitHub |
| Frontend can log in | Browser test on Vercel URL | Browser |
| Backup created | `aws s3 ls s3://eventsnest-backups/postgres/` | 💻 LOCAL |
| Pod is healthy | `docker inspect --format '{{.State.Health.Status}}' events-nest-server` | 🖥️ EC2 |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `ssh: Permission denied (publickey)` | Wrong key file permissions | `chmod 400 ~/.ssh/eventsnest-ec2.pem` |
| `aws ecr get-login-password` returns AccessDenied | Instance profile not attached or IAM perms missing | Check §2.3 + §2.4; `aws sts get-caller-identity` should show role/EventsNestEC2Role |
| App container crashlooping with "Unknown database" | RDS "Initial database name" was blank | `psql ... -c 'CREATE DATABASE events_nest_db;'` from the EC2 |
| `docker compose pull` fails with "no basic auth credentials" | ECR token expired (12h lifetime) | Re-run the `aws ecr get-login-password ... \| docker login` command |
| `curl http://$EC2_HOST/...` times out | Port 80 not open in SG | Re-check §2.2's authorize-security-group-ingress for port 80 |
| GHA fails: "Permission denied" SSH | `EC2_SSH_PRIVATE_KEY` secret malformed | Paste with `-----BEGIN...` and `-----END...` lines included, no extra blank lines |
| CloudFront 502/504 | Origin still old EKS ALB, or EC2 down | Re-run §7; `aws cloudfront get-distribution --id $DIST_ID --query 'Distribution.DistributionConfig.Origins'` to inspect |
| Backup script errors "psql: connection refused" | RDS SG doesn't allow EC2 SG | Re-check §3 |

---

## Total cost expectation

| Item | First year (free tier) | After free tier |
|---|---|---|
| EC2 t3.medium | $30/mo | $30/mo |
| Elastic IP (attached) | $0 | $0 |
| EBS 30GB gp3 | $0 (first 30GB free) | $3/mo |
| RDS db.t4g.micro | $0 (free tier, 750 hrs/mo) | $13/mo |
| CloudFront | ~$0 (1TB/mo free) | ~$2/mo |
| S3 backups (~30 dumps × 1MB) | ~$0.01/mo | ~$0.01/mo |
| ECR storage | $0 (first 500MB free) | ~$1/mo |
| **Total** | **~$30/mo** | **~$50/mo** |

---

*Migration source: this guide replaces the EKS deployment described in
`docs/DEPLOYMENT.md` (now obsolete). Keep DEPLOYMENT.md for reference until
the EC2 setup is verified end-to-end, then archive it.*
