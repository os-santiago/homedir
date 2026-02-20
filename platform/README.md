# HomeDir VPS Platform

Configs and scripts to provision the VPS that runs HomeDir. All secrets are stripped; fill them from your secure store before applying. This folder is meant to be copied to a fresh host to get the same behavior we have now (GitHub Actions SSH deploy + timer fallback + rollback + maintenance page).

## Contents
- `scripts/homedir-update.sh` – pulls a tagged image, restarts the container, and rolls back on failure.
- `scripts/homedir-webhook.py` – optional listener for Quay webhooks that triggers `homedir-update.sh` with the tag from the payload (ignores `latest`-only webhooks). GET `/` on the same port returns `podman ps`-like status and the tail of the webhook log.
- `scripts/homedir-auto-deploy.sh` – fallback poller that checks Quay tags and deploys the newest semver tag when webhook delivery is missing.
- `scripts/homedir-discord-alert.sh` – sends deploy and webhook alerts to Discord with severity icons/colors (`WARN`, `FAIL`, `RECOVERY`).
- `systemd/homedir-webhook.service` – runs the optional webhook listener.
- `systemd/homedir-auto-deploy.service` / `systemd/homedir-auto-deploy.timer` – periodic fallback auto-deploy from Quay.
- `systemd/homedir-update.service` / `systemd/homedir-update.timer` – optional manual/timer runner; keep disabled unless you set a tag.
- `nginx/homedir.conf`, `nginx/int.conf` – HTTPS reverse proxies with a maintenance page for 502/503/504.
- `assets/maintenance.html` – friendly maintenance page served by nginx.
- `env.example` – sample `/etc/homedir.env` with required variables (no secrets).

## Bootstrap steps (summary)
1) Install dependencies: `podman`, `python3`, `nginx`, `certbot` (or provide your own TLS certs).  
2) Copy scripts to `/usr/local/bin/` and make them executable.  
3) Create `/etc/homedir.env` from `platform/env.example` and fill all secrets.  
   - To enable Discord alerts, set `ALERTS_DISCORD_WEBHOOK_URL` and keep `DISCORD_ALERTS_ENABLED=true`.
4) Install systemd units from `platform/systemd/` into `/etc/systemd/system/`, run `systemctl daemon-reload`, then enable:
   - `homedir-auto-deploy.timer` (fallback every 1 min)
   - `homedir-webhook.service` only if you want Quay webhook support
5) Place nginx configs into `/etc/nginx/sites-available/`, symlink to `sites-enabled`, and reload nginx.  
6) Configure GitHub Actions deploy secrets/vars (for immediate deploy on release):
   - Secret: `DEPLOY_SSH_PRIVATE_KEY`
   - Optional secret: `DEPLOY_SSH_KNOWN_HOSTS`
   - Vars: `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`, optional `DEPLOY_SSH_PORT`, optional `DEPLOY_SSH_COMMAND`, optional `DEPLOY_HEALTHCHECK_URL`
7) Validate fallback poller with `systemctl start homedir-auto-deploy.service` and inspect `/var/log/homedir-auto-deploy.log`.
8) Optional webhook verification: send a POST with `updated_tags`/`docker_tags` and check `/var/log/homedir-webhook.log` plus `podman ps`.
9) `homedir-update.sh` logs to `/var/log/homedir-update.log` and performs rollback using the previous container image if a run fails.

## Notes
- The update script expects a specific tag; no `latest` is used.  
- `homedir-update.sh` normalizes tags like `v3.361.0` to `3.361.0` to avoid pull failures.  
- Release workflow now deploys by SSH immediately after pushing the image; timer remains active as a safety net.
- Discord alerts are non-blocking: if webhook delivery fails, deploy flow continues and logs the warning in `/var/log/homedir-alerts.log`.
- Secrets must never land in git—always inject via `/etc/homedir.env` or your secret manager.  
- Nginx returns the maintenance page when the backend is down, avoiding default 502/Cloudflare responses.
- Persistence path must be consistent across deploys: set `HOMEDIR_DATA_DIR` and keep `JAVA_TOOL_OPTIONS=-Dhomedir.data.dir=<same-path>` in `/etc/homedir.env`.
- The default volume mapping (`/work/data:/work/data:Z`) and data-dir env vars must point to the same in-container path.
- For concurrency hardening, set explicit container limits in `/etc/homedir.env`:
  - `CONTAINER_MEMORY_LIMIT` (recommended `2g`)
  - `CONTAINER_CPU_LIMIT` (recommended `3`)
  - `CONTAINER_PIDS_LIMIT` (recommended `2048`)
