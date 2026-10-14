-- V20261014.02__Insert_User_Roles.sql
-- Development seed data for Users and Roles

-- Insert seed users
INSERT INTO users (
    username,
    password,
    first_name,
    last_name,
    email,
    about,
    job_title,
    languages,
    skills,
    projects_experiences,
    assignments,
    profile_pic,
    active,
    verified
) VALUES
    (
        'JohnDoe',
        '$2a$10$uUrOkbePqGEjlcwEpd/1y.K6/PDFytlGmrCr/Y6aQA9/SKjfJUmlm',
        'John',
        'Doe',
        'john@example.com',
        'Generated User account 1',
        'Junior',
        'English, Spanish',
        'Java, Python',
        'Fintech mobile app',
        'Maybank',
        'https://image/example.com',
        TRUE,
        TRUE
    ),
    (
        'admin',
        '$2a$12$7nyVIeYRr3QzFTT.BanJluTFSu2018rZoP14JI3eqthlBN8F9U/HW',
        'Admin',
        'One',
        'admin@example.com',
        'Generated Admin account 1',
        'Admin',
        'English',
        'SQL',
        'Administration of software',
        'Softbank',
        'https://image/admin/example.com',
        TRUE,
        TRUE
    );

-- Insert seed roles
INSERT INTO roles (
    user_id,
    role
) VALUES
    (
        (SELECT id FROM users WHERE username = 'JohnDoe'),
        'USER'
    ),
    (
        (SELECT id FROM users WHERE username = 'admin'),
        'ADMIN'
    );
