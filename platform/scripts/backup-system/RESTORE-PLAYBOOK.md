# HomeDir VPS Restore Playbook

## 1) Prepare new VPS
- Install: `podman`, `nginx`, `certbot` (if reissuing TLS), `rsync` (optional)
- Create dirs:
  - `/work/data`
  - `/etc/systemd/system`
  - `/etc/nginx/sites-available`
  - `/usr/local/bin`

## 2) Copy backup from laptop/cloud
From latest snapshot:
- `latest/work/data` -> `/work/data`
- `latest/etc/homedir.env` -> `/etc/homedir.env`
- `latest/etc/systemd/system/homedir-*.service|timer*` -> `/etc/systemd/system/`
- `latest/etc/nginx/sites-available/*.conf` -> `/etc/nginx/sites-available/`
- `latest/usr/local/bin/homedir-*.sh` + `homedir-webhook.py` -> `/usr/local/bin/`
- `latest/root/homedir/platform` -> `/root/homedir/platform` (optional but recommended)

Permissions:
- `chmod 600 /etc/homedir.env`
- `chmod +x /usr/local/bin/homedir-*.sh`
- `chmod +x /usr/local/bin/homedir-webhook.py`

## 3) TLS
Option A (recommended): re-issue certs in new VPS with certbot.
Option B: restore cert archive `archives/homedir-letsencrypt-<timestamp>.tar.gz`:
- `tar -xpf homedir-letsencrypt-<timestamp>.tar.gz -C /`

## 4) Run container
Example:
`podman run -d --name homedir --restart=always -p 8080:8080 --env-file /etc/homedir.env -v /work/data:/work/data:Z quay.io/sergio_canales_e/homedir:<tag>`

Use `<tag>` from `latest/backup-metadata.json` field `image`.

## 5) Enable services and nginx
- `systemctl daemon-reload`
- `systemctl enable --now homedir-webhook.service`
- `systemctl enable --now homedir-auto-deploy.timer`
- `ln -sf /etc/nginx/sites-available/homedir.conf /etc/nginx/sites-enabled/homedir.conf`
- `nginx -t && systemctl reload nginx`

## 6) Validate
- `curl -I http://127.0.0.1:8080/`
- `curl -I https://homedir.opensourcesantiago.io/` (when DNS/TLS ready)
- Check key routes: `/`, `/comunidad`, `/eventos`, `/proyectos`
