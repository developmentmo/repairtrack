#!/usr/bin/env bash
# Restore test WITHOUT touching the running database: restores a dump into a throw-away PostgreSQL container
# (no network), checks the Flyway history and counts the main tables, and removes the container again.
#
#   /opt/repairtrack/production/scripts/verify-backup.sh              # newest dump
#   /opt/repairtrack/production/scripts/verify-backup.sh <file.dump>
#
# Runs weekly from cron (setup-vps.sh). A backup that has never been restored is not a backup.

source "$(dirname "$0")/lib.sh"

file="${1:-$(ls -1t "$RT_BACKUP_DIR"/db/repairtrack-"$RT_ENV"-*.dump 2>/dev/null | head -n 1)}"
[[ -n "$file" && -f "$file" ]] || die "no backup found"
if [[ -f "$file.sha256" ]]; then
  (cd "$(dirname "$file")" && sha256sum --quiet -c "$(basename "$file").sha256") || die "checksum mismatch: $file"
fi

name="repairtrack-verify-$RT_ENV-$$"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

log "restore test of $(basename "$file") in temporary container $name"
docker run -d --name "$name" --network none -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  -e POSTGRES_DB=verify postgres:18-alpine >/dev/null
for _ in $(seq 1 60); do
  docker exec "$name" pg_isready -U postgres -d verify >/dev/null 2>&1 && break
  sleep 1
done
docker exec -i "$name" pg_restore -U postgres -d verify --no-owner --no-privileges --exit-on-error <"$file" \
  || die "restore FAILED for $file"

docker exec "$name" psql -U postgres -d verify -v ON_ERROR_STOP=1 -Atc "
  select 'flyway_version=' || max(version::int) from flyway_schema_history where success;
  select 'users=' || count(*) from app_user;
  select 'vehicles=' || count(*) from vehicle;
  select 'repair_events=' || count(*) from repair_event;
  select 'documents=' || count(*) from document;
  select 'audit_events=' || count(*) from audit_event;" | while read -r line; do log "  $line"; done
log "restore test OK"
