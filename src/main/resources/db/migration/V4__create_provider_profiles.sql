-- V4: provider profiles, one per PROVIDER user (primary key = user id).
-- Skills, service area, availability, and approval status arrive with provider onboarding.
CREATE TABLE provider_profiles (
    user_id             UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    bio                 VARCHAR(1000),
    years_of_experience INTEGER,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_provider_profiles_experience CHECK (years_of_experience BETWEEN 0 AND 60)
);

-- Providers registered before this migration get an empty profile.
INSERT INTO provider_profiles (user_id)
SELECT id FROM users WHERE role = 'PROVIDER';
