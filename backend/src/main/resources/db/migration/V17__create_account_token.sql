-- Email verification and password reset (Phase 9b).
-- Accounts that existed before verification was introduced count as verified.

ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE app_user SET email_verified_at = created_at;

-- Single-use tokens sent by email. Only the SHA-256 hash is stored.
CREATE TABLE account_token (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    purpose    VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    version    BIGINT      NOT NULL,
    CONSTRAINT pk_account_token PRIMARY KEY (id),
    CONSTRAINT uk_account_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_token_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_account_token_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_account_token_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_account_token_user_id_purpose ON account_token (user_id, purpose);
