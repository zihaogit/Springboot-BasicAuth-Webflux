-- V20261014.01__Initial_Schema.sql
-- Consolidated Initial Schema & Least-Privilege Application Role

-- 1. Create restricted application user role if it does not already exist
DO
$do$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_user') THEN
      CREATE ROLE app_user WITH LOGIN PASSWORD 'app_user_pass';
   END IF;
END
$do$;

-- 2. Core Users table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    about VARCHAR(255),
    job_title VARCHAR(255),
    languages VARCHAR(255),
    skills VARCHAR(255),
    projects_experiences VARCHAR(255),
    assignments VARCHAR(255),
    profile_pic VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    verified BOOLEAN DEFAULT FALSE,
    fusionauth_user_id VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_fusionauth_user_id ON users(fusionauth_user_id);

-- 3. Roles table
CREATE TABLE IF NOT EXISTS roles (
    role_id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(255) NOT NULL
);

-- 4. Verification OTP table
CREATE TABLE IF NOT EXISTS verification_otp (
    otp_id UUID PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    otp INTEGER,
    token VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 5. Processed Webhook Events table (Idempotency)
CREATE TABLE IF NOT EXISTS processed_webhook_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 6. Permissions & Grants for application runtime user (app_user)
DO
$do$
DECLARE
    curr_db TEXT;
BEGIN
    SELECT current_database() INTO curr_db;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_user', curr_db);
END
$do$;

GRANT USAGE ON SCHEMA public TO app_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO app_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO app_user;
