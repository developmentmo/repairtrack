-- Users and their platform-wide roles.
-- Users are never physically deleted (status DELETED) so historical records keep a valid author.
-- Garage roles (GARAGE_ADMIN, MECHANIC) are NOT stored here; they belong to garage membership (garage_user).

CREATE TABLE app_user (
    id            UUID         NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    -- the application stores normalized (lower-case) emails; enforce it so uniqueness is case-insensitive
    CONSTRAINT ck_app_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'))
);

CREATE TABLE app_user_role (
    user_id UUID        NOT NULL,
    role    VARCHAR(30) NOT NULL,
    CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_app_user_role_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_app_user_role_role CHECK (role IN ('OWNER', 'SYSTEM_ADMIN'))
);
