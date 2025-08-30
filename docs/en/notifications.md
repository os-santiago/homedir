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

## Iteración 3 – Centro de Notificaciones

La tercera iteración expone un centro accesible desde una campana en el menú y
una API REST autenticada para operar las notificaciones.

### Endpoints

La API REST bajo `/api/notifications` fue retirada. Las notificaciones ahora se
distribuyen públicamente vía WebSocket en `/ws/global-notifications` y la UI
gestiona el estado de lectura de forma local usando `localStorage`.

### UX

- Campana con contador de no leídas (`aria-live="polite"`).
- Centro con filtros *Todas*, *No leídas* y *Últimas 24h*.
- Paginación por cursor (`createdAt`) y acción *Cargar más*.
- Acciones unitarias y masivas para marcar como leídas o eliminar.

### Accesibilidad

- Navegación por teclado y foco visible.
- Contraste AA en el contador y botones.

## Iteración 4 – Integración runtime

```
Mis Charlas -> Evaluador -> Servicio Notif -> SSE/Poll -> UI
```

### Configuración
- `notifications.scheduler.enabled`
- `notifications.scheduler.interval`
- `notifications.upcoming.window`
- `notifications.endingSoon.window`
- `notifications.sse.enabled`
- `notifications.sse.heartbeat`
- `notifications.poll.interval`
- `notifications.poll.limit`
- `notifications.stream.maxConnectionsPerUser`

### Seguridad
- No existen endpoints por usuario; el WebSocket global es público.
- El contador de no leídas y el centro de notificaciones operan solo con
  almacenamiento local.

### Autenticación y expiración de sesión
- Las páginas HTML protegidas redirigen a `/ingresar` cuando la sesión no es válida.
- El antiguo REST `/api/notifications` ya no está disponible.
- Las cookies de sesión deben emitirse con `Secure` y `SameSite=None` en producción.
 
## Iteración 5 – A11y y Mobile

Esta iteración mejora la accesibilidad y experiencia móvil del módulo de
notificaciones.

- Campana con texto accesible y contador `aria-live`.
- Centro con landmarks semánticos, foco visible y elementos navegables por
  teclado.
- Tap targets de al menos 44×44 px y contraste AA en botones y enlaces.
- Soporte para `prefers-reduced-motion` en CSS y JS, permitiendo cerrar toasts
  con <kbd>Esc</kbd>.
- Layout mobile-first sin scroll horizontal, títulos con *line-clamp* y
  contenedores con `overflow-wrap:anywhere`.
- Rendimiento visual: reserva de alto en toasts, `aspect-ratio` fijo para
  avatares y batching de cambios en el DOM para evitar repaints.

## Iteración 6 – Operabilidad y Observabilidad

Esta fase cierra el módulo con métricas y logs estructurados para operación,
además de tareas de mantenimiento y guía de uso.

### Configuración
- `notifications.metrics.enabled` (true)
- `notifications.logs.level` (info)
- `notifications.user-hash.salt` (changeme)
- `notifications.retention-days` (30)
- `notifications.max-file-size` (3MB)
- `notifications.maintenance.interval` (PT30M)
- `notifications.backpressure.queue.max` (10000)
- `notifications.backpressure.cutoff.evaluator-queue-depth` (8000)
- `notifications.poll.rate-limit.window` (PT30S)
- `notifications.poll.rate-limit.max` (8)

### Métricas
- `notifications.enqueued.total`
- `notifications.persisted.total`
- `notifications.deduped.total`
- `notifications.dropped.backpressure.total`
- `notifications.volatile.accepted.total`
- `notifications.queue.depth`
- `notifications.users.active`
- `notifications.maintenance.purged.total`
- `notifications.maintenance.compacted.total`
- `notifications.maintenance.duration.ms`
- API: `notifications.api.list.requests.total`, `notifications.api.stream.connections.active`,
  `notifications.api.stream.rejected.max_per_user.total`, `notifications.api.poll.requests.total`,
  `notifications.api.errors{code}`

### Logs
- Enqueue: `result`, `type`, `reason`, `user_hash`.
- Eventos SSE: conexión/desconexión con `user_hash` y `reason`.
- Polling servido: `items`, `since`, `limit`.

### Mantenimiento
- Purga notificaciones con más de `notifications.retention-days` días.
- Compacta snapshots que superan `notifications.max-file-size` dejando solo
  no leídas y las últimas N leídas.
- Intervalo controlado por `notifications.maintenance.interval`.

### Documentación
Se agrega `docs/runbook-notifications.md` con procedimientos de operación,
detección de backpressure y SLOs sugeridos.
