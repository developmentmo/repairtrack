#!/usr/bin/env bash
# Creates this environment's .env from .env.example with fresh random secrets (only if .env does not exist yet).
# Afterwards edit IMAGE_REGISTRY, the domains and (production) the SMTP settings.
source "$(dirname "$0")/lib.sh"

target="$RT_ENV_DIR/.env"
example="$RT_ENV_DIR/.env.example"
[[ ! -e "$target" ]] || die "$target already exists; not overwriting it"
[[ -f "$example" ]] || die "$example is missing (it is copied by the CD workflow or from the repository)"
command -v openssl >/dev/null || die "openssl is required"

hex() { openssl rand -hex "$1"; }
declare -A generated=(
  [POSTGRES_PASSWORD]="$(hex 24)"
  [JWT_SECRET]="$(hex 48)"
  [S3_ACCESS_KEY]="GK$(hex 16)"
  [S3_SECRET_KEY]="$(hex 32)"
  [GARAGE_RPC_SECRET]="$(hex 32)"
)

tmp="$(mktemp "$RT_ENV_DIR/.env.XXXXXX")"
while IFS= read -r line || [[ -n "$line" ]]; do
  key="${line%%=*}"
  if [[ "$line" == *"=__GENERATE__" && -n "${generated[$key]:-}" ]]; then
    printf '%s=%s\n' "$key" "${generated[$key]}"
  else
    printf '%s\n' "$line"
  fi
done <"$example" >"$tmp"
chmod 600 "$tmp"
mv "$tmp" "$target"
log "created $target with new secrets; now edit IMAGE_REGISTRY, the domains and the mail settings"
