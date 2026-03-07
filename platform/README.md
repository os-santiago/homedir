# HomeDir VPS Platform

Configs and scripts to provision the VPS that runs HomeDir. All secrets are stripped; fill them from your secure store before applying. This folder is meant to be copied to a fresh host to get the same behavior we have now (GitHub Actions SSH deploy + timer fallback + rollback + maintenance page).

## Contents
- `scripts/homedir-update.sh` – pulls a tagged image, restarts the container, and rolls back on failure.
- `scripts/homedir-env-lib.sh` – shared env loader with `*_FILE` secret resolution.
- `scripts/homedir-webhook.py` – optional listener for Quay webhooks that triggers `homedir-update.sh` with the tag from the payload (ignores `latest`-only webhooks), validates webhook signatures, and exposes a token-protected status endpoint.
- `scripts/homedir-auto-deploy.sh` – fallback poller that checks Quay tags and deploys the newest semver tag when webhook delivery is missing.
- `scripts/homedir-discord-alert.sh` – sends deploy and webhook alerts to Discord with severity icons/colors (`WARN`, `FAIL`, `RECOVERY`).
- `scripts/homedir-ir-first-level.sh` – first-level incident response (`status`, `snapshot`, `shield-on`, `recover`, `shield-off`) for attacks/DoS.
- `scripts/homedir-security-hardening.sh` – hardening baseline automation (`audit`, `apply`) for VPS + runtime controls.
- `scripts/homedir-cfp-traffic-guard.sh` – monitors CFP/community critical routes for 429/5xx/timeouts and triggers alerts on threshold breaches.
- `scripts/homedir-dr-backup.sh` – creates an encrypted DR backup artifact (`tar.gz.age`) + sha256 + metadata.
- `scripts/homedir-dr-recover.sh` – one-command disaster recovery orchestrator for a pre-provisioned VM.
- `scripts/homedir-dr-restore.py` – safe archive extractor used by DR recovery (blocks path traversal/symlinks).
- `scripts/homedir-secrets-rotate.sh` – rotates internal runtime secrets with backup + optional service restart.
- `systemd/homedir-webhook.service` – runs the optional webhook listener.
- `systemd/homedir-auto-deploy.service` / `systemd/homedir-auto-deploy.timer` – periodic fallback auto-deploy from Quay.
- `systemd/homedir-cfp-traffic-guard.service` / `systemd/homedir-cfp-traffic-guard.timer` – periodic CFP route resilience monitoring.
- `systemd/homedir-update.service` / `systemd/homedir-update.timer` – optional manual/timer runner; keep disabled unless you set a tag.
- `nginx/homedir.conf`, `nginx/int.conf` – HTTPS reverse proxies with a maintenance page for 502/503/504.
- `nginx/snippets/homedir-incident-guard.conf` – lock-file based emergency shield to force maintenance mode during incidents.
- `nginx/snippets/homedir-security-hardening.conf` – baseline edge hardening (timeouts, headers, method filtering, body-size cap).
- `assets/maintenance.html` – friendly maintenance page served by nginx.
- `env.example` – sample `/etc/homedir.env` with required variables (no secrets).

## Bootstrap steps (summary)
1) Install dependencies: `podman`, `python3`, `nginx`, `certbot` (or provide your own TLS certs).  
2) Copy scripts to `/usr/local/bin/` and make them executable.  
3) Create `/etc/homedir.env` from `platform/env.example`. Prefer `*_FILE` variables for sensitive values.  
   - Store secret files under `/etc/homedir-secrets` with `root:root` and mode `600`.
   - To enable Discord alerts, set `ALERTS_DISCORD_WEBHOOK_URL_FILE` and keep `DISCORD_ALERTS_ENABLED=true`.
   - For webhook security, define `WEBHOOK_SHARED_SECRET_FILE` and `WEBHOOK_STATUS_TOKEN_FILE`, keep `WEBHOOK_REQUIRE_SIGNATURE=true`, and bind webhook to localhost (`WEBHOOK_BIND_ADDRESS=127.0.0.1`).
   - Keep OAuth callback baseline values in env:
     - `APP_PUBLIC_URL=https://homedir.opensourcesantiago.io`
     - `QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED=true`
     - `QUARKUS_HTTP_PROXY_ALLOW_FORWARDED=false`
     - `QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME=true`
4) Install systemd units from `platform/systemd/` into `/etc/systemd/system/`, run `systemctl daemon-reload`, then enable:
   - `homedir-auto-deploy.timer` (fallback every 1 min)
   - `homedir-cfp-traffic-guard.timer` (CFP route guard every 5 min)
   - `homedir-webhook.service` only if you want Quay webhook support
