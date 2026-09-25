# Security

## Current posture (Phase 1)

There is **no authentication yet**; Spring Security arrives in Phase 2. Do not deploy this build
anywhere reachable from the internet.

Already in place:

- Only `/actuator/health` and `/actuator/info` are exposed; health details are hidden outside `local`.
- Error responses never contain stack traces or exception messages (`GlobalExceptionHandler`,
  `server.error.include-*=never`).
- No secrets in the repository. Connection settings come from environment variables with no
  defaults in `application.yml`; local-only defaults live in the `local` profile.
- Local PostgreSQL is bound to `127.0.0.1` only.
- `flyway clean` is disabled.

## Principles for upcoming phases

- JWT access tokens + refresh tokens; passwords hashed with Argon2 or BCrypt.
- Authorization is checked server-side in application services, beyond roles
  (e.g. a mechanic must belong to a garage that is authorized for the vehicle).
- Never trust client-supplied roles, verification status, source type, garage IDs or ownership claims.
- Verification status and source type are derived by the backend only.
- Public share links use random tokens stored as hashes; internal UUIDs are never public identifiers.
- Public vehicle history never exposes owner identity, contact details or internal IDs.
- Audit important mutations; keep sensitive data out of logs and audit payloads where not required.
