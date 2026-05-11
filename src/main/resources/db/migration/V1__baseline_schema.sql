-- Flyway V1 baseline — PostgreSQL.
--
-- Equivalent to the prior MySQL baseline, rewritten for Postgres:
--   * BINARY(16) UUID PKs        -> native `uuid`
--   * bit(1)                     -> `boolean`
--   * enum('A','B',...)          -> `varchar(N)` (JPA stores @Enumerated(STRING))
--   * datetime(6)                -> `timestamp(6)`
--   * ENGINE / CHARSET / COLLATE -> removed
--
-- Tables are ordered to satisfy foreign-key dependencies (parents first).

-- ─── users (root: no FK dependencies) ──────────────────────────────────────
CREATE TABLE users (
    id            varchar(12)  PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,
    first_name    varchar(255) NOT NULL,
    last_name     varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role          varchar(20)  NOT NULL,
    enabled       boolean      NOT NULL,
    created_at    timestamp(6),
    updated_at    timestamp(6)
);

-- ─── events (FK → users.created_by) ────────────────────────────────────────
CREATE TABLE events (
    id                  uuid         PRIMARY KEY,
    title               varchar(255) NOT NULL,
    description         text,
    venue               varchar(255) NOT NULL,
    start_time          timestamp(6) NOT NULL,
    end_time            timestamp(6) NOT NULL,
    check_in_start_time timestamp(6),
    status              varchar(30)  NOT NULL,
    rejection_reason    text,
    has_pending_update  boolean      NOT NULL,
    pending_description text,
    created_by          varchar(12) REFERENCES users (id),
    created_at          timestamp(6),
    updated_at          timestamp(6)
);
CREATE INDEX idx_events_created_by ON events (created_by);

-- ─── ticket_tiers (FK → events) ────────────────────────────────────────────
CREATE TABLE ticket_tiers (
    id                 uuid           PRIMARY KEY,
    event_id           uuid           NOT NULL REFERENCES events (id),
    name               varchar(100)   NOT NULL,
    price              numeric(10, 2) NOT NULL,
    row_prefix         varchar(10)    NOT NULL,
    row_count          integer        NOT NULL,
    seats_per_row      integer        NOT NULL,
    total_capacity     integer        NOT NULL,
    available_capacity integer        NOT NULL,
    version            integer,
    created_at         timestamp(6)
);
CREATE INDEX idx_ticket_tiers_event_id ON ticket_tiers (event_id);

-- ─── bookings (FK → users, events, ticket_tiers) ───────────────────────────
CREATE TABLE bookings (
    id                uuid           PRIMARY KEY,
    attendee_id       varchar(12)    NOT NULL REFERENCES users (id),
    event_id          uuid           NOT NULL REFERENCES events (id),
    tier_id           uuid           NOT NULL REFERENCES ticket_tiers (id),
    quantity          integer        NOT NULL,
    total_amount      numeric(10, 2) NOT NULL,
    payment_reference varchar(100),
    payment_status    varchar(20)    NOT NULL,
    status            varchar(20)    NOT NULL,
    created_at        timestamp(6)
);
CREATE INDEX idx_bookings_attendee_id ON bookings (attendee_id);
CREATE INDEX idx_bookings_event_id    ON bookings (event_id);
CREATE INDEX idx_bookings_tier_id     ON bookings (tier_id);

-- ─── tickets (FK → users, bookings, ticket_tiers) ──────────────────────────
CREATE TABLE tickets (
    id                  uuid         PRIMARY KEY,
    attendee_id         varchar(12)  NOT NULL REFERENCES users (id),
    booking_id          uuid         NOT NULL REFERENCES bookings (id),
    tier_id             uuid         NOT NULL REFERENCES ticket_tiers (id),
    seat_number         varchar(20)  NOT NULL,
    qr_code             varchar(255) NOT NULL UNIQUE,
    status              varchar(20)  NOT NULL,
    issued_at           timestamp(6),
    checked_in_at       timestamp(6),
    checked_in_by       varchar(12) REFERENCES users (id),
    checked_in_by_label varchar(100),
    CONSTRAINT uk_tickets_tier_seat UNIQUE (tier_id, seat_number)
);
CREATE INDEX idx_tickets_attendee_id   ON tickets (attendee_id);
CREATE INDEX idx_tickets_booking_id    ON tickets (booking_id);
CREATE INDEX idx_tickets_checked_in_by ON tickets (checked_in_by);

