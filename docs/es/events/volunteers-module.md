# Modulo de Voluntarios (stub)

Este documento es el espejo en espanol de `docs/en/events/volunteers-module.md`.
Para contenido canonico y mantenimiento, usar la version en ingles.

## Resumen
- Permite postular voluntarios por evento con ventana configurable.
- Admin revisa, califica y cambia estado de postulaciones.
- Voluntarios seleccionados acceden a una sala privada (Volunteer Lounge).
- La trayectoria de voluntariado se refleja en perfiles privado/publico.

## Estados
- `applied`
- `under_review`
- `selected`
- `not_selected`
- `withdrawn`

## Guardrails clave
- Control por ventana (`opens_at`, `closes_at`, `accepting_submissions`).
- Validacion estricta de transiciones de estado.
- Concurrencia optimista con `expected_updated_at`.
- Lounge: max 200 caracteres, 1 post/minuto por usuario/evento, max 500 mensajes por evento.

## Notificaciones
- Cambio de estado por admin notifica al postulante en Notifications Center.

## Metricas e Insights
- Funnel:
  - `volunteer_submit`
  - `volunteer_selected`
  - `volunteer_lounge_post`
- Insights automaticos:
  - `VOLUNTEER_SUBMITTED`
  - `VOLUNTEER_UPDATED`
  - `VOLUNTEER_WITHDRAWN`
  - `VOLUNTEER_STATUS_*`
  - `VOLUNTEER_RATING_UPDATED`
  - `VOLUNTEER_LOUNGE_POSTED`

## Referencia canonica
- [EN: Volunteers Module](../../en/events/volunteers-module.md)
