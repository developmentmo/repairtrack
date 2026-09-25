# Security

## Authentication

| Aspect | Design |
|---|---|
| Access token | JWT, HS256, 15 min (`JWT_ACCESS_TOKEN_TTL`). Claims: `sub` (user ID), `iss`, `iat`, `exp`, `jti`. No email, name or roles. |
| Signing key | `JWT_SECRET`, >= 32 bytes; the app refuses to start with a weaker or missing secret (outside `local`). |
| Validation | Signature, expiry (60 s clock skew), issuer. |
| Refresh token | Opaque 256-bit random value, 30 days (`REFRESH_TOKEN_TTL`). Only its SHA-256 hash is stored. |
| Rotation | Every refresh revokes the used token and issues a new one in the same *family* (one family per login). |
| Reuse detection | Presenting a revoked token revokes the whole family (assumed theft). Committed even though the request fails. |
| Concurrency | Refresh takes a row lock on the token, so parallel refreshes cannot both succeed. |
| Logout | Revokes the token's whole family. Public endpoint: holding the refresh token is the authorization. Idempotent. |
| Passwords | BCrypt via Spring's `DelegatingPasswordEncoder` (hashes are prefixed `{bcrypt}`), so the algorithm can later move to Argon2 without a migration. Policy: min 12 characters, max 72 bytes (BCrypt limit; longer input is rejected, not truncated). |
| Transport | Tokens in the `Authorization` header / JSON body, never in cookies. The API is stateless (no HTTP session), so CSRF protection is disabled on purpose. |

### Per-request user loading

Every authenticated request loads the user by ID (one primary-key query):

- a **blocked or deleted** user is rejected immediately, even with an unexpired access token;
- **roles come from the database**, never from the token, so granting or revoking a role needs no re-login.

### Account enumeration

- Login returns the same `INVALID_CREDENTIALS` for unknown email and wrong password, and runs a
  password hash comparison in both cases so response times are similar.
- `ACCOUNT_BLOCKED` is only returned after a correct password.
- Registration does reveal whether an email is taken (409). Accepted trade-off for V1; it can be replaced by
  an email-verification flow later.

## Authorization

Two layers:

1. **HTTP layer (`SecurityConfiguration`)**: deny by default. Public: `POST /api/v1/auth/{register,login,refresh,logout}`,
   `GET /api/v1/public/**` (share links, Phase 7), `GET /actuator/health/**`, `GET /actuator/info`.
   Everything else requires a valid access token.
2. **Application layer**: every use case receives the caller as an explicit `AuthenticatedUser` parameter and
   checks permissions against **data**, not just roles (e.g. `vehicleAccessService.canModify(user, vehicle)`,
   `garageAccessService.validateCanCreateRepair(...)`, from Phases 3 to 5). URL patterns are never used for
   business authorization.

### Roles

| Role | Scope | Assigned by |
|---|---|---|
| `OWNER` | platform | automatically on registration (every user) |
| `SYSTEM_ADMIN` | platform | operators only (database / future admin tooling); never via the public API |
| `GARAGE_ADMIN`, `MECHANIC` | **one garage** | garage membership (`garage_user`, Phase 3) |

Garage roles are not global user roles: a person can be a mechanic at garage A and nobody at garage B.

### Never trusted from clients

Roles, account status, verification status, source type, garage IDs and ownership claims. Request DTOs don't have
fields for server-decided values; unknown JSON properties can never set them.

## Other measures

- Uniform 401/403/5xx bodies without internal details; 401 responses carry `WWW-Authenticate: Bearer`.
- Actuator: only `health` and `info` are exposed and public; health details hidden outside `local`.
- Secrets only from environment variables; no production secrets in the repository.
- DTOs, commands and token records override `toString()` so passwords and tokens never end up in logs.
- Local PostgreSQL bound to `127.0.0.1`; `flyway clean` disabled.

## Known gaps (planned hardening, Phase 9)

- No rate limiting / lockout on login, register and refresh yet.
- No email verification or password reset.
- No cleanup job for expired refresh tokens.
- CORS not configured (only needed for Flutter web; mobile apps don't use it).
- No admin endpoints to block users or grant roles (done directly in the database for now).
