-- V6: service catalog. A category (e.g. Plumbing) groups services (e.g. Leak repair).
-- Slugs are stable, URL-friendly identifiers used by the API and the frontend.
-- Inactive rows are hidden from browsing but kept, so past bookings can still refer to them.
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(60)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    icon        VARCHAR(50),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE services (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id      UUID          NOT NULL REFERENCES categories (id),
    slug             VARCHAR(80)   NOT NULL,
    name             VARCHAR(120)  NOT NULL,
    description      VARCHAR(1000),
    base_price       NUMERIC(10, 2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'GBP',
    duration_minutes INTEGER       NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_services_slug UNIQUE (slug),
    CONSTRAINT ck_services_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_services_base_price CHECK (base_price >= 0),
    CONSTRAINT ck_services_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Bookings are made in 15-minute slots, up to a 12-hour day.
    CONSTRAINT ck_services_duration CHECK (duration_minutes BETWEEN 15 AND 720 AND duration_minutes % 15 = 0)
);

CREATE INDEX ix_services_category_id ON services (category_id);
