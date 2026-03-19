# Campaigns Module (stub)

Documento espejo de `docs/en/features/campaigns.md`.

Resumen:

- `Campaigns` sera el modulo interno de operaciones de marketing de HomeDir.
- Su funcion inicial es generar borradores internos a partir de datos reales del producto.
- El flujo interno actual ya permite revisar, aprobar y programar borradores sin publicar a canales externos.
- Discord queda implementado como primer publisher, pero solo opera si existe configuración explícita, dry-run controlado y guardrails activos.
- Bluesky y Mastodon quedan implementados como publishers adicionales, también protegidos por configuración explícita, dry-run y guardrails activos.
- El rollout obligatorio es:
  - borradores internos
  - aprobacion/programacion
  - autopublicacion por canal tras validacion en produccion

Ver documento canonico:

- [EN: Campaigns Module](../../en/features/campaigns.md)
