-- Link vendor contracts to their associated DM conversation.
-- Nullable because contracts created before this migration have no conversation.
ALTER TABLE vendor_contracts
    ADD COLUMN conversation_id UUID REFERENCES conversations(id);
