# Architecture

## Style: modular monolith

RepairTrack V1 is a **single deployable Spring Boot application** with **strict internal module
boundaries**. One process, one PostgreSQL database, one transaction boundary — but modules are
designed so any of them can later be extracted into a separate service without rewriting its domain.

Explicitly out of scope for V1: microservices, Kafka, Kubernetes, distributed databases.

## Modules

Every top-level package under `com.repairtrack` is an application module (Spring Modulith convention).

| Module | Responsibility | Phase |
|---|---|---|
| `common` | Shared kernel: error model, clock. **No business concepts.** Declared `OPEN`. | 1 |
| `security` | Users, authentication (JWT), roles, authorization services | 2 |
| `garage` | Garages, garage users, garage verification | 3 |
| `vehicle` | Vehicles (VIN identity), ownership over time | 4 |
| `repair` | Repair events, parts, void/correction | 5 |
| `mileage` | Mileage records, anomaly warnings | 5 |
| `verification` | Server-side derivation of source type and verification status | 5 |
| `audit` | Append-only audit trail | 5 |
| `document` | Document metadata, object storage, SHA-256 | 6 |
| `sharing` | Share links, public vehicle history | 7 |

### Layers inside a module

```text
<module>
├── api             REST controllers + request/response DTOs (records). No business logic.
├── application     Use cases. @Transactional boundaries. Authorization checks. Publishes events.
├── domain          Entities, value objects, enums, domain rules. No Spring MVC / HTTP types.
└── infrastructure  Spring Data repositories, external adapters (S3, RDW, ...).
```

Dependency direction: `api → application → domain`, and `application → infrastructure` for
Spring Data repositories and technical configuration. There is deliberately no port/adapter layer
(repository interfaces in `domain` implemented in `infrastructure`): with Spring Data that would be
indirection without a current benefit. Domain classes never depend on `api` or `infrastructure`.

### Module public API

Types in a module's **base package** are its public API for other modules. Example (security):

| Type | Purpose |
|---|---|
| `AuthenticatedUser` | The caller (ID, email, platform roles). Other modules receive it as a method parameter. |
| `Role` | Platform roles `OWNER`, `SYSTEM_ADMIN`. |
| `UserRegisteredEvent` | Published after registration (for audit/notifications). |
| `UserDirectory`, `UserSummary` | Read-only user lookup (e.g. add a garage member by email). No credentials. |

Garage module public API:

| Type | Purpose |
|---|---|
| `GarageAccessService` | Explicit garage authorization: `validateCanRecordWork(actor, garageId)` returns a `GarageWorkPermit`, plus `requireGarageAdmin`, `requireMemberOrSystemAdmin`. |
| `GarageWorkPermit` | Result of the repair-authorization check. Carries the garage's verification status, from which the repair module derives `GARAGE` vs `VERIFIED_GARAGE`. |
| `GarageRole`, `GarageVerificationStatus` | Published enums. |
| `GarageEvents.*` | Registered, verification status changed, member added/removed. |

Vehicle module public API:

| Type | Purpose |
|---|---|
| `VehicleAccessService` | `requireExists`, `isActiveOwner`, `requireActiveOwner` for the repair/document/sharing modules. |
| `VehicleEvents.*` | Registered, details changed (with `VehicleFieldChange` list), ownership started/ended. |

Repair, mileage, verification and audit public APIs:

| Type | Purpose |
|---|---|
| `verification.VerificationService` + `RecordingContext` (sealed) → `Provenance` | The only place that decides source type and verification status. |
| `mileage.MileageService` | `record` (returns anomalies involving the new reading), `voidForSourceEvent`, `history`. |
| `repair.RepairEvents.*` | Created, corrected, voided, part added (with snapshots for the audit trail). |
| `garage.GarageDirectory`, `vehicle.VehicleDirectory` | Read-only summaries for other modules' responses. |

Module dependencies (all through base-package APIs; no cycles, verified by `ModularityTest`):

```text
audit ──► repair ──► mileage ──► verification
  │         │  └───► verification
  │         ├──────► vehicle ──► garage ──► security ──► common
  └─────────┴──────────────────────┴──────────┘
```
`audit` is a pure consumer: nothing depends on it.

### Events and atomicity

Modules publish Spring application events from inside their transaction. The audit module handles them with
**synchronous `@EventListener`s**, so the mutation and its audit entry commit or roll back together (spec §34).
After-commit or asynchronous listeners (`@TransactionalEventListener`, Modulith `@ApplicationModuleListener`) are
deliberately not used for audit: an entry could be lost. The future outbox will be written the same way, in the same
transaction.

### Vehicle history model

- `repair_event` is the history entry. Its provenance (source type + verification status) is set once, from a
  `RecordingContext` established by authorization code, and is guarded again by a DB check constraint.
- **Void** instead of delete: the record stays, with reason, and its mileage reading is voided.
- **Correct** instead of update: one `repair_correction` row per field (original, corrected, reason, who, on behalf of
  which garage). The current row holds the latest values, the correction rows reconstruct every earlier version.
- **Mileage** is a separate projection (`mileage_record`). Anomalies are computed on read over the full timeline,
  so a backdated entry can never leave stale flags behind.

### Deferred: `verification` table

