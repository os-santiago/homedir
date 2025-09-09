# Tareas: notificaciones de eventos y ruptura

Para apoyar el requisito de notificar los cambios en el estado para los eventos y romper las ranuras cinco minutos antes de que comiencen o terminen, implementen las siguientes tareas:

- [] Establecer `notificaciones.upround.window` y` notifications.endingsoon.window` predeterminada a ** pt5m **.
- [] Crear un `eventstateEvaluator` para emitir` upcoming`, `iniciado`,` ending_soon` y 'terminados' notificaciones para el evento general.
- [] Evalúe las ranuras para romper (las conversaciones marcadas como `break`) y emiten las mismas notificaciones de estado, reutilizando` talkstateEvaluator` o introduciendo un evaluador dedicado.
- [] Notificaciones de eventos de superficie y ruptura en el centro de notificaciones junto con alertas de charlas.
- [] Actualizar documentación para describir la cobertura de eventos y romper y las nuevas ventanas de cinco minutos.
- [] Agregar pruebas de integración que verifican las notificaciones de eventos y rupturas están enqueadas en los momentos esperados.