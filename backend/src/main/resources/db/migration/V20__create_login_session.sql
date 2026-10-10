-- Login sessions (idle timeout). One row per login; its id is the family_id of that login's refresh tokens and the
-- "sid" claim of its access tokens. A session whose last_activity_at is older than the idle timeout is over.

CREATE TABLE login_session (
    id               UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    CONSTRAINT pk_login_session PRIMARY KEY (id),
    CONSTRAINT fk_login_session_user_id FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_login_session_user_id ON login_session (user_id);

-- Logins that still have a usable refresh token become sessions. Their last activity is their last refresh, so
-- sessions that have been idle longer than the timeout are over as soon as the new version runs.
INSERT INTO login_session (id, user_id, created_at, last_activity_at)
SELECT family_id, user_id, MIN(created_at), MAX(created_at)
FROM refresh_token
WHERE revoked_at IS NULL AND expires_at > now()
GROUP BY family_id, user_id;
