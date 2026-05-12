-- V5: day-aware ticketing.
--
-- A ticket tier can be either:
--   * whole-event (event_day_id IS NULL) — valid for every day of the event
--   * day-specific (event_day_id = <day>) — valid only for that single day
--
-- Existing tiers remain whole-event after this migration (event_day_id = NULL).
-- The column is nullable; the application-level service validates that any
-- non-null value points to a day belonging to the same event.

ALTER TABLE ticket_tiers
    ADD COLUMN event_day_id uuid;

ALTER TABLE ticket_tiers
    ADD CONSTRAINT fk_ticket_tiers_event_day
        FOREIGN KEY (event_day_id) REFERENCES event_days (id) ON DELETE RESTRICT;

CREATE INDEX idx_ticket_tiers_event_day_id ON ticket_tiers (event_day_id);
