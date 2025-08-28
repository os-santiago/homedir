# EventFlow — Historia de Desarrollo  
*De cero a producción con Quarkus + OIDC + Qute, iterando con asistencia de IA.*

---

## 1) Historia desde el punto de vista de **requerimientos de usuario**

### Visión
- **EventFlow** nace como una app libre para *descubrir eventos y navegar su contenido* (escenarios, charlas, horarios) de forma **simple, moderna y muy visual**.
- Priorizamos: **claridad de información**, **navegación intuitiva**, **bajo impacto** (costo/huella) y **rapidez de entrega**.

### Épicas y funcionalidades
- **Descubrimiento & navegación**
  - Home con *tarjetas de evento*, ordenadas por proximidad temporal (“Faltan X días”).
  - Detalle de evento con **agenda por día**, escenarios y charlas enlazadas.
  - Flujo bidireccional: *Evento* ⇄ *Escenario* ⇄ *Charla* (enlaces cruzados).
  - Botón “**Cómo llegar**”: muestra imagen del mapa del escenario.
- **Perfil y personalización**
  - “**Mis Charlas**”: registro personal de charlas favoritas.
  - Estados por tiempo: *A tiempo* / *Pronto* / *En curso* / *Finalizada*.
  - Mejoras UX: feedback de éxito/error, botón “Ir a Mis Charlas”.
- **Administración**
  - CRUD de **Eventos**, **Escenarios** y **Charlas** (acceso restringido por admin-list).
  - Auto-ID por timestamp (no pedir ID).
  - **Fecha del evento** administrable; inicio/fin del evento calculados desde la agenda.
  - **mapUrl** administrable (link “Ver mapa” en el detalle).
  - Importar/Exportar datos: pasó a **Backup/Restore** (ZIP) cuando agregamos persistencia.
- **Oradores (Ponentes)**
  - Modelo reutilizable: *Orador ↔ Charla*; una charla pertenece a un orador; una charla puede incluirse en múltiples eventos.
  - Detalle de orador con biografía y charlas.
- **UX/UI**
  - Eliminación de entradas redundantes del menú (p. ej. “Eventos” si Home ya los muestra).
  - Header con **logos** (EventFlow + “powered by OpenSourceSantiago”).
  - **Project Header** (dashboard de estado): versión, releases, issues y Ko-fi.
  - Home con *timeline* y layout coherente con la identidad (agua/viento, “code snippets”, consola/chat).

---

## 2) Historia desde el punto de vista de **evolución técnica**

### Autenticación & autorización
- **OIDC con Google**  
  - Incidentes iniciales: `invalid_client`, `redirect_uri_mismatch`, JWKS y scopes.
  - Ajustes de Quarkus OIDC: `redirect-path`, `scopes=openId profile email`, corrección de callback y obtención fiable de *email* y *claims* (desde **ID Token** o *userinfo*).
  - Logout: corrección de flujos y pruebas (303 esperado, cabecera `Location`).

### Renderizado & templates (Qute)
- Errores típicos resueltos:
  - Tags mal cerrados (`{#main}` / `{/main}`, `{#raw}` / `{/raw}`).
  - Doble `{#insert}` y *partials* con `include` + *slots*.
  - Fugas de lógica al HTML (aparecía `{:else if ...}` en la vista): limpieza de condicionales.
- Refactor: *partials* reutilizables y estilos consistentes.

### Datos & persistencia
- **Fase 1 (in-memory)**  
  - `EventService` con `ConcurrentHashMap`.  
  - Problema: en clúster/escala o reinicios, los datos desaparecían → *export vacíos* y vistas inconsistentes.
- **Fase 2 (persistencia ligera)**
  - **PVC de Kubernetes** como directorio de datos de la app.
  - Capa asíncrona de **cola de persistencia**: cada cambio en Event/Orador/Charla se serializa a JSON (JSON-B/Jackson soportados por Quarkus) y se escribe a disco.
  - **Carga al arranque**: bootstrap de datos desde el PVC y *logs* explícitos del proceso.
  - **Backup/Restore manual**: ZIP de la última foto de datos y restauración segura (reemplazo atómico; validaciones y logs).
  - Dashboard admin: métrica simple de espacio disponible y alertas básicas.
- **Git sync**  
  - Intento inicial (JGit) para “eventos como código” → bloqueado por **errores nativos** (Mandrel/GraalVM) y configuración `--initialize-at-run-time` frágil.  
  - Decisión: **retirar Git** del runtime (v2.0.0) y centrarnos en **persistencia local + backup**.

