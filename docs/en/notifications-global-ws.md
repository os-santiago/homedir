# Global notifications over WebSocket

This module exposes public notifications to any visitor through the `/ws/global-notifications` endpoint.

Clients connect via WebSocket and send a `hello` message with the last received `createdAt` cursor. The server acknowledges and streams backlog notifications followed by live updates. Notifications are stored in a lightweight ring buffer persisted to `data/notifications-global-ws.json`.

Example protocol:

```json
// Client -> server
{"t":"hello","cursor":0}
// Server -> client
{"t":"hello-ack"}
{"id":"01...","type":"AGENDA_UPDATED","title":"Agenda updated","t":"notif"}
```

Configuration is done via `application.properties`:

- `notifications.global.enabled`
- `notifications.global.buffer-size`
- `notifications.global.dedupe-window`
- `notifications.upcoming.window` (default `PT5M`)
- `notifications.endingSoon.window` (default `PT5M`)
- `notifications.simulation.enabled`
- `notifications.simulation.allow-real`
- `notifications.simulation.max-items`

The browser script `global-notifications-ws.js` connects automatically and forwards notifications to `window.EventFlowNotifications.accept` for toast display. It also stores messages locally so the notifications center page can render them without hitting the backend.

## Notification center

Visitors can review the backlog of global notices at `/notifications/center`. The page is rendered entirely on the client using data from `localStorage` and offers filters for unread or recent messages as well as local actions to mark notifications as read or remove them.

### Event and break notifications

State changes for events and break slots are published five minutes before they begin or end. The `category` field distinguishes the source and notifications are deduplicated by `(category, id, type, slotEdge)` in the event's time zone.

Example payloads:

```json
{"id":"01","type":"UPCOMING","category":"event","eventId":"e1","title":"Event starting soon","message":"Conference keynote"}
{"id":"02","type":"STARTED","category":"break","eventId":"e1","talkId":"b1","title":"Break in progress","message":"Coffee break"}
```

## Admin broadcast

Administrators may send announcements and prune the backlog through `/admin/api/notifications`:

* `POST /admin/api/notifications/broadcast` – broadcast a new notification to all connected clients and persist it.
* `GET /admin/api/notifications/latest?limit=N` – fetch the last N notifications in the ring buffer.
* `DELETE /admin/api/notifications/{id}` – remove a notification so new clients will not receive it.

An accompanying admin page at `/admin/notifications` uses `admin-notifications.js` to interact with these endpoints.

## Simulation tools for administrators

The simulation workflow helps teams validate notification timelines without touching production users. All endpoints are protected with the `admin` role and honour the simulation configuration keys listed above.

### REST endpoints

* `POST /admin/api/notifications/sim/dry-run` plans notifications for an event around an optional pivot instant and returns the simulated payloads without queuing them.
* `POST /admin/api/notifications/sim/execute` enqueues the simulated notifications. The body accepts:
  - `mode`: `preview` (default), `test-broadcast`, or `real-broadcast`. Real broadcasts are rejected unless `notifications.simulation.allow-real=true`.
  - `sequence`: when `true`, enqueue notifications sequentially using `paceMs` as the delay between items; otherwise they are enqueued immediately.
  - `paceMs`: delay in milliseconds between sequential emissions (defaults to `1500`).
  - `includeEvent`, `includeTalks`, `includeBreaks`: booleans to filter the sources considered.
  - `states`: limit the lifecycle states included (`UPCOMING`, `STARTED`, `ENDING_SOON`, `FINISHED`).
  - `eventId` and `pivot`: scope the simulation to a single event and time snapshot.

Both endpoints cap the response or enqueued items to `notifications.simulation.max-items` to avoid accidental floods.

### Admin UI

The admin panel links to `/admin/notifications/sim`, a dedicated page that lets administrators:

- Choose the target event ID and pivot date/time.
- Toggle whether to include the event wrapper, talks, and breaks.
- Limit which lifecycle states are simulated.
- Preview results inline, launch a test broadcast, or replay the plan sequentially (mirroring `sequence`/`paceMs`).
- Opt-in the current browser to receive the `test` notifications that are emitted during simulations.

The page relies on `admin-notifications-sim.js`, which stores the opt-in flag in `localStorage` so administrators can subscribe to follow-up test emissions.
