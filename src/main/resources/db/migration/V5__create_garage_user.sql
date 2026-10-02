-- Membership of users in garages with a garage-scoped role.
-- Rows are never deleted: ended memberships stay as history (status ENDED).
-- At most one ACTIVE membership per (garage, user); re-joining creates a new row.

CREATE TABLE garage_user (
    id         UUID        NOT NULL,
    garage_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    added_by   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    ended_by   UUID,
    version    BIGINT      NOT NULL,
    CONSTRAINT pk_garage_user PRIMARY KEY (id),
    CONSTRAINT fk_garage_user_garage_id FOREIGN KEY (garage_id) REFERENCES garage (id),
    CONSTRAINT fk_garage_user_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_garage_user_added_by FOREIGN KEY (added_by) REFERENCES app_user (id),
    CONSTRAINT fk_garage_user_ended_by FOREIGN KEY (ended_by) REFERENCES app_user (id),
    CONSTRAINT ck_garage_user_role CHECK (role IN ('GARAGE_ADMIN', 'MECHANIC')),
    CONSTRAINT ck_garage_user_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_garage_user_ended
        CHECK ((status = 'ACTIVE' AND ended_at IS NULL AND ended_by IS NULL)
            OR (status = 'ENDED' AND ended_at IS NOT NULL AND ended_by IS NOT NULL))
);

CREATE UNIQUE INDEX uk_garage_user_active_membership ON garage_user (garage_id, user_id) WHERE status = 'ACTIVE';
-- "which garages does this user work at?" (authorization checks, /garages/mine)
CREATE INDEX ix_garage_user_user_id ON garage_user (user_id);
CREATE INDEX ix_garage_user_garage_id ON garage_user (garage_id);
