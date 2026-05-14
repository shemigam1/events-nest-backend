-- ─── V16: Vendor Applications ────────────────────────────────────────────────
-- Vendors apply to provide services (catering, AV, security, etc.) for events.
-- One application per vendor per event per service type (unique constraint).

CREATE TABLE vendor_applications (
    id              uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid           NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    applicant_id    varchar(12)    NOT NULL REFERENCES users (id),
    service_type    varchar(100)   NOT NULL,
    description     text,
    proposed_amount numeric(19, 2),
    status          varchar(20)    NOT NULL DEFAULT 'PENDING',
    reviewed_by     varchar(12)    REFERENCES users (id),
    reviewed_at     timestamp(6),
    created_at      timestamp(6)   NOT NULL DEFAULT now(),
    updated_at      timestamp(6)   NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_application UNIQUE (event_id, applicant_id, service_type)
);

CREATE INDEX idx_vendor_applications_event     ON vendor_applications (event_id);
CREATE INDEX idx_vendor_applications_applicant ON vendor_applications (applicant_id);
CREATE INDEX idx_vendor_applications_status    ON vendor_applications (status);
