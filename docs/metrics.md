# Métricas de uso (v1)

El sistema registra eventos de interacción y los persiste de forma asíncrona en `data/metrics-v1.json`.

## Eventos

- `page_view:{route}`
- `event_view:{eventId}`
- `talk_view:{talkId}` (deduplicado brevemente por sesión)
- `talk_register:{talkId}` (idempotente por usuario+charla)
- `stage_visit:{stageId}:{yyyy-mm-dd}` (zona horaria del evento)
- `speaker_popularity:{speakerId}` (derivado de registros)

## Persistencia

Los contadores se mantienen en memoria y se guardan periódicamente en `data/metrics-v1.json` usando escrituras atómicas. El intervalo puede configurarse con `metrics.flush-interval` (por defecto 10s).

## Validación local

1. Iniciar la aplicación:
   ```bash
   mvn -f quarkus-app/pom.xml quarkus:dev
   ```
2. Navegar por el sitio y registrar una charla.
3. Ver archivo `quarkus-app/data/metrics-v1.json` para observar los contadores.

## Lectura en Admin → Métricas

La vista de administración lee directamente `data/metrics-v1.json` y muestra:

- Tarjetas de resumen con vistas de eventos, charlas vistas, registros y visitas a escenarios.
- Conversión global y asistentes esperados (aprox. suma de registros).
- Tablas Top 10 de charlas, oradores y escenarios con mejor conversión.
- Exportación a CSV del contenido visible.

Las claves se mapean a entidades existentes usando los servicios en memoria:

- `talk_*` → título de la charla (`EventService.findTalk`).
- `speaker_popularity:*` → nombre del orador (`SpeakerService.getSpeaker`).
- `stage_visit:*` → nombre del escenario (`EventService.findScenario`).

### Conversión

- **Charlas:** `talk_register:{talkId} / talk_view:{talkId}`. Si las vistas son 0 se muestra "—".
- **Evento:** se utiliza la política **A** (suma de registros / suma de vistas de sus charlas). La alternativa **B** (promedio simple por charla) se consideró pero no se utiliza actualmente.
- **Escenarios y oradores:** agregados de las charlas asociadas.

Para evitar sesgos se aplica un umbral mínimo de vistas (`metrics.min-view-threshold`, default 20) para que una charla, escenario u orador aparezca en los rankings.

Si no hay datos suficientes el panel muestra un mensaje informativo en lugar de tablas vacías.
