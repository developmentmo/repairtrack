# Security

## Authentication

| Aspect | Design |
|---|---|
| Access token | JWT, HS256, 15 min (`JWT_ACCESS_TOKEN_TTL`). Claims: `sub` (user ID), `sid` (login session ID), `iss`, `iat`, `exp`, `jti`. No email, name or roles. |
| Signing key | `JWT_SECRET`, >= 32 bytes; the app refuses to start with a weaker or missing secret (outside `local`). |
| Validation | Signature, expiry (60 s clock skew), issuer. |
| Refresh token | Opaque 256-bit random value, 30 days (`REFRESH_TOKEN_TTL`). Only its SHA-256 hash is stored. |
| Rotation | Every refresh revokes the used token and issues a new one in the same *family* (one family per login). |
| Reuse detection | Presenting a revoked token revokes the whole family (assumed theft). Committed even though the request fails. |
| Concurrency | Refresh takes a row lock on the token, so parallel refreshes cannot both succeed. |
| Logout | Revokes the token's whole family and ends its login session, so the access token stops working too. Public endpoint: holding the refresh token is the authorization. Idempotent. |
| Idle timeout | A login (`login_session`, id = refresh-token family) ends after 15 minutes without requests (`SESSION_IDLE_TIMEOUT`). Every authenticated request and every refresh extends it (written at most once a minute). An idle session rejects its access token (401) and its refresh token (`401 SESSION_EXPIRED`, family revoked); the user logs in again. Access tokens issued before sessions existed have no `sid` and are accepted until they expire. |
| Passwords | BCrypt via Spring's `DelegatingPasswordEncoder` (hashes are prefixed `{bcrypt}`), so the algorithm can later move to Argon2 without a migration. Policy: min 12 characters, max 72 bytes (BCrypt limit; longer input is rejected, not truncated). |
| Transport | Tokens in the `Authorization` header / JSON body, never in cookies. The API is stateless (no HTTP session), so CSRF protection is disabled on purpose. |

### Per-request user loading

Every authenticated request loads the user by ID (one primary-key query):

- a **blocked or deleted** user is rejected immediately, even with an unexpired access token;
- **roles come from the database**, never from the token, so granting or revoking a role needs no re-login;
- the **login session** (`sid`) must still be active: logout, a password reset and the idle timeout take effect at
  once, not when the access token expires.

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
   `garageAccessService.validateCanRecordWork(...)`, from Phases 3 to 5). URL patterns are never used for
   business authorization.

### Roles

| Role | Scope | Assigned by |
|---|---|---|
| `OWNER` | platform | automatically on registration (every user) |
| `SYSTEM_ADMIN` | platform | operators only (database / future admin tooling); never via the public API |
| `GARAGE_ADMIN`, `MECHANIC` | **one garage** | garage membership (`garage_user`, Phase 3) |

Garage roles are not global user roles: a person can be a mechanic at garage A and nobody at garage B.

### Garage authorization (Phase 3)

All garage checks live in `GarageAccessService` and are based on the caller's **active membership of that
specific garage**:

| Action | Rule |
|---|---|
| Register garage | any authenticated user; becomes its first `GARAGE_ADMIN`; status always `PENDING` |
| View garage profile | any authenticated user (business information) |
| List members | active member of that garage, or `SYSTEM_ADMIN` |
| Add member / remove other member | active `GARAGE_ADMIN` of that garage |
| Leave garage | the member themselves (not the last admin) |
| Re-apply for verification | active `GARAGE_ADMIN`, only from `UNVERIFIED` |
| Verification decision | `SYSTEM_ADMIN` only |
| Record work (Phase 5) | `validateCanRecordWork`: active member (admin or mechanic) **and** garage not `SUSPENDED` |

A `SYSTEM_ADMIN` is not implicitly a garage member and cannot record work for a garage.
Adding members by email reveals to garage admins whether an account exists (accepted; an invitation
flow can replace this later).

### Vehicle authorization (Phase 4)

| Action | Rule |
|---|---|
| Register as owner | any user; becomes owner. VIN must not exist yet. |
| Register for a garage | `GarageAccessService.validateCanRecordWork` (active member, garage not suspended); no owner is created |
| View vehicle / search | any authenticated user; **VIN hidden** except for current owner, SYSTEM_ADMIN, members of the registering garage |
| Edit details | current owner, SYSTEM_ADMIN, or registering-garage member **while unowned**. VIN never editable. |
| Claim | vehicle has no active owner **and** caller supplies the correct VIN |
| End ownership | current owner only |

