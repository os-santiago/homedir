# Campaigns Module (stub)

Documento espejo de `docs/en/features/campaigns.md`.

Resumen:

- `Campaigns` sera el modulo interno de operaciones de marketing de HomeDir.
- Su funcion inicial es generar borradores internos a partir de datos reales del producto.
- El flujo interno actual ya permite revisar, aprobar y programar borradores sin publicar a canales externos.
- Discord queda implementado como primer publisher, pero solo opera si existe configuración explícita, dry-run controlado y guardrails activos.
- Bluesky y Mastodon quedan implementados como publishers adicionales, también protegidos por configuración explícita, dry-run y guardrails activos.
- LinkedIn queda implementado como handoff asistido: copy manual + confirmación desde admin, sin autopublicación.
- Campaigns ya expone observabilidad operativa con resumen de colas, actividad reciente y visibilidad de handoffs pendientes.
- Campaigns ahora recomienda ventanas de envío usando patrones reales de actividad de HomeDir antes de programar borradores.
- Campaigns ahora expone preview packs internos por canal para revisar el copy final antes de aprobar o programar.
- Campaigns ahora expone URLs trazables y un resumen interno de visitas atribuidas por borrador.
- El rollout obligatorio es:
  - borradores internos
  - aprobacion/programacion
  - autopublicacion por canal tras validacion en produccion

Ver documento canonico:

- [EN: Campaigns Module](../../en/features/campaigns.md)
