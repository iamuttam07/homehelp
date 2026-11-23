-- V12: bookings and their history.
-- A booking copies the service, price, and address at booking time, so later catalog
-- changes or a deleted address never change a past booking.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID           NOT NULL REFERENCES users (id),
    provider_id          UUID           NOT NULL REFERENCES users (id),
    service_id           UUID           NOT NULL REFERENCES services (id),
    service_slug         VARCHAR(80)    NOT NULL,
    service_name         VARCHAR(120)   NOT NULL,
    duration_minutes     INTEGER        NOT NULL,
    price_amount         NUMERIC(10, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    address_line1        VARCHAR(200)   NOT NULL,
    address_line2        VARCHAR(200),
    address_city         VARCHAR(100)   NOT NULL,
    address_postcode     VARCHAR(20)    NOT NULL,
    address_country_code VARCHAR(2)     NOT NULL,
    latitude             DOUBLE PRECISION NOT NULL,
    longitude            DOUBLE PRECISION NOT NULL,
    start_at             TIMESTAMPTZ    NOT NULL,
    end_at               TIMESTAMPTZ    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    notes                VARCHAR(500),
    reschedule_count     INTEGER        NOT NULL DEFAULT 0,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         UUID           REFERENCES users (id) ON DELETE SET NULL,
    cancelled_by_role    VARCHAR(20),
    cancellation_reason  VARCHAR(500),
    late_cancellation    BOOLEAN,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT ck_bookings_times CHECK (start_at < end_at),
    CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_bookings_not_self CHECK (customer_id <> provider_id),
    CONSTRAINT ck_bookings_cancellation CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT ck_bookings_reschedules CHECK (reschedule_count >= 0),
    -- A provider can never have two active bookings that overlap, whichever code path writes them.
    CONSTRAINT ex_bookings_provider_overlap EXCLUDE USING gist (
        provider_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status IN ('CONFIRMED', 'IN_PROGRESS'))
);

CREATE INDEX ix_bookings_customer_start ON bookings (customer_id, start_at);
CREATE INDEX ix_bookings_provider_start ON bookings (provider_id, start_at);

-- Every change to a booking: who did what, and when.
CREATE TABLE booking_events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    type        VARCHAR(20)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    actor_id    UUID         REFERENCES users (id) ON DELETE SET NULL,
    actor_role  VARCHAR(20)  NOT NULL,
    details     VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_booking_events_type CHECK (type IN ('CREATED', 'RESCHEDULED', 'STARTED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX ix_booking_events_booking ON booking_events (booking_id, occurred_at);
