# API

## Conventions

- Business endpoints live under `/api/v1`.
- JSON only. Request/response bodies are DTO records; JPA entities are never exposed.
- Timestamps: ISO-8601 UTC (`2026-09-20T10:30:00Z`). Dates: ISO-8601 (`2026-09-20`).
- Historical records are never deleted through the API; they are voided or corrected.

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
| `NOT_FOUND` | 404 | No such endpoint/resource |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported (with `Allow` header) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type not supported |
| `INTERNAL_ERROR` | 500 | Unexpected error (details only in server logs) |

Business codes (e.g. `INVALID_MILEAGE`) are added with the modules that raise them.

## Endpoints (Phase 1)

| Method | Path | Description |
|---|---|---|
| GET | `/actuator/health` | Overall health (includes database) |
| GET | `/actuator/health/liveness` | Liveness probe |
| GET | `/actuator/health/readiness` | Readiness probe |
| GET | `/actuator/info` | Build/app info |
