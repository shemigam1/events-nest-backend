-- V7: Guest list & RSVP.
--
-- Organizers add guests manually; each guest gets an email with an RSVP link
-- containing a raw token. The hash is stored here for O(1) lookup.
-- One guest per email per event (uk_guests_event_email).

CREATE TABLE guests (
    id                uuid         PRIMARY KEY,
    event_id          uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name              varchar(255) NOT NULL,
    email             varchar(255) NOT NULL,
    phone             varchar(50),
    rsvp_status       varchar(20)  NOT NULL DEFAULT 'PENDING',
    rsvp_token_hash   varchar(64),
    invited_at        timestamp(6) NOT NULL,
    responded_at      timestamp(6),
    note              TEXT,
    CONSTRAINT uk_guests_event_email UNIQUE (event_id, email)
);

CREATE INDEX idx_guests_event_id       ON guests (event_id);
CREATE INDEX idx_guests_rsvp_token    ON guests (rsvp_token_hash) WHERE rsvp_token_hash IS NOT NULL;
