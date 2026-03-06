# Security Hardening Baseline (VPS + App)

This baseline defines first-level hardening controls for HomeDir runtime and disaster recovery operations, without storing sensitive data in git.

## Scope

- VPS runtime controls
- deploy/webhook channel protection
- DR backup/recovery security checks

## Automation

Script:

- `platform/scripts/homedir-security-hardening.sh`

Commands:

```bash
/usr/local/bin/homedir-security-hardening.sh audit
/usr/local/bin/homedir-security-hardening.sh apply
```

`apply` is intentionally non-destructive:
- enforces restrictive permissions on `/etc/homedir.env`
- restricts incident/backup directories
- installs a network sysctl baseline
- enables `fail2ban` if installed
- restarts webhook service only when enabled

## Baseline controls

1. Secrets and backups
- `/etc/homedir.env` owned by `root:root` with mode `600`.
- Backup artifacts encrypted (`*.age`) and validated with sha256 before restore.
- No plaintext secrets or backup artifacts stored in git.

2. Incident response readiness
- Emergency shield lock file support (`/etc/homedir.incident.lock`).
- Incident snapshots stored with restricted permissions.
- Recovery scripts use `umask 077`.

3. Deploy channel hardening
- Webhook listener bound to localhost by default.
- Signed webhook validation enabled (`WEBHOOK_REQUIRE_SIGNATURE=true`).
- Shared secret configured (`WEBHOOK_SHARED_SECRET`).
- Status endpoint protected with token (`WEBHOOK_STATUS_TOKEN`).

4. Edge hardening
- Nginx hardening snippet included (timeouts, header baseline, method filtering, body size cap).
- Nginx incident guard snippet enabled for emergency maintenance mode.

5. Host-level controls
- `homedir-auto-deploy.timer` enabled and active.
- `homedir-cfp-traffic-guard.timer` enabled and active.
- Host firewall active (`firewalld` or `ufw`).
- SSH baseline: root login restricted, password auth disabled.

## DR integration

During DR execution, include:

```bash
/usr/local/bin/homedir-dr-recover.sh ... --apply-hardening
```

After recovery, run:

```bash
/usr/local/bin/homedir-security-hardening.sh audit
```

Release criteria for DR drill:
- Healthcheck and key routes return 200.
- `audit` reports zero FAIL checks.
