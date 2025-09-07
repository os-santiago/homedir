# Servicio de persistencia centralizado

La solución implementada expone un servicio HTTP dedicado a almacenar y recuperar el estado de EventFlow.

## Endpoints
- `GET /state/{key}`: obtiene el estado asociado a `key`.
- `PUT /state/{key}`: crea o reemplaza el estado.
- `DELETE /state/{key}`: elimina el estado.
- `POST /locks/{key}` y `DELETE /locks/{key}`: administración de locks.

## ETag y control de concurrencia
Cada respuesta `GET` incluye un `ETag` que representa la versión del recurso. Las actualizaciones deben enviar `If-Match` con el `ETag` recibido. Si no coincide, el servicio retorna `412 Precondition Failed` para evitar sobrescrituras.

## Locks
Se ofrecen locks optimistas y pesimistas. Los clientes pueden solicitar un lock explícito sobre una clave para operaciones de larga duración. Los locks expiran automáticamente para prevenir bloqueos permanentes.

## Write-Ahead Log (WAL)
Todas las mutaciones se registran en un WAL. En caso de fallo, el servicio puede reproducir las entradas para restaurar el estado. El WAL también sirve como fuente para replicación.

## Eventos
Cuando cambia el estado, el servicio publica un evento `state.updated`. Esto permite que otros componentes de EventFlow reaccionen a la modificación y mantengan caches o vistas materializadas.
