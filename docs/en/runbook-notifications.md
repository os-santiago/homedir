# Runbook de Operación – Notificaciones

Este documento describe cómo monitorear y operar el módulo de
notificaciones en producción.

## Métricas

Las métricas se exponen vía `micrometer`/`/metrics`.

- `notifications.enqueued.total` – notificaciones recibidas.
- `notifications.persisted.total` – escritas a disco.
- `notifications.deduped.total` – descartadas por duplicadas.
- `notifications.dropped.backpressure.total` – descartadas por límites.
- `notifications.volatile.accepted.total` – aceptadas sin persistir.
- `notifications.queue.depth` – tamaño de la cola de persistencia.
- `notifications.users.active` – usuarios con notificaciones cargadas.
- `notifications.maintenance.purged.total` – registros purgados por retención.
- `notifications.maintenance.compacted.total` – snapshots compactados.
- `notifications.maintenance.duration.ms` – duración de la tarea de limpieza.
- API: `notifications.api.list.requests.total`,
  `notifications.api.stream.connections.active`,
  `notifications.api.stream.rejected.max_per_user.total`,
  `notifications.api.poll.requests.total`,
  `notifications.api.errors{code}`.

### Ejemplos de consultas (Prometheus)

```promql
rate(notifications.enqueued.total[5m])
notifications_queue_depth
```

## Procedimientos

### Detectar backpressure
1. Revisar `notifications.queue.depth` y `notifications.dropped.backpressure.total`.
2. Revisar logs de enqueue con `reason=capacity` o `reason=drop.queue`.

### Ajustar límites
- `notifications.backpressure.queue.max`: máximo de la cola de persistencia.
- `notifications.retention-days`: días que se conservan las notificaciones.
- `notifications.max-file-size`: tamaño máximo de snapshot por usuario.

### Escalamiento de SSE
1. Si `notifications.api.stream.rejected.max_per_user.total` crece,
   considerar aumentar `notifications.stream.maxConnectionsPerUser` o
   forzar fallback a polling.

### Checklist post despliegue
- Métricas expuestas y sin errores en `/metrics`.
- `notifications.queue.depth` estable <70% de `notifications.backpressure.queue.max`.
- Errores API <1% (`sum(rate(notifications.api.errors{code=~"5.."}[5m]))`).

### Diagnóstico de 401 o redirecciones
1. Probar `/whoami` (solo admin) para validar identidad y claims.
2. Revisar cookies `q_session` (`Secure`, `SameSite=None`, dominio correcto).
3. Confirmar cabeceras `X-Forwarded-*` desde el proxy y que `proxy-address-forwarding` esté habilitado.
4. Verificar `quarkus.oidc.application-type=web-app` y `quarkus.oidc.authentication.redirect-path=/ingresar`.
5. Habilitar `%dev.quarkus.log.category."io.quarkus.oidc".level=DEBUG` en pruebas para ver el flujo OIDC.

## Seguridad y Privacidad
- Nunca registrar `userId` crudo; usar `user_hash`.
- Mantener la sal en `notifications.user-hash.salt` en secreto.

## SLO sugeridos
- Notificación emitida → visible en UI: p50 <3s, p95 <10s.
- Error rate API <1% en 5 min.
- Profundidad de cola <70% del máximo sostenido.