The original plan lists a `verification` table. In Phase 5 a record's verification status is fully determined at
creation and stored on `repair_event`, and every change is in `audit_event`. A separate table would duplicate that.
It becomes useful when a record's status can change *after* creation (Phase 6: an owner record upgraded to
`DOCUMENTED` by attaching a document; later: a garage confirming an owner record) and is introduced then.

Controllers obtain the caller with `@AuthenticationPrincipal AuthenticatedUser` and pass it explicitly into
application services. Services never read `SecurityContextHolder` themselves: authorization inputs stay
visible in method signatures and unit tests need no security context.

### Boundary rules (enforced)

`ModularityTest` runs `ApplicationModules.verify()` on every `mvn test`. It fails on:

- **dependency cycles** between modules;
- **access to another module's internals.** In Spring Modulith, only a module's *base package* is
  public by default; all sub-packages (`api`, `application`, `domain`, `infrastructure`) are internal.

Consequence: when module A genuinely needs something from module B, B must expose it deliberately,
e.g. a small facade in a sub-package annotated with `@NamedInterface`, or — preferred for reactions
to state changes — an **application event** that A listens to. Note the naming: a module's `api`
*layer* (REST) is **not** its module API for other modules; other modules never call controllers.

### Cross-module communication

- **Queries / commands:** through explicitly exposed application-level interfaces.
- **Side effects:** Spring application events (e.g. `RepairCreatedEvent` → audit, mileage).
  Events carry IDs and essential values, not JPA entities.
- **Future outbox:** because side effects already flow through events, an outbox table can later be
  written in the same transaction and relayed to a broker, without changing publishers.

### References between modules

Entities reference entities in *other* modules **by ID (UUID)**, not by JPA association
(e.g. `RepairEvent.vehicleId`, not `@ManyToOne Vehicle`). This keeps modules independently
extractable. Foreign keys still exist at the database level while we share one database.

## Cross-cutting decisions (Phase 1)

| Decision | Rationale |
|---|---|
| Flyway owns the schema; Hibernate `ddl-auto=validate` | Schema changes are reviewed, versioned SQL; entity/schema drift fails at startup. |
| `spring.jpa.open-in-view=false` | No lazy loading in controllers; transactions end at the application service. |
| All instants in UTC (`Instant`, `TIMESTAMPTZ`, JDBC time zone UTC, `Clock` bean) | Unambiguous, testable time. `LocalDate` for calendar dates such as event date. |
| Uniform `ApiErrorResponse` via `GlobalExceptionHandler` | Stable `code` field for clients; no stack traces or internal messages leak. |
| Actuator exposes only `health` and `info` | Liveness/readiness probes available; nothing sensitive exposed. |
| Connection settings from env vars, no defaults in `application.yml` | Fail fast on misconfiguration; secrets never in the repo. |
| Integration tests on real PostgreSQL (Testcontainers), never H2 | Tests the SQL dialect, constraints and migrations we actually run. |
| Business errors extend `common.error.ApplicationException` with an `ErrorCategory` | Modules express *what* went wrong; only `GlobalExceptionHandler` knows HTTP statuses. |
| Stateless JWT access tokens + rotating opaque refresh tokens (Phase 2) | Mobile-friendly, no server session; refresh tokens stay revocable. Details in SECURITY.md. |
| Platform roles on the user; garage roles on garage membership | A global `MECHANIC` role cannot express "mechanic *at garage X*". |
| Multi-row invariants guarded by locking the aggregate root row (`findByIdForUpdate`) | e.g. "a garage keeps at least one admin" cannot be broken by two concurrent removals. |
| Memberships and other history-bearing rows are ended, never deleted | Past work stays attributable to the person and garage that did it. |
| `common.time.BusinessCalendar` for "now"/"today" | Instants in UTC; user-entered dates judged in Europe/Amsterdam (`repairtrack.business-time-zone`). |
| VIN is identity, license plate is an attribute | A vehicle keeps its history through plate changes and owners. |
| Entities get their UUID at construction; `@Version` on entities | ID is known before persisting (events, links); `@Version` gives optimistic locking and lets Spring Data detect new entities without an extra SELECT. |

## Domain model (target)

```text
User ─┬─ GarageUser ── Garage
      └─ VehicleOwnership ── Vehicle (VIN)
                               ├── RepairEvent ─┬─ RepairPart
                               │                ├─ Document (metadata; file in object storage)
                               │                └─ Verification
                               ├── MileageRecord
                               └── VehicleShare
AuditEvent (any entity)
```

Details are specified per phase and documented here as each module is implemented.

## Object storage

Documents will be stored in private S3-compatible object storage, accessed via presigned URLs
(Phase 6). The application codes against the S3 API only, so the provider is swappable.

**Note on MinIO:** in late 2025 MinIO stopped publishing community-edition Docker images and the
open-source repository was later archived. Before Phase 6 we choose the local S3-compatible
emulator deliberately (options: a pinned last-published MinIO image, or a maintained alternative
such as SeaweedFS, Garage or LocalStack). Production uses AWS S3 or another S3-compatible provider.

## Future service extraction

Candidate services: Identity, Vehicle, Repair, Garage, Document. Extraction path per module:
its events become broker messages (via outbox), its exposed interfaces become HTTP/gRPC APIs,
its tables move to its own database. ID-only references between modules make this feasible.
