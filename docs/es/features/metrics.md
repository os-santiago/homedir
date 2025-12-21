# Sistema de Métricas (V1)

Homedir registra eventos de interacción y los persiste asincrónicamente para mostrar insights a través del Dashboard de Administración.

## Descripción General
- **Almacenamiento**: `data/metrics-v1.json` (Escritura atómica, intervalo configurable).
- **Visualización**: Panel de Administración (`/private/admin`).
- **Privacidad**: Sin PII en exportaciones y vistas; datos agregados solamente.

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