### JSON y librerías
- Uso de **JSON-B (Yasson)** o **Jackson** (ambas soportadas en Quarkus + Java 21).  
- Buenas prácticas:
  - DTOs como *POJOs* o `record`.
  - `java.time` (`LocalDateTime`, `LocalTime`) con formatos consistentes.
  - `fail-on-unknown` según caso de compatibilidad; incluir solo non-null si aplica.
  - Validación con Hibernate Validator (`@NotNull`, `@Email`, etc.).
  - Tests con `quarkus-junit5` + `rest-assured` para serialización y endpoints.

### Observabilidad & calidad
- Reducción de *spam* de logs irrelevantes.
- **Logs de pasos críticos** con clase, método y objetivo (p. ej., “PersistQueue.save(eventId=…) ok”).
- Página admin de **estado** (carga inicial, errores de persistencia, métricas).
- Revisión de *dead code*, duplicaciones (`fillDefaults`), uso seguro de streams/IO (try-with-resources).

### CI/CD y flujo GitHub
- Scripts `gh` para *issues*, *branches* y *PRs* desde PowerShell.
- Reversiones y *tags*: volver a `v1.1.0` como base estable y marcar `v2.0.0` sin Git.
- Problemas de red en `mvn test` → mitigaciones (repos locales, reintentos, flags).

---

## 3) Historia desde el punto de vista de **desarrollo asistido por IA (Codex/ChatGPT)**

### Lo que **funcionó bien**
- **Iteraciones cortas y enfocadas**: prompts por *issue* con *Objetivo/Alcance/Archivos/Criterios de aceptación/Pruebas*.  
- **Prompts “estilo PRD”** (requisitos de usuario): evitamos imponer soluciones; describimos resultados observables.
- **Diagnóstico por logs**: incluir *stack traces*, URLs exactas, payloads y *headers* ayudó a cerrar incidentes rápido.
- **Plantillas de prompt para UI**: especificar *paths*, *clases/IDs CSS*, “no hacer”, *responsive* y *accesibilidad*.
- **Desambiguación de OIDC**: listar scopes, callback, `redirect-path`, cómo sacar claims del **ID Token** y *userinfo*.

### Lo que **no funcionó bien**
- **Prompts “megapaquete”**: demasiados cambios en uno → regresiones y *loops* de incidentes.  
- **Suposiciones técnicas** en requerimientos de usuario: Qute se rompía por pequeños detalles (tags, inserts).  
- **Persistir en Git** bajo *native image*: se volvió un callejón con JGit.  
- **Ambiente ≠ local**: *redirect_uri*, *JWKS*, */q/oauth2/callback*, *Secure cookies*… variaban entre dev/prod.

### Aprendizajes (cómo pedir mejor)
- **Formato recomendado de prompt**
  1. **Objetivo** (qué cambia para el usuario).
  2. **Alcance** (qué sí / qué no toca).
  3. **Archivos y rutas** exactas a editar/crear.
  4. **Requisitos visuales** (layout, componentes, estilos, responsive).
  5. **Criterios de aceptación** con ejemplos concretos.
  6. **Pruebas manuales** y *reglas de no regresión*.
  7. **No hacer** (explícito).
- **Separar incidentes de features**: primero estabilizar (errores 500, plantillas), luego UI/UX.
- **Logs accionables**: una línea por paso crítico, con *contexto* (clase/método/IDs).
- **Reversión rápida**: si un hilo técnico (p. ej. JGit nativo) se empantana, *retirar y seguir*; documentar como deuda.
- **Datos realistas**: para vistas y validaciones, proveer ejemplos (evento con 2 días, 3 escenarios, 5 charlas).

### Playbook para futuras iteraciones
- **Checklists** iniciales (OIDC, Qute, persistencia, rutas).
- **Ramas cortas** + PRs chicos con *review checklist* (accesibilidad, responsive, logs).
- **Feature flags** para cambios de navegación y layouts.
- **Backups y migraciones**: versión de datos alineada con versión de app (compatibilidad).
- **Trazabilidad de UX**: cada iteración parte de un *user story* y termina con *criterios checkeados* en prod.

---

## Cierre (estado actual)
- Versión estable sin Git en runtime, con **persistencia local** (PVC) y **backup/restore** manual.
- UX más clara: Home como hub de eventos, **Project Header** informativo, navegación simple Evento ⇄ Escenario ⇄ Charla, “Mis Charlas” con feedback y filtros.
- Base sólida para seguir: **Oradores** reutilizables, **agenda** precisa basada en fecha del evento, y **observabilidad** mínima lista.

**Siguiente foco**: pulir “Mis Eventos/Mis Charlas”, completar administración de Oradores↔Charlas reutilizables, reforzar pruebas E2E y accesibilidad, y continuar con mejoras visuales incrementales sin romper el flujo simple del sitio.
