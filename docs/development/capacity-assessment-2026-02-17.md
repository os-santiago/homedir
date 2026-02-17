# Capacity Assessment - 2026-02-17 (v3.348.0)

## Contexto

- Entorno evaluado: producción `homedir.opensourcesantiago.io`.
- Versión desplegada: `3.348.0` (commit `0f37f1b`).
- Fecha/hora base del levantamiento: `2026-02-17 15:20 UTC`.

## Snapshot actual (producción)

### Host VPS

- CPU: `4 vCPU`
- RAM: `15.99 GB` totales (`~1.03 GB` usados al momento del snapshot)
- Swap: `0`
- Disco `/`: `193 GB` total, `21 GB` usados (`11%`)
- Load average: `0.03 / 0.02 / 0.00`

### Contenedor `homedir`

- Imagen: `quay.io/sergio_canales_e/homedir:3.348.0`
- Límite de memoria/CPU en contenedor: **sin límite explícito** (`mem_limit=0`, `nano_cpus=0`)
- Política de reinicio: `always`
- Límite de procesos: `2048`
- Memoria estable (idle reciente): `~273-316 MB`
- Memoria luego de prueba de carga: `~520 MB` (`memory.peak ~522 MB`)
- Proceso JVM observado:
  - `VmRSS ~527 MB` (post stress)
  - `Threads` de `~44` a `~118` durante carga

## Tráfico observado

Muestra sobre últimas `1200` líneas de `nginx/access.log`:

- `653` requests a `/ws/global-notifications` (handshake websocket, `101`)
- `202` requests no-websocket
- Top rutas no-websocket:
  - `/` (`38`)
  - `/about` (`15`)
  - `/comunidad` (`12`)
  - `/api/community/content` (`10`)
- Códigos HTTP:
  - `200: 159`
  - `301/302/303: 35`
  - `404: 6`
  - `400: 2`
  - `101: 653` (websocket upgrade)

## Pruebas rápidas de rendimiento (interna al contenedor)

### Latencia base (sin concurrencia alta)

- `GET /` -> p50 `5.8ms`, p95 `10.2ms`
- `GET /comunidad` -> p50 `6.7ms`, p95 `9.1ms`
- `GET /api/community/content?view=featured&limit=10` -> p50 `214.3ms`, p95 `221.3ms`

### Throughput de landing (`/`) con concurrencia

Prueba: `12000` requests, concurrencia `120`:

- Resultado: `12000 OK`, `0 fail`
- Tiempo total: `8.534s`
- CPU consumida por cgroup del contenedor: `16.416 CPU-seconds`
- Consumo promedio durante prueba: `1.924 cores` (sobre 4 vCPU = `48.1%`)

## Hallazgos críticos para escalar a 400 usuarios

1. Existe rate limit API global por IP:
   - `rate.limit.api.limit = 120` requests/min (ventana 60s).
   - Bajo prueba intensa a `/api/community/content` aparecieron `429 Too Many Requests`.
2. No hay límites explícitos de CPU/MEM del contenedor.
3. `/api/community/content` es significativamente más costoso que páginas públicas.

## Proyección para 400 usuarios concurrentes

Supuestos para estimación:

- 1 websocket por usuario conectado.
- Mezcla de tráfico: navegación pública + consumo de APIs (`community/events/cfp`).
- Escenarios por tasa promedio de requests por usuario:
  - Conservador: `0.05 rps/user`
  - Medio: `0.10 rps/user`
  - Alto: `0.20 rps/user`

| Escenario | RPS total (400 users) | CPU estimada | RAM estimada | Riesgo principal |
|---|---:|---:|---:|---|
| Conservador | 20 rps | 15-25% de 4 vCPU | 0.7-0.9 GB | Bajo |
| Medio | 40 rps | 25-45% de 4 vCPU | 0.9-1.2 GB | Medio (API bursts) |
| Alto | 80 rps | 45-75% de 4 vCPU | 1.2-1.8 GB | Alto (429/latencia API) |

Notas:

- La landing tiene margen alto (>= 1k rps internos en prueba puntual), pero no representa todo el mix real.
- El límite de `120 req/min` por IP puede bloquear tráfico legítimo si muchos usuarios quedan detrás de la misma IP efectiva para backend.

## Recomendaciones operativas inmediatas

1. Ajustar rate limiting por endpoint y por tipo de cliente:
   - Mantener agresivo para auth.
   - Subir bucket API público o separar bucket para `/api/community/content`.
2. Fijar límites de contenedor para estabilidad:
   - Ejemplo inicial: `--memory=2g --cpus=3` (o equivalente systemd/podman).
3. Activar benchmark reproducible en staging (k6/oha/wrk) con escenario de 400 usuarios.
4. Monitorear permanentemente:
   - p95/p99 por endpoint
   - tasa de `429`
   - `memory.current`, `memory.peak`, `cpu.stat`
   - cola de persistencia (`writesOk/writesFail/depth`) desde admin metrics.

