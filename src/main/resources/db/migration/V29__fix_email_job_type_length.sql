-- job_type was varchar(30) but BOOKING_CONFIRMED_WITH_CALENDAR is 31 chars.
-- Widen to 50 to accommodate all current and future EmailJobType values.
ALTER TABLE email_jobs ALTER COLUMN job_type TYPE varchar(50);
