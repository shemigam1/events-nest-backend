-- ─── V19: Fix vendor_ratings.score column type ────────────────────────────────
-- Hibernate validates `int` fields against JDBC Types#INTEGER (not SMALLINT).
-- Change score from smallint (int2) to integer so schema validation passes.
-- The CHECK constraint (score BETWEEN 1 AND 5) is preserved automatically.

ALTER TABLE vendor_ratings
    ALTER COLUMN score TYPE integer;
