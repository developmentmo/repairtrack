#!/usr/bin/env bash
# Read-only checks against a deployed environment, from outside (the CD workflow runs it after a deploy):
#
#   deploy/scripts/smoke-test.sh https://repairtrack.example.nl [https://api.repairtrack.example.nl]
#
# Nothing is created or changed: health, the web app, an authenticated endpoint without a token (401) and a
# public share lookup with a made-up token (404 from the database). Retries for up to ~2 minutes, because
# the proxy may still be obtaining a certificate on a first deploy.

set -Euo pipefail

app="${1:?usage: $0 <app-url> [api-url]}"
api="${2:-}"
app="${app%/}"
api="${api%/}"
failures=0
body="$(mktemp)"
trap 'rm -f "$body"' EXIT

# check <name> <url> <expected status> [<text the body must contain>]
check() {
  local name="$1" url="$2" expected="$3" needle="${4:-}" status=""
  for attempt in $(seq 1 12); do
    status="$(curl -sS -o "$body" -w '%{http_code}' --max-time 10 "$url" 2>/dev/null || true)"
    if [[ "$status" == "$expected" ]] && { [[ -z "$needle" ]] || grep -q -- "$needle" "$body"; }; then
      echo "ok    $name ($status)"
      return 0
    fi
    sleep 10
  done
  echo "FAIL  $name: expected $expected${needle:+ containing '$needle'}, got '${status:-no answer}'"
  head -c 300 "$body" 2>/dev/null; echo
  failures=$((failures + 1))
}

check "backend ready (app + database)" "$app/actuator/health/readiness" 200 '"UP"'
check "web app" "$app/" 200 'flutter'
check "deep link served by the web app" "$app/v/smoke-test" 200 'flutter'
check "API requires authentication" "$app/api/v1/vehicles" 401
check "public report lookup (database)" "$app/api/v1/public/vehicles/smoke-test-not-a-real-token" 404 'SHARE_NOT_FOUND'
check "actuator internals not exposed" "$app/actuator/env" 200 'flutter' # falls through to the web app
if [[ -n "$api" ]]; then
  check "API host ready" "$api/actuator/health/readiness" 200 '"UP"'
  check "API host serves no web app" "$api/" 404
fi

# Informational only: the overall health also includes the malware scanner, which needs a few minutes to load its
# signatures after a first start. Readiness (app + database) above is what decides.
overall="$(curl -s --max-time 10 "$app/actuator/health" || true)"
echo "info  overall health: ${overall:-no answer}"

if ((failures > 0)); then
  echo "$failures smoke test(s) failed"
  exit 1
fi
echo "all smoke tests passed"
