-- V3: per-event module toggles (EventConfig).
--
-- Each event gets exactly one config row. The Event Workspace UI reads these
-- toggles to decide which module tabs to render. New modules (guest list,
-- programme, ratings) are off by default — organisers opt in from Settings.
--
-- Ticketing is included as a toggle for symmetry with future modules but
-- defaults to true. Enforcement of `ticketing_enabled = false` will land when
-- a real use case appears.
--
-- Backfill: every existing event gets a default config so the FK stays valid
-- after the auto-create wiring lands in the service layer.

CREATE TABLE event_configs (
    id                 uuid    PRIMARY KEY,
    event_id           uuid    NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    ticketing_enabled  boolean NOT NULL DEFAULT TRUE,
    guest_list_enabled boolean NOT NULL DEFAULT FALSE,
    programme_enabled  boolean NOT NULL DEFAULT FALSE,
    ratings_enabled    boolean NOT NULL DEFAULT FALSE,
    created_at         timestamp(6),
    updated_at         timestamp(6),
    CONSTRAINT uk_event_configs_event_id UNIQUE (event_id)
);

INSERT INTO event_configs (id, event_id, ticketing_enabled, guest_list_enabled, programme_enabled, ratings_enabled, created_at, updated_at)
SELECT gen_random_uuid(), id, TRUE, FALSE, FALSE, FALSE, NOW(), NOW()
FROM events;
