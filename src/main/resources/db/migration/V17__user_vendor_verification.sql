-- V17: Platform-level vendor verification.
--
-- This is separate from vendor_applications, which are per-event proposals.
-- These columns drive the admin Vendors tab and the user's vendor console state.

ALTER TABLE users
    ADD COLUMN vendor_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN vendor_verification_status varchar(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN vendor_service_type varchar(100),
    ADD COLUMN vendor_profile_description text,
    ADD COLUMN vendor_verification_rejection_reason text,
    ADD COLUMN vendor_verification_submitted_at timestamp(6),
    ADD COLUMN vendor_verified_at timestamp(6);

CREATE INDEX idx_users_vendor_verification_status
    ON users (vendor_verification_status, vendor_verification_submitted_at);