5) Place nginx configs into `/etc/nginx/sites-available/`, symlink to `sites-enabled`, and reload nginx.  
6) Configure GitHub Actions deploy secrets/vars (for immediate deploy on release):
   - Secret: `DEPLOY_SSH_PRIVATE_KEY`
   - Optional secret: `DEPLOY_SSH_KNOWN_HOSTS`
   - Vars: `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`, optional `DEPLOY_SSH_PORT`, optional `DEPLOY_SSH_COMMAND`, optional `DEPLOY_HEALTHCHECK_URL`
7) Validate fallback poller with `systemctl start homedir-auto-deploy.service` and inspect `/var/log/homedir-auto-deploy.log`.
8) Optional webhook verification: send a POST with `updated_tags`/`docker_tags` and check `/var/log/homedir-webhook.log` plus `podman ps`.
9) `homedir-update.sh` logs to `/var/log/homedir-update.log` and performs rollback using the previous container image if a run fails.

## First-level incident response (attack/DoS)

Emergency shield on:

```bash
/usr/local/bin/homedir-ir-first-level.sh shield-on
```

Capture diagnostics:

```bash
/usr/local/bin/homedir-ir-first-level.sh snapshot
```

Recover to known good tag and reopen traffic:

```bash
/usr/local/bin/homedir-ir-first-level.sh recover vX.Y.Z
```

Manual reopen:

```bash
/usr/local/bin/homedir-ir-first-level.sh shield-off
```

## CFP monitoring guard

Manual check:

```bash
/usr/local/bin/homedir-cfp-traffic-guard.sh status
```

Enforced check (exit non-zero on breach):

```bash
/usr/local/bin/homedir-cfp-traffic-guard.sh check
```

## DR backup flow (recommended)

Generate encrypted backups regularly and store them in your secure backup location:

```bash
/usr/local/bin/homedir-dr-backup.sh \
  --age-recipient <AGE_PUBLIC_RECIPIENT> \
  --output-dir /var/backups/homedir-dr
```

Outputs:
- `*.tar.gz.age` encrypted backup artifact
- `*.sha256` integrity file
- `*.metadata.json` non-sensitive metadata

## DR recovery flow (pre-provisioned VM)

Minimal example:

```bash
/usr/local/bin/homedir-dr-recover.sh \
  --env-file /secure/homedir.env.age \
  --age-identity /root/.config/age/keys.txt \
  --backup-file /secure/homedir-data-YYYYMMDDTHHMMSSZ.tar.gz.age \
  --backup-sha256-file /secure/homedir-data-YYYYMMDDTHHMMSSZ.tar.gz.age.sha256 \
  --apply-hardening
```

What it automates:
1) Pull repo from GitHub (`--repo-ref` selectable).
2) Install platform scripts + systemd units.
3) Install secure env file into `/etc/homedir.env` with strict permissions.
4) Restore application data safely to host data directory.
5) Deploy from Quay (`--deploy-tag` or latest semver fallback).
6) Verify local healthcheck (`/q/health` by default).

## Notes
- The update script expects a specific tag; no `latest` is used.  
- `homedir-update.sh` normalizes tags like `v3.361.0` to `3.361.0` to avoid pull failures.  
- Release workflow now deploys by SSH immediately after pushing the image; timer remains active as a safety net.
- Discord alerts are non-blocking: if webhook delivery fails, deploy flow continues and logs the warning in `/var/log/homedir-alerts.log`.
- Secrets must never land in git—always inject via `/etc/homedir.env` + `*_FILE` secure files or your secret manager.  
- Nginx returns the maintenance page when the backend is down, avoiding default 502/Cloudflare responses.
- First-level shield uses `/etc/homedir.incident.lock`; when present, public traffic receives maintenance (503) while local `/q/health` remains available.
- `homedir-webhook.py` now requires signed requests by default (`X-Quay-Signature`) and does not log raw webhook payloads.
- `GET /` on webhook is disabled unless `WEBHOOK_STATUS_TOKEN` is configured and sent by caller.
- Persistence path must be consistent across deploys: set `HOMEDIR_DATA_DIR` and keep `JAVA_TOOL_OPTIONS=-Dhomedir.data.dir=<same-path>` in `/etc/homedir.env`.
- The default volume mapping (`/work/data:/work/data:Z`) and data-dir env vars must point to the same in-container path.
- For concurrency hardening, set explicit container limits in `/etc/homedir.env`:
  - `CONTAINER_MEMORY_LIMIT` (recommended `2g`)
  - `CONTAINER_CPU_LIMIT` (recommended `3`)
  - `CONTAINER_PIDS_LIMIT` (recommended `2048`)
- Never store plaintext secrets or unencrypted backups in git. Keep `/etc/homedir.env`, age identity keys, and backup artifacts in secure storage with restricted access.
- Run `/usr/local/bin/homedir-security-hardening.sh audit` periodically and after each DR recovery.
- Rotate internal runtime secrets periodically (monthly or after incident):
  - `/usr/local/bin/homedir-secrets-rotate.sh --restart-services`
