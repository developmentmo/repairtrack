# RepairTrack backend

RepairTrack is a **verifiable vehicle history platform**. It records maintenance and repairs as a
chronological, auditable history per vehicle (identified by VIN), where every record carries a clear
**source** (owner, garage, verified garage, official source) and a **verification status** that is
always determined server-side. Records are never silently changed or deleted: they are voided or
corrected, and every mutation is audited.

> Current state: **Phase 7 — public sharing.** Authentication, garages, vehicles and ownership, repair history (parts, void, corrections, mileage warnings, server-side verification), documents in S3-compatible storage with SHA-256 integrity, an append-only audit trail, and revocable share links that expose a privacy-safe public vehicle history. Next: Flutter app (Phase 8).

## Tech stack

Java 21 · Spring Boot 4.1 · Spring MVC · Spring Security (JWT) · Spring Data JPA / Hibernate · Flyway ·
PostgreSQL 18 · Bean Validation · Spring Boot Actuator · Spring Modulith (boundary verification) ·
JUnit · Mockito · Testcontainers · Maven

## Prerequisites

- JDK 21
- Maven: use the wrapper `./mvnw` (no separate installation needed)
- Docker (for PostgreSQL and for Testcontainers)

## Run locally

All commands below run from the `backend/` folder.

```bash
cd backend

# 1. Start PostgreSQL and Garage (S3-compatible document storage)
cp .env.example .env          # optional; defaults work without it
docker compose up -d
docker compose ps             # wait until postgres is "healthy"
curl -s localhost:3903/health # Garage: "Garage is fully operational"

# 2. Start the application with the local profile
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

In IntelliJ: use the shared run configuration **RepairTrack (local)** (`.run/`), which sets
`SPRING_PROFILES_ACTIVE=local`. Without that profile the app stops at startup with
`'url' must start with "jdbc"`: outside `local`, `DATABASE_URL` etc. must be set (fail fast by design).

**Without docker compose:** run `TestRepairTrackApplication` (in `src/test/java`). It starts a throwaway
PostgreSQL container through Testcontainers and wires it in automatically. Data is lost on stop.

### Verify

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP","components":{"db":{"status":"UP",...},...}}   (details shown in `local` profile only)

curl http://localhost:8081/actuator/health/readiness
```

### Try authentication

```bash
curl -s -X POST localhost:8081/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jan@example.nl","password":"correct horse battery staple","firstName":"Jan","lastName":"Jansen"}'

TOKEN=$(curl -s -X POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jan@example.nl","password":"correct horse battery staple"}' | jq -r .accessToken)

curl -s localhost:8081/api/v1/users/me -H "Authorization: Bearer $TOKEN"
curl -s localhost:8081/api/v1/users/me    # 401 {"code":"UNAUTHORIZED",...}
```

## Configuration

All environment-specific settings come from environment variables. `application.yml` has **no defaults**
for connection settings, so a misconfigured deployment fails at startup instead of connecting somewhere
unexpected. The `local` profile supplies defaults matching `docker-compose.yml`.

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | yes (outside `local`) | JDBC URL, e.g. `jdbc:postgresql://host:5432/repairtrack` |
| `DATABASE_USERNAME` | yes (outside `local`) | Database user |
| `DATABASE_PASSWORD` | yes (outside `local`) | Database password |
| `DATABASE_POOL_SIZE` | no (default 10) | Hikari maximum pool size |
| `SERVER_PORT` | no (default 8080; `local`: 8081) | HTTP port |
| `JWT_SECRET` | yes (outside `local`) | HS256 signing secret, >= 32 bytes (`openssl rand -base64 48`) |
| `JWT_ISSUER` | no (default `repairtrack`) | `iss` claim of access tokens |
| `JWT_ACCESS_TOKEN_TTL` | no (default `15m`) | Access-token lifetime |
| `REFRESH_TOKEN_TTL` | no (default `30d`) | Refresh-token lifetime |
| `S3_REGION`, `S3_BUCKET` | yes (outside `local`) | Document storage region and (private) bucket |
| `S3_ENDPOINT` | no | S3 API endpoint; empty = AWS default for the region |
| `S3_PUBLIC_ENDPOINT` | no | Endpoint used in presigned download URLs, if clients reach storage differently |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | no | Empty = AWS default credentials chain (e.g. IAM role) |
| `S3_PATH_STYLE_ACCESS` | no (default `true`) | Bucket in the URL path (required for Garage) |
| `PUBLIC_BASE_URL` | yes (outside `local`) | Base URL of the Flutter web app; share links are `{PUBLIC_BASE_URL}/v/{token}` (`local`: `http://localhost:5173`) |
| `MAIL_HOST`, `MAIL_PORT` | yes (outside `local`) / no (default 587) | SMTP server for account emails (`local`: Mailpit on `localhost:1025`) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | no | SMTP credentials |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | no (default `true`) | SMTP options |
| `MAIL_SMTP_SSL` | no (default `false`) | `true` for implicit TLS (port 465, e.g. TransIP) |
| `MAIL_FROM` | yes (outside `local`) | Sender, e.g. `RepairTrack <noreply@repairtrack.nl>` |
| `MALWARE_SCAN_MODE` | no (default `clamav`) | `clamav`: scan every upload (fail closed); `disabled`: development only |
| `CLAMAV_HOST`, `CLAMAV_PORT` | no (default `localhost:3310`) | clamd address |
| `INTEGRITY_SWEEP_CRON` | no (default `0 0 4 * * SUN`) | Weekly SHA-256 re-check of all stored documents (`-` disables) |
| `RDW_MODE` | no (default `rdw`) | `rdw`: look up public vehicle data by plate at RDW Open Data; `disabled`: lookups answer 503 |
| `RDW_BASE_URL` | no (default `https://opendata.rdw.nl`) | RDW Open Data host |
| `RDW_APP_TOKEN` | no | Optional Socrata app token for higher RDW rate limits |
| `RATE_LIMIT_ENABLED` | no (default `true`) | Per-IP limits on login, register, refresh, claim and public reports |
| `FORWARD_HEADERS_STRATEGY` | no (default `none`) | `framework` behind a trusted reverse proxy, so limits see the real client IP |
| `REFRESH_TOKEN_CLEANUP_CRON` | no (default `0 30 3 * * *`) | Daily deletion of expired refresh tokens (`-` disables) |
| `CORS_ALLOWED_ORIGINS` | no (default none) | Comma-separated browser origins allowed to call the API, i.e. the web app (`local`: `http://localhost:5173`) |

