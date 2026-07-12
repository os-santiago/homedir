# AI SDLC observability dashboard

The authenticated dashboard is served at `/sdlc/dashboard`. A background job reads worker state
from `HOMEDIR_SDLC_STATE_DIR` (default: `/var/lib/homedir-sdlc`) every 30 seconds. HTTP requests
serve the resulting immutable in-memory snapshot and never read worker files directly. A failed
refresh preserves the last good snapshot.

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
