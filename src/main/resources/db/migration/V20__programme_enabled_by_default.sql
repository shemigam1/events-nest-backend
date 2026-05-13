-- ─── V20: Programme enabled by default for all events ────────────────────────
-- Programme is a universal feature (visible on event page, emailed to attendees)
-- and should not require explicit opt-in. Flip all existing rows and change the
-- column default so new events start with programme enabled.

UPDATE event_configs SET programme_enabled = TRUE;

ALTER TABLE event_configs
    ALTER COLUMN programme_enabled SET DEFAULT TRUE;
