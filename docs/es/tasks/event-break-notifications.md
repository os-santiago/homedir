# Tareas – notificaciones de eventos y descansos

Estado: ✅ Completado. Estas notas documentan el alcance que se entregó.

Para apoyar el requisito de notificar los cambios de estado de los eventos y los descansos cinco minutos antes de que comiencen o terminen, se implementó lo siguiente:

- [x] Configurar los valores predeterminados de `notifications.upcoming.window` y `notifications.endingSoon.window` en **PT5M**.
- [x] Crear un `EventStateEvaluator` que emite notificaciones `UPCOMING`, `STARTED`, `ENDING_SOON` y `FINISHED` para el evento general.
- [x] Evaluar las sesiones marcadas como `break` y emitir las mismas notificaciones de estado mediante un evaluador dedicado que reutiliza la lógica de charlas.
- [x] Mostrar las notificaciones de eventos y descansos en el centro de notificaciones junto con las alertas de charlas.
- [x] Actualizar la documentación para describir la cobertura de eventos y descansos y las ventanas de cinco minutos.
- [x] Agregar pruebas de integración que verifican que las notificaciones de eventos y descansos se encolan en los momentos esperados.
