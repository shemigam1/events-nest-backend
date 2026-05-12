-- V2: Add event short code and ticket short code columns. (PostgreSQL)
--
-- events.code        — 8-char NanoID generated on event creation. Used by
--                      check-in staff to locate an event without knowing its UUID.
-- tickets.short_code — 8-char NanoID generated at ticket issuance. Fallback
--                      for manual entry when a QR code cannot be scanned.

ALTER TABLE events
    ADD COLUMN code varchar(12);
ALTER TABLE events
    ADD CONSTRAINT uk_events_code UNIQUE (code);

ALTER TABLE tickets
    ADD COLUMN short_code varchar(10);
ALTER TABLE tickets
    ADD CONSTRAINT uk_tickets_short_code UNIQUE (short_code);
