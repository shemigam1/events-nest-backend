-- ─── V15: Chat ───────────────────────────────────────────────────────────────
-- Conversations (direct or event-scoped group chats).
-- Participants — one row per user per conversation.
-- Messages — chat messages with sender reference.

CREATE TABLE conversations (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    title       varchar(255),
    event_id    uuid         REFERENCES events (id) ON DELETE SET NULL,
    type        varchar(20)  NOT NULL DEFAULT 'DIRECT',
    created_by  varchar(12)  NOT NULL REFERENCES users (id),
    created_at  timestamp(6) NOT NULL DEFAULT now(),
    updated_at  timestamp(6) NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_event_id  ON conversations (event_id);
CREATE INDEX idx_conversations_created_by ON conversations (created_by);

CREATE TABLE conversation_participants (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         varchar(12)  NOT NULL REFERENCES users (id),
    joined_at       timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conv_participants_conversation ON conversation_participants (conversation_id);
CREATE INDEX idx_conv_participants_user         ON conversation_participants (user_id);

CREATE TABLE messages (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       varchar(12)  NOT NULL REFERENCES users (id),
    content         text         NOT NULL,
    message_type    varchar(20)  NOT NULL DEFAULT 'TEXT',
    sent_at         timestamp(6) NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id);
CREATE INDEX idx_messages_sender       ON messages (sender_id);
