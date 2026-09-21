ALTER TABLE users
    ADD COLUMN onboarding_version integer DEFAULT 1 NOT NULL;

ALTER TABLE users
    ALTER COLUMN onboarding_version SET DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_onboarding_version_non_negative
        CHECK (onboarding_version >= 0);