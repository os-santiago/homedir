# Notifications smoke test

1. Open two browser sessions: one authenticated and one anonymous (incognito).
2. From **Admin → Broadcast demo**, send a demo notification.
3. Ensure the toast appears on both clients and "Ver detalle" opens the `targetUrl`.
4. Delete the notification from Admin.
5. Open a new tab or client session and verify the deleted notification is not delivered.