Claim proof is intentionally modest in V1: the VIN is never revealed by the API to non-owners, so knowing it
suggests access to the car or its registration documents. It does not prove legal ownership (a VIN is also
visible on the car itself). Mitigations: one active owner at a time, ownership changes are evented (audit), and
the design allows stronger proofs later (document review, RDW) without API changes.
Owner identity is never exposed through any vehicle endpoint.

### Vehicle history authorization (Phase 5)

Rules live in `RepairAccessPolicy` (one class, reviewable as a whole):

| Action | Rule |
|---|---|
| Create owner record | current owner of the vehicle |
| Create garage record | `GarageAccessService.validateCanRecordWork(actor, garageId)`: active member, garage not suspended. Any registered vehicle (the garage has the car). |
| View history / mileage / parts | current owner, SYSTEM_ADMIN, members of a garage that registered the vehicle or recorded work on it |
| Correct / add parts | garage record: active member of the **recording** garage (not suspended). Owner record: its creator while still the current owner. |
| Void | as correct, plus SYSTEM_ADMIN (moderation) |
| Delete | **not possible** (no endpoint; `405`) |

Source type and verification status are decided only by `VerificationService` from a server-built
`RecordingContext`; the request DTOs have no such fields and a database check constraint rejects any combination the
service cannot produce. Previous owners lose access to the history when their ownership ends, including through share links they created.

### Audit trail

- Written in the same transaction as the change (synchronous listeners); a failed audit write rolls the change back.
- `audit_event` is append-only, enforced by a database trigger (UPDATE/DELETE raise an error).
- Entries contain IDs and business values only, never emails, names or credentials (covered by `AuditTrailIT`).
- Readable by SYSTEM_ADMIN only.

### Documents (Phase 6)

- **Private bucket.** Files are only reachable through presigned URLs (5 minutes) handed out after the same
  authorization as viewing the vehicle history. URLs are not stored and not logged.
- **Upload rights** equal correction rights for the record (owner never on garage records and vice versa);
  voided records accept no documents.
- **Type checking by content.** PDF/JPEG/PNG are recognised from their magic bytes; the client's file name and
  Content-Type are ignored. Downloads are served as `Content-Disposition: attachment` with the detected type, so a
  stored file is never rendered as HTML in the browser.
- **Size limits** in the servlet container (20 MB) and the service.
- **File names** are sanitized (no paths, quotes or control characters), stored for display only and never used in
  storage keys; they are not written to the audit trail (they may contain personal data).
- **Integrity:** SHA-256 computed by the server while receiving the upload. `GET /documents/{id}/integrity`
  detects any later change or loss of the stored object.
- **Credentials:** static keys only for local/test; in production prefer the AWS default credentials chain (IAM role).

### Vehicle photos (issue #7)

- Only the **current owner** (from `vehicle_ownership`) can upload or view a photo, and only their own: other users,
  garages and system admins get `403`; after a sale neither owner sees the other's photo. Never in the public report.
- Same pipeline as documents: size limit, **JPEG/PNG/WebP by content**, ClamAV before storing (malware audited on the
  vehicle as `VEHICLE_PHOTO_MALWARE_REJECTED`), SHA-256, private bucket under `vehicles/{id}/photos/{photoId}`,
  presigned downloads (5 minutes, neutral file name `vehicle-photo.<ext>`). The client's file name is not stored.
- Replacing never deletes: the old row becomes `REPLACED` and its object stays. Audited on the vehicle as
  `VEHICLE_PHOTO_UPLOADED` / `VEHICLE_PHOTO_REPLACED` (IDs, type, size, hash only).

### Public share links (Phase 7)

- **Tokens:** 256 bits from `SecureRandom` (43 URL-safe characters), returned once on creation. Only the SHA-256
  hash is stored, so a database leak exposes no working links. Internal UUIDs are never public identifiers.
- **Lifetime:** 1–365 days (default 30); revocable at any time by the owner; dead as soon as the creator stops being
  the owner. Only the current owner can create, list (own links only) and revoke links.
- **One answer for every invalid link** (`404 SHARE_NOT_FOUND`): unknown, expired, revoked and ownership-ended links
  are indistinguishable, so the endpoint reveals nothing about which tokens exist(ed).
- **Data minimisation:** the report has no VIN, internal IDs, owner/user identities, emails or file names; only the
  number of registered owners. Garages appear by name and city (public business data).
- **Documents** are hidden unless the owner chose `includeDocuments`; even then only via short presigned URLs with a
  neutral file name (`invoice-2026-10-02.pdf`), referenced by SHA-256.
- **Referrer-Policy: no-referrer** on all responses, so the token in the URL does not leak to linked sites.
- Creation and revocation are audited (`SHARE_CREATED`, `SHARE_REVOKED`); the token never appears in audit entries or
  logs (`toString()` overrides). Views are counted per link (`access_count`), not audited per view.

