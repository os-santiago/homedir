# Disaster Recovery Playbook (Pre-Provisioned VM)

This playbook defines a fast, repeatable recovery process for HomeDir when a VPS is lost and a replacement VM is already provisioned.

## Recovery objective

- Rebuild runtime from:
  - GitHub repository (`platform/` automation assets)
  - Quay image tags
  - encrypted backup artifact of host data directory
  - secure env/secrets file from secret storage
- Keep secrets out of git and avoid plaintext backup handling.

For attack-time first-level containment (before full DR), use:
- [Incident Response - First Level](incident-response-first-level.md)

## Current gaps that this playbook closes

1. Gap: bootstrap steps were documented but mostly manual.
   - Improvement: `platform/scripts/homedir-dr-recover.sh` automates install + restore + deploy + healthcheck.
2. Gap: backup generation was not standardized with encryption/integrity metadata.
   - Improvement: `platform/scripts/homedir-dr-backup.sh` creates encrypted artifacts + sha256 + metadata.
3. Gap: restore path traversal/symlink protections were not available at host-level.
   - Improvement: `platform/scripts/homedir-dr-restore.py` performs safe archive extraction.
4. Gap: recovery sequence was split across scripts/runbooks.
   - Improvement: one orchestrator script drives end-to-end DR flow.
5. Gap: post-recovery host hardening checks were inconsistent.
   - Improvement: `platform/scripts/homedir-security-hardening.sh` adds repeatable baseline `audit` and `apply` controls.

## Security model

- Secrets:
  - `homedir.env` must come from a secure secret store (never from git).
  - Recommended format for transport: `*.age`, decrypted only in-memory/on-host temp files.
- Backups:
  - Recommended format: `*.tar.gz.age` plus `*.sha256`.
  - Validate checksum before restore.
- Webhook/deploy channel:
  - Require signed webhook requests (`WEBHOOK_REQUIRE_SIGNATURE=true`).
  - Keep webhook status endpoint token protected (`WEBHOOK_STATUS_TOKEN`).
- Local safety:
  - DR scripts run with `umask 077`.
  - Temporary decrypted files are removed.
  - Existing data dir is preserved as `*.pre-dr-<timestamp>` before replacement.

## Backup creation (periodic)

```bash
/usr/local/bin/homedir-dr-backup.sh \
  --age-recipient <AGE_PUBLIC_RECIPIENT> \
  --retain-count 28 \
  --output-dir /var/backups/homedir-dr
```

Outputs:
- encrypted archive (`.tar.gz.age`)
- integrity file (`.sha256`)
- metadata file (`.metadata.json`)
- automatic pruning of older backup sets beyond `--retain-count` (`28` by default)

## Recovery execution (one command)

```bash
/usr/local/bin/homedir-dr-recover.sh \
  --env-file /secure/homedir.env.age \
  --age-identity /root/.config/age/keys.txt \
  --backup-file /secure/homedir-data-YYYYMMDDTHHMMSSZ.tar.gz.age \
  --backup-sha256-file /secure/homedir-data-YYYYMMDDTHHMMSSZ.tar.gz.age.sha256 \
  --apply-hardening
```

Optional flags:
- `--repo-ref <tag|branch>` to recover from a specific release reference.
- `--deploy-tag vX.Y.Z` to force a specific Quay tag.
- `--skip-nginx` if nginx is managed externally.
- `--enable-webhook` to restore webhook listener service.
- `--skip-data-restore` for stateless recovery drills.
- `--apply-hardening` to execute VPS/app hardening baseline right after recovery.

## What the DR orchestrator does

1. Clones repository from GitHub (`--repo-url`, `--repo-ref`).
2. Installs platform scripts into `/usr/local/bin`.
3. Installs systemd units into `/etc/systemd/system` and reloads daemon.
4. Optionally installs nginx configs and maintenance page.
5. Installs validated env file to `/etc/homedir.env` with mode `0600`.
6. Restores backup archive safely to host data directory.
7. Enables `homedir-auto-deploy.timer`.
8. Deploys requested tag or latest semver from Quay.
9. Optionally applies baseline hardening (`homedir-security-hardening.sh apply`).
10. Waits for local healthcheck success (`/q/health`).

## DR drill checklist

1. Run a backup with `homedir-dr-backup.sh`.
2. Start a fresh pre-provisioned VM.
3. Execute `homedir-dr-recover.sh` with encrypted env + backup.
4. Verify:
   - `/q/health` returns 200
   - `/`, `/comunidad`, `/eventos`, `/proyectos` return 200
   - `homedir-cfp-traffic-guard.timer` is enabled and active
   - `/usr/local/bin/homedir-security-hardening.sh audit` reports zero FAIL checks
   - admin backup page can list/download/restore as expected
5. Record elapsed recovery time and issues.
