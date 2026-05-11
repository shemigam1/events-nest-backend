-- V12: Optimistic locking on bookings (M3.2.4).
--
-- Protects concurrent finalize-payment calls. Two Monnify webhooks for the
-- same transaction reference (one direct, one retry after timeout) race to
-- flip the booking from PENDING to PAID. Without a version column the
-- second writer wins silently — both code paths fire, both issue tickets,
-- both publish to Kafka. With @Version the second commit's UPDATE returns
-- 0 rows and Hibernate throws ObjectOptimisticLockingFailureException;
-- when Monnify retries again it sees PAID and the idempotency short-circuit
-- in finalizeBookingPayment returns 200.

ALTER TABLE bookings
    ADD COLUMN version integer NOT NULL DEFAULT 0;
