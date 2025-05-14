CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    about VARCHAR(255),
    job_title VARCHAR(255),
    languages VARCHAR(255),
    skills VARCHAR(255),
    projects_experiences VARCHAR(255),
    assignments VARCHAR(255),
    profile_pic VARCHAR(255),
    active BOOLEAN,
    verified BOOL DEFAULT FALSE
);


CREATE TABLE IF NOT EXISTS roles (
    role_id SERIAL PRIMARY KEY,
    user_id SERIAL REFERENCES users(id),
    role VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS verification_token (
    token_id SERIAL PRIMARY KEY,
    user_id SERIAL REFERENCES users(id),
    token VARCHAR(255),
    creation_time TIMESTAMP
);