## Conclusión

Con el estado actual, el despliegue soporta holgadamente el tráfico observado hoy y debería sostener `400` usuarios concurrentes en escenario conservador/medio, pero requiere ajuste de rate limiting API y validación formal con prueba de carga realista para evitar rechazos `429` y degradación en picos.

## Cambios aplicados después del análisis

- Se creó release formal: `v3.348.0`.
- Se ajustó el rate limiting para Community API:
  - Nuevo bucket dedicado para `/api/community/content`.
  - Nueva propiedad: `rate.limit.api.community-content.limit` (default `600 req/min`).
- Se mejoró la identificación de cliente detrás de proxy/CDN:
  - Nuevo fallback a `CF-Connecting-IP` cuando `X-Forwarded-For` no está presente.
- Se optimizó `GET /api/community/content` para concurrencia:
  - `view=new`: calcula agregados de votos solo para la página solicitada.
  - `view=featured`: calcula agregados solo para candidatos de ventana destacada (7 días por default).
- Se agregó cache corto para agregados de votos:
  - `community.votes.aggregate-cache-ttl` (default `PT10S`), para reducir consultas repetitivas en tráfico de lectura.
- Se agregó telemetría de rate limiting en admin metrics:
  - `GET /private/admin/metrics/persistence` incluye estado/config/totales por bucket.
- Se habilitó hardening runtime en deploy script:
  - `CONTAINER_MEMORY_LIMIT` (default `2g`)
  - `CONTAINER_CPU_LIMIT` (default `3`)
  - `CONTAINER_PIDS_LIMIT` (default `2048`)
- Se agregó herramienta reproducible de carga:
  - `tools/load-test/community_capacity_probe.py`

## Validación adicional (solo este equipo -> VPS)

Fecha de ejecución: `2026-02-17` (UTC).

Restricción validada: por ahora las pruebas de carga salen desde un único cliente hacia el VPS.

### 1) Prueba pública desde Internet (`homedir.opensourcesantiago.io`)

- `users=20`, `duration=45s`:
  - error rate `0.00%`
  - `/api/community/content?view=featured&limit=10`: p95 `1553.6ms`
- `users=40`, `duration=45s`:
  - error rate `6.10%`
  - `/api/community/content?view=featured&limit=10`: `429=138`
- `users=80`, `duration=45s`:
  - error rate `20.98%`
  - `/api/community/content?view=featured&limit=10`: `429=1199`

Conclusión de esta ruta:
- Desde un solo origen público se activa rate limiting por IP en Community API, por lo que no representa bien un patrón multiusuario real.

### 2) Prueba desde este equipo, ejecutada dentro del VPS por SSH (`root@72.60.141.165`)

Target: `http://127.0.0.1:8080` (sin Cloudflare), usando el mismo probe.

- `users=80`, `duration=45s`, probe normal:
  - error rate `26.90%`
  - `/api/community/content?view=featured&limit=10`: `429=2365`, `-1(timeout)=28`
- `users=80`, `duration=45s`, IP emulada por request (XFF/CF-Connecting-IP random):
  - error rate `2.23%`
  - `/api/community/content?view=featured&limit=10`: p95 `8008.2ms`, `-1(timeout)=71`, sin `429`
- `users=120`, `duration=45s`, IP emulada por request:
  - error rate `12.01%`
  - `/api/community/content?view=featured&limit=10`: p95 `8011.7ms`, `-1(timeout)=374`

### 3) Recursos durante prueba en VPS

Durante la ronda más exigente (`users=120`, IP emulada):
- CPU contenedor observada por `podman stats`: ~`47%` a `53%` (sobre límite de `3 CPU` configurado).
- Memoria contenedor: pico observado ~`1.40GB` / `2.147GB`.
- Estado host (`top`) al terminar: CPU global ociosa (sin saturación de nodo).

Conclusión:
- El cuello de botella actual es el endpoint `/api/community/content?view=featured` bajo concurrencia alta.
- No se observó saturación de máquina/host en esta ventana.

### Proyección revisada para objetivo `400 usuarios por hora`

`400 usuarios/hora` equivale a ~`6.67 usuarios/min`, muy por debajo de las rondas de estrés ejecutadas.

Con base en los resultados:
- Capacidad para carga esperada actual: **suficiente** con margen.
- Riesgo principal en picos: latencia/timeouts de `community featured` (no CPU/mem del host).

Acciones recomendadas para siguiente iteración:
1. Materializar cache de agregados/votos (TTL corto) para reducir costo por request en `featured`.
2. Añadir cache de respuesta para `featured` (5-15s) invalidada por nuevos votos.
3. Separar completamente el ranking de `featured` en un snapshot periódico (ej. cada 30-60s), servido desde memoria.
