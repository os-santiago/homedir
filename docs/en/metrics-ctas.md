# Métricas de CTAs

## Definiciones y fórmulas
- **CTAs (rango)**: suma de clics en Releases, Reportar issue y Ko-fi dentro del rango seleccionado.
- **Media diaria (por CTA y Total)** = `suma_en_rango / nº_días_contemplados_en_el_rango`.
- **Desviación simple** sobre Total diario dentro del rango (método poblacional).
- **Picos**:
  - *Opción A (por defecto)*: Top-3 Totales diarios del rango.
  - *Opción B (configurable)*: Total ≥ media + 2×desv.est.
- **Zona horaria**: se utiliza la del evento para agrupar por día.

## Reglas de presentación
- Tabla ordenada por fecha descendente.
- Badges "Pico" con tooltip "Pico según regla configurada para este rango.".
- Placeholders y mensajes de "Sin datos para exportar en este rango." cuando corresponde.

## Enlaces
- URLs parametrizables mediante `links.releases-url`, `links.issues-url` y `links.donate-url`.
- Seguridad recomendada: `target="_blank"` + `rel="noopener"`.

## Privacidad y rendimiento
- No se expone PII; solo datos agregados.
- Se reutiliza snapshot/caché de métricas para evitar degradar tiempos de carga.
