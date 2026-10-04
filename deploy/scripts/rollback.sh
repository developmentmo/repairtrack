#!/usr/bin/env bash
# Starts the previously deployed version again, from the image already on this server (no rebuild):
#
#   /opt/repairtrack/production/scripts/rollback.sh            # back to PREVIOUS_VERSION
#   /opt/repairtrack/production/scripts/rollback.sh v1.3.2     # or to any earlier version
#
# The database is NOT rolled back: Flyway migrations are forward-only. Releases must keep the schema compatible
# with the previous version (see docs/operations.md, "Migrations and rollback"). If a migration broke data, restore
# the pre-deploy backup with restore-db.sh.

source "$(dirname "$0")/lib.sh"

TARGET="${1:-$(previous_version)}"
[[ -n "$TARGET" ]] || die "no previous version known (PREVIOUS_VERSION is empty); pass a version explicitly"
CURRENT="$(current_version)"
[[ "$TARGET" != "$CURRENT" ]] || die "$TARGET is already the current version"

log "rollback: $CURRENT -> $TARGET"
exec "$RT_SCRIPT_DIR/deploy.sh" "$TARGET" --by "rollback by ${USER:-unknown}${ROLLBACK_REASON:+: $ROLLBACK_REASON}" --no-auto-rollback
