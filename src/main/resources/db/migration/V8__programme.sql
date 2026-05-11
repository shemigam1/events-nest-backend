-- V8: Event programme / agenda items.
--
-- Items can be scoped to the whole event (event_day_id IS NULL) or to a
-- specific day. display_order controls the sort within a day (or the event).

CREATE TABLE programme_items (
    id              uuid         PRIMARY KEY,
    event_id        uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    event_day_id    uuid         REFERENCES event_days (id) ON DELETE SET NULL,
    title           varchar(255) NOT NULL,
    description     TEXT,
    speaker_name    varchar(255),
    speaker_bio     TEXT,
    start_time      timestamp(6),
    end_time        timestamp(6),
    display_order   int          NOT NULL DEFAULT 0
);

CREATE INDEX idx_programme_items_event_id ON programme_items (event_id);
