#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 24.04 LTS VPS for RepairTrack. Run as root; safe to run again.
#
#   sudo ./setup-vps.sh --deploy-key "ssh-ed25519 AAAA... github-actions" \
#        [--admin-user alice --admin-key "ssh-ed25519 AAAA... alice@laptop"] [--ssh-port 22] [--harden-ssh] [--swap 2G]
#
# Installs Docker Engine + Compose plugin, unattended security upgrades, ufw (only SSH, 80, 443) and fail2ban;
# creates the deploy user "repairtrack-deploy" (key-only, member of the docker group), /opt/repairtrack, the
# edge networks, Docker log rotation and the backup cron jobs. --harden-ssh disables password and root login,
# but only if a non-root admin with an SSH key exists (so you cannot lock yourself out).

set -Eeuo pipefail

DEPLOY_USER=repairtrack-deploy
ROOT_DIR=/opt/repairtrack
SSH_PORT=22
DEPLOY_KEY=""
ADMIN_USER=""
ADMIN_KEY=""
HARDEN_SSH=false
SWAP_SIZE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-key) DEPLOY_KEY="$2"; shift 2 ;;
    --admin-user) ADMIN_USER="$2"; shift 2 ;;
    --admin-key) ADMIN_KEY="$2"; shift 2 ;;
    --ssh-port) SSH_PORT="$2"; shift 2 ;;
    --harden-ssh) HARDEN_SSH=true; shift ;;
    --swap) SWAP_SIZE="$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ $EUID -eq 0 ]] || { echo "run as root (sudo)" >&2; exit 1; }
[[ -n "$DEPLOY_KEY" ]] || { echo "--deploy-key is required (public key of the GitHub Actions deploy key)" >&2; exit 1; }
[[ "$SSH_PORT" =~ ^[0-9]+$ ]] || { echo "--ssh-port must be a number" >&2; exit 1; }
. /etc/os-release
[[ "$ID" == "ubuntu" ]] || echo "WARNING: written for Ubuntu LTS, found $PRETTY_NAME"

step() { printf '\n==> %s\n' "$*"; }

add_key() { # add_key <user> <public key>
  local user="$1" key="$2" home
  home="$(getent passwd "$user" | cut -d: -f6)"
  install -d -m 700 -o "$user" -g "$user" "$home/.ssh"
  touch "$home/.ssh/authorized_keys"
  grep -qxF "$key" "$home/.ssh/authorized_keys" || printf '%s\n' "$key" >>"$home/.ssh/authorized_keys"
  chown "$user:$user" "$home/.ssh/authorized_keys"
  chmod 600 "$home/.ssh/authorized_keys"
}

step "packages and automatic security updates"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q ca-certificates curl gnupg ufw fail2ban unattended-upgrades openssl cron
cat >/etc/apt/apt.conf.d/20auto-upgrades <<'CONF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
CONF

step "Docker Engine and Compose plugin"
if ! command -v docker >/dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    >/etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
if [[ ! -f /etc/docker/daemon.json ]]; then
  # Rotate container logs (all logs go to stdout); keep containers running during a Docker upgrade.
  cat >/etc/docker/daemon.json <<'CONF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "5" },
  "live-restore": true
}
CONF
  systemctl restart docker
fi
systemctl enable --now docker

if [[ -n "$SWAP_SIZE" && ! -f /swapfile ]]; then
  step "swap file ($SWAP_SIZE)"
  fallocate -l "$SWAP_SIZE" /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi

step "deploy user $DEPLOY_USER"
id "$DEPLOY_USER" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash "$DEPLOY_USER"
passwd -l "$DEPLOY_USER" >/dev/null # key-only
# Note: the docker group is root-equivalent on this host. The key is only in GitHub's production/staging secrets.
usermod -aG docker "$DEPLOY_USER"
add_key "$DEPLOY_USER" "$DEPLOY_KEY"

if [[ -n "$ADMIN_USER" ]]; then
  step "admin user $ADMIN_USER (sudo)"
  id "$ADMIN_USER" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash --groups sudo "$ADMIN_USER"
  [[ -n "$ADMIN_KEY" ]] && add_key "$ADMIN_USER" "$ADMIN_KEY"
