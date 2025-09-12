# Notificaciones globales sobre WebSocket

Este módulo expone notificaciones públicas a cualquier visitante a través del punto final `/WS/Global-Notifications`.

Los clientes se conectan a través de WebSocket y envían un mensaje `Hello` con el último cursor` CreateAT` recibido. El servidor reconoce y transmite notificaciones de retraso seguidas de actualizaciones en vivo. Las notificaciones se almacenan en un búfer de anillo ligero persisten a `Datos/notificaciones-global-Ws.json`.

Ejemplo de protocolo:

`` `JSON
// Cliente -> servidor
{"t": "hola", "cursor": 0}
// servidor -> cliente
{"T": "Hello-Aack"}
{"id": "01 ...", "tipo": "agenda_updated", "título": "agenda actualizada", "t": "notif"}
`` `` ``

La configuración se realiza a través de `Application.Properties`:

- `notificaciones.global.enabled`
- `notificaciones.global.buffer-size`
- `notificaciones.global.dedupe-window`
- `Notifications.Upcoming.Window` (predeterminado` PT5M`)
- `Notifications.endingSoon.Window` (predeterminado` PT5M`)

El script de navegador `global-notifications-ws.js` conecta automáticamente y reenvía notificaciones a` Window.Eventflownotifications.accept` para la pantalla de tostadas. También almacena mensajes localmente para que la página del centro de notificaciones pueda renderizarlos sin golpear el backend.

## Centro de notificaciones

Los visitantes pueden revisar la acumulación de avisos globales en `/Notificaciones/Center`. La página se presenta completamente en el cliente utilizando datos de 'LocalStorage' y ofrece filtros para mensajes no leídos o recientes, así como acciones locales para marcar las notificaciones como leerlos o eliminarlos.

### Notificaciones de evento y descanso

Los cambios de estado para los eventos y las ranuras para romper se publican cinco minutos antes de que comiencen o terminen. El campo 'Categoría' distingue la fuente y las notificaciones se dedican por `(categoría, id, tipo, slotedge)` en la zona horaria del evento.

Ejemplo de carga útil:

`` `JSON
{"id": "01", "tipo": "upcuartion", "categoría": "evento", "eventId": "e1", "title": "el eviento comienza pronto", "mensaje": "conf"}
{"id": "02", "tipo": "iniciado", "categoría": "break", "eventId": "e1", "talkId": "b1", "title": "break en curso", "mensaje": "café"}}
`` `` ``

## transmisión de administración

Los administradores pueden enviar anuncios y podar el acumulación a través de `/admin/API/Notifications`:

* `Post/admin/api/notifications/broadcast` & ndash; Transmitir una nueva notificación a todos los clientes conectados y persistirlo.
* `Get/admin/api/notifications/último? Limit = n` & ndash; Obtenga las últimas N notificaciones en el búfer de anillo.
* `Delete/admin/api/notifications/{id}` & ndash; Eliminar una notificación para que los nuevos clientes no lo reciban.

Una página de administración adjunta en `/admin/notifications` utiliza` admin-notations.js` para interactuar con estos puntos finales.