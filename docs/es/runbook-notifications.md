# Operation Runbook - Notifications

This document describes how to monitor and operate the module of
Production notifications.

## Metrics

The metrics are exposed via `micrometer`/`/Metrics`.

- `Notifications.
- `Notifications.persisted.total` - written to disk.
- `Notifications.deduped.Total` - Discarded by duplicate.
- `Notifications.dropped.backpressure.total` - Discarded by limits.
- `Notifications.volatile.accepted.total` - Accepted without persisting.
- `Notifications.
- `Notifications.users.active` - Users with loaded notifications.
- `Notifications.Maintenance.purged.total` - Purged Retention Registries.
- `Notifications.Maintenance.compacted.total` - Compacted Snapshots.
- `Notifications.Maintenance.Duration.ms` - Duration of the cleaning task.
- API: `Notifications.api.list.requests.total`,
  `Notifications.api.stream.conneccions.active`,
  `Notifications.api.stream.rejected.max_per_user.total`,
  `Notifications.api.poll.requests.total`,
  `Notifications.api.erors {code}`.

### Examples of consultations (Prometheus)

`` Promql
Rate (Notifications.enqueued.total [5m])
Notifications_queue_DEpth
``

## Procedures

### Detect backpressure
1. Review `Notifications.queue.deph` and` Notifications.dropped.backpressure.total`.
2. Review enqueue logs with `reason = capacity` or` reason = drop.queue`.

### Adjust limits
- `Notifications.backpressure.queue.Max`: Maximum of the persistence tail.
- `Notifications.
-`Notifications.Max-File-Size`: Maximum Snapshot size by user.

### SSE Escalation
1. If `notifications.api.stream.rejected.max_per_user.total` grows,
   consider increasing `notifications.stream.MaxConneCtionsperuse` or
   Force Polling Fallback.

### Checklist post deployment
- Exposed metrics and without errors in `/Metrics`.
- `Notifications.
- API ERRORS <1% (`Sum (Rate (Notifications.api.erors {code = ~" 5 .. "} [5m]))`).

### Diagnosis of 401 or redirections
1. Test `/Whoami` (only admin) to validate identity and Claims.
2. Check cookies `q_Session` (` secure`, `samesite = none`, correct domain).
3. Confirm head headers
4. Verify `quarkus.oidc.application-type = web -app` and` quarkus.oidc.authentication.redirect-path =/enter`.
5. Enable `%dev.quarkus.log.category." Io.quarkus.oidc ".level = Debug` in tests to see the Oidc flow.

## Security and privacy
- Never register `Userid` raw; Use `user_hash`.
- Keep salt at `notifications.user-hash.salt` in secret.

## only suggested
- Notification issued → Visible in UI: P50 <3s, p95 <10s.
- Rate API Error <1% in 5min.
- Tail depth <70% of the sustained maximum.