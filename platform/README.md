# HomeDir VPS Platform

Configs and scripts to provision the VPS that runs HomeDir. All secrets are stripped; fill them from your secure store before applying. This folder is meant to be copied to a fresh host to get the same behavior we have now (webhook-driven deploys with rollback and maintenance page).

## Contents
- `scripts/homedir-update.sh` – pulls a tagged image, restarts the container, and rolls back on failure.
- `scripts/homedir-webhook.py` – listens for Quay webhooks and triggers `homedir-update.sh` with the tag from the payload. GET `/` on the same port returns `podman ps`-like status and the tail of the webhook log.
- `systemd/homedir-webhook.service` – runs the webhook listener.
- `systemd/homedir-update.service` / `systemd/homedir-update.timer` – optional manual/timer runner; keep disabled unless you set a tag.
- `nginx/homedir.conf`, `nginx/int.conf` – HTTPS reverse proxies with a maintenance page for 502/503/504.
- `assets/maintenance.html` – friendly maintenance page served by nginx.
- `env.example` – sample `/etc/homedir.env` with required variables (no secrets).

## Bootstrap steps (summary)
1) Install dependencies: `podman`, `python3`, `nginx`, `certbot` (or provide your own TLS certs).  
2) Copy scripts to `/usr/local/bin/` and make them executable.  
3) Create `/etc/homedir.env` from `platform/env.example` and fill all secrets.  
4) Install systemd units from `platform/systemd/` into `/etc/systemd/system/`, run `systemctl daemon-reload`, then enable only `homedir-webhook.service` (`systemctl enable --now homedir-webhook.service`). Leave the timer disabled unless you explicitly set a tag.  
5) Place nginx configs into `/etc/nginx/sites-available/`, symlink to `sites-enabled`, and reload nginx.  
6) Verify the webhook: send a POST to `http(s)://int.opensourcesantiago.io/` with a JSON body containing `updated_tags`/`docker_tags` and check `/var/log/homedir-webhook.log` plus `podman ps`.  
7) Deployments happen only via webhook; `homedir-update.sh` logs to `/var/log/homedir-update.log` and performs rollback using the previous container image if a run fails.

## Notes
- The update script expects a specific tag; no `latest` is used.  
- Secrets must never land in git—always inject via `/etc/homedir.env` or your secret manager.  
- Nginx returns the maintenance page when the backend is down, avoiding default 502/Cloudflare responses.
