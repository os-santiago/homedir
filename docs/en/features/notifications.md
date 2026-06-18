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