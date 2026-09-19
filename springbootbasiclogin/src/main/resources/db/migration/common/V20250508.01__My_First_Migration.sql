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
    active BOOLEAN,
    verified BOOL DEFAULT FALSE,
    fusionauth_user_id VARCHAR(64)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_fusionauth_user_id ON users(fusionauth_user_id);

CREATE TABLE IF NOT EXISTS roles (
    role_id SERIAL PRIMARY KEY,
    user_id SERIAL REFERENCES users(id),
    role VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS verification_otp (
    otp_id UUID PRIMARY KEY,
    user_id SERIAL REFERENCES users(id),
    otp INTEGER,
    token VARCHAR(255),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS processed_webhook_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
