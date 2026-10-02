-- Document metadata. The bytes are in private S3-compatible object storage under storage_key.
-- Append-only: documents are part of the history and are never updated or deleted.

CREATE TABLE document (
    id              UUID         NOT NULL,
    repair_event_id UUID         NOT NULL,
    vehicle_id      UUID         NOT NULL,
    document_type   VARCHAR(30)  NOT NULL,
    file_name       VARCHAR(200) NOT NULL,
    storage_key     VARCHAR(200) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    file_size       BIGINT       NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    uploaded_by     UUID         NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_document PRIMARY KEY (id),
    CONSTRAINT uk_document_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_document_repair_event_id FOREIGN KEY (repair_event_id) REFERENCES repair_event (id),
    CONSTRAINT fk_document_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (id),
    CONSTRAINT ck_document_document_type
        CHECK (document_type IN ('INVOICE', 'WORK_ORDER', 'INSPECTION_REPORT', 'PHOTO', 'OTHER')),
    CONSTRAINT ck_document_mime_type CHECK (mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_document_file_size CHECK (file_size > 0),
    CONSTRAINT ck_document_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_document_repair_event_id ON document (repair_event_id);
-- "which vehicles have documents" (public report, Phase 7)
CREATE INDEX ix_document_vehicle_id ON document (vehicle_id);
