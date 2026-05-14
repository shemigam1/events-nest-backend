-- V13: Event Manager Panel foundation (M3.3.1).
--
-- Three new tables and one column addition. No existing data is modified.
--
--   event_manager_applications  User applies; admin approves/rejects.
--                               On approval, users.role flips USER → EVENT_MANAGER
--                               and an event_manager_profiles row is created.
--   event_manager_profiles      One row per approved manager. Stores public
--                               profile data (bio, location, etc.) shown to
--                               organisers browsing who to hire.
--   event_manager_invitations   Organiser → manager invite for a single event.
--                               On accept, an event_memberships row is created
--                               with role=EVENT_MANAGER and the chosen permissions.
--   event_memberships.permissions
--                               TEXT array. Stores ["GUEST_LIST","PROGRAMME",…]
--                               for EVENT_MANAGER memberships; NULL for every
--                               other role. Postgres native text[] avoids the
--                               JSONB-parsing overhead and gives us GIN-index
--                               support later if needed.
--
-- Global role.EVENT_MANAGER comes as a Java enum extension — the users.role
-- column is varchar storing @Enumerated(STRING), so no schema change there.

-- ─── applications ──────────────────────────────────────────────────────────
CREATE TABLE event_manager_applications (
    id                  uuid          PRIMARY KEY,
    applicant_id        varchar(12)   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    display_name        varchar(255)  NOT NULL,
    bio                 TEXT,
    years_of_experience int,
    past_events_count   int,
    specialties         TEXT,
    location            varchar(255),
    portfolio_url       varchar(1024),
    status              varchar(20)   NOT NULL DEFAULT 'PENDING',
    reviewed_by         varchar(12)   REFERENCES users (id),
    reviewed_at         timestamp(6),
    review_notes        TEXT,
    applied_at          timestamp(6)  NOT NULL,
    updated_at          timestamp(6)
);

-- One outstanding application per user at a time. Re-applying after rejection
-- updates the existing row rather than stacking duplicates.
CREATE UNIQUE INDEX uk_event_manager_applications_applicant
    ON event_manager_applications (applicant_id);

CREATE INDEX idx_event_manager_applications_status
    ON event_manager_applications (status);

-- ─── profiles (created on approval) ────────────────────────────────────────
CREATE TABLE event_manager_profiles (
    id                  uuid          PRIMARY KEY,
    user_id             varchar(12)   NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    display_name        varchar(255)  NOT NULL,
    bio                 TEXT,
    years_of_experience int,
    past_events_count   int,
    specialties         TEXT,
    location            varchar(255),
    portfolio_url       varchar(1024),
    suspended           boolean       NOT NULL DEFAULT false,
    created_at          timestamp(6)  NOT NULL,
    updated_at          timestamp(6)
);

-- ─── invitations (organiser → manager, per event) ─────────────────────────
CREATE TABLE event_manager_invitations (
    id              uuid          PRIMARY KEY,
    event_id        uuid          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    manager_user_id varchar(12)   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invited_by      varchar(12)   NOT NULL REFERENCES users (id),
    permissions     text[]        NOT NULL DEFAULT '{}',
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',
    responded_at    timestamp(6),
    created_at      timestamp(6)  NOT NULL,
    -- A manager can have at most one outstanding invite per event. A
    -- rejected invite blocks re-inviting until removed/updated.
    CONSTRAINT uk_event_manager_invitations_event_manager
        UNIQUE (event_id, manager_user_id)
);

CREATE INDEX idx_event_manager_invitations_manager
    ON event_manager_invitations (manager_user_id, status);

-- ─── permissions array on memberships ──────────────────────────────────────
-- text[] (Postgres native) — meaningful only for EVENT_MANAGER memberships.
-- Stored values come from the ManagerPermission Java enum (GUEST_LIST,
-- PROGRAMME, CHECK_IN_STAFF, ANALYTICS, EVENT_DETAILS, etc.).
ALTER TABLE event_memberships
    ADD COLUMN permissions text[];
