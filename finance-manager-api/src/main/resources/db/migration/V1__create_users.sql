CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name varchar(100) NOT NULL,
    email varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_users_email_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_users_password_hash_not_blank CHECK (length(trim(password_hash)) > 0)
);

CREATE UNIQUE INDEX uq_users_email_normalized
    ON users (lower(email));
