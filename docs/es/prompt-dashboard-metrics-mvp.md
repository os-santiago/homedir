# Prompt para Codex — Iteración 1 · Dashboard de Métricas (MVP)

## Objetivo (visión de negocio)
Como administradora/or quiero un dashboard de Métricas simple y rápido que me muestre, de un vistazo, la actividad clave del sitio para tomar decisiones sin navegar múltiples pantallas. Debe cargar rápido, ser claro y no exponer PII.

## ALCANCE — IMPLEMENTACIÓN (CÓDIGO EN LA APLICACIÓN)

1) Pantalla/ruta de Admin → “Métricas”
   - Crear/actualizar la vista única de dashboard.
   - Mostrar “Última actualización: hace X min” con base en el timestamp de datos mostrados (no del sistema).
   - Skeleton loaders breves mientras se resuelven los datos.

2) Selector de rango global (aplica a TODO el dashboard)
   - Opciones: Hoy / Últimos 7 días / Últimos 30 días / Todo el evento.
   - Usar la misma zona horaria del evento.
   - Al cambiar el rango, refrescar tarjetas y tablas sin navegación y sin bloquear la UI.

3) Tarjetas de resumen (fila superior)
   - “Registros a Mis Charlas (rango)” — total dentro del rango.
   - “Visitas a eventos (rango)” — total dentro del rango.
   - “Visitas a inicio (rango)” — total dentro del rango.
   - “Visitas a perfil de usuario (rango)” — total dentro del rango.
   - “CTAs (rango)” — tres contadores visibles: Releases | Reportar issue | Ko-fi ☕.
   - Debajo de cada tarjeta, texto secundario breve explicando qué se cuenta (sin tecnicismos).
   - Estados vacíos: mostrar “0” y el texto aclaratorio (no ocultar la tarjeta).

4) Tablas esenciales (Top 10, sin paginación)
   - “Charlas con más registros (rango)” — Columnas: Charla · Evento · Registros.
   - “Eventos más visitados (rango)” — Columnas: Evento · Visitas.
   - “Speakers más visitados (rango)” — Columnas: Orador/a · Visitas a perfil.
   - “Escenarios más visitados (rango)” — Columnas: Escenario · Evento · Visitas.
   - Orden descendente por la métrica principal; máximo 10 filas.
   - Placeholder cuando no haya datos: “Sin datos suficientes en este rango.”

5) Adaptadores de datos (solo lectura; sin PII)
   - Conectar con las fuentes de métricas existentes y aplicar el rango seleccionado.
   - Mapear IDs a nombres legibles (charla, evento, orador, escenario) usando servicios/domino existentes.
   - Asegurar agregaciones correctas por rango para cada tarjeta/tabla.
   - Performance: evitar N+1; realizar una sola lectura por refresh (reutilizar snapshot/caché si existe).
   - No introducir nuevos identificadores personales ni datos sensibles.

6) Estados y errores de presentación
   - Mostrar placeholders y mensajes claros ante ausencia de datos.
   - Manejo amable de errores de lectura (mensaje no técnico; no bloquear toda la vista).

7) Accesibilidad y responsive (mínimos)
   - Etiquetas accesibles/aria en tarjetas y tablas.
   - Orden de tabulación lógico.
   - Correcto comportamiento en pantallas medianas (laptop).
   - Añadir data-testids para QA (ej.: `data-testid="metrics-card-registrations"`).

8) Rendimiento (presupuesto de producto)
   - Carga inicial percibida < 300 ms con datos típicos.
   - Transiciones de rango fluidas sin jank.

## ALCANCE — DOCUMENTACIÓN (DOCS A REGISTRAR)

D1) Definiciones funcionales (diccionario de métricas)
- “Registros a Mis Charlas”: total de registros confirmados a charlas dentro del rango.
- “Visitas a eventos”: suma de vistas a páginas de detalle/listado de cada evento dentro del rango.
- “Visitas a inicio”: vistas de- La lógica de cálculo y reglas se describen en `features/metrics.md`. a perfil de usuario”: vistas a la sección de perfil (agregado, no PII).
- “Speakers más visitados”: vistas al perfil del orador/a dentro del rango.
- “Escenarios más visitados”: vistas al escenario (y su evento) dentro del rango.
- “CTAs”: clics en “Releases”, “Reportar issue”, “Ko-fi”.

D2) Guía de uso del dashboard (README corto en `docs/`)
- Qué muestra cada tarjeta/tabla.
- Cómo funcionan los rangos y la “Última actualización”.
- Estados comunes (“Sin datos suficientes…”).

D3) Copys/UX (glosario de textos)
- Títulos/etiquetas de tarjetas y tablas.
- Mensajes de estado (loaders, sin datos, error no técnico).

D4) Criterios de rendimiento y privacidad
- Presupuesto de carga (<300 ms).
- Confirmación de “sin PII”.
- Buenas prácticas de agregación/lectura.

## CRITERIOS DE ACEPTACIÓN (DoD)

— Código —
- CA1: Tarjetas muestran totales correctos según el rango seleccionado.
- CA2: Tablas Top 10 ordenadas por su métrica; máximo 10 filas; placeholder cuando aplique.
- CA3: Cambiar rango refresca tarjetas y tablas de forma consistente y fluida.
- CA4: “Última actualización” se calcula con base en los datos mostrados y es legible (“hace X min”).
- CA5: Carga inicial < 300 ms con datos típicos; transiciones sin bloqueos.
- CA6: Sin PII en UI; accesibilidad mínima (aria, tab-order, contraste).

— Docs —
- CA7: Existe diccionario de métricas actualizado.
- CA8: Existe guía breve de uso del dashboard en `docs/`.
- CA9: Copys/UX documentados para mantener consistencia.

## PRUEBAS FUNCIONALES (USUARIO/OPERACIÓN)
- Cambiar entre Hoy / 7 días / 30 días / Todo el evento → cifras cambian coherentemente.
- Rango sin actividad en alguna categoría → ver “Sin datos suficientes” SOLO en esa tabla.
- Verificar que sumas y totales son coherentes por rango.
- Validar accesibilidad básica (tab-order, labels) y responsive en laptop.
- Verificar que “Actualizado hace X min” cambia tras un nuevo refresh.

## FUERA DE ALCANCE (Iteración 1)
- Acciones “Ver” hacia pantallas de detalle, búsqueda y export CSV (Iteración 2).
- Tendencias (%Δ), comparativas y picos (Iteración 3).
- Segmentación por evento/escenario/speaker adicional (Iteración 4).
- Insights de CTAs extendidos (históricos con medias/desviaciones) (Iteración 5).
- Estado de salud del módulo de datos (Iteración 6).