fi

step "directories under $ROOT_DIR"
for dir in "$ROOT_DIR" "$ROOT_DIR/edge" "$ROOT_DIR/garage" "$ROOT_DIR/staging" "$ROOT_DIR/production"; do
  install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$dir"
done
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$ROOT_DIR/backups"

step "Docker networks for the edge proxy"
for env in staging production; do
  docker network inspect "repairtrack-$env-edge" >/dev/null 2>&1 || docker network create "repairtrack-$env-edge"
done

step "firewall (ufw): SSH $SSH_PORT, HTTP 80, HTTPS 443"
ufw default deny incoming
ufw default allow outgoing
ufw allow "$SSH_PORT/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable
# Docker publishes ports past ufw; the stacks therefore publish nothing except Caddy (80/443) and staging's
# Mailpit UI on 127.0.0.1 only. PostgreSQL is never published.

step "fail2ban for SSH"
cat >/etc/fail2ban/jail.d/repairtrack-sshd.local <<CONF
[sshd]
enabled = true
port = $SSH_PORT
CONF
systemctl enable --now fail2ban
systemctl restart fail2ban

step "backup cron jobs (logs: journalctl -t repairtrack-backup)"
cat >/etc/cron.d/repairtrack <<CONF
# RepairTrack backups (UTC). Managed by setup-vps.sh.
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
15 2 * * * $DEPLOY_USER [ -f $ROOT_DIR/production/.env ] && $ROOT_DIR/production/scripts/backup-db.sh daily 2>&1 | logger -t repairtrack-backup
45 2 * * * $DEPLOY_USER [ -f $ROOT_DIR/production/.env ] && $ROOT_DIR/production/scripts/backup-files.sh 2>&1 | logger -t repairtrack-backup
30 3 * * * $DEPLOY_USER [ -f $ROOT_DIR/staging/.env ] && $ROOT_DIR/staging/scripts/backup-db.sh daily 2>&1 | logger -t repairtrack-backup
0 5 * * 0 $DEPLOY_USER [ -f $ROOT_DIR/production/.env ] && $ROOT_DIR/production/scripts/verify-backup.sh 2>&1 | logger -t repairtrack-backup
CONF
chmod 644 /etc/cron.d/repairtrack

if [[ "$HARDEN_SSH" == true ]]; then
  step "SSH hardening"
  admins_with_keys=0
  while IFS=: read -r user _ uid _ _ home _; do
    if [[ "$uid" -ge 1000 && "$user" != "$DEPLOY_USER" && -s "$home/.ssh/authorized_keys" ]] && id -nG "$user" | grep -qw sudo; then
      admins_with_keys=$((admins_with_keys + 1))
    fi
  done </etc/passwd
  if [[ "$admins_with_keys" -eq 0 ]]; then
    echo "SKIPPED: no sudo user with an SSH key found; add one (--admin-user/--admin-key) first, or you would lock yourself out."
  else
    cat >/etc/ssh/sshd_config.d/60-repairtrack.conf <<CONF
# Managed by setup-vps.sh
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
CONF
    [[ "$SSH_PORT" != 22 ]] && echo "Port $SSH_PORT" >>/etc/ssh/sshd_config.d/60-repairtrack.conf
    sshd -t
    systemctl reload ssh || systemctl reload sshd
    echo "Password and root login disabled. Test a NEW ssh session as $ADMIN_USER before closing this one."
  fi
fi

step "done"
cat <<NEXT
Next steps (see docs/operations.md, "New VPS"):
  1. Copy the deploy/ files to $ROOT_DIR (as $DEPLOY_USER).
  2. Create the env files:  $ROOT_DIR/production/scripts/init-env.sh  and  $ROOT_DIR/staging/scripts/init-env.sh
     and $ROOT_DIR/edge/.env from edge/.env.example; edit domains, IMAGE_REGISTRY and SMTP.
  3. Point the DNS records at this server, then run the CD workflow.
  4. The host key for the GitHub secret VPS_KNOWN_HOSTS:  ssh-keyscan -p $SSH_PORT <this-host>
NEXT
