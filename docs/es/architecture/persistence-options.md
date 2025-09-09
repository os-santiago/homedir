# Opciones de persistencia – EventFlow

## Opción A – Sidecar por pod
### Pros
- Simplicidad al desplegarse junto con cada réplica.
- Latencia mínima al operar localmente.
### Contras
- Duplica recursos y estado en cada pod.
- Sincronización compleja entre réplicas.
### Cuándo aplicaría
- Despliegues de baja escala o entornos de desarrollo.

## Opción B – Servicio centralizado
### Pros
- Consistencia fuerte y control de concurrencia.
- Escala de forma independiente de EventFlow.
### Contras
- Añade una latencia de red.
- Requiere alta disponibilidad.
### Cuándo aplicaría
- Cuando se necesitan múltiples réplicas con estado compartido inmediato.

## Opción C – Object Storage
### Pros
- Escalabilidad prácticamente ilimitada.
- Coste operativo reducido.
### Contras
- Consistencia eventual.
- Latencia superior para operaciones de lectura/escritura.
### Target state
- Destino a largo plazo para cargas masivas y durabilidad.

## Comparación
| Criterio | Opción A – Sidecar | Opción B – Servicio centralizado | Opción C – Object Storage |
| --- | --- | --- | --- |
| Complejidad | Baja | Media | Media |
| Latencia | Mínima | Moderada | Alta |
| Consistencia | Local, no compartida | Fuerte | Eventual |
| Escalabilidad | Ligada a pods | Independiente | Ilimitada |
| Operación | Gestión por pod | Servicio único | Servicio administrado |

## Decisión
Se adopta la **Opción B – servicio centralizado** como solución inmediata porque ofrece consistencia fuerte y permite escalar sin duplicar el estado como en la Opción A.
