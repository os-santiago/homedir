# Prompt para Codex — Iteración 1 · Dashboard de Métricas (MVP)

## Objetivo (visión de negocio)
Como administradora/or quiero un dashboard de Métricas simple y rápido que me muestre, de un vistazo, la actividad clave del sitio para tomar decisiones sin navegar múltiples pantallas. Debe cargar rápido, ser claro y no exponer PII.

## Alcance (qué debe existir, sin imponer tecnología)

1) Tarjetas de resumen (fila superior)
   - "Registros a Mis Charlas (rango)" — total de registros a charlas por personas usuarias.
   - "Visitas a eventos (rango)" — total de vistas a páginas de evento, agregado por todos los eventos.
   - "Visitas a inicio (rango)" — total de vistas a la página de inicio.
   - "Visitas a perfil de usuario (rango)" — total de vistas al perfil de usuario.
   - "CTAs (rango)" — tres contadores visibles en la misma tarjeta:
       • Releases   • Reportar issue   • Ko-fi ☕
   - "Última actualización" — timestamp legible (ej.: "Actualizado hace 2 min").

   Texto secundario breve bajo cada tarjeta explicando qué se está contando (lenguaje no técnico).

2) Tablas esenciales (Top 10)
   - "Charlas con más registros (rango)"
       Columnas: Charla · Evento · Registros
   - "Eventos más visitados (rango)"
       Columnas: Evento · Visitas
   - "Speakers más visitados (rango)"
       Columnas: Orador/a · Visitas a perfil
   - "Escenarios más visitados (rango)"
       Columnas: Escenario · Evento · Visitas

   Requisitos:
   - Orden descendente por la métrica principal.
   - Máximo 10 filas por tabla (no paginación en MVP).
   - Placeholders de "Sin datos suficientes" cuando corresponda (en vez de tabla vacía).

3) Filtros de tiempo (aplican a TODO el dashboard)
   - Selector único: Hoy / Últimos 7 días / Últimos 30 días / Todo el evento.
   - Usar la misma zona horaria que el evento para consistencia.
   - Cambio de rango debe refrescar tarjetas y tablas sin forzar navegación.

4) UX, contenidos y estados
   - Carga inicial del dashboard < 300 ms con datos típicos (objetivo de producto).
   - Skeleton loaders breves mientras llega la data.
   - Mensajes de estado:
       • "Sin datos suficientes en este rango."
       • "Datos actualizados hace X min."
   - Lenguaje consistente: títulos, mayúsculas, etiquetas y unidades.
   - Sin PII (no mostrar emails, IDs personales ni IPs).

5) Accesibilidad y responsive (mínimos)
   - Etiquetas accesibles en tarjetas y tablas.
   - Orden de tabulación lógico.
   - Diseño que funcione en pantallas medianas (admin en laptop).

## Definiciones funcionales (mapping conceptual de negocio)
- "Registros a Mis Charlas": total de registros confirmados a charlas dentro del rango.
- "Visitas a eventos": suma de vistas a páginas de detalle/listado de cada evento dentro del rango.
- "Visitas a inicio": vistas de la página de inicio del sitio dentro del rango.
- "Visitas a perfil de usuario": vistas a la sección de perfil (agregado, no PII).
- "Speakers más visitados": vistas al perfil de cada orador/a dentro del rango.
- "Escenarios más visitados": vistas a cada escenario (y su evento) dentro del rango.
- "CTAs": conteos de clics en botones "Releases", "Reportar issue", "Ko-fi".

## Criterios de aceptación (DoD)
- CA1: Las tarjetas muestran totales correctos según el rango seleccionado.
- CA2: Las tablas muestran Top 10 ordenados por su métrica; si no hay datos, aparece "Sin datos suficientes".
- CA3: Cambiar el rango actualiza tarjetas y tablas de forma consistente y fluida.
- CA4: "Última actualización" muestra un tiempo relativo legible y coherente con los datos.
- CA5: Carga inicial del dashboard percibida como rápida (objetivo < 300 ms con datos típicos).
- CA6: No se expone PII; los textos son claros y consistentes.

## Pruebas funcionales (usuario/operación)
- Cambiar entre Hoy / 7 días / 30 días / Todo el evento → cifras cambian coherentemente.
- Rango sin actividad en alguna categoría → ver "Sin datos suficientes" SOLO en esa tabla.
- Validar que los totales de tarjetas igualan la suma de sus fuentes (según el rango).
- Verificar accesibilidad básica (tab-order, etiquetas) y comportamiento en pantalla mediana.
- Verificar "Actualizado hace X min" cambia tras una nueva lectura/refresh.

## Fuera de alcance (Iteración 1)
- Acciones "Ver" hacia pantallas de detalle, búsqueda y export CSV (llegan en Iteración 2).
- Tendencias (% Δ), comparativas y picos (Iteración 3).
- Segmentación por evento/escenario/speaker adicional (Iteración 4).
- Insights de CTAs extendidos (históricos con medias/desviaciones) (Iteración 5).
- Estado de salud del módulo de datos (Iteración 6).