-- ─── event_memberships (FK → users, events) ────────────────────────────────
CREATE TABLE event_memberships (
    id          uuid        PRIMARY KEY,
    user_id     varchar(12) NOT NULL REFERENCES users (id),
    event_id    uuid        NOT NULL REFERENCES events (id),
    role        varchar(30) NOT NULL,
    status      varchar(20) NOT NULL,
    assigned_by varchar(12) REFERENCES users (id),
    created_at  timestamp(6),
    CONSTRAINT uk_event_memberships_user_event_role UNIQUE (user_id, event_id, role)
);
CREATE INDEX idx_event_memberships_event_id    ON event_memberships (event_id);
CREATE INDEX idx_event_memberships_assigned_by ON event_memberships (assigned_by);

-- ─── check_in_invites (FK → users, events) ─────────────────────────────────
CREATE TABLE check_in_invites (
    id           uuid         PRIMARY KEY,
    event_id     uuid         NOT NULL REFERENCES events (id),
    name         varchar(100) NOT NULL,
    email        varchar(255),
    token_hash   varchar(255) NOT NULL UNIQUE,
    status       varchar(20)  NOT NULL,
    expires_at   timestamp(6) NOT NULL,
    last_used_at timestamp(6),
    created_by   varchar(12)  NOT NULL REFERENCES users (id),
    created_at   timestamp(6)
);
CREATE INDEX idx_check_in_invites_event_id   ON check_in_invites (event_id);
CREATE INDEX idx_check_in_invites_created_by ON check_in_invites (created_by);

-- ─── event_edit_requests (FK → users, events) ──────────────────────────────
CREATE TABLE event_edit_requests (
    id               uuid        PRIMARY KEY,
    event_id         uuid        NOT NULL REFERENCES events (id),
    submitted_by     varchar(12) NOT NULL REFERENCES users (id),
    proposed_changes text        NOT NULL,
    status           varchar(20) NOT NULL,
    rejection_reason text,
    reviewed_at      timestamp(6),
    created_at       timestamp(6)
);
CREATE INDEX idx_event_edit_requests_event_id     ON event_edit_requests (event_id);
CREATE INDEX idx_event_edit_requests_submitted_by ON event_edit_requests (submitted_by);

-- ─── notifications (no FK constraints) ─────────────────────────────────────
CREATE TABLE notifications (
    id          uuid         PRIMARY KEY,
    user_id     varchar(12)  NOT NULL,
    type        varchar(30)  NOT NULL,
    title       varchar(255) NOT NULL,
    message     text,
    dedupe_key  varchar(100) NOT NULL,
    is_read     boolean      NOT NULL,
    created_at  timestamp(6),
    CONSTRAINT uq_notifications_type_dedupe UNIQUE (type, dedupe_key)
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at);

-- ─── email_jobs (no FK constraints — outbox is intentionally decoupled) ────
CREATE TABLE email_jobs (
    id              uuid         PRIMARY KEY,
    to_email        varchar(255) NOT NULL,
    job_type        varchar(30),
    payload_json    text,
    staff_name      varchar(100),
    raw_token       text,
    event_title     varchar(255),
    status          varchar(20)  NOT NULL,
    attempts        integer      NOT NULL,
    max_attempts    integer      NOT NULL,
    last_attempt_at timestamp(6),
    failure_reason  text,
    created_at      timestamp(6)
);

-- ─── admin_invitations (no FK constraints) ─────────────────────────────────
CREATE TABLE admin_invitations (
    id         uuid         PRIMARY KEY,
    email      varchar(255) NOT NULL UNIQUE,
    token      varchar(255) NOT NULL UNIQUE,
    expires_at timestamp(6) NOT NULL,
    used       boolean      NOT NULL,
    created_at timestamp(6)
);
