-- V9: provider service area, a base point plus a radius.
-- As with addresses (V5, ADR 0004), clients send coordinates and PostGIS derives
-- `base_location`; it stays NULL until the provider sets an area.
ALTER TABLE provider_profiles
    ADD COLUMN base_latitude     DOUBLE PRECISION,
    ADD COLUMN base_longitude    DOUBLE PRECISION,
    ADD COLUMN service_radius_km INTEGER,
    ADD COLUMN base_location     GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(base_longitude, base_latitude), 4326)::geography) STORED,

    -- All three are set together, or none.
    ADD CONSTRAINT ck_provider_profiles_area_complete CHECK (
        (base_latitude IS NULL AND base_longitude IS NULL AND service_radius_km IS NULL)
        OR (base_latitude IS NOT NULL AND base_longitude IS NOT NULL AND service_radius_km IS NOT NULL)),
    ADD CONSTRAINT ck_provider_profiles_base_latitude CHECK (base_latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_provider_profiles_base_longitude CHECK (base_longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_provider_profiles_radius CHECK (service_radius_km BETWEEN 1 AND 50);

CREATE INDEX ix_provider_profiles_base_location ON provider_profiles USING GIST (base_location);
