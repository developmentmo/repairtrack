-- Odometer readings. Never updated: corrected/voided sources void the reading (status VOIDED).
-- source_event_id has no FK on purpose: future sources (RDW) have no repair event.

CREATE TABLE mileage_record (
    id              UUID        NOT NULL,
    vehicle_id      UUID        NOT NULL,
    mileage         INTEGER     NOT NULL,
    recorded_date   DATE        NOT NULL,
    source_type     VARCHAR(20) NOT NULL,
    source_event_id UUID,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    voided_at       TIMESTAMPTZ,
    version         BIGINT      NOT NULL,
    CONSTRAINT pk_mileage_record PRIMARY KEY (id),
    CONSTRAINT fk_mileage_record_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT ck_mileage_record_mileage CHECK (mileage >= 0),
    CONSTRAINT ck_mileage_record_source_type CHECK (source_type IN
        ('OWNER', 'OWNER_DOCUMENT', 'GARAGE', 'VERIFIED_GARAGE', 'MANUFACTURER', 'RDW')),
    CONSTRAINT ck_mileage_record_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_mileage_record_voided CHECK ((status = 'ACTIVE') = (voided_at IS NULL))
);

CREATE INDEX ix_mileage_record_vehicle_id_recorded_date ON mileage_record (vehicle_id, recorded_date);
CREATE INDEX ix_mileage_record_source_event_id ON mileage_record (source_event_id);