## Database

- Schema is managed **only** by Flyway (`src/main/resources/db/migration`). Hibernate runs with
  `ddl-auto=validate` and never changes the schema.
- `flyway clean` is disabled.
- Reset the local database: `docker compose down -v && docker compose up -d`.
- Connect: `docker exec -it repairtrack-postgres psql -U repairtrack -d repairtrack`

See [DATABASE.md](DATABASE.md).

## Tests

```bash
mvn test      # unit tests + module boundary verification (no Docker needed)
mvn verify    # + integration tests (*IT) against PostgreSQL via Testcontainers (Docker required)
```

## Object storage (Garage)

Documents are stored in S3-compatible object storage. Locally that is [Garage](https://garagehq.deuxfleurs.fr)
(`dxflrs/garage:v2.3.0`), started by `docker compose` as a single node that creates its bucket and access key on
startup (`--single-node --default-bucket`; config in `docker/garage/garage.toml`). Production can use AWS S3 or any
S3-compatible store; only configuration changes.

- S3 API: `http://localhost:3900` (region `garage`, path-style), admin/health: `http://localhost:3903/health`
- Local bucket `repairtrack-documents`, key `GK0123…cdef` (throwaway values, see `.env.example`)
- Reset all local documents: `docker compose down -v`
- Android emulator: set `S3_PUBLIC_ENDPOINT=http://10.0.2.2:3900` so presigned URLs point at the host

Integration tests start their own Garage container (Testcontainers), no setup needed.

## Malware scanning (ClamAV)

`docker compose` also starts ClamAV (`clamd` on `localhost:3310`). It needs about 1.5 GB of memory and downloads its
virus definitions on the first start, which takes a few minutes; until then uploads answer `503 SCANNER_UNAVAILABLE`.
Check it with `docker logs repairtrack-clamav` (wait for "socket found, clamd started"). If your machine cannot spare
the memory, run the backend with `MALWARE_SCAN_MODE=disabled` (development only). Integration tests use a fake scanner;
`ClamAvScannerTest` covers the clamd protocol.

## Email (Mailpit)

Locally, `docker compose` also starts [Mailpit](https://mailpit.axllent.org): it catches every email the app sends.
Open **http://localhost:8025** to read verification and password-reset mails and click their links (they open the
web app on `http://localhost:5173`). Integration tests record emails in memory instead (`RecordingAccountMailer`).

## Docker image

`docker build -t repairtrack-backend .` builds the production image (Maven build stage, JRE 21 runtime, non-root
user, health check on `/actuator/health/readiness`, which covers the app and the database). It contains no
configuration: every value in the table above comes from the environment. Deployment (staging/production, GHCR,
rollback, backups) is described in [../docs/operations.md](../docs/operations.md).

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — modular monolith, module boundaries, domain model, roadmap
- [DATABASE.md](DATABASE.md) — schema conventions and migrations
- [API.md](API.md) — REST conventions and endpoints
- [SECURITY.md](SECURITY.md) — security principles and current posture
