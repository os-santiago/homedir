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

The browser script `global-notifications-ws.js` connects automatically and forwards notifications to `window.EventFlowNotifications.accept` for toast display. It also stores messages locally so the notifications center page can render them without hitting the backend.
