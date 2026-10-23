-- Smart Parking Lot - relational schema (PostgreSQL dialect)

CREATE TABLE parking_lot (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE parking_floor (
    id          BIGSERIAL PRIMARY KEY,
    lot_id      BIGINT  NOT NULL REFERENCES parking_lot(id),
    level       INT     NOT NULL,
    UNIQUE (lot_id, level)
);

CREATE TABLE parking_spot (
    id          BIGSERIAL PRIMARY KEY,
    floor_id    BIGINT      NOT NULL REFERENCES parking_floor(id),
    spot_number INT         NOT NULL,
    size        VARCHAR(10) NOT NULL CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    is_occupied BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,      -- maintenance / closed spots
    version     BIGINT      NOT NULL DEFAULT 0,         -- optimistic locking
    UNIQUE (floor_id, spot_number)
);
-- Fast lookup of free spots by size, nearest floor / lowest number first.
CREATE INDEX idx_spot_free ON parking_spot (size, floor_id, spot_number)
    WHERE is_occupied = FALSE AND is_active = TRUE;

CREATE TABLE vehicle (
    id            BIGSERIAL PRIMARY KEY,
    license_plate VARCHAR(15) NOT NULL UNIQUE,
    vehicle_type  VARCHAR(12) NOT NULL CHECK (vehicle_type IN ('MOTORCYCLE', 'CAR', 'BUS'))
);

CREATE TABLE rate_card (
    id             BIGSERIAL PRIMARY KEY,
    vehicle_type   VARCHAR(12)   NOT NULL,
    hourly_rate    NUMERIC(10,2) NOT NULL CHECK (hourly_rate >= 0),
    daily_cap      NUMERIC(10,2) NOT NULL CHECK (daily_cap >= 0),
    grace_minutes  INT           NOT NULL DEFAULT 10,
    effective_from TIMESTAMPTZ   NOT NULL,
    effective_to   TIMESTAMPTZ,
    UNIQUE (vehicle_type, effective_from)
);

CREATE TABLE parking_ticket (
    id          UUID PRIMARY KEY,
    vehicle_id  BIGINT        NOT NULL REFERENCES vehicle(id),
    spot_id     BIGINT        NOT NULL REFERENCES parking_spot(id),
    entry_time  TIMESTAMPTZ   NOT NULL,
    exit_time   TIMESTAMPTZ,
    fee         NUMERIC(10,2),
    status      VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    CHECK (exit_time IS NULL OR exit_time >= entry_time),
    CHECK ((status = 'ACTIVE' AND exit_time IS NULL) OR (status = 'CLOSED' AND exit_time IS NOT NULL))
);
-- Concurrency guards enforced by the database itself:
--   a vehicle can have only one active session, and a spot only one active vehicle.
CREATE UNIQUE INDEX uq_active_ticket_vehicle ON parking_ticket (vehicle_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_active_ticket_spot    ON parking_ticket (spot_id)    WHERE status = 'ACTIVE';
CREATE INDEX idx_ticket_entry ON parking_ticket (entry_time);

-- Allocation in a single transaction (multiple gates / app instances safe):
--   SELECT s.id FROM parking_spot s JOIN parking_floor f ON f.id = s.floor_id
--    WHERE s.size = :size AND s.is_occupied = FALSE AND s.is_active = TRUE
--    ORDER BY f.level, s.spot_number
--    LIMIT 1 FOR UPDATE SKIP LOCKED;
--   UPDATE parking_spot SET is_occupied = TRUE, version = version + 1 WHERE id = :id;
--   INSERT INTO parking_ticket (...) VALUES (...);   -- unique index rejects duplicate entry
