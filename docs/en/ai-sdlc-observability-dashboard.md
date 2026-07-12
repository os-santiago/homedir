# AI SDLC observability dashboard

The authenticated dashboard is served at `/sdlc/dashboard`. A background consumer tails the
worker's existing append-only `run-summaries/*.jsonl` journal every 30 seconds. The AI SDLC worker
does not call, wait for, or receive feedback from this consumer. HTTP requests serve an immutable
in-memory projection and never read worker files directly.

Resource containment is enforced independently of traffic: each ingestion cycle reads at most
250 files and 64 KiB, retains at most 2,000 events, skips overlapping executions, and applies
exponential backoff up to five minutes after journal failures. Initial catch-up therefore remains
bounded even with a large historical journal. Malformed lines are isolated without stopping later
events, and failed cycles preserve the last valid projection.

## API

All endpoints require the existing Quarkus authentication and admin-view permission.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/sdlc/status` | Worker and component health |
| GET | `/api/sdlc/snapshot` | Preferred consolidated, in-memory dashboard projection |
| GET | `/api/sdlc/heartbeat` | Latest heartbeat and age |
| GET | `/api/sdlc/pipeline` | Counts, dwell time and anomalies by stage |
| GET | `/api/sdlc/issues` | Active issue snapshots |
| GET | `/api/sdlc/prs` | Active pull request snapshots |
| GET | `/api/sdlc/metrics?days=30` | 7, 30 or 90-day metrics |
| GET | `/api/sdlc/anomalies` | Threshold-based anomaly feed |
| GET | `/api/sdlc/audit/{number}` | Audit events for an issue or PR |
| GET | `/api/sdlc/configuration` | Non-secret worker configuration |
| POST | `/api/sdlc/control/{action}` | `pause`, `resume`, `reconcile`, or `clear-locks` |

Control calls are disabled by default. Enabling `HOMEDIR_SDLC_DASHBOARD_CONTROLS_ENABLED=true`
requires admin-manage permission; calls validate the action allow-list and append an entry to
`admin-audit.jsonl`. Read-only viewers cannot mutate worker state. The API limits each
authenticated principal to 120 calls/minute.

Example:

```shell
curl --fail --cookie session.txt https://homedir.dev/api/sdlc/status
```

Keep the route behind the private/VPN ingress. Authentication is defense in depth and is not a
replacement for the network access policy.
