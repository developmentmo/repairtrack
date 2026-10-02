-- Ownership periods. At most one ACTIVE ownership per vehicle; ended periods are kept forever.

CREATE TABLE vehicle_ownership (
    id         UUID        NOT NULL,
    vehicle_id UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    version    BIGINT      NOT NULL,
    CONSTRAINT pk_vehicle_ownership PRIMARY KEY (id),
    CONSTRAINT fk_vehicle_ownership_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_vehicle_ownership_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_vehicle_ownership_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_vehicle_ownership_ended
        CHECK ((status = 'ACTIVE' AND end_date IS NULL AND ended_at IS NULL)
            OR (status = 'ENDED' AND end_date IS NOT NULL AND ended_at IS NOT NULL)),
    CONSTRAINT ck_vehicle_ownership_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX uk_vehicle_ownership_active ON vehicle_ownership (vehicle_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_vehicle_ownership_vehicle_id ON vehicle_ownership (vehicle_id);
-- "my vehicles"
CREATE INDEX ix_vehicle_ownership_user_id ON vehicle_ownership (user_id);
