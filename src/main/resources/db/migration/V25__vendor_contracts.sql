CREATE TABLE vendor_contracts (
    id                  UUID         NOT NULL PRIMARY KEY,
    event_id            UUID         NOT NULL REFERENCES events(id),
    organizer_id        VARCHAR(12)  NOT NULL REFERENCES users(id),
    vendor_id           VARCHAR(12)  NOT NULL REFERENCES users(id),
    vendor_application_id UUID       REFERENCES vendor_applications(id),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    terms               TEXT,
    amount              NUMERIC(14,2) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    signed_at           TIMESTAMP,
    funded_at           TIMESTAMP,
    activated_at        TIMESTAMP,
    completed_at        TIMESTAMP,
    terminated_at       TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vc_event_id     ON vendor_contracts (event_id);
CREATE INDEX idx_vc_organizer_id ON vendor_contracts (organizer_id);
CREATE INDEX idx_vc_vendor_id    ON vendor_contracts (vendor_id);
