-- V4: multi-day event support (EventDay).
--
-- Every event is now modelled as 1..N days. Single-day events keep one row
-- (auto-created on event creation; UI hides the concept). Multi-day events
-- gain additional rows.
--
-- Events.start_time / end_time / check_in_start_time / venue stay as the
-- organiser-facing "event-wide window" — useful for listings and the public
-- page. EventDay carries the detailed structure inside.
--
-- Backfill: every existing event gets a single day (#1) mirroring its
-- start/end/check-in/venue so the FK invariant holds before the service
-- layer auto-create wiring takes effect for new events.

CREATE TABLE event_days (
    id                  uuid         PRIMARY KEY,
    event_id            uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    day_number          integer      NOT NULL,
    title               varchar(255),
    start_time          timestamp(6) NOT NULL,
    end_time            timestamp(6) NOT NULL,
    check_in_start_time timestamp(6),
    venue               varchar(255),
    created_at          timestamp(6),
    updated_at          timestamp(6),
    CONSTRAINT uk_event_days_event_day UNIQUE (event_id, day_number)
);
CREATE INDEX idx_event_days_event_id ON event_days (event_id);

INSERT INTO event_days (id, event_id, day_number, title, start_time, end_time, check_in_start_time, venue, created_at, updated_at)
SELECT gen_random_uuid(),
       id,
       1,
       NULL,
       start_time,
       end_time,
       check_in_start_time,
       NULL,
       NOW(),
       NOW()
FROM events;
