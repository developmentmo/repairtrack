-- The core of RepairTrack: one row per history entry. Rows are never deleted (void instead) and
-- never silently changed (corrections are recorded in repair_correction).

CREATE TABLE repair_event (
    id                  UUID          NOT NULL,
    vehicle_id          UUID          NOT NULL,
    garage_id           UUID,
    created_by          UUID          NOT NULL,
    event_type          VARCHAR(20)   NOT NULL,
    source_type         VARCHAR(20)   NOT NULL,
    verification_status VARCHAR(20)   NOT NULL,
    event_date          DATE          NOT NULL,
    mileage             INTEGER       NOT NULL,
    title               VARCHAR(150)  NOT NULL,
    description         VARCHAR(5000),
    status              VARCHAR(20)   NOT NULL,
    voided_at           TIMESTAMPTZ,
    voided_by           UUID,
    void_reason         VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL,
    CONSTRAINT pk_repair_event PRIMARY KEY (id),
    CONSTRAINT fk_repair_event_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_repair_event_garage_id FOREIGN KEY (garage_id) REFERENCES garage (id),
    CONSTRAINT fk_repair_event_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_repair_event_voided_by FOREIGN KEY (voided_by) REFERENCES app_user (id),
    CONSTRAINT ck_repair_event_event_type CHECK (event_type IN
        ('MAINTENANCE', 'REPAIR', 'INSPECTION', 'TYRE_CHANGE', 'DAMAGE_REPAIR', 'APK', 'RECALL', 'OTHER')),
    CONSTRAINT ck_repair_event_mileage CHECK (mileage BETWEEN 0 AND 2000000),
    CONSTRAINT ck_repair_event_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_repair_event_voided
        CHECK ((status = 'ACTIVE' AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
            OR (status = 'VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL)),
    -- Defence in depth: only the combinations the VerificationService can produce are storable.
    CONSTRAINT ck_repair_event_provenance CHECK (
           (source_type = 'OWNER'           AND verification_status = 'UNVERIFIED'      AND garage_id IS NULL)
        OR (source_type = 'OWNER_DOCUMENT'  AND verification_status = 'DOCUMENTED'      AND garage_id IS NULL)
        OR (source_type = 'GARAGE'          AND verification_status = 'GARAGE_VERIFIED' AND garage_id IS NOT NULL)
        OR (source_type = 'VERIFIED_GARAGE' AND verification_status = 'GARAGE_VERIFIED' AND garage_id IS NOT NULL)
        OR (source_type IN ('RDW', 'MANUFACTURER') AND verification_status = 'OFFICIAL_SOURCE'))
);

CREATE INDEX ix_repair_event_vehicle_id ON repair_event (vehicle_id);
CREATE INDEX ix_repair_event_vehicle_id_event_date ON repair_event (vehicle_id, event_date);
CREATE INDEX ix_repair_event_vehicle_id_mileage ON repair_event (vehicle_id, mileage);
CREATE INDEX ix_repair_event_garage_id ON repair_event (garage_id);
