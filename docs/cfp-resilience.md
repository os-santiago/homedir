# CFP Resilience Baseline

This document defines the no-data-loss strategy for CFP submissions in Homedir.

## Goals

- CFP data must survive container restart, deploys, and node replacement.
- CFP data must be recoverable if a JSON file is corrupted.
- CFP data must be portable to a new environment.

## Current baseline (PR1)

- Primary store: `${homedir.data.dir}/cfp-submissions.json`.
- Automatic local snapshots: `${homedir.data.dir}/backups/cfp/`.
- Snapshot retention: configurable (`cfp.persistence.backups.max-files`, default `120`).
- Snapshot frequency guard: configurable (`cfp.persistence.backups.min-interval-ms`, default `300000`).
- Recovery flow:
  1. Try primary file.
  2. If primary missing/corrupted, recover from newest valid snapshot.
  3. Quarantine corrupted primary as `cfp-submissions.corrupt-<timestamp>.json`.
  4. Rebuild primary from recovered snapshot.

## CFP schema compatibility baseline

- Current persisted schema uses an envelope:
  - `schema_version`
  - `kind`
  - `updated_at`
  - `checksum_sha256` (integrity guard)
  - `submissions` (map by id)
- Backward compatibility is preserved:
  - Legacy map-only payloads are still readable from primary, backups, and WAL frames.
  - Legacy payloads are auto-migrated to the versioned envelope on successful load.
  - Envelope payloads missing checksum are auto-hydrated on successful load.

## Configuration

- `cfp.persistence.backups.enabled=true`
- `cfp.persistence.backups.max-files=120`
- `cfp.persistence.backups.min-interval-ms=300000`
- `cfp.persistence.checksum.enabled=true`
- `cfp.persistence.checksum.required=false` (can be switched to `true` after legacy fleet migration)

## Required ops baseline

- `homedir.data.dir` must point to a persistent volume (PVC/host disk).
- Do not run with ephemeral-only storage in production.
- Include `${homedir.data.dir}` in platform backup policy.

## Iteration roadmap

- PR1: durable CFP local snapshots + automatic recovery from corruption. (implemented)
- PR2: portable CFP export/import bundle and recursive admin backup/restore checks. (implemented)
- PR3: CFP storage observability endpoint for admin verification + restore drill checklist. (implemented)
- PR4: CFP persistence schema versioning + automatic legacy migration across primary/WAL/backups. (implemented)
- PR5: checksum-based CFP integrity guard + auto-hydration and recovery fallback. (implemented)

## Restore validation checklist

1. Stop app traffic.
2. Restore `homedir.data.dir` backup.
3. Start app.
4. Verify `/api/events/{eventId}/cfp/submissions/mine` returns existing submissions.
5. Verify admin CFP moderation queue reads historical entries.

