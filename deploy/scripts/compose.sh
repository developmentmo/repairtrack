#!/usr/bin/env bash
# docker compose for this environment with the right env files, e.g.:
#   /opt/repairtrack/production/scripts/compose.sh ps
#   /opt/repairtrack/production/scripts/compose.sh logs -f --tail 200 backend
#   /opt/repairtrack/production/scripts/compose.sh restart backend
source "$(dirname "$0")/lib.sh"
require_env_file
rt_compose "$@"
