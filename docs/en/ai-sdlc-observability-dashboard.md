# AI SDLC observability dashboard

The authenticated dashboard is served at `/sdlc/dashboard`. It reads worker snapshots from
`HOMEDIR_SDLC_STATE_DIR` (default: `/var/lib/homedir-sdlc`) and refreshes every three seconds.

## API

All endpoints require the existing Quarkus authentication and admin-view permission.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/sdlc/status` | Worker and component health |
| GET | `/api/sdlc/heartbeat` | Latest heartbeat and age |
| GET | `/api/sdlc/pipeline` | Counts, dwell time and anomalies by stage |
| GET | `/api/sdlc/issues` | Active issue snapshots |
| GET | `/api/sdlc/prs` | Active pull request snapshots |
| GET | `/api/sdlc/metrics?days=30` | 7, 30 or 90-day metrics |
| GET | `/api/sdlc/anomalies` | Threshold-based anomaly feed |
| GET | `/api/sdlc/audit/{number}` | Audit events for an issue or PR |
| GET | `/api/sdlc/configuration` | Non-secret worker configuration |
| POST | `/api/sdlc/control/{action}` | `pause`, `resume`, `reconcile`, or `clear-locks` |

Control calls require admin-manage permission, validate the action allow-list, and append an
entry to `admin-audit.jsonl`. The API limits each authenticated principal to 120 calls/minute.

Example:

```shell
curl --fail --cookie session.txt https://homedir.dev/api/sdlc/status
```

Keep the route behind the private/VPN ingress. Authentication is defense in depth and is not a
replacement for the network access policy.
