-- V10: provider weekly availability.
-- Windows are wall-clock times in the provider's time zone (e.g. Monday 09:00-17:00
-- Europe/London), so they stay correct across daylight-saving changes.
ALTER TABLE provider_profiles
    ADD COLUMN time_zone VARCHAR(50) NOT NULL DEFAULT 'Europe/London';

CREATE TABLE provider_availability (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID        NOT NULL REFERENCES provider_profiles (user_id) ON DELETE CASCADE,
    day_of_week INTEGER     NOT NULL,  -- ISO 8601: 1 = Monday ... 7 = Sunday
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_provider_availability_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_provider_availability_order CHECK (start_time < end_time),
    -- Whole quarter hours only, matching 15-minute booking slots.
    CONSTRAINT ck_provider_availability_quarter_hours CHECK (
        EXTRACT(SECOND FROM start_time) = 0 AND EXTRACT(MINUTE FROM start_time)::int % 15 = 0
        AND EXTRACT(SECOND FROM end_time) = 0 AND EXTRACT(MINUTE FROM end_time)::int % 15 = 0)
);

CREATE INDEX ix_provider_availability_provider_day ON provider_availability (provider_id, day_of_week);
