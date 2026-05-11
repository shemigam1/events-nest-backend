-- V10: event cover image + visibility (M3.1).
--
-- cover_image_url is nullable for backward-compat. Going forward,
-- submitForApproval enforces that it must be set.
--
-- visibility is a varchar (NOT NULL, default 'PUBLIC') storing the
-- @Enumerated(STRING) value. Existing events are public by definition —
-- they were already browseable before this column existed.

ALTER TABLE events
    ADD COLUMN cover_image_url varchar(1024);

ALTER TABLE events
    ADD COLUMN visibility varchar(20) NOT NULL DEFAULT 'PUBLIC';

-- Cover an existing index need: filter PRIVATE out of browse efficiently.
CREATE INDEX idx_events_status_visibility ON events (status, visibility);
