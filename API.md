# API

## Conventions

- Business endpoints live under `/api/v1`.
- JSON only. Request/response bodies are DTO records; JPA entities are never exposed.
- Timestamps: ISO-8601 UTC (`2026-09-20T10:30:00Z`). Dates: ISO-8601 (`2026-09-20`).
- Historical records are never deleted through the API; they are voided or corrected.
- Authentication: `Authorization: Bearer <accessToken>` on every endpoint except those marked *public*.

## Error format

Every error uses the same body:

```json
{
  "timestamp": "2026-09-20T10:30:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "title: must not be blank",
  "path": "/api/v1/..."
}
```

Clients should branch on `code`, never on `message`.

| Code | HTTP | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation failed on a request body |
| `MALFORMED_REQUEST` | 400 | Body missing or not valid JSON |
| `INVALID_PARAMETER` | 400 | Path/query parameter has the wrong type |
| `MISSING_PARAMETER` | 400 | Required query parameter missing |
| `UNAUTHORIZED` | 401 | Missing, invalid or expired access token; blocked/deleted account (`WWW-Authenticate: Bearer`) |
| `FORBIDDEN` | 403 | Authenticated but not allowed |
| `NOT_FOUND` | 404 | No such endpoint/resource |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported (with `Allow` header) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type not supported |
| `INTERNAL_ERROR` | 500 | Unexpected error (details only in server logs) |

Business codes:

| Code | HTTP | Raised by |
|---|---|---|
| `INVALID_PASSWORD` | 400 | Password violates the policy (min 12 characters, max 72 bytes) |
| `INVALID_CREDENTIALS` | 401 | Login: wrong email or password (identical for both) |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh: unknown, expired, revoked or replayed token |
| `ACCOUNT_BLOCKED` | 403 | Login/refresh of a blocked account (login: only after a correct password) |
| `USER_NOT_FOUND` | 404 | |
| `EMAIL_ALREADY_REGISTERED` | 409 | Registration with an existing email (case-insensitive) |

## Authentication

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | public | `{email, password, firstName, lastName}` | 201 `UserResponse` |
| POST | `/api/v1/auth/login` | public | `{email, password}` | 200 `TokenResponse` |
| POST | `/api/v1/auth/refresh` | public | `{refreshToken}` | 200 `TokenResponse` (new refresh token!) |
| POST | `/api/v1/auth/logout` | public | `{refreshToken}` | 204 (idempotent) |

`TokenResponse`:
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJ...",
  "accessTokenExpiresAt": "2026-09-25T10:15:00Z",
  "refreshToken": "q3Jx...",
  "refreshTokenExpiresAt": "2026-10-25T10:00:00Z"
}
```

Client rules:
- Refresh tokens are **single-use**. Always store the refresh token from the latest response.
- Reusing an old refresh token revokes the entire session (all its tokens); the user must log in again.
- Refresh proactively shortly before `accessTokenExpiresAt`, or on a 401 from a protected endpoint.

Registration never accepts roles or status; every new account is an active `OWNER`.

## Users

| Method | Path | Access | Response |
|---|---|---|---|
| GET | `/api/v1/users/me` | authenticated | 200 `UserResponse` |

`UserResponse`: `{id, email, firstName, lastName, status, roles, createdAt}`. Never contains credentials.

## Operational

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/actuator/health` | public | Overall health (includes database) |
| GET | `/actuator/health/liveness` | public | Liveness probe |
| GET | `/actuator/health/readiness` | public | Readiness probe |
| GET | `/actuator/info` | public | Build/app info |
