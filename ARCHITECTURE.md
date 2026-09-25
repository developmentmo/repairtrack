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

Dependency direction: `api → application → domain ← infrastructure`.

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
