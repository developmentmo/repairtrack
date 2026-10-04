# RepairTrack operations

How RepairTrack is built, released, deployed, rolled back and backed up. Everything here is reproducible from the
repository; the only things that are not in git are the secrets (`.env` files on the server, GitHub secrets).

- [Overview](#overview)
- [GitHub setup](#github-setup) (environments, secrets, variables, reviewers)
- [New VPS](#new-vps) (setup commands, DNS, first deployment)
- [Releasing and deploying](#releasing-and-deploying)
- [Rollback](#rollback)
- [Database migrations and rollback](#database-migrations-and-rollback)
- [Backups and restore](#backups-and-restore)
- [Logs, health and status](#logs-health-and-status)
- [Security](#security)
- [Mobile releases](#mobile-releases)
- [Troubleshooting](#troubleshooting)
- [Pipeline acceptance test](#pipeline-acceptance-test)
- [Growing beyond one VPS](#growing-beyond-one-vps)

## Overview

```text
developer -> pull request -> CI (tests, Docker build check)
          -> merge to main -> CD: tests -> images to GHCR (sha-<commit>) -> staging (automatic)
                                 -> health + smoke tests -> approval -> production -> health + smoke tests
          -> tag vX.Y.Z    -> CD: same, images also tagged vX.Y.Z; Mobile release: Android/iOS builds
```

On the VPS (Ubuntu LTS, Docker Compose):

```text
Internet --443--> Caddy (edge, TLS by Let's Encrypt, no access log)
                    |-- repairtrack.example.nl         -> production web (nginx) + /api, /actuator/health -> backend
                    |-- api.repairtrack.example.nl     -> production backend (mobile apps)
                    |-- files.repairtrack.example.nl   -> production Garage, GET/HEAD only (presigned downloads)
                    '-- staging.*, api.staging.*, files.staging.*  -> the staging stack

production stack (Compose project repairtrack-production)      staging stack (repairtrack-staging): same, plus
  backend  (Spring Boot, Flyway runs at startup)                 Mailpit instead of real e-mail, ClamAV optional
  web      (Flutter web build, nginx, non-root, read-only)
  postgres (volume repairtrack-production-postgres-data, internal network only, no published port)
  garage   (S3 object storage, volumes ...-garage-meta/-data)
  clamav   (malware scanning of uploads)
```

| Item | Where |
|---|---|
| Images | `ghcr.io/<owner>/repairtrack-backend`, `ghcr.io/<owner>/repairtrack-web`, tags `sha-<7 chars>` and `vX.Y.Z` (never `latest`) |
| Server files | `/opt/repairtrack/{edge,garage,staging,production,backups}` |
| Per environment | `docker-compose.yml`, `.env` (secrets, chmod 600), `release.env` (running version), `CURRENT_VERSION`, `PREVIOUS_VERSION`, `deployments.log`, `scripts/` |
| Backups | `/opt/repairtrack/backups/<env>/db/*.dump` (+ `.sha256`), `/opt/repairtrack/backups/<env>/files/` |

Staging and production share nothing but the VPS and the Caddy container: separate Compose projects, networks
(`repairtrack-<env>-db|services|edge`), volumes, `.env` files and secrets. Staging can never reach the production
database (different internal network, different credentials).

**Server size.** Both stacks with ClamAV need about 6 GB RAM: 8 GB RAM / 2–4 vCPU / 80 GB disk is a comfortable
start. On a 4 GB VPS, turn ClamAV off for staging (`COMPOSE_PROFILES=` and `MALWARE_SCAN_MODE=disabled` in
`staging/.env`) and add swap (`setup-vps.sh --swap 2G`). Production always scans uploads.

## GitHub setup

### Environments (Settings -> Environments)

Create two environments, `staging` and `production`.

- **production**: tick *Required reviewers* and add yourself (and whoever may release). Optionally *Prevent
  self-review* off for a one-person team, and under *Deployment branches and tags* choose *Selected branches and
  tags*: `main` and `v*`.
- **staging**: no reviewers (deploys automatically).

### Secrets and variables

| Name | Kind | Where | Value |
|---|---|---|---|
| `VPS_HOST` | secret | staging + production environment | VPS host name or IP |
| `VPS_USER` | secret | staging + production | `repairtrack-deploy` |
| `VPS_PORT` | secret | staging + production | `22` (or your SSH port) |
| `VPS_SSH_KEY` | secret | staging + production | private key of the deploy key pair (see below) |
| `VPS_KNOWN_HOSTS` | secret | staging + production | output of `ssh-keyscan -p <port> <host>` (pins the host key) |
| `APP_DOMAIN` | variable | staging: `staging.repairtrack.example.nl`, production: `repairtrack.example.nl` | web app host |
| `API_DOMAIN` | variable | staging: `api.staging.repairtrack.example.nl`, production: `api.repairtrack.example.nl` | API host |
| `MOBILE_API_BASE_URL` | variable | repository | `https://api.repairtrack.example.nl` (mobile builds) |
| `PRODUCTION_FROM_MAIN` | variable | repository, optional | `false` = only release tags go to production |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | secrets | repository, optional | Android upload key (see [Mobile releases](#mobile-releases)) |

Why where:

- **GitHub environment secrets** are what a deployment needs to reach the server. They are only given to jobs of
  that environment, so production's key is only used after the production approval. Staging and production can use
  different keys (recommended), even on the same server.
- **GitHub variables** are not secret (host names) and show up in logs and URLs.
- **VPS `.env` files** hold the application secrets (database password, JWT secret, storage keys, SMTP). They never
  leave the server: not in git, not in images, not in GitHub. A GitHub compromise therefore does not leak them.
- **GHCR** needs no secret: workflows push with the run's `GITHUB_TOKEN`; at deploy time the VPS is logged in with
  that same short-lived token and logged out again.

Deploy key (on your laptop):

```bash
ssh-keygen -t ed25519 -N '' -C 'repairtrack-deploy production' -f repairtrack-deploy-production
# public key  -> setup-vps.sh --deploy-key "$(cat repairtrack-deploy-production.pub)"
# private key -> GitHub secret VPS_SSH_KEY (production environment); then delete the local copy
```

## New VPS

Ubuntu 24.04 LTS, with your own SSH key for root (or a sudo user) as the provider sets it up.

```bash
# On your laptop, in the repository:
scp deploy/scripts/setup-vps.sh root@<vps>:/root/

# On the VPS as root:
bash /root/setup-vps.sh \
  --deploy-key "ssh-ed25519 AAAA... repairtrack-deploy production" \
  --admin-user <you> --admin-key "ssh-ed25519 AAAA... you@laptop" \
  --swap 2G
# add the staging deploy key too (second key for the same user):
bash /root/setup-vps.sh --deploy-key "ssh-ed25519 AAAA... repairtrack-deploy staging"
# after checking that "ssh <you>@<vps>" + "sudo -v" works in a new terminal:
bash /root/setup-vps.sh --deploy-key "ssh-ed25519 AAAA... repairtrack-deploy production" --harden-ssh
```

`setup-vps.sh` is idempotent. It installs Docker (official repository), unattended security upgrades, ufw
(SSH, 80, 443 only), fail2ban, Docker log rotation, the user `repairtrack-deploy` (key login only, in the
`docker` group), `/opt/repairtrack`, the edge networks and the backup cron jobs. `--harden-ssh` disables password
and root login, but refuses to do so if no sudo user with a key exists.

Copy the deployment files once (afterwards every deployment syncs them):

```bash
# On your laptop, in the repository:
VPS=repairtrack-deploy@<vps>
tar -C deploy -czf - edge garage | ssh $VPS 'tar -xzf - -C /opt/repairtrack'
for env in staging production; do
  ssh $VPS "mkdir -p /opt/repairtrack/$env/scripts"
  scp deploy/$env/docker-compose.yml deploy/$env/.env.example $VPS:/opt/repairtrack/$env/
  scp deploy/scripts/*.sh $VPS:/opt/repairtrack/$env/scripts/
done
```

Create the configuration (on the VPS as `repairtrack-deploy`):

```bash
/opt/repairtrack/production/scripts/init-env.sh   # random secrets into production/.env
/opt/repairtrack/staging/scripts/init-env.sh      # different random secrets into staging/.env
nano /opt/repairtrack/production/.env   # IMAGE_REGISTRY=ghcr.io/<owner>, APP_DOMAIN, FILES_DOMAIN, MAIL_*
nano /opt/repairtrack/staging/.env      # IMAGE_REGISTRY, APP_DOMAIN, FILES_DOMAIN
cp /opt/repairtrack/edge/.env.example /opt/repairtrack/edge/.env && chmod 600 /opt/repairtrack/edge/.env
nano /opt/repairtrack/edge/.env         # ACME_EMAIL and the six host names
```

Keep a copy of both `.env` files in your password manager: without `JWT_SECRET` all sessions end, and without the
storage keys the documents cannot be read.

### DNS

Six records, all pointing at the VPS (A for IPv4, plus AAAA if the VPS has IPv6):

| Name | Type | Value |
|---|---|---|
| `repairtrack.example.nl` | A | VPS IPv4 |
| `api.repairtrack.example.nl` | A | VPS IPv4 |
| `files.repairtrack.example.nl` | A | VPS IPv4 |
| `staging.repairtrack.example.nl` | A | VPS IPv4 |
| `api.staging.repairtrack.example.nl` | A | VPS IPv4 |
| `files.staging.repairtrack.example.nl` | A | VPS IPv4 |

Caddy requests the certificates on the first start; ports 80 and 443 must be reachable. If the domain has a CAA
record, it must allow `letsencrypt.org`.

### First deployment

Push to `main` (or Actions -> CD -> Run workflow, with an existing version). The first staging deployment starts
Caddy, Postgres, Garage, ClamAV, Mailpit, backend and web. ClamAV needs a few minutes to download its signatures;
uploads answer `503 SCANNER_UNAVAILABLE` until then, the rest works. Create the first system admin afterwards
(`backend/README.md`, user administration) with `scripts/compose.sh exec postgres psql ...`.

## Releasing and deploying

Normal work never needs SSH:

1. Pull request -> CI must be green.
2. Merge to `main` -> CD builds `sha-<commit>` images and deploys them to **staging** automatically, followed by
   health checks and smoke tests.
3. The production job waits for approval (Actions -> the run -> *Review deployments*). Approve -> the same images
   go to **production** (immutable `sha-` tag, so production runs exactly what staging tested).

A versioned release (Semantic Versioning, `MAJOR.MINOR.PATCH`):

```bash
git switch main && git pull
git tag -a v1.0.0 -m "RepairTrack 1.0.0"
git push origin v1.0.0
```

The tag builds images `v1.0.0` (+ `sha-<commit>`), deploys staging, waits for approval, deploys production, and the
mobile workflow attaches Android/iOS builds to the GitHub release `v1.0.0`. Use MAJOR for incompatible API changes
(old mobile apps break), MINOR for features, PATCH for fixes.

Traceability: `deployments.log` on the server records time, version, previous version, commit, image digest and the
GitHub run; the images carry `org.opencontainers.image.revision`/`version` labels; GitHub keeps the deployment
history per environment (repository home page -> *Deployments*).

What a deployment does on the server (`scripts/deploy.sh <version>`): pull images -> production: database backup ->
write `release.env` -> `docker compose up -d` -> wait until the backend is healthy (max 5 minutes) -> record it. If the
backend does not become healthy, the previous version is started again automatically and the job fails. If the
backend is healthy but the external smoke tests fail, the workflow runs `rollback.sh`. A deployment has a short
downtime (the backend restarts, ~30–60 s); see [Growing beyond one VPS](#growing-beyond-one-vps) for zero downtime.

Manual deployment (rarely needed): Actions -> CD -> Run workflow -> environment + version, or on the server:

```bash
/opt/repairtrack/production/scripts/deploy.sh v1.2.0
```

## Rollback

From GitHub (preferred, leaves a trace): Actions -> CD -> Run workflow -> `production`, version = the previous
version (see `deployments.log` or the Deployments page). Production still asks for approval.

On the server (images are kept locally, so this also works when GitHub or GHCR is down):

```bash
/opt/repairtrack/production/scripts/status.sh          # current / previous version, containers, last deployments
/opt/repairtrack/production/scripts/rollback.sh        # back to PREVIOUS_VERSION
/opt/repairtrack/production/scripts/rollback.sh v1.3.2 # or a specific earlier version
```

A rollback restarts the old images; it does not rebuild anything and does not touch the database.

## Database migrations and rollback

Flyway runs pending migrations when the new backend starts; `ddl-auto` is `validate` and `flyway clean` is
disabled, so a deployment can never reset the database. Migrations are forward-only, therefore:

- **Keep every release compatible with the previous version's code** (expand/contract): add columns/tables first
  (nullable or with defaults), switch the code in a next release, remove old columns only in a later release.
  Then `rollback.sh` always works.
- Every production deployment makes a database backup first (`backups/production/db/*-pre-deploy-<version>.dump`).
- If a release with a breaking migration must be undone: `rollback.sh` the code, then restore the pre-deploy dump
  with `restore-db.sh` (loses data written since the deployment, so only for real emergencies).
- A failing migration stops the backend from starting -> the deployment is rolled back automatically; Flyway
  records the failed migration. Fix it in a new release (never edit an applied migration).

## Backups and restore

Automatic (cron, set up by `setup-vps.sh`, UTC):

| When | What |
|---|---|
| daily 02:15 | production database dump (`backup-db.sh`) |
| daily 02:45 | production documents/evidence copy (`backup-files.sh`, incremental) |
| daily 03:30 | staging database dump |
| Sunday 05:00 | restore test of the newest production dump in a throw-away container (`verify-backup.sh`) |
| every production deployment | database dump before the new version starts |

Dumps are `pg_dump` custom format with a `.sha256`, checked after writing. Retention: `BACKUP_RETENTION_DAYS` in
`.env` (production 30, staging 7); the 7 newest dumps are always kept. Logs: `journalctl -t repairtrack-backup`.

Manual:

```bash
/opt/repairtrack/production/scripts/backup-db.sh before-maintenance   # database
/opt/repairtrack/production/scripts/backup-files.sh                   # documents
/opt/repairtrack/production/scripts/verify-backup.sh                  # restore test (newest dump, no impact)
```

Restore (replaces the database; asks to type the environment name, makes a safety dump first, stops the backend,
restores, starts the backend and waits until it is healthy):

```bash
ls -lt /opt/repairtrack/backups/production/db/
/opt/repairtrack/production/scripts/verify-backup.sh /opt/repairtrack/backups/production/db/<file>.dump
/opt/repairtrack/production/scripts/restore-db.sh /opt/repairtrack/backups/production/db/<file>.dump
```

Documents: copy the files back into the bucket with rclone (`backup-files.sh` shows the configuration; use
`copy /backup src:<bucket>` in the other direction).

**Off-site.** Backups on the same VPS do not survive losing the VPS. Install an off-site hook:

```bash
cp /opt/repairtrack/production/scripts/backup-offsite.example.sh /opt/repairtrack/backup-offsite.sh
chmod 700 /opt/repairtrack/backup-offsite.sh   # edit the bucket; create /opt/repairtrack/rclone.conf (chmod 600)
```

Every database dump and file backup is then copied with rclone to S3 / Backblaze B2 / any rclone remote. Use a
bucket with versioning or object lock and a write-only key. Also enable the provider's VPS snapshots if available.

## Logs, health and status

All containers log to stdout; Docker rotates the logs (10 MB x 5 per container).

```bash
S=/opt/repairtrack/production/scripts
$S/status.sh                            # versions, containers, last deployments
$S/compose.sh logs -f --tail 200 backend
$S/compose.sh logs --since 1h postgres
docker logs -f repairtrack-caddy        # proxy / certificates
cat /opt/repairtrack/production/deployments.log
journalctl -t repairtrack-backup --since today
curl -s https://repairtrack.example.nl/actuator/health/readiness   # {"status":"UP"} = app + database
```

`/actuator/health/readiness` (app started + database) is what deployments wait for; `/actuator/health` also
includes storage and the malware scanner. Details are never shown publicly; other actuator endpoints are not
exposed (Caddy only forwards `/actuator/health*`). Staging mail: `ssh -L 8026:127.0.0.1:8026 <you>@<vps>` and open
http://localhost:8026.

## Security

- SSH: keys only, no root login (`--harden-ssh`), fail2ban; deployments use `repairtrack-deploy` with a key that
  exists only in the GitHub environment secret, and a pinned host key (`VPS_KNOWN_HOSTS`).
- Firewall: only SSH, 80 and 443. No container publishes a port except Caddy (staging Mailpit on 127.0.0.1 only).
  PostgreSQL is on an internal Docker network without internet access and is never published.
- Secrets: only in `/opt/repairtrack/<env>/.env` (chmod 600, owner `repairtrack-deploy`), different per
  environment; never in git (`.gitignore`), images or workflow logs. Images contain no configuration.
- Containers: backend and web run as non-root; the web container is read-only; Docker logs are size-limited.
- Updates: unattended security upgrades for Ubuntu; base images are refreshed with every build.
- TLS: Caddy, automatic Let's Encrypt, HSTS. No access log (share-link tokens are in paths).
- Least privilege in workflows: `contents: read`, `packages: write` only for the image jobs, `packages: read`
  for deployments, `contents: write` only to attach mobile builds to a release.
- Known trade-off: membership of the `docker` group is root-equivalent, so the deploy key effectively has root on
  the VPS. Protect it accordingly (only in GitHub environment secrets, production behind reviewers).

## Mobile releases

`mobile-release.yml` runs for `v*` tags (and on demand): Android app bundle + APK, iOS unsigned build, attached to
the GitHub release. Store upload is prepared but not automated.

- Android signing: create an upload key once (`keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA
  -keysize 2048 -validity 10000 -alias upload`), then set `ANDROID_KEYSTORE_BASE64` (`base64 -w0
  upload-keystore.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Without them
  the builds are debug-signed (testing only). Upload the `.aab` to Google Play Console (internal testing first).
  Automating that later needs a Play service account (e.g. fastlane `supply`).
- iOS: needs an Apple Developer account, a distribution certificate and provisioning profile; then `flutter
  build ipa` with signing (e.g. fastlane `match` + `pilot` for TestFlight). The workflow currently proves the iOS
  release build compiles.
- Version: build name from the tag, build number = workflow run number. The apps call `MOBILE_API_BASE_URL`;
  the web app uses its own origin.

## Troubleshooting

| Symptom | Check / fix |
|---|---|
| **Container does not start** | `status.sh`; `compose.sh logs --tail 200 <service>`; missing variable -> compose names it (`set X in .env`). Backend exits at startup with a configuration error -> a required variable is missing in `.env`. |
| **Database connection failure** | `compose.sh ps postgres` healthy? `compose.sh logs postgres`. Credentials in `.env` changed after the first start? Postgres keeps the original password in its volume: change it back, or `ALTER USER` inside psql. |
| **GHCR authentication failure** (`denied`/`unauthorized` on pull) | The package must be linked to the repository (it is when pushed by this repo's workflow) or public. Deploy job needs `packages: read`. Manual pull on the server: `echo <PAT with read:packages> \| docker login ghcr.io -u <user> --password-stdin`, then `docker logout ghcr.io`. |
| **SSL certificate failure** | `docker logs repairtrack-caddy`. DNS must point at this server (`dig +short <host>`), ports 80/443 open (`ufw status`), CAA allows Let's Encrypt. Let's Encrypt rate limits: wait an hour; never delete the `caddy-data` volume. |
| **Health check failure** (deploy waits, then rolls back) | `compose.sh logs --tail 200 backend`: usually a migration error, a missing variable, or the database not ready. `docker inspect --format '{{json .State.Health}}' $(compose.sh ps -q backend)`. |
| **Migration failure** | The backend refuses to start and is rolled back. `compose.sh exec postgres psql -U repairtrack -d repairtrack -c 'select version, success from flyway_schema_history order by installed_rank desc limit 5'`. Fix forward in a new release; if the failed migration left partial changes, restore the pre-deploy dump. |
| **Uploads 503 SCANNER_UNAVAILABLE** | ClamAV still loading signatures (first start) or out of memory: `compose.sh logs clamav`, `free -m`. |
| **Document downloads fail** | `FILES_DOMAIN` DNS + certificate; `S3_PUBLIC_ENDPOINT` must be `https://<files domain>`. |
| **Disk full** | `df -h`, `docker system df`. Remove only old, unused images, never volumes: `docker image prune` (dangling only). Do not run `docker system prune -a --volumes`. |
| **Smoke test fails in CD** | The job log shows which check; the workflow already rolled back. Run `deploy/scripts/smoke-test.sh https://<app> https://<api>` locally to reproduce. |

## Pipeline acceptance test

Run once on the real VPS after the setup, and after big infrastructure changes. Record the outcome here.

| # | Test | How | Expected | Result |
|---|---|---|---|---|
| 1 | CI | open a PR | CI green, backend image builds | |
| 2 | Staging deploy | merge to main | images `sha-…` in GHCR, staging deployed, smoke tests green | |
| 3 | Production deploy | approve the run | production deployed, smoke tests green, `deployments.log` entry | |
| 4 | Release | `git tag v0.1.0 && git push origin v0.1.0` | images `v0.1.0`, both environments, GitHub release with mobile builds | |
| 5 | Rollback | Actions -> CD -> production, previous version; then `rollback.sh` on the server | previous version healthy, logged | |
| 6 | Failed deploy | on a throw-away branch add a broken migration (`V999__broken.sql` with invalid SQL), tag it `v0.0.0-test.1`, push the tag; reject the production approval; delete the tag | staging: backend not healthy, automatic rollback, job red; production untouched | |
| 7 | Migration | release with a new `V19__…` migration | applied once on staging, then production; pre-deploy dump exists | |
| 8 | Backend restart | `compose.sh restart backend` | healthy again within ~1 min | |
| 9 | Container crash | `docker kill $(compose.sh ps -q backend)` | Docker restarts it (`restart: unless-stopped`) | |
| 10 | VPS reboot | `sudo reboot` | all stacks and Caddy come back by themselves; smoke tests green | |
| 11 | Backup + restore | `backup-db.sh test`, `verify-backup.sh`, then `restore-db.sh` on **staging** | restore test OK; staging works after the restore | |
| 12 | Database not public | from your laptop: `nc -vz <vps> 5432` | connection refused / filtered | |

## Growing beyond one VPS

- **Separate servers for staging and production:** copy `deploy/edge` to both servers (each Caddy serves only its
  own three host names: remove the other block), point the environment secrets `VPS_HOST` to the new server,
  move the data with `backup-db.sh`/`restore-db.sh` and `backup-files.sh`.
- **Managed database / object storage:** set `DATABASE_URL`-related values and `S3_*` in `.env` (the compose files
  already allow an external `S3_ENDPOINT`), remove the `postgres`/`garage` services.
- **Zero-downtime deployments:** run two backend containers (blue/green) behind Caddy's `reverse_proxy` with
  health checks and switch the upstream after the new one is healthy; `deploy.sh` is the single place to change.
- **More instances:** move rate limits and the RDW cache to a shared store (Redis) first (`backend/SECURITY.md`).
