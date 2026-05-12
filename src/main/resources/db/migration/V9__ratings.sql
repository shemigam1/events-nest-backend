-- V9: Post-event ratings & feedback.
--
-- One form per event (uk_rating_forms_event_id). The organizer designs
-- questions; the dispatcher emails ticket holders after the event ends.
-- Responses are anonymous when collect_anonymous = true, otherwise the
-- respondent_id (user id) is stored and uniqueness is enforced.

CREATE TABLE rating_forms (
    id                  uuid         PRIMARY KEY,
    event_id            uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    title               varchar(255) NOT NULL,
    collect_anonymous   boolean      NOT NULL DEFAULT false,
    send_delay_hours    int          NOT NULL DEFAULT 24,
    sent_at             timestamp(6),
    created_at          timestamp(6) NOT NULL,
    updated_at          timestamp(6),
    CONSTRAINT uk_rating_forms_event_id UNIQUE (event_id)
);

CREATE TABLE rating_questions (
    id              uuid         PRIMARY KEY,
    form_id         uuid         NOT NULL REFERENCES rating_forms (id) ON DELETE CASCADE,
    question_text   TEXT         NOT NULL,
    question_type   varchar(30)  NOT NULL,
    display_order   int          NOT NULL DEFAULT 0,
    required        boolean      NOT NULL DEFAULT true,
    options         TEXT
);

CREATE INDEX idx_rating_questions_form_id ON rating_questions (form_id);

CREATE TABLE rating_responses (
    id              uuid         PRIMARY KEY,
    form_id         uuid         NOT NULL REFERENCES rating_forms (id) ON DELETE CASCADE,
    respondent_id   varchar(36),
    submitted_at    timestamp(6) NOT NULL
);

CREATE INDEX idx_rating_responses_form_id ON rating_responses (form_id);
-- Unique per (form, respondent) when attribution is required (enforced at app layer).

CREATE TABLE rating_answers (
    id              uuid         PRIMARY KEY,
    response_id     uuid         NOT NULL REFERENCES rating_responses (id) ON DELETE CASCADE,
    question_id     uuid         NOT NULL REFERENCES rating_questions (id) ON DELETE CASCADE,
    answer_text     TEXT,
    answer_number   int
);

CREATE INDEX idx_rating_answers_response_id ON rating_answers (response_id);
