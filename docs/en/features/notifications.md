# Notifications System

Homedir implements a real-time notification system for talk status updates and global announcements.

## Architecture

### Real-Time Updates (WebSocket)
- **Channel**: `/ws/global-notifications`
- **Auth**: WebSocket is open for anonymous connections. Messages are scoped server-side by authenticated session.
- **Scope**: Global announcements and user-specific alerts.

### Components
- **Backend**: Quarkus WebSocket endpoint.
- **Frontend**: Vanilla JS client (reconnect with exponential backoff).
- **Security**:
    - **RBAC**: Admin broadcast endpoints protected by `@RolesAllowed("admin")` via OIDC session.
    - **CORS/Origin**: Restricted to trusted domains.
    - **Rate Limiting**: Prevent flood.

## Operations (Runbook)

### Broadcast Announcement
To send a message to all connected users, use the Admin API (requires admin OIDC session).

```bash
curl -X POST https://homedir.opensourcesantiago.io/admin/api/notifications/broadcast \
  -H "Content-Type: application/json" \
  -H "Cookie: <session_cookie>" \
  -d '{"title": "Notice", "message": "Maintenance in 10 mins", "level": "WARN"}'
```

### Troubleshooting
- **Connection Failed**: Check if `NOTIFICATIONS_USER_HASH_SALT` is set on server.
- **No Messages**: Verify WebSocket connection in browser DevTools (Network > WS).
- **Latency**: System is built for eventual consistency; slight delays (<2s) are normal under load.

## Integration
- **Talk Status Change**: Triggered automatically when talk state moves (e.g., to `ACCEPTED`).
- **UI**: Toast notifications appear top-right.

## Frontend JS Ownership

The notification runtime is consolidated (single owner), with page-specific UIs kept separate:

| File | Responsibility |
|------|----------------|
| `js/core-bundle.js` | **Runtime owner**: WebSocket client to `/ws/global-notifications`, reconnect with backoff, toast queue manager, unread badge, `EFNotificationsAdapter`, `HomeDirNotifications` API. |
| `js/utils.js` | Shared DOM/string utilities (`HomeDirUtils.escapeHtml` / `escapeAttr`). Loaded in the layout `<head>` before any page script. |
| `js/notifications-center.js` | UI for `/notifications/center`: localStorage-backed list, filters, read/unread, delete, selection. |
| `js/admin-notifications.js` | Admin notifications module: broadcast page (send global/scoped announcements, list and delete backlog) and simulator page (dry-run/execute audience targeting). Each mode guards on its root element and no-ops when absent. |

Rules:
- The WebSocket connection, notification inbox state (`ef_global_notifs`), and unread counter (`ef_global_unread_count`) are owned by `core-bundle.js`.
- Page scripts must not re-open their own WebSocket; they consume notifications via `HomeDirNotifications` or `window.__EF_GLOBAL_NOTIF_ACCEPT__`.
- HTML escaping belongs in `js/utils.js` (`HomeDirUtils.escapeHtml`); page scripts reference `window.HomeDirUtils.*` directly — `utils.js` is a hard dependency loaded in the layout `<head>` before any page script, so defensive fallbacks are not needed.