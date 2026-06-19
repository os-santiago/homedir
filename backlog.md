# Product Backlog

- [ ] **ISSUE-794: [P6] Voluntario: CFV No se ve reflejado en perfil de usuario**
  - *Description*: **Descripción del error**
  - El estado de Voluntario aceptado no se ve reflejado en ninguna parte del perfil de usuario
  - **Pasos para reproducir**
  - 1. Ir a Perfil
  - 2. Hacer click en Ver Perfil

- [ ] **ISSUE-743: [P5] Spike: bot de Discord para crear issues y notificar resolución**
  - *Description*: ## Tipo
  - Spike / baja prioridad
  - ## Objetivo
  - Explorar un bot de Discord que reciba solicitudes de ayuda, cree issues en GitHub y notifique al usuario cuando el issue esté resuelto y listo para validación.
  - ## Alcance

- [ ] **ISSUE-742: [P1] Soporte: canal oficial de Discord visible en HomeDir para eventos**
  - *Description*: ## Objetivo
  - Crear un canal oficial de soporte en el Discord de OpenSourceSantiago y referenciarlo en HomeDir en los puntos relevantes de nuevas implementaciones y flujos de evento.
  - ## Alcance
  - - Definir o crear el canal oficial de soporte en Discord.
  - - Referenciar ese canal dentro de HomeDir.

- [ ] **ISSUE-741: [P0] CFV: tabla de turnos y asignación de disponibilidad por evento multi día**
  - *Description*: ## Objetivo
  - Permitir que los voluntarios seleccionados definan su disponibilidad por segmentos horarios dentro del evento.
  - ## Reglas funcionales
  - - Cada segmento dura 2 horas.
  - - Cada voluntario debe elegir un mínimo de 2 segmentos por día.

- [ ] **ISSUE-740: [P1] Notificaciones: segmentación por grupo CFP, CFV y staff de evento**
  - *Description*: ## Objetivo
  - Extender el módulo de notificaciones de HomeDir para permitir envíos segmentados por grupos de usuarios asociados al evento.
  - ## Audiencias requeridas
  - - Grupo CFP seleccionado
  - - Grupo CFV seleccionado

- [ ] **ISSUE-738: [P0] CFP: gestión de presentación, bloques y seguimiento de entrega**
  - *Description*: ## Objetivo
  - Permitir que los ponentes gestionen su ponencia seleccionada, incluyendo la carga de su presentación, la visualización de sus datos de sesión y el seguimiento de completitud por parte de administración.
  - ## Alcance
  - - Mostrar al ponente su tema seleccionado y duración.
  - - Permitir que el sistema o un administrador asigne bloque y escenario.

- [ ] **ISSUE-737: [P0] Evento: publicar seleccionados de CFP y CFV con historial público por usuario**
  - *Description*: ## Objetivo
  - En el contexto de DevOpsDays Santiago 2026, publicar de forma visible dentro del evento a las personas seleccionadas para CFP y CFV, y registrar esa participación en el perfil público e interno de cada usuario de HomeDir.
  - ## Alcance
  - - Crear un dashboard público por evento para CFP seleccionados.
  - - Crear un dashboard público por evento para CFV seleccionados.

- [ ] **ISSUE-736: [P4] [Backend] EconomyService: gamificación en cada listado de contenido y límite de compra solo advierte**
  - *Description*: ## Descripción del error
  - Dos problemas en EconomyService y CommunityContentApiResource:
  - 1. **Gamificación en cada page load** — CommunityContentApiResource.java: Cada GET autenticado a la lista de contenido comunitario dispara gamificationService.award(), amplificando escrituras innecesariamente.
  - 2. **Límite de compra solo advierte** — EconomyService.java: enforcePurchaseBurstGuard() solo lanza excepción si economy.guard.strict=true. Si está en false, el rate limit es informativo.
  - ## Comportamiento esperado

- [ ] **ISSUE-734: [P5] [Refactor R6] CI/CD: quality gates, pruebas en deploy y protección de rama**
  - *Description*: ## Refactor R6 — Pipeline CI/CD
  - Agrupa issues de la cadena de integración y despliegue continuo.
  - ### Alcance
  - - Tests omitidos en build de producción (release.yml usa -DskipTests)
  - - Pipeline de calidad documentado (SBOM, SAST, escaneo vulnerabilidades) no existe en workflows reales

- [ ] **ISSUE-733: [P5] [Refactor R5] Arquitectura y deuda técnica: persistencia, concurrencia, documentación y diseño**
  - *Description*: ## Refactor R5 — Arquitectura y Deuda Técnica
  - Agrupa issues de arquitectura, diseño y deuda técnica profunda. Incluye persistencia, concurrencia, documentación y plantillas.
  - ### Alcance
  - - Configuración de notificaciones con campos public static mutables sin volatile
  - - Persistencia: errores silenciosos, pérdida de datos, fsync ineficiente, serialización completa en cada escritura

