# Homedir Persist (HDP)

## Resumen
Servicio embebido de persistencia JSON.

## Arquitectura
- memoria con WAL y persistencia en volumen
- namespaces equivalen a módulos y colecciones a agregados
- aislamiento por namespace mediante archivos, pools y cuotas

## Durabilidad
- políticas `safe`, `balanced` y `fast`
- recuperación crash-safe y compactor en background

## API
SDK Java con opción REST.

## Roadmap
MVP → índices compuestos → replicación.

## Migración
Criterios claros para pasar a una base de datos externa.
