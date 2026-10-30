-- V20261029.01__Create_Bulk_Jobs_Table.sql
-- Bulk Jobs & Error Tracking Schema

CREATE TABLE IF NOT EXISTS bulk_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE,
    status VARCHAR(32) NOT NULL,
    submitted_by VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    total_records INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    fail_count INTEGER NOT NULL DEFAULT 0,
    continue_on_error BOOLEAN DEFAULT TRUE,
    payload_json TEXT
);

CREATE TABLE IF NOT EXISTS bulk_job_errors (
    id SERIAL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES bulk_jobs(job_id) ON DELETE CASCADE,
    record_index INTEGER NOT NULL,
    client_ref_id VARCHAR(255),
    field VARCHAR(255),
    message TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bulk_job_errors_job_id ON bulk_job_errors(job_id);
CREATE INDEX IF NOT EXISTS idx_bulk_jobs_status ON bulk_jobs(status);
CREATE INDEX IF NOT EXISTS idx_bulk_jobs_submitted_at ON bulk_jobs(submitted_at);
CREATE INDEX IF NOT EXISTS idx_bulk_jobs_idempotency_key ON bulk_jobs(idempotency_key);

-- Grant permissions to app_user if role exists
DO
$do$
BEGIN
   IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_user') THEN
      GRANT ALL PRIVILEGES ON TABLE bulk_jobs TO app_user;
      GRANT ALL PRIVILEGES ON TABLE bulk_job_errors TO app_user;
      GRANT ALL PRIVILEGES ON SEQUENCE bulk_job_errors_id_seq TO app_user;
   END IF;
END
$do$;
