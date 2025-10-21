# Notificaciones globales sobre WebSocket

Este módulo expone notificaciones públicas a cualquier visitante a través del endpoint `/ws/global-notifications`.

Los clientes se conectan mediante WebSocket y envían un mensaje `hello` con el último cursor `createdAt` recibido. El servidor lo reconoce y transmite primero las notificaciones pendientes y luego las actualizaciones en vivo. Las notificaciones se almacenan en un búfer circular ligero persistido en `data/notifications-global-ws.json`.

Ejemplo de protocolo:

```json
// Cliente -> servidor
{"t":"hello","cursor":0}
// Servidor -> cliente
{"t":"hello-ack"}
{"id":"01...","type":"AGENDA_UPDATED","title":"Agenda actualizada","t":"notif"}
```

La configuración se realiza en `application.properties`:

- `notifications.global.enabled`
- `notifications.global.buffer-size`
- `notifications.global.dedupe-window`
- `notifications.upcoming.window` (valor por defecto `PT5M`)
- `notifications.endingSoon.window` (valor por defecto `PT5M`)
- `notifications.simulation.enabled`
- `notifications.simulation.allow-real`
- `notifications.simulation.max-items`

El script de navegador `global-notifications-ws.js` se conecta automáticamente y reenvía las notificaciones a `window.EventFlowNotifications.accept` para mostrarlas como toasts. También almacena los mensajes localmente para que la página del centro de notificaciones pueda renderizarlos sin consultar el backend.

## Centro de notificaciones

Las personas usuarias pueden revisar el historial de avisos globales en `/notifications/center`. La página se renderiza completamente en el cliente usando datos de `localStorage` y ofrece filtros para mensajes no leídos o recientes, además de acciones locales para marcarlos como leídos o eliminarlos.

### Notificaciones de eventos y descansos

Los cambios de estado de eventos y descansos se publican cinco minutos antes de que comiencen o terminen. El campo `category` distingue la fuente y las notificaciones se desduplican por `(category, id, type, slotEdge)` en la zona horaria del evento.

Ejemplos de carga útil:

```json
{"id":"01","type":"UPCOMING","category":"event","eventId":"e1","title":"El evento comienza pronto","message":"Keynote"}
{"id":"02","type":"STARTED","category":"break","eventId":"e1","talkId":"b1","title":"Break en curso","message":"Coffee break"}
```

## Difusión desde administración

Las personas administradoras pueden enviar avisos y depurar el historial mediante `/admin/api/notifications`:

* `POST /admin/api/notifications/broadcast` – emite una notificación a todas las personas conectadas y la persiste.
* `GET /admin/api/notifications/latest?limit=N` – obtiene las últimas N notificaciones del búfer.
* `DELETE /admin/api/notifications/{id}` – elimina una notificación para que las nuevas conexiones no la reciban.

La página `/admin/notifications` usa `admin-notifications.js` para trabajar con estos endpoints.

## Herramientas de simulación para administración

El flujo de simulación permite validar cronogramas de notificaciones sin impactar a las personas usuarias reales. Todos los endpoints requieren el rol `admin` y respetan las claves de configuración de simulación listadas arriba.

### Endpoints REST

* `POST /admin/api/notifications/sim/dry-run` planifica notificaciones para un evento alrededor de un instante pivote opcional y devuelve los mensajes simulados sin encolarlos.
* `POST /admin/api/notifications/sim/execute` encola las notificaciones simuladas. El cuerpo acepta:
  - `mode`: `preview` (por defecto), `test-broadcast` o `real-broadcast`. Las emisiones reales se rechazan si `notifications.simulation.allow-real=false`.
  - `sequence`: cuando es `true`, encola las notificaciones de forma secuencial usando `paceMs` como retraso entre cada una; de lo contrario se encolan al instante.
  - `paceMs`: retraso en milisegundos entre emisiones secuenciales (por defecto `1500`).
  - `includeEvent`, `includeTalks`, `includeBreaks`: booleanos para filtrar las fuentes consideradas.
  - `states`: limita los estados incluidos (`UPCOMING`, `STARTED`, `ENDING_SOON`, `FINISHED`).
  - `eventId` y `pivot`: acotan la simulación a un evento y momento específicos.

Ambos endpoints limitan la respuesta o la cola a `notifications.simulation.max-items` para evitar emisiones accidentales masivas.

### Interfaz administrativa

El panel incluye la página `/admin/notifications/sim`, donde se puede:

- Elegir el ID del evento objetivo y la fecha/hora pivote.
- Activar o desactivar la inclusión del evento, charlas y descansos.
- Limitar qué estados del ciclo de vida se simulan.
- Previsualizar los resultados en la página, lanzar una emisión de prueba o reproducir el plan de forma secuencial (siguiendo `sequence`/`paceMs`).
- Optar por recibir, desde el navegador actual, las notificaciones marcadas como `test` que se generan durante la simulación.

La página depende de `admin-notifications-sim.js`, que guarda la preferencia de opt-in en `localStorage` para suscribirse a las emisiones de prueba posteriores.
