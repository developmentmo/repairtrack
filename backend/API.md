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
| `USER_NOT_FOUND` | 404 | No (active) account for the given user / email |
| `EMAIL_ALREADY_REGISTERED` | 409 | Registration with an existing email (case-insensitive) |
| `INVALID_GARAGE_DATA` | 400 | Garage data rejected by domain validation |
| `GARAGE_ACCESS_DENIED` | 403 | Not a (sufficiently privileged) member of the garage |
| `GARAGE_SUSPENDED` | 403 | Suspended garage tries to record work |
| `SYSTEM_ADMIN_REQUIRED` | 403 | Verification decisions |
| `GARAGE_NOT_FOUND` | 404 | |
| `GARAGE_MEMBER_NOT_FOUND` | 404 | User has no active membership in that garage |
| `ALREADY_GARAGE_MEMBER` | 409 | User already has an active membership |
| `INVALID_VERIFICATION_TRANSITION` | 422 | Status change not allowed from the current status |
| `LAST_GARAGE_ADMIN` | 422 | Removing the garage's last admin |
| `INVALID_VEHICLE_DATA` | 400 | Invalid VIN, plate, model year, or a date in the future |
| `INVALID_VEHICLE_SEARCH` | 400 | Search without, or with both, `vin` and `licensePlate` |
| `VEHICLE_ACCESS_DENIED` | 403 | Not allowed to edit the vehicle / not its current owner |
| `OWNERSHIP_PROOF_INVALID` | 403 | Claim with a VIN that does not match |
| `VEHICLE_NOT_FOUND` | 404 | |
| `VEHICLE_ALREADY_REGISTERED` | 409 | A vehicle with this VIN exists: search and claim it instead |
| `VEHICLE_ALREADY_OWNED` | 409 | Vehicle has an active owner |
| `ALREADY_VEHICLE_OWNER` | 409 | Caller already owns it |
| `INVALID_OWNERSHIP_PERIOD` | 422 | Start in the future, end before start, or overlapping the previous owner |
| `INVALID_REPAIR_DATA` | 400 | Future event date, implausible mileage, missing title/reason, invalid part |
| `NO_CHANGES` | 400 | Correction that changes nothing |
| `REPAIR_ACCESS_DENIED` | 403 | Not allowed to see the vehicle history or to change this record |
| `REPAIR_NOT_FOUND` | 404 | |
| `REPAIR_ALREADY_VOIDED` | 422 | Voided records cannot be voided, corrected or extended again |
| `EMPTY_FILE` | 400 | Uploaded file has no content |
| `DOCUMENT_NOT_FOUND` | 404 | |
| `FILE_TOO_LARGE` | 413 | Upload larger than 20 MB |
| `MALWARE_DETECTED` | 422 | Upload contains malware (nothing stored; audited on the record) |
| `SCANNER_UNAVAILABLE` | 503 | The malware scanner gave no verdict; upload refused, try again later |
| `UNSUPPORTED_FILE_TYPE` | 415 | Content is not PDF, JPEG or PNG (decided by the file's bytes) |
| `INVALID_SHARE` | 400 | Share validity outside 1–365 days |
| `EMAIL_NOT_VERIFIED` | 403 | Login before the email link was followed |
| `INVALID_TOKEN` | 400 | Verification or reset link unknown, expired, already used or of the wrong kind |
| `CANNOT_BLOCK_YOURSELF` | 422 | Admin tries to block their own account |
| `RATE_LIMITED` | 429 | Too many requests from this IP (login, register, refresh, claim, public report); see `Retry-After` |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | Too many failed logins for this email address; see `Retry-After` |
| `SHARE_NOT_FOUND` | 404 | Unknown, expired, revoked or ownership-ended link (one answer for all), or another owner's share |

## Authentication

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | public | `{email, password, firstName, lastName}` | 201 `UserResponse` |
| POST | `/api/v1/auth/login` | public | `{email, password}` | 200 `TokenResponse` |
| POST | `/api/v1/auth/refresh` | public | `{refreshToken}` | 200 `TokenResponse` (new refresh token!) |
| POST | `/api/v1/auth/logout` | public | `{refreshToken}` | 204 (idempotent) |
| POST | `/api/v1/auth/verify-email` | public | `{token}` (from the email link) | 204 |
| POST | `/api/v1/auth/resend-verification` | public | `{email}` | 202 (always) |
| POST | `/api/v1/auth/forgot-password` | public | `{email}` | 202 (always) |
| POST | `/api/v1/auth/reset-password` | public | `{token, newPassword}` | 204; all sessions are logged out |

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

**Email verification (Phase 9b):** registration sends an email with a link `{PUBLIC_BASE_URL}/verify-email?token=…`
(valid 24 hours). Login answers `403 EMAIL_NOT_VERIFIED` until the link is followed (only after a correct password).
The web app's page posts the token to `/auth/verify-email`. `resend-verification` and `forgot-password` always answer
202, so they never reveal whether an account exists. A password-reset link (`/reset-password?token=…`) is valid for
one hour, works once, also confirms the email address and logs the user out on all devices. A new email makes the
previous link of the same kind invalid.

## Users

| Method | Path | Access | Response |
|---|---|---|---|
| GET | `/api/v1/users/me` | authenticated | 200 `UserResponse` |

`UserResponse`: `{id, email, firstName, lastName, status, roles, emailVerified, createdAt}`. Never contains credentials.

## Garages

| Method | Path | Who | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/garages` | any user (becomes GARAGE_ADMIN) | `{name, kvkNumber, address, postalCode, city, phone?, email?}` | 201 `GarageResponse` (status `PENDING`) |
| GET | `/api/v1/garages/mine` | any user | | 200 `[{garageId, name, city, verificationStatus, role, memberSince}]` |
| GET | `/api/v1/garages/{garageId}` | any user | | 200 `GarageResponse` |
| POST | `/api/v1/garages/{garageId}/verification-request` | garage admin | | 200; `UNVERIFIED` &rarr; `PENDING` |
| POST | `/api/v1/garages/{garageId}/verification` | SYSTEM_ADMIN | `{status, note?}` | 200 `GarageResponse` |

Validation: `kvkNumber` 8 digits; `postalCode` Dutch format (`1234AB` / `1234 AB`, stored as `1234 AB`).

Verification transitions (system admin): `PENDING → VERIFIED|UNVERIFIED`, `VERIFIED → SUSPENDED|UNVERIFIED`,
`SUSPENDED → VERIFIED|UNVERIFIED`. Garage admin: `UNVERIFIED → PENDING`.

`GarageResponse`: `{id, name, kvkNumber, address, postalCode, city, phone, email, verificationStatus, verificationChangedAt, createdAt}`

### Garage members

| Method | Path | Who | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/garages/{garageId}/users` | garage admin | `{email, role}` (`GARAGE_ADMIN` / `MECHANIC`) | 201 member |
| GET | `/api/v1/garages/{garageId}/users` | members, SYSTEM_ADMIN | | 200 `[{userId, email, firstName, lastName, role, memberSince}]` |
| DELETE | `/api/v1/garages/{garageId}/users/{userId}` | garage admin, or the member themselves | | 204 |

The added user must already have an account. DELETE ends the membership; the row is kept as history.
A garage always keeps at least one garage admin.

`GET /api/v1/garages/{garageId}/vehicles` from the original plan follows with repairs (Phase 5): it lists the
vehicles a garage has worked on.

## Vehicles

| Method | Path | Who | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/vehicles` | any user; with `garageId`: garage member | `{vin, licensePlate?, make, model, modelYear?, firstRegistrationDate?, garageId?, ownedSince?}` | 201 `VehicleResponse` |
| GET | `/api/v1/vehicles` | any user | | 200 `[VehicleResponse]`: vehicles the caller currently owns |
| GET | `/api/v1/vehicles/search?vin=…` or `?licensePlate=…` | any user | | 200 `[{id, licensePlate, make, model, modelYear}]` (exact match, never a VIN) |
| GET | `/api/v1/vehicles/{vehicleId}` | any user | | 200 `VehicleResponse` |
| PUT | `/api/v1/vehicles/{vehicleId}` | see below | `{licensePlate?, make, model, modelYear?, firstRegistrationDate?}` | 200 `VehicleResponse` |
| POST | `/api/v1/vehicles/{vehicleId}/claim` | any user | `{vin, ownedSince?}` | 201 `OwnershipResponse` |
| POST | `/api/v1/vehicles/{vehicleId}/ownership/end` | current owner | `{endDate?}` (optional body) | 200 `OwnershipResponse` |

`VehicleResponse`: `{id, vin, licensePlate, make, model, modelYear, firstRegistrationDate, status, ownedByMe, canEdit, createdAt, updatedAt}`.
`vin` is `null` unless the caller is the current owner, a SYSTEM_ADMIN, or a member of the garage that registered
the vehicle. No response ever contains owner identity.

`OwnershipResponse`: `{vehicleId, startDate, endDate, status}`.

Rules:
- **Registration without `garageId`**: the caller becomes owner from `ownedSince` (default: today).
  **With `garageId`**: the caller must be an active member of that non-suspended garage; the vehicle has no owner.
- VIN: 17 characters, no I/O/Q, case and spaces ignored, unique, immutable (not part of PUT).
  License plate: stored without dashes/spaces, upper-case; searched the same way.
- **PUT** allowed for the current owner, SYSTEM_ADMIN, or a member of the registering garage *while the vehicle has no owner*.
- **Claim**: only when the vehicle has no active owner, with the correct VIN as proof. `ownedSince` must not
  be before the previous owner's end date.
- All "not in the future" checks use today's date in Europe/Amsterdam.

## Vehicle history (repairs)

| Method | Path | Who | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/vehicles/{vehicleId}/repairs` | current owner; with `garageId`: garage member | `CreateRepairRequest` | 201 `RepairResponse` (+ `warnings`) |
| GET | `/api/v1/vehicles/{vehicleId}/repairs` | history viewers¹ | | 200 `[RepairResponse]`, newest first, **including voided** |
| GET | `/api/v1/vehicles/{vehicleId}/mileage` | history viewers¹ | | 200 `{readings, anomalies}` |
| GET | `/api/v1/repairs/{repairId}` | history viewers¹ | | 200 `RepairResponse` |
| POST | `/api/v1/repairs/{repairId}/void` | record's author side² or SYSTEM_ADMIN | `{reason}` | 200 `RepairResponse` (status `VOIDED`) |
| POST | `/api/v1/repairs/{repairId}/corrections` | record's author side² | `{eventType?, eventDate?, mileage?, title?, description?, reason}` | 200 `RepairResponse` (+ `warnings`) |
| POST | `/api/v1/repairs/{repairId}/parts` | record's author side² | `{parts: [PartRequest]}` | 201 `RepairResponse` |
| GET | `/api/v1/repairs/{repairId}/parts` | history viewers¹ | | 200 `[PartResponse]` |
| GET | `/api/v1/garages/{garageId}/vehicles` | garage members, SYSTEM_ADMIN | | 200 `[{id, licensePlate, make, model, modelYear}]` |

¹ current owner, SYSTEM_ADMIN, members of a garage that registered the vehicle or recorded work on it.
² garage record: active member of **that** garage (not suspended). Owner record: the owner who created it, while
still the current owner. An owner can never change a garage record and vice versa.

There is **no DELETE** for repairs (`405`). Records are voided (stay visible, with reason) or corrected
(original values stay visible).

`CreateRepairRequest`:
```json
{
  "eventType": "REPAIR",               // MAINTENANCE, REPAIR, INSPECTION, TYRE_CHANGE, DAMAGE_REPAIR, APK, RECALL, OTHER
  "eventDate": "2026-09-14",           // not in the future (Europe/Amsterdam)
  "mileage": 183421,                   // 0..2,000,000
  "title": "Brake replacement",
  "description": "Front discs and pads",
  "garageId": "…",                     // omit to record as owner
  "parts": [{"partNumber": "0986494521", "brand": "Bosch", "description": "Brake pads", "quantity": 1}]
}
```
There are no `sourceType` / `verificationStatus` fields. The backend derives them:

| Recorded by | sourceType | verificationStatus |
|---|---|---|
| current owner | `OWNER` | `UNVERIFIED` |
| garage, not verified | `GARAGE` | `GARAGE_VERIFIED` |
| verified garage | `VERIFIED_GARAGE` | `GARAGE_VERIFIED` |
| owner + document (Phase 6) | `OWNER_DOCUMENT` | `DOCUMENTED` |
| RDW / manufacturer import (future) | `RDW` / `MANUFACTURER` | `OFFICIAL_SOURCE` |

`RepairResponse`: `{id, vehicleId, eventType, eventDate, mileage, title, description, sourceType, verificationStatus,
status, garage: {id, name, city, verificationStatus} | null, parts: [...], corrections: [{field, originalValue,
correctedValue, reason, correctedByGarage | null (= owner), correctedAt}], voidedAt, voidReason, createdAt, updatedAt,
canCorrect, canVoid, warnings: [...]}`.

`canCorrect` (correct, add parts, upload documents) and `canVoid` tell the caller which actions the rules above allow
right now, so apps can show or hide buttons. They are derived from the same rules; the backend still checks every
request. Both are `false` on voided records.

**Mileage warnings** never block a request. A reading lower than the preceding reading (by date) produces
`{code: "MILEAGE_DECREASE", message, earlier: {date, mileage, sourceType}, later: {...}}`. It is reported as an
inconsistency, never as fraud.

## Documents

| Method | Path | Who | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/repairs/{repairId}/documents` | record's author side² | `multipart/form-data`: `file`, `documentType` | 201 `DocumentResponse` |
| GET | `/api/v1/repairs/{repairId}/documents` | history viewers¹ | | 200 `[DocumentResponse]` |
| GET | `/api/v1/documents/{documentId}` | history viewers¹ | | 200 `DocumentResponse` with `downloadUrl` |
| GET | `/api/v1/documents/{documentId}/integrity` | history viewers¹ | | 200 `{documentId, expectedSha256, actualSha256, intact, checkedAt}` |

`documentType`: `INVOICE`, `WORK_ORDER`, `INSPECTION_REPORT`, `PHOTO`, `OTHER`. Accepted content: PDF, JPEG, PNG
(detected from the bytes), max 20 MB. No update or delete (`405`): documents are part of the history.

`DocumentResponse`: `{id, repairEventId, documentType, fileName, mimeType, fileSize, sha256, uploadedAt,
repairVerificationRaised, downloadUrl, downloadUrlExpiresAt}`. `downloadUrl` is a presigned URL to the private bucket,
valid for 5 minutes. Request a new one via `GET /documents/{id}` when it expires. `sha256` is computed by the server
over the stored bytes.

**Effect on verification:** an `INVOICE`, `WORK_ORDER` or `INSPECTION_REPORT` uploaded by the owner to their own
`OWNER`/`UNVERIFIED` record raises it to `OWNER_DOCUMENT`/`DOCUMENTED` (`repairVerificationRaised: true`).
Photos and "other" files do not. Garage records are unaffected.

## Sharing

| Method | Path | Who | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/vehicles/{vehicleId}/shares` | current owner | optional `{validDays (1–365, default 30), includeDocuments (default false)}` | 201 `{share, token, url}` |
| GET | `/api/v1/vehicles/{vehicleId}/shares` | current owner | | 200 `[ShareResponse]` (own links only) |
| POST | `/api/v1/shares/{shareId}/revoke` | current owner who created it | | 200 `ShareResponse` (idempotent) |

`ShareResponse`: `{id, createdAt, expiresAt, includeDocuments, status, accessCount, lastAccessedAt}`, `status` one of
`ACTIVE`, `EXPIRED`, `REVOKED`, `OWNER_CHANGED`. The `token` and `url` (`{PUBLIC_BASE_URL}/v/{token}`) are returned
**only once**, on creation: the server stores only a hash. Links are never deleted, only revoked.

### Public vehicle history (no authentication)

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/public/vehicles/{token}` | 200 `Report` |
| GET | `/api/v1/public/vehicles/{token}/documents/{reference}` | 200 `{downloadUrl, expiresAt}` (presigned, 5 minutes) |

`Report`: `{vehicle {make, model, modelYear, firstRegistrationDate, licensePlate, registeredOwnerCount}, summary
{totalRecords, voidedRecords, recordsByVerification, firstEventDate, lastEventDate, lastRecordedMileage,
mileageInconsistencies, documentCount}, history [Entry], mileage {readings, inconsistencies}, documentsDownloadable,
generatedAt, linkValidUntil}`.

`Entry`: `{eventType, eventDate, mileage, title, description, sourceType, verificationStatus, voided, voidReason,
garage {name, city, verificationStatus} | null, parts, corrections [{field, originalValue, correctedValue, reason,
correctedBy ("OWNER" or garage name), correctedAt}], documents [{documentType, mimeType, fileSize, uploadedAt,
downloadable, reference}]}`.

Deliberately absent: internal IDs, VIN, owner and user identities, file names. Voided records stay visible (marked)
so the history cannot be cleaned up silently. `reference` (the document's SHA-256) is only set when the link allows
downloads. Every view increments the link's `accessCount`. Any invalid link returns `404 SHARE_NOT_FOUND`.

## Administration (SYSTEM_ADMIN)

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/garages?verificationStatus=PENDING` | 200 `[GarageResponse]`, oldest first (decide with `POST /garages/{id}/verification`) |
| GET | `/api/v1/admin/users?email=…` | 200 `{id, email, firstName, lastName, status, roles, emailVerified, createdAt}` (exact email) |
| POST | `/api/v1/admin/users/{userId}/block` | 200; takes effect immediately and ends all sessions |
| POST | `/api/v1/admin/users/{userId}/unblock` | 200 |

Other users get `403 SYSTEM_ADMIN_REQUIRED`. Blocking and unblocking are audited (`USER_BLOCKED`, `USER_UNBLOCKED`).

## Audit

| Method | Path | Who | Response |
|---|---|---|---|
| GET | `/api/v1/audit-events?entityType=USER\|GARAGE\|VEHICLE\|REPAIR_EVENT\|DOCUMENT\|VEHICLE_SHARE&entityId=…` | SYSTEM_ADMIN | 200 `[{id, entityType, entityId, action, actorId, oldValue, newValue, createdAt}]` |

## Operational

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/actuator/health` | public | Overall health (includes database) |
| GET | `/actuator/health/liveness` | public | Liveness probe |
| GET | `/actuator/health/readiness` | public | Readiness probe |
| GET | `/actuator/info` | public | Build/app info |
