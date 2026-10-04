#!/usr/bin/env bash
# Deploys one image version to THIS environment (derived from the script's location):
#
#   /opt/repairtrack/production/scripts/deploy.sh v1.2.0 [--by "<who/what>"]
#   /opt/repairtrack/staging/scripts/deploy.sh sha-1a2b3c4
#
# 1. pull the backend and web images of that version (or use them if already present: rollbacks work offline)
# 2. production: back up the database first (Flyway migrations run when the new backend starts)
# 3. write release.env and (re)start the stack; the edge proxy is started/reloaded too
# 4. wait until the backend container is healthy (actuator readiness: app started + database reachable)
# 5. success: CURRENT_VERSION / PREVIOUS_VERSION updated, one line appended to deployments.log
#    failure: the previous version is started again automatically, and the script exits non-zero
#
# Nothing is ever deleted: no volume, image or database is removed by this script.

source "$(dirname "$0")/lib.sh"

usage() { echo "usage: $0 <sha-xxxxxxx|vX.Y.Z> [--by <text>] [--no-auto-rollback]" >&2; exit 2; }

[[ $# -ge 1 ]] || usage
VERSION="$1"
shift
DEPLOYED_BY="${USER:-unknown}@$(hostname)"
AUTO_ROLLBACK=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --by) DEPLOYED_BY="${2:?--by needs a value}"; shift 2 ;;
    --no-auto-rollback) AUTO_ROLLBACK=false; shift ;;
    *) usage ;;
  esac
done
valid_version "$VERSION" || die "not a release version: $VERSION (expected sha-<hex> or vX.Y.Z)"

HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"

acquire_lock
require_env_file
ensure_edge_network

REGISTRY="$(env_value IMAGE_REGISTRY)"
[[ -n "$REGISTRY" ]] || die "IMAGE_REGISTRY is not set in $RT_ENV_DIR/.env"
FROM="$(current_version)"

# Makes VERSION's images available locally. Pull first; fall back to a local copy (e.g. a rollback after the
# registry login expired).
fetch_images() {
  local version="$1" image
  for image in repairtrack-backend repairtrack-web; do
    if ! docker pull --quiet "$REGISTRY/$image:$version" >/dev/null; then
      docker image inspect "$REGISTRY/$image:$version" >/dev/null 2>&1 \
        || die "image $REGISTRY/$image:$version is not in the registry and not on this server"
      log "using local copy of $image:$version (pull failed)"
    fi
  done
}

write_release() {
  printf 'RELEASE_TAG=%s\n' "$1" >"$RT_ENV_DIR/release.env.tmp"
  mv "$RT_ENV_DIR/release.env.tmp" "$RT_ENV_DIR/release.env"
}

backend_health() {
  local id
  id="$(rt_compose ps -q backend)"
  [[ -n "$id" ]] || { echo "missing"; return; }
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id"
}

wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) status
  while ((SECONDS < deadline)); do
    status="$(backend_health)"
    case "$status" in
      healthy) return 0 ;;
      unhealthy) log "backend reported unhealthy"; return 1 ;;
    esac
    sleep 5
  done
  log "backend not healthy after ${HEALTH_TIMEOUT}s (last status: $status)"
  return 1
}

start_edge() {
  local edge="$RT_ROOT/edge"
  [[ -f "$edge/.env" ]] || { log "edge/.env missing: the reverse proxy is not started (see docs/operations.md)"; return 0; }
  docker compose --project-directory "$edge" -f "$edge/docker-compose.yml" --env-file "$edge/.env" up -d --quiet-pull
  # Apply a changed Caddyfile without downtime; an invalid file is rejected and the running config stays.
  docker compose --project-directory "$edge" -f "$edge/docker-compose.yml" --env-file "$edge/.env" \
    exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null \
    || log "WARNING: Caddyfile reload failed; the previous proxy configuration is still active"
}

activate() {
  local version="$1"
  write_release "$version"
  rt_compose up -d --remove-orphans --quiet-pull
}

record() {
  local digest revision
  digest="$(docker image inspect --format '{{index .RepoDigests 0}}' "$REGISTRY/repairtrack-backend:$VERSION" 2>/dev/null || echo unknown)"
  revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' \
    "$REGISTRY/repairtrack-backend:$VERSION" 2>/dev/null || echo unknown)"
  if [[ -n "$FROM" && "$FROM" != "$VERSION" ]]; then
    printf '%s\n' "$FROM" >"$RT_ENV_DIR/PREVIOUS_VERSION"
  fi
  printf '%s\n' "$VERSION" >"$RT_ENV_DIR/CURRENT_VERSION"
  printf '%s\tdeployed\t%s\tfrom=%s\tcommit=%s\timage=%s\tby=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$VERSION" \
    "${FROM:-none}" "$revision" "$digest" "$DEPLOYED_BY" >>"$RT_ENV_DIR/deployments.log"
}

log "deploying $VERSION (current: ${FROM:-none})"
fetch_images "$VERSION"

if [[ "$RT_ENV" == "production" && -n "$(rt_compose ps -q postgres 2>/dev/null)" ]]; then
  log "pre-deploy database backup"
  "$RT_SCRIPT_DIR/backup-db.sh" "pre-deploy-$VERSION" || die "pre-deploy backup failed; nothing was changed"
fi

activate "$VERSION"
start_edge

if wait_healthy; then
  record
  log "deployed $VERSION: backend healthy"
  exit 0
fi

log "deployment of $VERSION FAILED; last backend log lines:"
rt_compose logs --no-color --tail 60 backend || true
printf '%s\tfailed\t%s\tfrom=%s\tby=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$VERSION" "${FROM:-none}" \
  "$DEPLOYED_BY" >>"$RT_ENV_DIR/deployments.log"

if [[ "$AUTO_ROLLBACK" == true && -n "$FROM" && "$FROM" != "$VERSION" ]]; then
  log "rolling back to $FROM"
  activate "$FROM"
  if wait_healthy; then
    printf '%s\trolled-back\t%s\tfailed=%s\tby=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$FROM" "$VERSION" \
      "$DEPLOYED_BY" >>"$RT_ENV_DIR/deployments.log"
    log "rolled back to $FROM (healthy). If $VERSION ran a database migration, check docs/operations.md."
  else
    log "ROLLBACK TO $FROM IS NOT HEALTHY EITHER: manual action needed (docs/operations.md, troubleshooting)"
  fi
fi
exit 1
