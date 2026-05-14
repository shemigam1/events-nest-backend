-- V30: Remove Monnify-specific naming from the bookings table.
--
-- Rename monnify_transaction_ref → payment_gateway_ref so the column is
-- provider-agnostic. The UNIQUE constraint and index are recreated under
-- the new names. Existing data (NULLs for immediate-confirm bookings,
-- SEED-TXN-* values for seeded rows) is preserved unchanged.

ALTER TABLE bookings
    RENAME COLUMN monnify_transaction_ref TO payment_gateway_ref;

ALTER TABLE bookings
    RENAME CONSTRAINT uk_bookings_monnify_ref TO uk_bookings_payment_gateway_ref;
