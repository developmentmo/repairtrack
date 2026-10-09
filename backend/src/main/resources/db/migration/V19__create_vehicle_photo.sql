-- Vehicle photos (issue #7): one current photo per vehicle and owner, private to that owner. The bytes are in
-- private object storage under storage_key. Never deleted: a new upload marks the previous photo REPLACED.

CREATE TABLE vehicle_photo (
    id             UUID         NOT NULL,
    vehicle_id     UUID         NOT NULL,
    uploaded_by    UUID         NOT NULL,
    storage_key    VARCHAR(200) NOT NULL,
    mime_type      VARCHAR(100) NOT NULL,
    file_size      BIGINT       NOT NULL,
    sha256         CHAR(64)     NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    replaced_at    TIMESTAMPTZ,
    replaced_by_id UUID,
    version        BIGINT       NOT NULL,
    CONSTRAINT pk_vehicle_photo PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_photo_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_vehicle_photo_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_vehicle_photo_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (id),
    -- deferred: the previous photo is marked REPLACED before its successor is inserted (partial unique index below)
    CONSTRAINT fk_vehicle_photo_replaced_by_id FOREIGN KEY (replaced_by_id) REFERENCES vehicle_photo (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_vehicle_photo_mime_type CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_vehicle_photo_file_size CHECK (file_size > 0),
    CONSTRAINT ck_vehicle_photo_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_vehicle_photo_status CHECK (status IN ('ACTIVE', 'REPLACED')),
    CONSTRAINT ck_vehicle_photo_replaced
        CHECK ((status = 'ACTIVE' AND replaced_at IS NULL AND replaced_by_id IS NULL)
            OR (status = 'REPLACED' AND replaced_at IS NOT NULL AND replaced_by_id IS NOT NULL))
);

-- One current photo per vehicle and owner.
CREATE UNIQUE INDEX uk_vehicle_photo_active ON vehicle_photo (vehicle_id, uploaded_by) WHERE status = 'ACTIVE';
CREATE INDEX ix_vehicle_photo_vehicle_id ON vehicle_photo (vehicle_id);
CREATE INDEX ix_vehicle_photo_uploaded_by ON vehicle_photo (uploaded_by);
