#!/usr/bin/env bash
# What is running in this environment and where it came from.
source "$(dirname "$0")/lib.sh"
require_env_file
echo "environment: $RT_ENV"
echo "current:     $(current_version)"
echo "previous:    $(previous_version)"
echo
rt_compose ps
echo
echo "last deployments:"
tail -n 10 "$RT_ENV_DIR/deployments.log" 2>/dev/null || echo "(none)"
