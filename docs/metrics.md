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
