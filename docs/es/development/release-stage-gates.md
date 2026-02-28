# Release Stage Gates (stub)

Documento canonico: `docs/en/development/release-stage-gates.md`.

Resumen:

- Define los criterios minimos para avanzar entre `Alpha`, `Beta`, `RC` y `GA`.
- Establece gates tecnicos de CI/CD (coverage + smoke) y criterios de estabilidad.
- Incluye plan de marcha blanca para subir cobertura por etapas sin romper la cadena de entrega.
- Incluye monitoreo automatizado de salud de pipelines (`Pipeline Health`) en ventana de 14 dias.
- Incluye gate de seguridad en modo advisory (`Security Advisory`) para dependency review + CodeQL con opcion `enforce=true` en ejecucion manual.

Para cambios y mantenimiento, editar primero la version en ingles y luego actualizar este espejo.
