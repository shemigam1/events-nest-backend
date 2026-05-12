-- V6: per-day check-in records (day-aware check-in).
--
-- Whole-event tiers (ticket_tiers.event_day_id IS NULL): one row per (ticket, event_day)
-- for each day the attendee enters — pass-style behaviour.
-- Day-scoped tiers: a single row for that tier's day; ticket status becomes USED.
--
-- Legacy checked_in_at on tickets is mirrored into day #1 for analytics continuity.

CREATE TABLE ticket_check_ins (
    id                   uuid         PRIMARY KEY,
    ticket_id            uuid         NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    event_day_id         uuid         NOT NULL REFERENCES event_days (id) ON DELETE RESTRICT,
    checked_in_at        timestamp(6) NOT NULL,
    checked_in_by_label  varchar(100),
    CONSTRAINT uk_ticket_check_ins_ticket_day UNIQUE (ticket_id, event_day_id)
);

CREATE INDEX idx_ticket_check_ins_ticket_id ON ticket_check_ins (ticket_id);
CREATE INDEX idx_ticket_check_ins_event_day_id ON ticket_check_ins (event_day_id);

INSERT INTO ticket_check_ins (id, ticket_id, event_day_id, checked_in_at, checked_in_by_label)
SELECT gen_random_uuid(),
       t.id,
       ed.id,
       t.checked_in_at,
       t.checked_in_by_label
FROM tickets t
JOIN ticket_tiers tt ON tt.id = t.tier_id
JOIN event_days ed ON ed.event_id = tt.event_id AND ed.day_number = 1
WHERE t.checked_in_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM ticket_check_ins x WHERE x.ticket_id = t.id AND x.event_day_id = ed.id
  );
