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

## Configuration

- `cfp.persistence.backups.enabled=true`
- `cfp.persistence.backups.max-files=120`
- `cfp.persistence.backups.min-interval-ms=300000`

## Required ops baseline

- `homedir.data.dir` must point to a persistent volume (PVC/host disk).
- Do not run with ephemeral-only storage in production.
- Include `${homedir.data.dir}` in platform backup policy.

## Iteration roadmap

- PR1: durable CFP local snapshots + automatic recovery from corruption. (implemented)
- PR2: portable CFP export/import bundle and recursive admin backup/restore checks.
- PR3: automated scheduled offsite backup (object storage) + restore drill checklist.

## Restore validation checklist

1. Stop app traffic.
2. Restore `homedir.data.dir` backup.
3. Start app.
4. Verify `/api/events/{eventId}/cfp/submissions/mine` returns existing submissions.
5. Verify admin CFP moderation queue reads historical entries.

