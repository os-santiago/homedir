# Dashboard de Métricas – Tendencias

## Definición de tendencia
- **Δ% = (Actual − Base) / Base × 100** cuando `Base ≥ min-baseline`.
- Ventanas comparativas (misma zona horaria del evento):
  - **Hoy** → comparado con *Ayer*.
  - **Últimos 7 días** → comparado con los *7 días anteriores*.
  - **Últimos 30 días** → comparado con los *30 días anteriores*.
  - **Todo el evento** → tendencia deshabilitada (`N/A`).

## Reglas de casos límite
- `Base < min-baseline` → mostrar `muestra baja` (sin porcentaje).
- `Base = 0` y `Actual > 0` → badge `nuevo +n` (Δ absoluto).
- `Actual = 0` y `Base ≥ min-baseline` → `▼ 100%`.
- Redondeo: 1 decimal si `|Δ| < 10%`, enteros en caso contrario. Límite `"<0.1%"` y sin `−0%`.

## Tablas y ranking de crecimiento
- Cada fila de las tablas Top 10 incluye un badge Δ con las reglas anteriores.
- Tabla adicional: **Top 5 crecimiento (rango)** para *Registros a Mis Charlas*.
  - Columnas: `Charla · Evento · Registros · Δ`.
  - Orden principal por Δ absoluto descendente; desempates por Δ% y luego nombre.
  - Placeholder: `Datos insuficientes para calcular tendencias en este rango.`

## Copys / UX
- Badges: `nuevo`, `muestra baja`, `N/A (todo el evento)`.
- Tooltip: `Comparado con {período anterior}`.
- `aria-label` ejemplos: `Subió X% respecto al período anterior`, `Bajó…`.

## QA / Validaciones
- Matriz de pruebas con ejemplos de `base=0`, `base < min-baseline`, caídas y crecimientos.
- Confirmar que los Δ respetan el rango seleccionado y la zona horaria del evento.

