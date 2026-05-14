-- V31: Index on email_jobs.status
--
-- The EmailJobPoller queries SELECT ... WHERE status = 'PENDING' every 30 s.
-- Without an index this is a full table scan that grows linearly with the
-- number of sent/failed rows already in the table.

CREATE INDEX idx_email_jobs_status ON email_jobs (status);