### Abuse protection (Phase 9a)

- **Per IP** (`RateLimitFilter`, in-memory fixed windows): login 10/min, register 10/hour, refresh 30/min,
  vehicle claim 10/hour (VIN guessing), public share report 60/min (scraping). Over the limit: `429 RATE_LIMITED`
  with `Retry-After`. Configurable under `repairtrack.rate-limit.*`; `RATE_LIMIT_ENABLED=false` switches the per-IP
  limits off.
- **Per account**: 10 failed logins per email address per 15 minutes, regardless of IP, then
  `429 TOO_MANY_LOGIN_ATTEMPTS` (also with the right password) until the window ends. Unknown addresses are counted
  the same way, so the answer never reveals whether an account exists. Trade-off: someone who knows an address can
  block its logins for one window.
- **Behind a reverse proxy** set `FORWARD_HEADERS_STRATEGY=framework` and let only the proxy reach the app;
  otherwise every client shares the proxy's IP (or could fake `X-Forwarded-For`).
- Limits are per application instance. With several instances, move the counters to a shared store (e.g. Redis).
- **Refresh tokens** that expired more than a day ago, and login sessions that have been over for more than a day,
  are deleted daily (03:30 Europe/Amsterdam, `REFRESH_TOKEN_CLEANUP_CRON`).

### Email verification and password reset (Phase 9b)

- Login is refused (`403 EMAIL_NOT_VERIFIED`) until the address is confirmed through the emailed link; the check
  comes after the password check, so it reveals nothing to someone without the password.
- Links carry 256-bit single-use tokens; only their SHA-256 hash is stored (`account_token`). Verification links are
  valid 24 hours, reset links 1 hour. A new email invalidates the previous link of the same kind; a verification
  link can never reset a password.
- `resend-verification` and `forgot-password` always answer 202 (no account enumeration) and are rate limited per
  IP (5/hour each) against mail bombing; `verify-email` and `reset-password` 20/hour per IP.
- A password reset logs the user out on all devices (all refresh tokens revoked) and is audited.
- Emails are sent after the transaction commits; a mail-server failure is logged without the link and never rolls
  back the account change. The token never appears in logs, audit entries or `toString()` output.

### User administration (Phase 9b)

SYSTEM_ADMIN can look up a user by exact email (no listing/search of users), block and unblock. Blocking takes effect
on the next request and revokes all refresh tokens; an admin cannot block themselves. Both are audited.

### CORS (Phase 8c)

Only the origins in `CORS_ALLOWED_ORIGINS` (the Flutter web app) may call `/api/**` from a browser; the default is
none and `*` is rejected at startup. Credentials (cookies) are not allowed: tokens travel in the `Authorization`
header. Mobile apps are not affected by CORS.

Presigned downloads (the files host, for example a vehicle photo the web app shows) are served by Garage through the
edge proxy. Flutter web loads images with XHR, so Caddy adds `Access-Control-Allow-Origin` for that environment's own
web app origin only (`deploy/edge/Caddyfile`). The files host stays read-only (GET/HEAD).

### Malware scan and integrity sweep (Phase 9c)

- **Every upload is scanned by ClamAV (clamd, `INSTREAM`) before it is stored.** Infected: `422 MALWARE_DETECTED`,
  nothing stored, logged and audited on the record (`DOCUMENT_MALWARE_REJECTED`, with the signature only).
  No verdict (clamd down, error, timeout): `503 SCANNER_UNAVAILABLE` (fail closed: never stored unscanned).
- `MALWARE_SCAN_MODE=disabled` switches scanning off (logged as a warning at startup); for development only.
- **Integrity sweep** (weekly, Sunday 04:00 Europe/Amsterdam, `INTEGRITY_SWEEP_CRON`): every stored object is
  re-hashed and compared with the SHA-256 from the upload. Missing or changed objects are logged as errors and
  audited (`DOCUMENT_INTEGRITY_FAILED`, no actor); nothing is repaired automatically, the stored hash is the evidence.
- **Health:** `/actuator/health` includes `storage` (bucket reachable) and `malwareScanner` (clamd answers PING).
  Details are only shown in `local`; liveness/readiness probes are not affected.

### RDW vehicle data (Phase 10)

- `GET /api/v1/vehicle-registry/{plate}` (signed-in users) returns public RDW Open Data (make, trade name, type,
  colour, fuel, first admission, APK expiry) to pre-fill the registration form. It is **a suggestion only**: nothing
  is stored from it, and it never counts as proof of ownership, provenance or verification. The VIN stays the identity
  and the only ownership proof for a claim.
