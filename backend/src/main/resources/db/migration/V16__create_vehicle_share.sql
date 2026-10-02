-- Share links for the public vehicle history. Only the SHA-256 hash of the token is stored.
-- A link works while: not revoked, not expired, and its creator is still the vehicle's owner.

CREATE TABLE vehicle_share (
    id                UUID        NOT NULL,
    vehicle_id        UUID        NOT NULL,
    token_hash        VARCHAR(64) NOT NULL,
    include_documents BOOLEAN     NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    created_by        UUID        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    revoked_by        UUID,
    access_count      BIGINT      NOT NULL DEFAULT 0,
    last_accessed_at  TIMESTAMPTZ,
    version           BIGINT      NOT NULL,
    CONSTRAINT pk_vehicle_share PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_share_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_vehicle_share_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_vehicle_share_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_vehicle_share_revoked_by FOREIGN KEY (revoked_by) REFERENCES app_user (id),
    CONSTRAINT ck_vehicle_share_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_vehicle_share_validity CHECK (expires_at > created_at),
    CONSTRAINT ck_vehicle_share_revoked CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

CREATE INDEX ix_vehicle_share_vehicle_id ON vehicle_share (vehicle_id);
