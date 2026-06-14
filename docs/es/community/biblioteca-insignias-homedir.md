# Biblioteca de insignias de HomeDir

Autor: os-santiago

Esta biblioteca define insignias propias de HomeDir basadas en el Reputation Hub y en las señales públicas que ya existen en el producto.

El objetivo no es inventar etiquetas decorativas. El objetivo es definir un conjunto pequeño y medible de insignias que represente trabajo real dentro de HomeDir y que luego pueda mostrarse en el perfil, la reputación o la comunidad.

## Reglas de diseño

- Cada insignia debe mapear a una acción o resultado real de HomeDir.
- Cada insignia debe poder verificarse con datos del repo o del producto.
- Cada insignia debe tener una ruta clara de desbloqueo y una forma clara de fallar.
- Cada insignia debe entenderse sin contexto de chat privado.
- Cada insignia debe reforzar estabilidad, calidad o valor comunitario.

## Familias de insignias

### 1. Insignias Builder

Estas insignias premian trabajo visible sobre el producto.

#### First Build

- Representa: una primera contribución útil que llega a producción.
- Ruta de desbloqueo: abrir una issue pequeña, implementar el fix, agregar una prueba y fusionarlo.
- Evidencia: PR fusionada con issue enlazada y validación verde.
- Efecto visible: marca a la persona como alguien que entrega cambios útiles.

#### Builder Signal

- Representa: contribución repetida a superficies visibles del producto.
- Ruta de desbloqueo: varias PR fusionadas que toquen comportamiento, copy o navegación.
- Evidencia: historial de PR fusionadas en iteraciones separadas.
- Efecto visible: muestra que la persona puede seguir entregando sin desestabilizar la app.

### 2. Insignias Helper

Estas insignias premian apoyo práctico y buena revisión.

#### First Review

- Representa: una revisión útil de una PR que produjo una mejora.
- Ruta de desbloqueo: dejar feedback accionable y acompañarlo hasta su resolución.
- Evidencia: thread de review, comentarios resueltos y un cambio de código resultante.
- Efecto visible: reconoce el trabajo de revisión como parte de la entrega.

#### Helper Signal

- Representa: ayuda repetida que mejora la PR de otra persona.
- Ruta de desbloqueo: revisar varias PRs, enfocarse en riesgo y cerrar el ciclo.
- Evidencia: actividad de review y threads resueltos.
- Efecto visible: destaca a quienes reducen ambigüedad y regresiones.

### 3. Insignias Learner

Estas insignias premian consistencia y crecimiento.

#### First Step

- Representa: empezar desde una issue trazada y terminar la primera contribución real.
- Ruta de desbloqueo: tomar una issue, entregar una PR y cerrar el ciclo.
- Evidencia: enlace issue -> PR y merge.
- Efecto visible: marca el paso de observador a contribuidor.

#### Consistency Streak

- Representa: participación sostenida en el tiempo.
- Ruta de desbloqueo: contribuir en periodos consecutivos en lugar de hacerlo una sola vez.
- Evidencia: PR fusionadas, issues cerradas u otra actividad visible distribuida en el tiempo.
- Efecto visible: premia el ritmo confiable por sobre los picos ruidosos.

### 4. Insignias Coder

Estas insignias se basan en la sección Coders del Reputation Hub.

#### Code Explorer

- Representa: actividad equilibrada entre commits, issues y PRs.
- Ruta de desbloqueo: participar de forma relevante en las tres áreas.
- Evidencia: historial de commits, actividad en issues y PRs fusionadas.
- Efecto visible: muestra compromiso amplio con el repo, no solo churn de código.

#### Coders Lead

- Representa: la mayor actividad combinada entre quienes aparecen en el ranking Coders.
- Ruta de desbloqueo: seguir sumando commits, issues y PRs hasta liderar el ranking combinado.
- Evidencia: leaderboard Coders del Reputation Hub.
- Efecto visible: destaca involucramiento técnico amplio.

### 5. Insignias Speaker

Estas insignias premian contribución pública.

#### First Talk

- Representa: la primera contribución pública de speaking en el flujo comunitario.
- Ruta de desbloqueo: enviar, aceptar o completar un camino asociado a una charla o CFP en HomeDir.
- Evidencia: actividad de eventos o CFP visible en el producto.
- Efecto visible: reconoce presencia pública en la comunidad.

#### Speaker Signal

- Representa: actividad repetida relacionada con charlas.
- Ruta de desbloqueo: seguir contribuyendo en rutas de talks, CFPs o speaking.
- Evidencia: entradas repetidas asociadas a speaking.
- Efecto visible: muestra contribución pública sostenida.

## Superficies donde pueden mostrarse

HomeDir puede mostrar estas insignias en:

- el perfil público
- el Reputation Hub
- el resumen de contribución comunitaria
- futuras colecciones o widgets de perfil

## Orden sugerido de despliegue

1. Definir los nombres canónicos y los íconos.
2. Agregar reglas de elegibilidad usando datos que ya existen en el repositorio.
3. Exponer previsualizaciones de insignias en el perfil y en Reputation Hub.
4. Agregar tests para la lógica de desbloqueo y visibilidad.
5. Agregar solo las insignias que sigan siendo útiles después del uso real.

## Notas de estabilidad

- Mantener el catálogo pequeño hasta que las reglas demuestren estabilidad.
- Preferir insignias ganadas sobre etiquetas decorativas.
- Evitar duplicar el mismo concepto con nombres distintos.
- Revisar umbrales solo cuando haya evidencia de que la señal cambió.
