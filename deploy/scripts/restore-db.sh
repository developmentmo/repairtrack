#!/usr/bin/env bash
# REPLACES this environment's database with a backup. Destructive, so it asks for confirmation.
#
#   /opt/repairtrack/production/scripts/restore-db.sh /opt/repairtrack/backups/production/db/<file>.dump
#
# Steps: checksum + readability check -> safety backup of the current database -> stop the backend ->
# drop and recreate the database -> pg_restore -> start the backend and wait until it is healthy (Flyway then
# applies any migrations newer than the backup). Try the dump with verify-backup.sh first.

source "$(dirname "$0")/lib.sh"
require_env_file

file="${1:-}"
[[ -n "$file" && -f "$file" ]] || die "usage: $0 <backup.dump>"
if [[ -f "$file.sha256" ]]; then
  (cd "$(dirname "$file")" && sha256sum --quiet -c "$(basename "$file").sha256") || die "checksum mismatch: $file"
fi
rt_compose exec -T postgres pg_restore --list <"$file" >/dev/null || die "not a readable pg_dump file: $file"

echo "This REPLACES the $RT_ENV database with $(basename "$file")."
echo "The current database is backed up first. Type the environment name ($RT_ENV) to continue:"
read -r answer
[[ "$answer" == "$RT_ENV" ]] || die "cancelled"

acquire_lock
"$RT_SCRIPT_DIR/backup-db.sh" pre-restore || die "safety backup failed; nothing was changed"

log "stopping the backend"
rt_compose stop backend
log "recreating the database"
rt_compose exec -T postgres sh -c \
  'dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$POSTGRES_DB"'
log "restoring $(basename "$file")"
rt_compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges --exit-on-error' \
  <"$file" || die "restore failed: the database is incomplete. Restore the pre-restore backup the same way."
log "starting the backend"
rt_compose up -d --wait --wait-timeout 300 backend || die "backend not healthy after the restore; check: compose.sh logs backend"
printf '%s\trestored\t%s\tby=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(basename "$file")" "${USER:-unknown}" \
  >>"$RT_ENV_DIR/deployments.log"
log "restore complete"
