# RepairTrack: instructions for Claude

These instructions are for every Claude session in this repository, both interactive sessions and the agent pipeline
([docs/agents.md](docs/agents.md)). They are read on every run, so keep them short.

RepairTrack is a verifiable vehicle repair-history platform. It is a monorepo:

| Folder | What | Read first |
|---|---|---|
| `backend/` | Spring Boot 4 modular monolith: Java 21, Spring Modulith, PostgreSQL 18, Flyway, Jackson 3 (`tools.jackson`), S3 storage (Garage), ClamAV | `backend/ARCHITECTURE.md`, `DATABASE.md`, `API.md`, `SECURITY.md` |
| `app/` | Flutter app for iOS, Android and web: Riverpod 3, dio, go_router, json_serializable, mocktail | `app/README.md` |
| `deploy/`, `.github/`, `docs/operations.md` | infrastructure, CI/CD and operations | `docs/operations.md` |

## Rules that are never negotiable

1. Never trust roles, verification status, garage IDs or ownership claims sent by a client. The server determines
   them.
2. No hard deletes of history. Records are voided or corrected instead.
3. Important actions are written to the audit log (the `audit` module).
4. No secrets in code, configuration, tests, images or Git. No personal or sensitive data in logs: no email
   addresses, names, tokens or document contents.
5. The public report never exposes the owner's name, email address, address, internal IDs or private documents.
6. No microservices, Kafka, Kubernetes or premature abstractions. Follow the existing patterns of the module you
   change.
7. Respect the Spring Modulith module boundaries. Modules talk to each other only through a module's top-level API
   types and events. Every Spring bean class needs a unique simple name across the whole application.
8. Flyway: never edit an existing migration. Add `V<next>__<description>.sql` in
   `backend/src/main/resources/db/migration`, using the highest existing version + 1. A migration must be safe for
   the data that already exists in production.
9. Keep the API backwards compatible. Older app versions stay installed on phones. Add fields and endpoints; do not
   rename or remove them without a migration path.
10. Every behaviour change comes with tests. In the backend, that means unit tests, plus an integration test
    (Testcontainers) when the database is involved. In the app, that means unit or widget tests.
11. Text users see in the app is Dutch. Code, comments, commit messages and technical documentation are English.

## Commands

```bash
cd backend && ./mvnw --batch-mode --no-transfer-progress verify     # compile, unit, module and integration tests
cd app && flutter pub get && dart run build_runner build && flutter analyze && flutter test
```

Generated files (`*.g.dart`) are not committed. `flutter analyze` must report no issues at all, not even infos.
