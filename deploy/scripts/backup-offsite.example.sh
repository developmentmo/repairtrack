#!/usr/bin/env bash
# EXAMPLE off-site copy. Install as /opt/repairtrack/backup-offsite.sh (chmod 700) to have every backup copied
# off the VPS; backup-db.sh and backup-files.sh call it with <file-or-directory> <environment>.
#
# This example uses rclone (in Docker) with a remote called "offsite" defined in /opt/repairtrack/rclone.conf
# (chmod 600), e.g. Backblaze B2:
#   [offsite]
#   type = b2
#   account = <keyID>
#   key = <applicationKey>
# Use a bucket with object lock / versioning, and a key that may only write: a compromised VPS then cannot
# delete the off-site copies.

set -Eeuo pipefail
src="$1"
env="$2"
bucket="repairtrack-backups" # change me

if [[ -d "$src" ]]; then
  target="offsite:$bucket/$env/files"
  mount_src="$src"
  what="."
else
  target="offsite:$bucket/$env/db"
  mount_src="$(dirname "$src")"
  what="$(basename "$src")"
fi

docker run --rm --user "$(id -u):$(id -g)" \
  -v /opt/repairtrack/rclone.conf:/config/rclone/rclone.conf:ro \
  -v "$mount_src:/src:ro" \
  rclone/rclone:1.71 copy "/src/$what" "$target" --no-traverse
