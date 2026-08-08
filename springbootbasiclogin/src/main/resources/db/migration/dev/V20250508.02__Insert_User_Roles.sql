-- Insert into users
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
        '{bcrypt}$2a$12$M4mMPMJ/oDkW8nIoSS5gA.mqkYViTAPZPbh3HjK/g5wCoLreeUXj6', -- john12345
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
        '{bcrypt}$2a$12$7nyVIeYRr3QzFTT.BanJluTFSu2018rZoP14JI3eqthlBN8F9U/HW', -- admin12345
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

-- Insert into roles (role_id is auto-generated)
INSERT INTO roles (
    user_id,
    role
) VALUES
    (
        (SELECT id FROM users WHERE username = 'JohnDoe'), -- Reference JohnDoe's user_id
        'USER'
    ),
    (
        (SELECT id FROM users WHERE username = 'admin'), -- Reference admin's user_id
        'ADMIN'
    );
