# Guardrails

## Dependencias
Límites de dependencias reforzados con ArchUnit.

## Recursos
- pools de base de datos dedicados por módulo
- bulkheads, timeouts y rate limiting por módulo

## Operabilidad
- kill switches y feature flags
- health y readiness por módulo
- observabilidad etiquetada con `module`

## Edge
- prefijos de rutas por módulo
- readiness para mantenimiento selectivo
