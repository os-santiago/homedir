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

## Notification center

Visitors can review the backlog of global notices at `/notifications/center`. The page is rendered entirely on the client using data from `localStorage` and offers filters for unread or recent messages as well as local actions to mark notifications as read or remove them.

## Admin broadcast

Administrators may send announcements and prune the backlog through `/admin/api/notifications`:

* `POST /admin/api/notifications/broadcast` &ndash; broadcast a new notification to all connected clients and persist it.
* `GET /admin/api/notifications/latest?limit=N` &ndash; fetch the last N notifications in the ring buffer.
* `DELETE /admin/api/notifications/{id}` &ndash; remove a notification so new clients will not receive it.

An accompanying admin page at `/admin/notifications` uses `admin-notifications.js` to interact with these endpoints.
