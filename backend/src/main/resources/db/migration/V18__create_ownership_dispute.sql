-- Ownership disputes (Phase 11). Nothing here is ever deleted: decided disputes and their evidence stay.

-- An upheld dispute ends the contested ownership as REVOKED (kept in the history, not counted as an owner).
ALTER TABLE vehicle_ownership DROP CONSTRAINT ck_vehicle_ownership_status;
ALTER TABLE vehicle_ownership DROP CONSTRAINT ck_vehicle_ownership_ended;
ALTER TABLE vehicle_ownership
    ADD CONSTRAINT ck_vehicle_ownership_status CHECK (status IN ('ACTIVE', 'ENDED', 'REVOKED'));
ALTER TABLE vehicle_ownership
    ADD CONSTRAINT ck_vehicle_ownership_ended
        CHECK ((status = 'ACTIVE' AND end_date IS NULL AND ended_at IS NULL)
            OR (status IN ('ENDED', 'REVOKED') AND end_date IS NOT NULL AND ended_at IS NOT NULL));

CREATE TABLE ownership_dispute (
    id                     UUID          NOT NULL,
    vehicle_id             UUID          NOT NULL,
    claimant_id            UUID          NOT NULL,
    owner_id               UUID          NOT NULL,
    contested_ownership_id UUID          NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    claimant_statement     VARCHAR(2000) NOT NULL,
    owner_statement        VARCHAR(2000),
    response_deadline      TIMESTAMPTZ   NOT NULL,
    owner_responded_at     TIMESTAMPTZ,
    decided_by             UUID,
    decided_at             TIMESTAMPTZ,
    decision_note          VARCHAR(2000),
    new_owner_since        DATE,
    created_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_ownership_dispute PRIMARY KEY (id),
    CONSTRAINT fk_ownership_dispute_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_ownership_dispute_claimant_id FOREIGN KEY (claimant_id) REFERENCES app_user (id),
    CONSTRAINT fk_ownership_dispute_owner_id FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_ownership_dispute_decided_by FOREIGN KEY (decided_by) REFERENCES app_user (id),
    CONSTRAINT fk_ownership_dispute_contested_ownership_id
        FOREIGN KEY (contested_ownership_id) REFERENCES vehicle_ownership (id),
    CONSTRAINT ck_ownership_dispute_status
        CHECK (status IN ('OPEN', 'AWAITING_REVIEW', 'UPHELD', 'REJECTED')),
    CONSTRAINT ck_ownership_dispute_parties CHECK (claimant_id <> owner_id),
    CONSTRAINT ck_ownership_dispute_response
        CHECK ((owner_statement IS NULL) = (owner_responded_at IS NULL)),
    CONSTRAINT ck_ownership_dispute_decided
        CHECK ((status IN ('UPHELD', 'REJECTED'))
            = (decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_note IS NOT NULL)),
    CONSTRAINT ck_ownership_dispute_new_owner_since CHECK ((status = 'UPHELD') = (new_owner_since IS NOT NULL))
);

-- One open dispute per claimant and vehicle.
CREATE UNIQUE INDEX uk_ownership_dispute_open ON ownership_dispute (vehicle_id, claimant_id)
    WHERE status IN ('OPEN', 'AWAITING_REVIEW');
CREATE INDEX ix_ownership_dispute_vehicle_id ON ownership_dispute (vehicle_id);
CREATE INDEX ix_ownership_dispute_claimant_id ON ownership_dispute (claimant_id);
CREATE INDEX ix_ownership_dispute_owner_id ON ownership_dispute (owner_id);
CREATE INDEX ix_ownership_dispute_status ON ownership_dispute (status);

-- Evidence files of both parties. Private: only system admins can download them. Append-only.
CREATE TABLE dispute_evidence (
    id           UUID         NOT NULL,
    dispute_id   UUID         NOT NULL,
    party        VARCHAR(20)  NOT NULL,
    submitted_by UUID         NOT NULL,
    file_name    VARCHAR(200) NOT NULL,
    storage_key  VARCHAR(200) NOT NULL,
    mime_type    VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_dispute_evidence PRIMARY KEY (id),
    CONSTRAINT uk_dispute_evidence_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_dispute_evidence_dispute_id FOREIGN KEY (dispute_id) REFERENCES ownership_dispute (id),
    CONSTRAINT fk_dispute_evidence_submitted_by FOREIGN KEY (submitted_by) REFERENCES app_user (id),
    CONSTRAINT ck_dispute_evidence_party CHECK (party IN ('CLAIMANT', 'OWNER')),
    CONSTRAINT ck_dispute_evidence_mime_type CHECK (mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_dispute_evidence_file_size CHECK (file_size > 0),
    CONSTRAINT ck_dispute_evidence_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_dispute_evidence_dispute_id ON dispute_evidence (dispute_id);
