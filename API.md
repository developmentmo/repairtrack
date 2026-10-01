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
warnings: [...]}`.

**Mileage warnings** never block a request. A reading lower than the preceding reading (by date) produces
`{code: "MILEAGE_DECREASE", message, earlier: {date, mileage, sourceType}, later: {...}}`. It is reported as an
inconsistency, never as fraud.

## Audit

| Method | Path | Who | Response |
|---|---|---|---|
| GET | `/api/v1/audit-events?entityType=USER\|GARAGE\|VEHICLE\|REPAIR_EVENT&entityId=…` | SYSTEM_ADMIN | 200 `[{id, entityType, entityId, action, actorId, oldValue, newValue, createdAt}]` |

## Operational

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/actuator/health` | public | Overall health (includes database) |
| GET | `/actuator/health/liveness` | public | Liveness probe |
| GET | `/actuator/health/readiness` | public | Readiness probe |
| GET | `/actuator/info` | public | Build/app info |
