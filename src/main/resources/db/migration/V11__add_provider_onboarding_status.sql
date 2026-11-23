-- V11: provider onboarding and approval.
--   ONBOARDING      filling in skills, service area, availability
--   PENDING_REVIEW  submitted; waiting for an admin
--   APPROVED        can receive jobs
--   REJECTED        sent back with a reason; the provider can fix and resubmit
ALTER TABLE provider_profiles
    ADD COLUMN status           VARCHAR(20)  NOT NULL DEFAULT 'ONBOARDING',
    ADD COLUMN submitted_at     TIMESTAMPTZ,
    ADD COLUMN reviewed_at      TIMESTAMPTZ,
    ADD COLUMN reviewed_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejection_reason VARCHAR(500),

    ADD CONSTRAINT ck_provider_profiles_status
        CHECK (status IN ('ONBOARDING', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    -- A reason is required when, and only when, the profile is rejected.
    ADD CONSTRAINT ck_provider_profiles_rejection_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL));

-- Admins list providers by status, oldest submission first.
CREATE INDEX ix_provider_profiles_status_submitted ON provider_profiles (status, submitted_at);
