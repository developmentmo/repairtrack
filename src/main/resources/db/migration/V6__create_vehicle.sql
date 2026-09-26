-- Vehicles. The VIN is the permanent identity (unique, immutable); the license plate is a
-- changeable attribute (indexed for lookup, not unique over time). No owner column: ownership
-- is a separate history (vehicle_ownership).

CREATE TABLE vehicle (
    id                      UUID         NOT NULL,
    vin                     VARCHAR(17)  NOT NULL,
    license_plate           VARCHAR(12),
    make                    VARCHAR(100) NOT NULL,
    model                   VARCHAR(100) NOT NULL,
    model_year              INTEGER,
    first_registration_date DATE,
    status                  VARCHAR(20)  NOT NULL,
    registered_by           UUID         NOT NULL,
    registered_by_garage_id UUID,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL,
    CONSTRAINT pk_vehicle PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_vin UNIQUE (vin),
    CONSTRAINT fk_vehicle_registered_by FOREIGN KEY (registered_by) REFERENCES app_user (id),
    CONSTRAINT fk_vehicle_registered_by_garage_id FOREIGN KEY (registered_by_garage_id) REFERENCES garage (id),
    -- ISO 3779: 17 characters, no I, O or Q; stored upper-case
    CONSTRAINT ck_vehicle_vin CHECK (vin ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    -- stored normalized: upper-case, no dashes or spaces
    CONSTRAINT ck_vehicle_license_plate CHECK (license_plate ~ '^[A-Z0-9]{1,12}$'),
    CONSTRAINT ck_vehicle_model_year CHECK (model_year BETWEEN 1886 AND 2100),
    CONSTRAINT ck_vehicle_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX ix_vehicle_license_plate ON vehicle (license_plate);
