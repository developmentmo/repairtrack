#!/usr/bin/env bash
# PostgreSQL backup of this environment (pg_dump, custom format, compressed):
#
#   /opt/repairtrack/production/scripts/backup-db.sh [label]
#
# Written to /opt/repairtrack/backups/<env>/db/ (outside the containers and volumes), checked with pg_restore
# --list, with a .sha256 next to it. Local dumps older than BACKUP_RETENTION_DAYS (.env, default 14) are removed,
# but the 7 newest are always kept. If /opt/repairtrack/backup-offsite.sh exists and is executable, it is called
# with the dump file so it can be copied off the server (S3, Backblaze B2, ...): see backup-offsite.example.sh.

source "$(dirname "$0")/lib.sh"
require_env_file

label="${1:-}"
[[ -z "$label" || "$label" =~ ^[A-Za-z0-9._-]+$ ]] || die "label may only contain letters, digits, . _ -"
dir="$RT_BACKUP_DIR/db"
mkdir -p "$dir"
chmod 700 "$RT_ROOT/backups" "$RT_BACKUP_DIR" "$dir" 2>/dev/null || true

file="$dir/repairtrack-$RT_ENV-$(date -u +%Y%m%dT%H%M%SZ)${label:+-$label}.dump"
log "backing up database to $file"
rt_compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  >"$file.partial"
rt_compose exec -T postgres pg_restore --list <"$file.partial" >/dev/null || die "the dump is not readable"
mv "$file.partial" "$file"
(cd "$dir" && sha256sum "$(basename "$file")" >"$(basename "$file").sha256")
log "backup ok: $(du -h "$file" | cut -f1)"

if [[ -x "$RT_ROOT/backup-offsite.sh" ]]; then
  "$RT_ROOT/backup-offsite.sh" "$file" "$RT_ENV" || log "WARNING: off-site copy failed (the local backup is fine)"
fi

retention="$(env_value BACKUP_RETENTION_DAYS)"
retention="${retention:-14}"
[[ "$retention" =~ ^[0-9]+$ ]] || retention=14
# Never fewer than the 7 newest dumps, whatever their age.
mapfile -t old < <(ls -1t "$dir"/repairtrack-"$RT_ENV"-*.dump 2>/dev/null | tail -n +8)
for dump in "${old[@]}"; do
  if [[ -n "$(find "$dump" -mtime +"$retention" -print)" ]]; then
    rm -f -- "$dump" "$dump.sha256"
    log "removed expired backup $(basename "$dump")"
  fi
done
