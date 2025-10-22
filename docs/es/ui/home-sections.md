# Secciones de inicio

La página de inicio ahora diferencia eventos en dos bloques:

## Eventos disponibles
- Ordenados del más próximo al más lejano.
- Clasificación automática según la fecha/hora de término del evento.
- Badge `En curso` cuando el evento ya comenzó pero aún no finaliza.
- Microcopy de inicio: `Comienza hoy`, `Comienza en X días` o `Fecha por confirmar`.
- Estado vacío: *"No hay eventos próximos por ahora. Vuelve pronto 👀"*

## Eventos pasados
- Ordenados del más reciente al más antiguo.
- Badge `Finalizado` para todos los eventos listados.
- Se muestran con una apariencia equivalente a los eventos disponibles, pero en escala de grises para indicar que ya finalizaron.
- Estado vacío: *"Aún no tenemos eventos anteriores."*

## Now Box

Un bloque exclusivo de Now Box destaca qué está ocurriendo dentro de cada evento que está en curso:

- Muestra, por evento activo, la última actividad finalizada, la que está en progreso y la siguiente dentro de una ventana configurable.
- Identifica explícitamente los descansos para conservar el contexto de agenda incluso cuando no hay charlas en vivo.
- Cada tarjeta enlaza a la agenda del evento o al detalle de la actividad (`/event/{id}`, `/event/{id}/talk/{talkId}` o `#break-{talkId}`) para acelerar la navegación.
- La lista prioriza eventos con una actividad en curso; el resto se ordena por el próximo elemento más cercano.

Las claves de configuración en `application.properties` controlan su comportamiento:

- `nowbox.lookback` (valor por defecto `PT30M`) define cuán lejos mira al pasado para la actividad "anterior".
- `nowbox.lookahead` (valor por defecto `PT60M`) limita la búsqueda de la actividad "siguiente".
- `nowbox.refresh-interval` (valor por defecto `PT30S`) indica al frontend cada cuánto actualizar el bloque.

Si ninguna actividad cae dentro de las ventanas, el evento se omite del Now Box para mantener la sección enfocada en el contexto inmediato.
