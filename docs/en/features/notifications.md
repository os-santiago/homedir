# Notifications System

Homedir implements a real-time notification system for talk status updates and global announcements.

## Architecture

### Real-Time Updates (WebSocket)
- **Channel**: `/notifications/stream`
- **Auth**: Ticket-based (HMAC SHA-256). User obtains ticket from `/notifications/auth`.
- **Scope**: Global announcements and user-specific alerts.

### Components
- **Backend**: Quarkus WebSocket endpoint.
- **Frontend**: Vanilla JS client (reconnect with exponential backoff).
- **Security**:
    - **HMAC Signatures**: Verify source of notification requests.
    - **CORS/Origin**: Restricted to trusted domains.
    - **Rate Limiting**: Prevent flood.

## Operations (Runbook)

### Broadcast Announcement
To send a message to all connected users, use the Admin API (requires `ADMIN_API_KEY`).

```bash
curl -X POST https://homedir.opensourcesantiago.io/api/admin/notifications/broadcast \
  -H "X-API-Key: <SECRET_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"message": "Maintenance in 10 mins", "level": "WARN"}'
```

### Troubleshooting
- **Connection Failed**: Check if `NOTIFICATIONS_USER_HASH_SALT` is set on server.
- **No Messages**: Verify WebSocket connection in browser DevTools (Network > WS).
- **Latency**: System is built for eventual consistency; slight delays (<2s) are normal under load.

## Integration
- **Talk Status Change**: Triggered automatically when talk state moves (e.g., to `ACCEPTED`).
- **UI**: Toast notifications appear top-right.