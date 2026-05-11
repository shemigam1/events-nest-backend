-- V11: Monnify payment fields (M3.2 Phase 1).
--
-- Two additive changes that prepare bookings for real payment flow:
--
--   1. monnify_transaction_ref — Monnify's idempotency key. Stored on the
--      booking so a duplicate webhook (or a manual /verify retry) can be
--      detected and short-circuited. Unique constraint backs this — a
--      second INSERT with the same reference fails at the DB layer, which
--      is the strongest form of replay protection.
--
--   2. paid_at — set when the booking flips from PENDING to PAID via the
--      webhook or verify path. Lets us measure checkout-to-confirm latency
--      and surface "paid X minutes after booking" in the dashboard later.
--
-- PaymentStatus enum gains PENDING and FAILED at the application layer
-- (Java enum extension — no SQL change needed since the column is varchar
-- storing @Enumerated(STRING) values).
--
-- Existing rows: all current bookings are paymentStatus = 'PAID' with
-- payment_reference = 'SIMULATED-<uuid>'. They keep monnify_transaction_ref
-- NULL — the unique constraint allows multiple NULLs in Postgres.

ALTER TABLE bookings
    ADD COLUMN monnify_transaction_ref varchar(255);

ALTER TABLE bookings
    ADD CONSTRAINT uk_bookings_monnify_ref UNIQUE (monnify_transaction_ref);

ALTER TABLE bookings
    ADD COLUMN paid_at timestamp(6);

-- Helps the webhook handler look up by Monnify reference quickly. The UNIQUE
-- constraint already creates an index, but explicit is clearer.
CREATE INDEX idx_bookings_payment_status ON bookings (payment_status);
