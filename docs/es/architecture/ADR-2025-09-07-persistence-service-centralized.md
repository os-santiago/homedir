# ADR 2025-09-07: Persistencia centralizada

## Contexto
EventFlow necesita escalar a múltiples réplicas y persistir estado compartido. Se evaluaron tres opciones: sidecar, servicio centralizado y object storage.

## Decisión
Adoptar un **servicio centralizado de persistencia** (Solución B) en esta fase.

## Consecuencias
- **Positivas:** consistencia fuerte y operación más simple.
- **Positivas:** escala independiente de EventFlow.
- **Negativas:** latencia de red extra.
- **Futuro:** migración planificada a Object Storage (S3/MinIO).

## Estado
Aceptado
