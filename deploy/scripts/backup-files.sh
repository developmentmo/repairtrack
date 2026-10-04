#!/usr/bin/env bash
# Copies all stored documents and dispute evidence from this environment's Garage bucket to
# /opt/repairtrack/backups/<env>/files/ (incremental; rclone "copy" never deletes, documents are append-only).
# Then calls /opt/repairtrack/backup-offsite.sh <dir> <env> if present. Only for the stack's own Garage
# (with external S3, use that provider's versioning/replication instead).

source "$(dirname "$0")/lib.sh"
require_env_file

endpoint="$(env_value S3_ENDPOINT)"
[[ -z "$endpoint" || "$endpoint" == "http://garage:3900" ]] || { log "external S3 configured; skipping"; exit 0; }

dir="$RT_BACKUP_DIR/files"
mkdir -p "$dir"
creds="$(mktemp)"
trap 'rm -f "$creds"' EXIT
cat >"$creds" <<CREDS
RCLONE_CONFIG_SRC_TYPE=s3
RCLONE_CONFIG_SRC_PROVIDER=Other
RCLONE_CONFIG_SRC_ENDPOINT=http://garage:3900
RCLONE_CONFIG_SRC_REGION=garage
RCLONE_CONFIG_SRC_FORCE_PATH_STYLE=true
RCLONE_CONFIG_SRC_ACCESS_KEY_ID=$(env_value S3_ACCESS_KEY)
RCLONE_CONFIG_SRC_SECRET_ACCESS_KEY=$(env_value S3_SECRET_KEY)
CREDS

log "copying bucket $(env_value S3_BUCKET) to $dir"
docker run --rm --network "repairtrack-$RT_ENV-services" --env-file "$creds" --user "$(id -u):$(id -g)" \
  -v "$dir:/backup" rclone/rclone:1.71 copy "src:$(env_value S3_BUCKET)" /backup --checksum --stats-one-line \
  || die "file backup failed"
log "file backup ok: $(du -sh "$dir" | cut -f1)"

if [[ -x "$RT_ROOT/backup-offsite.sh" ]]; then
  "$RT_ROOT/backup-offsite.sh" "$dir" "$RT_ENV" || log "WARNING: off-site copy failed (the local copy is fine)"
fi
