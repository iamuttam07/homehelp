-- V8: provider skills, i.e. which catalog services each provider offers.
-- Deleting a provider removes their skills; a service can't be deleted while
-- providers offer it (deactivate it instead).
CREATE TABLE provider_services (
    provider_id UUID        NOT NULL REFERENCES provider_profiles (user_id) ON DELETE CASCADE,
    service_id  UUID        NOT NULL REFERENCES services (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (provider_id, service_id)
);

-- Matching looks up "providers who offer service X".
CREATE INDEX ix_provider_services_service_id ON provider_services (service_id);
