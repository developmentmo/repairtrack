#!/usr/bin/env bash
# Shared helpers for the RepairTrack operations scripts. Sourced, not executed.
#
# The scripts live in /opt/repairtrack/<environment>/scripts/ on the VPS (copied there by the CD workflow),
# so the environment is derived from their location: /opt/repairtrack/production/scripts/deploy.sh can only
# ever touch production.

set -Eeuo pipefail
umask 077

RT_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RT_ENV_DIR="$(dirname "$RT_SCRIPT_DIR")"
RT_ENV="$(basename "$RT_ENV_DIR")"
RT_ROOT="$(dirname "$RT_ENV_DIR")"
RT_BACKUP_DIR="$RT_ROOT/backups/$RT_ENV"

case "$RT_ENV" in
  staging | production) ;;
  *)
    echo "These scripts must run from /opt/repairtrack/<staging|production>/scripts (found: $RT_ENV_DIR)." >&2
    exit 2
    ;;
esac

log() { printf '%s [%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$RT_ENV" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

require_env_file() {
  [[ -f "$RT_ENV_DIR/.env" ]] || die "$RT_ENV_DIR/.env is missing. Create it with $RT_SCRIPT_DIR/init-env.sh."
  local mode
  mode="$(stat -c '%a' "$RT_ENV_DIR/.env")"
  [[ "$mode" == "600" || "$mode" == "400" ]] || die "$RT_ENV_DIR/.env must be chmod 600 (is $mode)."
}

# Reads one KEY=value from the stack's .env without evaluating the file as shell code.
env_value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$RT_ENV_DIR/.env" | tail -n 1 || true)"
  printf '%s' "${line#*=}"
}

# docker compose for this stack, with its secrets (.env) and its current image version (release.env).
rt_compose() {
  local args=(--project-directory "$RT_ENV_DIR" -f "$RT_ENV_DIR/docker-compose.yml" --env-file "$RT_ENV_DIR/.env")
  [[ -f "$RT_ENV_DIR/release.env" ]] && args+=(--env-file "$RT_ENV_DIR/release.env")
  docker compose "${args[@]}" "$@"
}

current_version() { cat "$RT_ENV_DIR/CURRENT_VERSION" 2>/dev/null || true; }
previous_version() { cat "$RT_ENV_DIR/PREVIOUS_VERSION" 2>/dev/null || true; }

# Release tags: sha-<7..40 hex> (every main commit) or vMAJOR.MINOR.PATCH[-pre] (releases).
valid_version() {
  [[ "$1" =~ ^(sha-[0-9a-f]{7,40}|v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?)$ ]]
}

ensure_edge_network() {
  docker network inspect "repairtrack-$RT_ENV-edge" >/dev/null 2>&1 \
    || docker network create "repairtrack-$RT_ENV-edge" >/dev/null
}

# One operation per environment at a time (deploy, rollback, restore).
acquire_lock() {
  exec 9>"$RT_ENV_DIR/.operation.lock"
  flock -n 9 || die "another deploy/rollback/restore is running for $RT_ENV"
}
