# Dashboard de métricas

## Mapa de navegación
- ** Talks: ** Cada fila enlaza a la vista de administración de la charla en `/private/admin/events/{eventId}/edit` con la charla específica en contexto.
- ** Eventos: ** Las filas navegan a `/private/admin/events/{eventId}/edit`.
- ** Altavoces: ** Las filas navegan a la vista de administración del altavoz en `/private/admin/altavers` centrado en el altavoz seleccionado.
- ** Escenarios: ** Las filas navegan a `/private/admin/events/{eventId}/edit` con el escenario resaltado.
- El tablero conserva el rango de tiempo y el filtro de eventos seleccionados al navegar y regresar desde estas páginas.

## FILTROS Y SMIGMACIÓN
- Los Filtros de ** Evento **, ** Escenario ** Y ** altavoz ** se combinan con el rango de fechas.
- El Evento Delimita Los Escenarios Disponibles; si un eviento no tiene escenarios se miscedra un selector deshabilitado con "sin escenarios".
- El filtro de altavoz restringe Charlas y Métricas Asociadas a ese orador.

## Persistencia de contexto
- LOS FILTROS Y RANGO SE Representan Mediante Consuly Params Legibles: `Range`,` Event`, `Stage` y 'Speaker`.
- Al Navegar A VISTAS DE MEDIANTE DE DETALLE "ver" se Mantienen Estos parámetros para poder volver con el mismo estado.

## Copys / UX
- Etiquetas de Filtros: "Evento", "Escenario", "Orador".
- Deflectores de posición: "Todos", "BUSCAR SANTANTE ..." Y "sin datos Suficientes en este rango/segmento".
- Botón: "Copiar resumen". Toast al Copiar: "Reumente Copido".

## Formato del resumen Copiado
- Plantilla: `rango: <RANGO> \ NEVEVO: <VENTO> \ NESCENARIO: <cenario> \ nspeaker: <hailer> \ nvamosos vistos: <n> \ ncharlas Vistas: <n> \ ncharlas Registras: <n> \ nVisitas a Escenarios: <n> \ núltima Realización: <timestón <n` timestón: <timestón <Timestón <Timestón.
- Solo Se incluyen Totes Agregados; nunca pii.

## Especificación de exportación
- Exportar solo las filas y columnas visibles en la tabla actual, respetando el rango de tiempo, los términos de búsqueda y el orden.
- columnas por tabla:
  - ** Talks: ** `Charla`,` Evento`, `Registros`
  - ** Eventos: ** `Evento`,` Visitas`
  - ** Altavoces: ** `orador/a`,` visitas a perfil`
  - ** Escenarios: ** `Escenario`,` Evento`, `Visitas`
-Formato de nombre de archivo: `Métricas- <Alta>-<RANGO>-<YYYYMMDD-HHMM> .CSV` usando la zona horaria del evento.
- CSV nunca incluye información de identificación personal.

## copia y ux
- Botones: `ver`,` exportar csv`
- marcador de posición: `Buscar ...`
- Mensaje de exportación deshabilitado: `sen datos para exportar en este rango.
- Tostador: `CSV Descargado`

## Tendencias
- CADA TARJETA Y FILA DE TABLA PUEDE MARTRAR UN INDERIA DE TENDENCIA CON LA VARIACIÓN VS. EL PERÍODO ANTERIOR.
- La Lógica de Cálculo y Reglas se describe en `Metrics Trends.md`.

## QA / Lista de verificación
- Verifique que la aplicación de rango, búsqueda y orden se refleje en el CSV exportado.
- Al hacer clic en `ver` abre la página de administración correcta y preserva el contexto al regresar.
- Cuando una tabla no tiene datos, el botón Exportar está deshabilitado y muestra el mensaje sin datos.
- La navegación del teclado alcanza todos los botones `ver` y` exportar CSV` y las etiquetas de aria describen el destino.
- Confirmar CSV no contiene identificadores personales como correos electrónicos o identificaciones personales.

## Salud de dato y auto-refresh

### Reglas de "Salud de Datos"
- Hoy: desactualizado Si la Edad del Snapshot Supera 2 min.
- Últimos 7 Días: desactualizado si> 15 min.
- Últimos 30 Días: desactualizado si> 30 min.
- TODO EL EVENTO: desactualizado si> 60 min.
- "sin datos": Todas las tarjetas en 0 y Todas las Tablas vacías tras aplicar filtros/rango.

### SEMANTICA DE "ÚLTIMA REALIZACIÓN"
- SE Toma del Timestamp del Snapshot de Datos, Nunca del Reloj del Cliente.
- Se MaSm. En Formato Relativo: "Hace 2 min", "Hace 45 s"; Para <1 S USAR "JUSTO AHORA".

### AUTO-FRESH
- Intervalo por defecto: 5 s (configurable).
- Counsce de Solicitudes: Si Hay Un Refresh en Curso, sin inicia otro.
- Reiniciar la condicional por hash de datos.
- Notifica Fallos de actualización Solo una Vez Hasta Un Refresco ExitoSo.-Controles: `Pausar/Continuar` (` data-testid = "metross-refresh-toggle" `,` aria prensado`) y `refrescar ahora` (` data-testid = "metrics-refresh-now" `, acelerador 2 s).

### Copys/UX
- Estados: "Ok", "desactualizado", "sin datos".
- Mensajes: "No Pudimos actualizar; Reintentaremos", "sen datos suficientes en este rango/segmento".
- AccesibiliDad: `Aria-Live` en Estado y" Última actualización "; Botones Con `aria prensada` ° de herramientas.

### PRIVACIDAD Y RENDIMIENTO
- Cero Pii en Mensajes o Datos Mostrados.
- Evitar parpadeos: Sólo Recargar Al Cambiar El Hash de Datos.