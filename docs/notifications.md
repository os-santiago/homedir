# Notifications

Iteración 1 introduce el servicio central de notificaciones. Su objetivo es
registrar eventos relevantes para cada usuario sin bloquear la UI y con
persistencia tolerante a fallos.

## Modelo
```java
class Notification {
  String id;
  String userId;
  String talkId;
  String eventId;
  NotificationType type; // UPCOMING | STARTED | ENDING_SOON | FINISHED | TEST
  String title;
  String message;
  long createdAt;
  Long readAt;
  Long dismissedAt;
  String dedupeKey; // sha1(userId|talkId|type|slot)
  Long expiresAt;
}
```
La clave de deduplicación se genera usando una ventana temporal
(`notifications.dedupe-window`, por defecto 30 min). El mismo evento dentro de
la ventana produce `DROPPED_DUPLICATE`.

## Lifecycle y backpressure
1. `enqueue` valida dedupe y límites por usuario/global.
2. Si la cola de persistencia tiene capacidad y hay espacio en disco, se
   programa una escritura asíncrona atómica por usuario en
   `data/notifications/<user>-v1.json` → `ACCEPTED_PERSISTED`.
3. Si la cola está llena se devuelve `ACCEPTED_VOLATILE` o
   `DROPPED_CAPACITY` según `notifications.drop-on-queue-full`.
4. Tareas periódicas eliminan notificaciones con más de
   `notifications.retention-days` días.

## Configuración
- `notifications.enabled` (true)
- `notifications.user-cap` (500)
- `notifications.global-cap` (100000)
- `notifications.flush-interval` (PT10S)
- `notifications.retention-days` (30)
- `notifications.max-queue-size` (10000)
- `notifications.dedupe-window` (PT30M)
- `notifications.drop-on-queue-full` (false)

## Métricas
Contadores mínimos:
- `notifications.enqueued`
- `notifications.persisted`
- `notifications.deduped`
- `notifications.dropped.backpressure`
- `notifications.volatile.accepted`

Gauges:
- `notifications.queue.depth`
- `notifications.users.active`

## Iteración 2 – UI (Qute)
- Contenedor de toasts: fragmento Qute `fragments/toasts.qute.html` incluido en el layout base.
- JS global `EventFlowNotifications.accept(dto)` para encolar toasts con apilado, auto-dismiss y botón “Cerrar todas”.
- Configuración vía data-attributes (`data-max-visible`, `data-auto-dismiss-ms`, `data-position`).
- Accesibilidad: `aria-live="polite"`, foco visible y soporte de `prefers-reduced-motion`.
- Para pruebas en desarrollo: `window.__notifyDev__({...})`.

## Próximos pasos
Iteración 3 añadirá el centro de notificaciones.