- The RDW open data has no owner details; plates are never logged. Answers (also "unknown plate") are cached in memory
  for 24 h; failures are not cached. Timeouts: 2 s connect, 4 s per answer; RDW down → `503 REGISTRY_UNAVAILABLE`,
  and the user fills in the form by hand. Per-IP limit 60/min (`RATE_LIMITED`).
- `RDW_MODE=disabled` switches lookups off (tests never call the RDW).

### Ownership disputes (Phase 11)

- **Filing** (`POST /api/v1/vehicles/{id}/disputes`, multipart): the full VIN (same proof as a claim, constant-time
  compare), a statement and at least one evidence file. Only against a vehicle with an active owner other than the
  caller; one undecided dispute per claimant and vehicle (partial unique index), at most 3 undecided per claimant,
  5 filings per IP per day (`RATE_LIMITED`).
- **Evidence** goes through the same pipeline as documents (size, PDF/JPEG/PNG by content, ClamAV, SHA-256), is stored
  under `disputes/{id}/` in the private bucket and is **only downloadable by system admins** (presigned, 5 min). Parties
  see only their own statement and files; the contested owner never learns who filed the dispute. Statements and file
  names are never audited or logged. At most 5 files per party. The weekly integrity sweep re-hashes evidence too
  (`DISPUTE_EVIDENCE_INTEGRITY_FAILED` on the dispute, no actor).
- **Response**: only the contested owner, once, within 14 days (`repairtrack.disputes.response-time`).
- **While undecided**: the owner keeps access but cannot create new share links (`409 VEHICLE_UNDER_DISPUTE`);
  existing reports show `ownershipUnderReview`.
- **Decision** (SYSTEM_ADMIN, after the response or the deadline; final): *upheld* revokes the contested ownership
  (status `REVOKED`, kept in the history, not counted as an owner) and assigns the claimant from a date the admin
  chooses; *rejected* changes nothing. Owner records entered by a revoked owner are labelled
  `enteredDuringRevokedOwnership` (also on the public report) and the rightful owner may void them; nothing is deleted.
- Audited: `DISPUTE_OPENED`, `DISPUTE_RESPONDED`, `DISPUTE_EVIDENCE_ADDED`, `DISPUTE_UPHELD`/`DISPUTE_REJECTED`,
  `VEHICLE_OWNERSHIP_REVOKED`, `VEHICLE_OWNERSHIP_ASSIGNED`. Both parties get an email when it is filed and decided.

### Account deletion (app store requirement)

- `POST /api/v1/users/me/delete` `{password}` (password asked again; wrong -> `403 PASSWORD_INCORRECT`, not 401).
  In the app: menu -> Account -> Account verwijderen; on the web: `https://<domain>/account` (the URL for the
  store listings).
- One transaction: active vehicle ownerships end today (history stays with the vehicle, which can be claimed by
  the next owner; share links die with the ownership), garage memberships end (refused with `LAST_GARAGE_ADMIN`
  when the user is the only admin of a garage with other members), then e-mail and name are replaced by
  placeholders (`deleted-<id>@deleted.invalid`, "Verwijderd account"), the password can never match, all refresh
  tokens and e-mail links are invalidated. Audited as `USER_DELETED`; the e-mail address can be registered again.
- Kept on purpose: records, documents and corrections the user added (they are the vehicle's history), audit
  entries (IDs only). Dispute mails are not sent to deleted or blocked accounts.

### Never trusted from clients

Roles, account status, verification status, source type, garage IDs and ownership claims. Request DTOs don't have
fields for server-decided values; unknown JSON properties can never set them.

## Other measures

- Uniform 401/403/5xx bodies without internal details; 401 responses carry `WWW-Authenticate: Bearer`.
- Actuator: only `health` and `info` are exposed and public; health details hidden outside `local`.
- Secrets only from environment variables; no production secrets in the repository.
- DTOs, commands and token records override `toString()` so passwords and tokens never end up in logs.
- Local PostgreSQL bound to `127.0.0.1`; `flyway clean` disabled.

## Known gaps

- No API to grant SYSTEM_ADMIN (deliberately: done directly in the database).
- Changes made before Phase 5 have no audit entries (no production data existed).
- Local and test Garage bucket/key use fixed throwaway values; production credentials come from the environment.
- Share tokens are part of the URL path. The deployed proxies keep no access log (Caddy without `log`, nginx `access_log off`); keep it that way when adding a CDN or another proxy.
- Rate limits and the RDW cache are in memory per instance; several instances would need a shared store (e.g. Redis).
