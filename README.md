# RepairTrack

RepairTrack is a **verifiable vehicle history platform**: a chronological, auditable record of maintenance and
repairs per vehicle (identified by VIN), with a server-determined source and verification status for every record.

## Repository layout

| Folder | Contents |
|---|---|
| [`backend/`](backend/) | Spring Boot modular monolith (Java 21, PostgreSQL, Flyway, S3-compatible storage). See [backend/README.md](backend/README.md). |
| [`frontend/`](frontend/) | Flutter app for iOS, Android and web (Riverpod, Dio, GoRouter). See [frontend/README.md](frontend/README.md). |
| [`deploy/`](deploy/) | Docker Compose stacks for staging and production, the Caddy edge proxy and the operations scripts. |
| [`docs/operations.md`](docs/operations.md) | Deployment, rollback, backups, VPS setup, secrets, DNS, troubleshooting. |
| [`docs/agents.md`](docs/agents.md) | The AI agent pipeline: Slack triage, backend and frontend agents, review and merge. |
| [`.github/workflows/`](.github/workflows/) | CI, CD, deployment and mobile release workflows. |

Backend documentation: [architecture](backend/ARCHITECTURE.md) · [database](backend/DATABASE.md) ·
[API](backend/API.md) · [security](backend/SECURITY.md)

## Development

```bash
# Backend: infrastructure in Docker (PostgreSQL, Garage, ClamAV, Mailpit), the application from the IDE
cd backend
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run      # or the shared IntelliJ run configuration

# App (other terminal)
cd frontend
flutter pub get && dart run build_runner build --delete-conflicting-outputs
flutter run -d chrome --web-port 5173                    # or an iOS/Android device
```

Open `backend/pom.xml` as the project in IntelliJ. Mail sent locally: http://localhost:8025.

## Testing

```bash
cd backend && ./mvnw verify                 # unit, module and integration tests (Testcontainers needs Docker)
cd frontend && flutter analyze && flutter test
```

## Docker

| Image | Build | Contents |
|---|---|---|
| `repairtrack-backend` | `docker build -t repairtrack-backend backend` | multi-stage: Maven build, then JRE 21 only; non-root; health check on `/actuator/health/readiness`; configured only through environment variables |
| `repairtrack-web` | `cd frontend && flutter build web --release --dart-define=API_BASE_URL=same-origin && docker build -t repairtrack-web .` | the Flutter web build served by unprivileged nginx (SPA fallback) |

Images are published to GitHub Container Registry as `ghcr.io/<owner>/repairtrack-backend` and
`ghcr.io/<owner>/repairtrack-web`, tagged `sha-<commit>` and, for releases, `vX.Y.Z`. There is no `latest`.

## CI/CD

| Workflow | Trigger | What it does |
|---|---|---|
| [`ci.yml`](.github/workflows/ci.yml) | pull request (and called by CD) | backend `mvnw verify`, app analyze + test, Docker build check |
| [`cd.yml`](.github/workflows/cd.yml) | push to `main`, tag `vX.Y.Z`, manual | tests -> images to GHCR -> staging -> approval -> production; manual: redeploy/rollback an existing version |
| [`deploy.yml`](.github/workflows/deploy.yml) | called by CD | sync files over SSH, `deploy.sh`, health check, external smoke tests, rollback on failure |
| [`mobile-release.yml`](.github/workflows/mobile-release.yml) | tag `vX.Y.Z`, manual | Android `.aab`/`.apk` and iOS build, attached to the GitHub release |
| [`agent-*.yml`](docs/agents.md) | issue labels, CI results | AI agents: implement issues, fix, review and merge agent PRs (see [docs/agents.md](docs/agents.md)) |

## Deployment

```text
pull request -> CI -> merge main -> staging (automatic) -> smoke tests -> approval -> production
```

Normal releases need no SSH. A versioned release:

```bash
git tag -a v1.0.0 -m "RepairTrack 1.0.0"
git push origin v1.0.0
```

Setting up a new VPS, the GitHub environments/secrets and DNS: [docs/operations.md](docs/operations.md).

## Environments

| | staging | production |
|---|---|---|
| Deployed | every merge to `main` | after approval (main or release tag) |
| Hosts | `staging.<domain>`, `api.staging.<domain>`, `files.staging.<domain>` | `<domain>`, `api.<domain>`, `files.<domain>` |
| Database, storage, secrets | own | own |
| E-mail | Mailpit (nothing is delivered) | SMTP provider |

Both run as separate Docker Compose projects (`repairtrack-staging`, `repairtrack-production`) behind one Caddy
proxy on the same VPS, with separate networks, volumes and `.env` files.

## Production

Production runs an immutable image version (`sha-…` or `vX.Y.Z`), recorded with commit, image digest and time
in `/opt/repairtrack/production/deployments.log`. Status on the server:
`/opt/repairtrack/production/scripts/status.sh`. Health: `https://<domain>/actuator/health/readiness`.

## Rollback

GitHub: Actions -> CD -> Run workflow -> `production` + the previous version. On the server:

```bash
/opt/repairtrack/production/scripts/rollback.sh          # previous version, from the local image
```

The database is not rolled back; releases keep migrations backward compatible ([details](docs/operations.md#database-migrations-and-rollback)).

## Backups

Daily database dumps and document copies, a dump before every production deployment, and a weekly automatic
restore test; optional off-site copy (S3/B2). Restore:

```bash
/opt/repairtrack/production/scripts/restore-db.sh /opt/repairtrack/backups/production/db/<file>.dump
```

See [docs/operations.md](docs/operations.md#backups-and-restore).
