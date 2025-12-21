# Sistema de Notificaciones

Homedir implementa un sistema de notificaciones en tiempo real para actualizaciones de estado de charlas y anuncios globales.

## Arquitectura

### Actualizaciones en Tiempo Real (WebSocket)
- **Canal**: `/notifications/stream`
- **Auth**: Basada en Tickets (HMAC SHA-256). Usuario obtiene ticket desde `/notifications/auth`.
- **Alcance**: Anuncios globales y alertas específicas de usuario.

### Componentes
- **Backend**: Endpoint Quarkus WebSocket.
- **Frontend**: Cliente Vanilla JS (reconect con backoff exponencial).
- **Seguridad**:
    - **Firmas HMAC**: Verifican fuente de solicitudes.
    - **CORS/Origin**: Restringido a dominios de confianza.
    - **Rate Limiting**: Previene inundación.

## Operaciones (Runbook)

### Broadcast de Anuncios
Para enviar mensaje a todos los conectados, usa la API Admin (requiere `ADMIN_API_KEY`).

```bash
curl -X POST https://homedir.opensourcesantiago.io/api/admin/notifications/broadcast \
  -H "X-API-Key: <SECRET_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"message": "Mantenimiento en 10 mins", "level": "WARN"}'
```

### Troubleshooting
- **Fallo de Conexión**: Verifica que `NOTIFICATIONS_USER_HASH_SALT` esté configurado en el servidor.
- **Sin Mensajes**: Revisa conexión WebSocket en DevTools del navegador (Network > WS).
- **Latencia**: Diseñado para consistencia eventual; retrasos leves (<2s) son normales bajo carga.

## Integración
- **Cambio Estado Charla**: Disparado automáticamente cuando el estado cambia (ej. a `ACCEPTED`).
- **UI**: Toasts aparecen arriba a la derecha.
