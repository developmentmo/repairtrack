# RepairTrack

RepairTrack is a **verifiable vehicle history platform**: a chronological, auditable record of maintenance and
repairs per vehicle (identified by VIN), with a server-determined source and verification status for every record.

## Repository layout

| Folder | Contents |
|---|---|
| [`backend/`](backend/) | Spring Boot modular monolith (Java 21, PostgreSQL, Flyway, S3-compatible storage). See [backend/README.md](backend/README.md). |
| [`app/`](app/) | Flutter app for iOS, Android and web (Riverpod, Dio, GoRouter). See [app/README.md](app/README.md). |

Backend documentation: [architecture](backend/ARCHITECTURE.md) · [database](backend/DATABASE.md) ·
[API](backend/API.md) · [security](backend/SECURITY.md)

## Quick start (backend)

```bash
cd backend
docker compose up -d
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Open `backend/pom.xml` as the project in IntelliJ.
