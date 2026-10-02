-- Provenance changes of history records AFTER creation, with their evidence
-- (e.g. OWNER/UNVERIFIED -> OWNER_DOCUMENT/DOCUMENTED because an invoice was attached).
-- The initial provenance is on repair_event itself. Append-only.

CREATE TABLE verification (
    id                   UUID        NOT NULL,
    repair_event_id      UUID        NOT NULL,
    previous_source_type VARCHAR(20) NOT NULL,
    previous_status      VARCHAR(20) NOT NULL,
    new_source_type      VARCHAR(20) NOT NULL,
    new_status           VARCHAR(20) NOT NULL,
    method               VARCHAR(30) NOT NULL,
    evidence_id          UUID,
    changed_by           UUID        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_verification PRIMARY KEY (id),
    CONSTRAINT fk_verification_repair_event_id FOREIGN KEY (repair_event_id) REFERENCES repair_event (id),
    CONSTRAINT fk_verification_changed_by FOREIGN KEY (changed_by) REFERENCES app_user (id),
    CONSTRAINT ck_verification_method CHECK (method IN ('DOCUMENT_ATTACHED'))
);

CREATE INDEX ix_verification_repair_event_id ON verification (repair_event_id);
