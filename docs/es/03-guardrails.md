# Rieles de seguridad

## Dependencias
- core no importa otros core.
- app no depende de adapters.
- core es framework-free.
- [ArchUnit](https://www.archunit.org/) obligatorio.

## Recursos por módulo
- datasources, pools, caches y threads dedicados.
- límites estrictos configurables.

## Resiliencia
- Fault Tolerance (timeouts, retries, circuit breaker).
- rate-limit y kill-switches por módulo.

## Operación
- health y readiness por módulo.
- métricas y trazas etiquetadas con `module`.
