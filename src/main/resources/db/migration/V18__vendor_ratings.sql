-- ─── V17: Vendor Ratings ─────────────────────────────────────────────────────
-- Organisers rate a vendor once per accepted application, after the event ends.
-- score is constrained to 1-5 by a CHECK constraint.

CREATE TABLE vendor_ratings (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_application_id uuid         NOT NULL UNIQUE REFERENCES vendor_applications (id) ON DELETE CASCADE,
    rated_by              varchar(12)  NOT NULL REFERENCES users (id),
    score                 smallint     NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment               text,
    created_at            timestamp(6) NOT NULL DEFAULT now(),
    updated_at            timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT uq_vendor_rating_application UNIQUE (vendor_application_id)
);

CREATE INDEX idx_vendor_ratings_application ON vendor_ratings (vendor_application_id);
CREATE INDEX idx_vendor_ratings_rated_by    ON vendor_ratings (rated_by);
