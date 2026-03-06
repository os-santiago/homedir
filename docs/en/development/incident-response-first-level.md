# Incident Response - First Level (Attack / DoS)

This runbook covers immediate containment and fast recovery when HomeDir suffers attack traffic or denial-of-service symptoms.

## Objective

- Protect availability and data integrity.
- Reduce blast radius quickly.
- Recover to a stable version with minimum operational steps.

## Entry criteria

Run this procedure when one or more are true:
- sustained 5xx spikes
- response latency collapse
- nginx/podman saturation
- abnormal traffic bursts with service degradation

## Automated command set

All commands are provided by:

- `platform/scripts/homedir-ir-first-level.sh`

### 1) Assess

```bash
/usr/local/bin/homedir-ir-first-level.sh status
```

### 2) Contain traffic (maintenance shield)

```bash
/usr/local/bin/homedir-ir-first-level.sh shield-on
```

This activates lock-file shield (`/etc/homedir.incident.lock`) via nginx snippet and serves maintenance to public traffic while keeping local health checks available.

### 3) Capture evidence

```bash
/usr/local/bin/homedir-ir-first-level.sh snapshot
```

Artifacts are stored under `/var/log/homedir-incident/<timestamp>`.

### 4) Recover service

Preferred:

```bash
/usr/local/bin/homedir-ir-first-level.sh recover vX.Y.Z
```

Alternative:

```bash
/usr/local/bin/homedir-ir-first-level.sh deploy-tag vX.Y.Z
/usr/local/bin/homedir-ir-first-level.sh shield-off
```

### 5) Validate before reopening

- `/q/health` returns 200
- `/`, `/comunidad`, `/eventos`, `/proyectos` return 200
- error rate back to baseline

## Security and sensitive data handling

- Do not export `/etc/homedir.env` in incident evidence.
- Keep incident snapshots in restricted directories (`umask 077` in script).
- Keep backup/env artifacts encrypted at rest and in transfer (`age` recommended).
- Keep webhook requests signed and avoid exposing webhook status endpoint without token protection.

## Post-incident actions

1. Preserve incident snapshot folder.
2. Document attack indicators and timeline.
3. Rotate sensitive tokens if compromise is suspected.
4. Execute DR drill if root cause indicates host compromise risk.
5. Run `/usr/local/bin/homedir-cfp-traffic-guard.sh check` and confirm thresholds are healthy.
6. Run `/usr/local/bin/homedir-security-hardening.sh audit` before declaring incident closure.
