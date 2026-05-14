-- Track when each participant last read a conversation.
-- NULL means the participant has never explicitly read the conversation.
-- Used to compute unread message counts in the conversation list.
ALTER TABLE conversation_participants
    ADD COLUMN last_read_at TIMESTAMP;
