# Metrics Dashboard

## Navigation Map
- **Talks:** each row links to the admin view of the talk at `/private/admin/events/{eventId}/edit` with the specific talk in context.
- **Events:** rows navigate to `/private/admin/events/{eventId}/edit`.
- **Speakers:** rows navigate to the speaker administration view at `/private/admin/speakers` focused on the selected speaker.
- **Scenarios:** rows navigate to `/private/admin/events/{eventId}/edit` with the scenario highlighted.
- The dashboard preserves the selected time range and event filter when navigating to and returning from these pages.

## Export specification
- Export only the rows and columns visible in the current table, respecting time range, search terms and order.
- Columns per table:
  - **Talks:** `Charla`, `Evento`, `Registros`
  - **Events:** `Evento`, `Visitas`
  - **Speakers:** `Orador/a`, `Visitas a perfil`
  - **Scenarios:** `Escenario`, `Evento`, `Visitas`
- File name format: `metrics-<tabla>-<rango>-<YYYYMMDD-HHMM>.csv` using the event's timezone.
- CSV never includes personal identifiable information.

## Copy & UX
- Buttons: `Ver`, `Exportar CSV`
- Placeholder: `Buscar…`
- Disabled export message: `Sin datos para exportar en este rango.`
- Toast: `CSV descargado`

## Tendencias
- Cada tarjeta y fila de tabla puede mostrar un badge de tendencia con la variación vs. el período anterior.
- La lógica de cálculo y reglas se describen en `metrics-trends.md`.

## QA / Checklist
- Verify that applying range, search and order is reflected in the exported CSV.
- Clicking `Ver` opens the correct admin page and preserves context when returning.
- When a table has no data, export button is disabled and shows the no-data message.
- Keyboard navigation reaches all `Ver` and `Exportar CSV` buttons and aria labels describe the destination.
- Confirm CSV contains no personal identifiers such as emails or personal IDs.
