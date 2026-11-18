-- V5: user addresses (where services take place).
-- Clients send latitude/longitude; PostGIS derives `location` from them, so the
-- two can never disagree. `location` is what distance queries will use.
CREATE TABLE addresses (
    id           UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label        VARCHAR(50),
    line1        VARCHAR(200)     NOT NULL,
    line2        VARCHAR(200),
    city         VARCHAR(100)     NOT NULL,
    postcode     VARCHAR(20)      NOT NULL,
    country_code VARCHAR(2)       NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    location     GEOGRAPHY(Point, 4326)
                 GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    is_default   BOOLEAN          NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    version      BIGINT           NOT NULL DEFAULT 0,

    CONSTRAINT ck_addresses_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_addresses_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_addresses_country_code CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX ix_addresses_user_id ON addresses (user_id);
CREATE INDEX ix_addresses_location ON addresses USING GIST (location);

-- At most one default address per user.
CREATE UNIQUE INDEX uq_addresses_one_default_per_user ON addresses (user_id) WHERE is_default;
