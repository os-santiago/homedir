# Sistema de Métricas (V1)

Homedir registra eventos de interacción y los persiste asincrónicamente para mostrar insights a través del Dashboard de Administración.

## Descripción General
- **Almacenamiento**: `data/metrics-v1.json` (Escritura atómica, intervalo configurable).
- **Visualización**: Panel de Administración (`/private/admin`).
- **Privacidad**: Sin PII en exportaciones y vistas; datos agregados solamente.

## Ingesta de Insights de Desarrollo (Opcional)
- **Endpoint interno**: `/api/internal/insights/*` (oculto, deshabilitado por defecto).
- **Guardrails**:
  - `insights.ingest.enabled=false` por defecto.
  - `X-Insights-Key` obligatorio en cada request.
- **Integración CI**:
  - `INSIGHTS_INGEST_BASE_URL` (variable de GitHub)
  - `INSIGHTS_INGEST_KEY` (secret de GitHub)
- **Comportamiento**: Si faltan esas variables, los pasos CI omiten la ingesta sin fallar builds/releases.
- **Señales de falla**: CI/CD también emite eventos de falla cuando aplica:
  - `PR_VALIDATION_FAILED`
  - `PRODUCTION_RELEASE_FAILED`
  Esto permite que los dashboards de lead-time incluyan resultados no exitosos.
- **Ratios de calidad** (estado de admin insights):
  - Tasa de éxito de validación PR (`exitosas / total`)
  - Tasa de éxito de validación PR (últimos 7 días)
  - Tasa de éxito en producción (`production_verified / (production_verified + release_failed)`)
  - Tasa de éxito en producción (últimos 7 días)
- **Tendencia de entrega de corto plazo** (estado de admin insights):
  - Eventos en últimos 7 días
  - Eventos en 7 días anteriores
  - Delta de tendencia vs 7 días anteriores
  - Iniciativas activas en últimos 7 días
  - Eventos en últimos 30 días
  - Eventos en 30 días anteriores
  - Delta de tendencia vs 30 días anteriores
  - Iniciativas activas en últimos 30 días
  - Tipos de evento más frecuentes en últimos 7 días (top 5)
- **Guardrail de frescura** (estado de admin insights):
  - Minutos desde el último evento
  - Estado de frescura (`saludable`/`desactualizado`) basado en `insights.ledger.stale-minutes` (default 1440)
- **Iniciativas abiertas envejecidas** (estado de admin insights):
  - Iniciativas abiertas con antigüedad mayor a 7 días
  - Iniciativas abiertas con antigüedad mayor a 30 días
- **Exportación CSV**: Admin insights se puede exportar desde `/api/private/admin/insights/initiatives/export.csv` (solo admin).

## Eventos Registrados
- **Vistas de Página**: `Page_view: {route}`
- **Vistas de Evento**: `Event_view: {eventid}`
- **Vistas de Charla**: `Talk_view: {Talkid}`
- **Registros a Charla**: `Talk_register: {Talkid}` (Idempotente)
- **Visitas a Escenario**: `Stage_visit: {Stageid}: {yyyy-mm-dd}`
- **Popularidad Speaker**: `Speaker_popularity: {Speakerid}`
- **Clics CTA**: Botones Releases, Report Issue, Ko-Fi.

## Definiciones del Dashboard
El dashboard agrega estos eventos para mostrar:
- **Registros**: Inscripciones confirmadas a charlas en el rango.
- **Visitas**: Vistas de detalle/listado de eventos, home y perfiles.
- **Top Lists**: Speakers y escenarios más visitados.

## Lógica y Tendencias
- **Conversión**: `Suma(Registros) / Suma(Vistas de Charla)`.
- **Tendencias**: Comparación vs período anterior. Requiere base mínima (defecto 20) para mostrar %.
- **Ranking**: Entidades requieren vistas mínimas (defecto 20) para aparecer.

## Navegación y Filtros
- **Filtros**: Evento, Escenario, Speaker, Rango de fechas. Persistidos en URL.
- **Salud de Datos**: Auto-refresh cada 5s. Estado "Desactualizado" (>2min) o "Sin Datos".
- **Exportación**: CSV disponible para filas visibles. Formato: `metrics-<tabla>-<rango>.csv`.

## Validación Local
1. Ejecuta `mvn quarkus:dev`.
2. Navega el sitio para generar eventos.
3. Revisa `quarkus-app/data/metrics-v1.json`.
4. Ve al dashboard en `/private/admin`.
