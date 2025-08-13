# User Data — Persistencia, Capacidad y Alto Rendimiento (v1)

Este documento resume las capacidades realmente implementadas para la persistencia de charlas y eventos registrados por los usuarios.

## Alcance actual

- **Persistencia asíncrona.** Los eventos, oradores y agendas de usuario se guardan en archivos JSON mediante una cola de un solo escritor con escrituras atómicas y reintentos. Si la cola se llena, las escrituras se descartan y se registra el error.
- **Carga del último año.** Al iniciar la aplicación se determina el año más reciente disponible y se carga en memoria la agenda correspondiente.
- **Lecturas desde memoria.** Las consultas se responden desde un _snapshot_ en memoria. La primera lectura abre una ventana de refresco de 1–2 s que coalesce múltiples solicitudes en un solo acceso a disco.
- **Histórico bajo demanda.** Existe soporte a nivel de servicio para cargar o liberar años anteriores (`loadHistorical` / `unloadHistorical`) sujeto a evaluación de capacidad, aunque aún no hay interfaz de usuario.
- **Admisión por capacidad.** Antes de acceder a rutas privadas se verifica memoria y espacio en disco; si el sistema está saturado se responde con “Debido a alta demanda, no podemos gestionar tus datos en este momento. Inténtalo más tarde.”
- **Panel de capacidad.** En `/private/admin/capacity` se muestran el modo actual, uso de memoria y disco, y métricas de lecturas y escrituras.

## Configuración relevante

```properties
read.window=PT2S        # ventana de refresco para lecturas
read.max-stale=PT10S    # máximo tiempo de datos obsoletos
persist.queue.max=10000 # tamaño máximo de la cola de escrituras
```

Los datos se almacenan en la carpeta `data/` y las escrituras son atómicas (`archivo.tmp → replace`) con hasta tres reintentos.

## Fuera de alcance en v1

- Interfaz gráfica para recuperar histórico por año.
- Edición de presupuestos de memoria o disco desde el panel de capacidad.
- Alertas externas cuando la capacidad se satura.

