-- V22: Comments module is on by default — it's the social fabric, not an
-- advanced feature like ratings or guest-lists. Existing events get the
-- flag flipped on so organisers don't have to opt every event in manually.

ALTER TABLE event_configs
    ADD COLUMN comments_enabled boolean NOT NULL DEFAULT TRUE;

UPDATE event_configs SET comments_enabled = TRUE;
