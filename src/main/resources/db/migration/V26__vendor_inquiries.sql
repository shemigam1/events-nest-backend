CREATE TABLE vendor_inquiries (
    id               UUID         NOT NULL PRIMARY KEY,
    event_id         UUID         NOT NULL REFERENCES events(id),
    organizer_id     VARCHAR(12)  NOT NULL REFERENCES users(id),
    vendor_id        VARCHAR(12)  NOT NULL REFERENCES users(id),
    message          TEXT         NOT NULL,
    service_type     VARCHAR(100),
    conversation_id  UUID         REFERENCES conversations(id),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (event_id, organizer_id, vendor_id)
);

CREATE INDEX idx_vi_event_id     ON vendor_inquiries (event_id);
CREATE INDEX idx_vi_organizer_id ON vendor_inquiries (organizer_id);
CREATE INDEX idx_vi_vendor_id    ON vendor_inquiries (vendor_id);
