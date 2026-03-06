# CFP Go-Live Resilience Checklist

This checklist defines the minimum operational resilience gates before CFP go-live.

Execution automation:
- GitHub Actions workflow: `CFP Go-Live Resilience` (`.github/workflows/cfp-go-live-resilience.yml`).

## Gate 1: Merge + Production Deploy + Health

- PR merged into `main`.
- Latest `Production Release` workflow completed successfully.
- Health endpoints validated:
  - `/q/health`
  - `/`
  - `/comunidad`
  - `/eventos`
  - `/proyectos`

## Gate 2: First-Level Incident Drill

- Run first-level incident commands:
  - `homedir-ir-first-level.sh status`
  - `homedir-ir-first-level.sh snapshot`
  - `homedir-ir-first-level.sh shield-on`
  - verify maintenance mode externally (HTTP `503`)
  - `homedir-ir-first-level.sh shield-off`
- Keep evidence in incident log directory.

## Gate 3: DR Readiness Drill

- Generate encrypted backup with `homedir-dr-backup.sh`.
- Verify restore extraction with `homedir-dr-restore.py`.
- Execute `homedir-dr-recover.sh --dry-run --skip-data-restore --apply-hardening`.
- Confirm no placeholder secrets in `/etc/homedir.env`.

## Gate 4: Monitoring + Alerting Active

- `homedir-cfp-traffic-guard.timer` enabled and active.
- `homedir-cfp-traffic-guard.sh check` passes.
- Alerting path configured (`homedir-discord-alert.sh`).

## Gate 5: Multi-Origin Load Validation

- Execute synthetic CFP/community probe with multi-origin simulation.
- Enforce thresholds on:
  - error rate
  - `429` volume
  - timeout volume
- Store artifact report in GitHub Actions run.
