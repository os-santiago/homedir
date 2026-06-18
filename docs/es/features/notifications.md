# Sistema de Notificaciones

Homedir implementa un sistema de notificaciones en tiempo real para actualizaciones de estado de charlas y anuncios globales.

## Arquitectura

### Actualizaciones en Tiempo Real (WebSocket)
- **Canal**: `/ws/global-notifications`
- **Auth**: WebSocket abierto para conexiones anónimas. Mensajes se scopean por sesión autenticada del lado del servidor.
- **Alcance**: Anuncios globales y alertas específicas de usuario.

### Componentes
- **Backend**: Endpoint Quarkus WebSocket.
- **Frontend**: Cliente Vanilla JS (reconect con backoff exponencial).
- **Seguridad**:
    - **RBAC**: Endpoints de broadcast admin protegidos por `@RolesAllowed("admin")` vía sesión OIDC.
    - **CORS/Origin**: Restringido a dominios de confianza.
    - **Rate Limiting**: Previene inundación.

## Operaciones (Runbook)

### Broadcast de Anuncios
Para enviar mensaje a todos los conectados, usa la API Admin (requiere sesión admin OIDC).

```bash
curl -X POST https://homedir.opensourcesantiago.io/admin/api/notifications/broadcast \
  -H "Content-Type: application/json" \
  -H "Cookie: <session_cookie>" \
  -d '{"title": "Aviso", "message": "Mantenimiento en 10 mins", "level": "WARN"}'
```

### Troubleshooting
- **Fallo de Conexión**: Verifica que `NOTIFICATIONS_USER_HASH_SALT` esté configurado en el servidor.
- **Sin Mensajes**: Revisa conexión WebSocket en DevTools del navegador (Network > WS).
- **Latencia**: Diseñado para consistencia eventual; retrasos leves (<2s) son normales bajo carga.

## Integración
- **Cambio Estado Charla**: Disparado automáticamente cuando el estado cambia (ej. a `ACCEPTED`).
- **UI**: Toasts aparecen arriba a la derecha.
