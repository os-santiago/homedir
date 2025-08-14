# Metrics Dashboard

## Navigation Map
- **Talks:** each row links to the admin view of the talk at `/private/admin/events/{eventId}/edit` with the specific talk in context.
- **Events:** rows navigate to `/private/admin/events/{eventId}/edit`.
- **Speakers:** rows navigate to the speaker administration view at `/private/admin/speakers` focused on the selected speaker.
- **Scenarios:** rows navigate to `/private/admin/events/{eventId}/edit` with the scenario highlighted.
- The dashboard preserves the selected time range and event filter when navigating to and returning from these pages.

## Filtros y segmentación
- Los filtros de **Evento**, **Escenario** y **Speaker** se combinan con el rango de fechas.
- El evento delimita los escenarios disponibles; si un evento no tiene escenarios se muestra un selector deshabilitado con "Sin escenarios".
- El filtro de speaker restringe charlas y métricas asociadas a ese orador.

## Persistencia de contexto
- Los filtros y rango se representan mediante query params legibles: `range`, `event`, `stage` y `speaker`.
- Al navegar a vistas de detalle mediante "Ver" se mantienen estos parámetros para poder volver con el mismo estado.

## Copys / UX
- Etiquetas de filtros: "Evento", "Escenario", "Speaker".
- Placeholders: "Todos", "Buscar speaker…" y "Sin datos suficientes en este rango/segmento".
- Botón: "Copiar resumen". Toast al copiar: "Resumen copiado".

## Formato del resumen copiado
- Plantilla: `Rango: <rango>\nEvento: <evento>\nEscenario: <escenario>\nSpeaker: <speaker>\nEventos vistos: <n>\nCharlas vistas: <n>\nCharlas registradas: <n>\nVisitas a escenarios: <n>\nÚltima actualización: <timestamp>`
- Solo se incluyen totales agregados; nunca PII.

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

## Salud de datos y auto-refresh

### Reglas de “Salud de datos”
- Hoy: desactualizado si la edad del snapshot supera 2 min.
- Últimos 7 días: desactualizado si > 15 min.
- Últimos 30 días: desactualizado si > 30 min.
- Todo el evento: desactualizado si > 60 min.
- “Sin datos”: todas las tarjetas en 0 y todas las tablas vacías tras aplicar filtros/rango.

### Semántica de “Última actualización”
- Se toma del timestamp del snapshot de datos, nunca del reloj del cliente.
- Se muestra en formato relativo: “hace 2 min”, “hace 45 s”; para <1 s usar “justo ahora”.

### Auto-refresh
- Intervalo por defecto: 5 s (configurable).
- Coalesce de solicitudes: si hay un refresh en curso, no inicia otro.
- Re-render condicional por hash de datos.
- Notifica fallos de actualización solo una vez hasta un refresco exitoso.
- Controles: `Pausar/Continuar` (`data-testid="metrics-refresh-toggle"`, `aria-pressed`) y `Refrescar ahora` (`data-testid="metrics-refresh-now"`, throttle 2 s).

### Copys/UX
- Estados: “OK”, “Desactualizado”, “Sin datos”.
- Mensajes: “No pudimos actualizar; reintentaremos”, “Sin datos suficientes en este rango/segmento”.
- Accesibilidad: `aria-live` en estado y “Última actualización”; botones con `aria-pressed` y tooltips.

### Privacidad y performance
- Cero PII en mensajes o datos mostrados.
- Evitar parpadeos: sólo recargar al cambiar el hash de datos.
