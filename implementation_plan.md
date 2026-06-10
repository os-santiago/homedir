# Implementation Plan

Siguiendo ADEV.md (ramas dedicadas, PR obligatorio, commits atómicos, docs bilingüe)
y agent workflows (reproducir local, plan antes de código, soluciones robustas).

---

## P0 — Issues críticos (CFP/CFV)

### #737 — Publicar seleccionados CFP/CFV con historial público

**Solución:**
- Nueva propiedad `selected: boolean` + `selectionHistory: List<SelectionEntry>` en `CfpSubmission.java`
- Nuevo endpoint `GET /api/events/{eventId}/cfp/selected` (reutiliza `CfpSubmissionApiResource`)
- Dashboard público vía nueva template Qute en `templates/CfpResource/selected.html`
- Perfil público: agregar sección "Charlas / Voluntariados" en `publicProfile.qute.html`
- Sin hardcode: eventId y datos vienen del repositorio de persistencia existente

**Archivos a modificar:**
- `CfpSubmission.java` — agregar campo `selected`
- `CfpSubmissionService.java` — lógica de selección y consulta
- `CfpSubmissionApiResource.java` — nuevo endpoint GET
- templates nuevas + `publicProfile.qute.html`

**Rama:** `feat/cfp-public-selection`

---

### #738 — CFP: gestión de presentación, bloques y seguimiento

**Solución:**
- Propiedades `block: String`, `stage: String`, `presentationUrl: String`, `status: SubmissionStatus` en `CfpSubmission.java`
- Admin setea bloque/escenario desde `AdminEventCfpResource`
- File upload con `PersistenceService.saveFile()` (reutilizar persistencia existente)
- Validación de formato vía `application.properties`: `cfp.presentation.allowed-formats=pdf,pptx,key`
- Seguimiento: dashboard admin con checklist en `AdminEventCfpResource/moderation.html`

**Archivos a modificar:**
- `CfpSubmission.java` — nuevos campos
- `AdminEventCfpResource.java` — endpoints de asignación
- `AdminEventCfpResource/moderation.html` — UI de gestión

**Rama:** `feat/cfp-presentation-management`

---

### #739 — Perfil mínimo obligatorio para seleccionados

**Solución:**
- Nueva interfaz `ProfileValidator` con método `validate(String userId): ValidationResult`
- Implementación concreta que verifica `UserProfileService.getProfile(userId)` tenga nombre, descripción, imagen, rol
- Endpoint `GET /api/profile/{userId}/completeness` en `ProfileAliasResource` o nuevo resource
- Bloqueo via `@RolesAllowed` + validator en endpoints de publicación
- Umbrales configurables: `profile.required.fields=displayName, bio, avatarUrl, role`

**Archivos a modificar:**
- `UserProfileService.java` — método `isProfileComplete()`
- `ProfileAliasResource.java` — endpoint de completeness
- Flujo de publicación CFP/CFV — integrar validator

**Rama:** `feat/profile-minimum-requirements`

---

### #741 — CFV: tabla de turnos

**Solución:**
- Nueva clase `VolunteerShift` con `userId, eventId, day, slotStart, slotEnd`
- Almacenamiento en `PersistenceService` como `data/volunteer-shifts-{eventId}.json`
- Endpoints REST: `GET/PUT /api/events/{eventId}/volunteer/shifts`
- Validación server-side: min 2 slots/día, max 4 slots/día, cada slot 2h
- Configurable via: `volunteer.shift.duration=PT2H`, `volunteer.shift.min-per-day=2`, `volunteer.shift.max-per-day=4`
- Template Qute con tabla visual de segmentos

**Archivos a modificar:**
- Nuevo: `VolunteerShift.java`, `VolunteerShiftService.java`, `VolunteerShiftResource.java`
- Templates nuevas

**Rama:** `feat/volunteer-shift-management`

---

## P1 — Issues altos

### #740 — Notificaciones segmentadas

**Solución:**
- Extender `Notification.java` con campo `audience: Set<AudienceGroup>` donde `AudienceGroup = CFP_SELECTED | CFV_SELECTED | STAFF | ALL`
- AdminNotificationResource: nuevo endpoint `POST /admin/api/notifications/broadcast-segmented` con selector de audiencia
- Filtro en envío: `NotificationService.enqueue()` verifica pertenencia del usuario al grupo
- Grupos configurables via `application.properties` o desde el sistema de persistencia
- Template admin: `admin/notifications.qute.html` extender con checkboxes de audiencia

**Archivos a modificar:**
- `Notification.java` — campo `audience`
- `NotificationService.java` — filtrado por audiencia
- `AdminNotificationResource.java` — endpoint segmentado
- Templates admin

**Rama:** `feat/segmented-notifications`

---

### #742 — Canal Discord visible

**Solución:**
- Nueva propiedad `discord.support-invite` en `application.properties` (sin hardcode)
- Template partial `fragments/discord-support.html` inyectado en páginas relevantes
- Render condicional solo si la propiedad está configurada: `{#if config:discord.support-invite}`
- Enlace visible en footer y páginas de error

**Archivos a modificar:**
- `application.properties` — nueva propiedad
- `fragments/discord-support.html` — nuevo partial
- `layout/main.html`, `templates/errors/` — incluir partial

**Rama:** `feat/discord-support-channel`

---

## P3 — Refactors de auditoría

### #729 [R1] Seguridad

**Soluciones (sin hardcode, una rama por sub-issue):**

| Sub-issue | Solución | Archivos |
|-----------|----------|----------|
| XSS admin-notifications.js | Reemplazar `innerHTML` por `textContent` + sanitizer | `admin-notifications.js` |
| XSS retro-theme.js | Reemplazar `innerHTML` por `textContent` | `retro-theme.js` |
| CSP header | `quarkus.http.header.csp.value` en `application.properties` | `application.properties` |
| CSRF tokens | `{#for csrf in inject:csrf}` en todos los forms POST | Todas las templates con forms |
| MessageDigest thread-safe | `ThreadLocal<MessageDigest>` | `NotificationService.java` |
| Salt "changeme" | Inicializar a `null`, requerir env `NOTIFICATIONS_USER_HASH_SALT` | `NotificationConfig.java` |
| CapacityLoginFilter path | `startsWith("/private")` | `CapacityLoginFilter.java` |
| Zip slip | `target.toRealPath()` después de resolver | `BackupArchiveService.java` |
| RedirectSanitizer | Normalizar + resolver contra base URI conocida | `RedirectSanitizer.java` |
| Rate limiter side-channel | Comparación constante + límite de entradas en mapa | `RateLimitingFilter.java` |
| SessionExpiryFilter | Remover `@PreMatching` o usar `Instance<SecurityIdentity>` | `HtmlSessionExpiryFilter.java` |

**Ramas:** `fix/r1-csp`, `fix/r1-csrf`, `fix/r1-messagedigest`, `fix/r1-salt`, etc. (una por sub-issue, según ADEV.md regla 4: no mezclar)

---

### #730 [R2] Secretos

| Sub-issue | Solución | Archivos |
|-----------|----------|----------|
| GitHub token leak | Helper `redactSensitive()` en logs | `GithubService.java`, `CommunitySyncService.java` |
| deploy credenciales | Mover a GitHub Secrets, eliminar del script | `deploy_with_limits.sh` |
| SSH MITM | Forzar `StrictHostKeyChecking=yes` + known_hosts por secret | `.github/workflows/release.yml` |
| dev-login credenciales | Cargar desde `application.properties` | `dev-login.html`, `DevAuthResource.java` |
| OIDC logging | Sanitizar callback params antes de loguear | `OidcCallbackLoggingFilter.java` |

**Ramas:** `fix/r2-github-token-log`, `fix/r2-deploy-credentials`, etc.

---

### #731 [R3] Frontend (18 sub-issues)

Agrupación por PRs según ADEV.md regla 4 (no mezclar categorías):

1. **SEO+Error pages** (`fix/r3-seo`): 404/403/500 templates, robots.txt, sitemap.xml
2. **Accesibilidad** (`fix/r3-a11y`): skip-link, modal role, SVGs aria-hidden, ARIA labels
3. **Layout+CSS** (`fix/r3-layout-css`): unificar layouts, viewbox, highlights h2, print, colores hardcodeados, tokens CSS
4. **JS+Assets** (`fix/r3-js-assets`): JS duplicado, Tailwind CDN, favicons, OG image externalizada
5. **Componentes** (`fix/r3-components`): Now Box empty, Card CFP, URL proyectos

Sin hardcode: OG image via `application.properties` (`homedir.og-image.url`), colores via variables CSS.

---

### #732 [R4] i18n

**Solución (rama `refactor/r4-i18n`):**
1. Unificar en `messages.properties` como único sistema (estándar Qute)
2. Migrar claves de `i18n.properties` → `messages.properties`
3. Completar `messages_es.properties` con todas las claves de `messages.properties`
4. Establecer CI check: `messages_es.properties` debe tener mismas keys que `messages.properties`

---

### #733 [R5] Arquitectura

Sub-refactor con múltiples ramas:
- `refactor/r5-config-volatile`: Hacer `NotificationConfig` CDI + `volatile` o inyectable
- `refactor/r5-persistence`: Mejorar error handling, fsync, WAL en `PersistenceService`
- `refactor/r5-concurrency`: Thread-safe deques, EconomyService ReadWriteLock, dedupe cleanup
- `refactor/r5-appmessages`: Dividir `AppMessages` por dominio
- `refactor/r5-httpclient`: HttpClient reutilizable vía CDI producer

---

### #734 [R6] CI/CD

**Rama `fix/r6-ci-cd`:**
1. `release.yml`: Separar test + package en jobs distintos
2. `pr-check.yml`: Agregar paso de upload de SBOM
3. `update-docs-on-release.yml`: Reemplazar push directo por `gh pr create`
4. `application.properties`: Externalizar registry URL, namespace K8s

---

### #735 [R7] Backend

**Rama `fix/r7-backend` (una rama por bug, o todas si son independientes):**
1. División por cero: validador `{#if divisor != 0}` en template
2. NPE HomeResource: null checks en `getUsername()` y `getAttribute("email")`
3. ClassCastException: `instanceof JsonWebToken` antes de castear
4. Health endpoint: `@Health` con `HealthCheck` que verifique disco + persistencia
5. NotificationRepository: loguear excepciones, retornar boolean
6. Thread leak: `@PreDestroy` con `shutdown()` + `awaitTermination()`

---

### #736 — EconomyService

**Rama `fix/economy-gamification-cooldown`:**
1. Agregar cooldown por usuario en `CommunityContentApiResource`:
   - `Map<String, Instant> lastAward` con limpieza periódica
   - Solo award si `now - lastAward >= economy.award.cooldown` (configurable)
2. Hacer `enforcePurchaseBurstGuard()` siempre efectivo (remover condicional `guardStrict`)

---

## P3 — Issues preexistentes

### #718 — LTA responsive
- **Rama:** `fix/lta-responsive`
- **Solución:** Agregar `overflow-x: auto` + `min-width` al contenedor. Sin hardcode de breakpoints.

### #719 — Post-Propose Content
- **Rama:** `fix/propose-content-status`
- **Solución:** Forzar refresco del listado "Mis propuestas" después de submit. Estado debe leerse del backend, no de cache local.

### #720 — Off-center Icon
- **Rama:** `fix/scenarios-icon-center`
- **Solución:** Ajustar CSS `display: flex; align-items: center; justify-content: center;` en icon-wrapper y material-symbols-outlined.

### #721 — Alineación textos
- **Rama:** `fix/home-text-alignment`
- **Solución:** `text-align: center` en `.home-top-metrics` y centrar botón `.home-onboarding-actions`.

### #722 — Botones con iconos
- **Rama:** `feat/icon-buttons`
- **Solución:** Reemplazar texto "Abrir" por icono Material Symbols `open_in_new`. Mantener texto como `aria-label`.

### #743 — Discord bot spike
- **Rama:** `spike/discord-bot`
- **Solución:** Evaluar GitHub API + Discord.js/webhook. Sin código productivo hasta aprobación.

---

## Orden de implementación sugerido

1. `fix/r7-backend` (bugs pequeños, impacto inmediato)
2. `fix/r1-messagedigest` + `fix/r1-salt` + `fix/r1-capacity-path` (3 líneas de cambio, bugs críticos)
3. `fix/r3-seo` + `fix/r3-a11y` (frontend, buen primer impacto)
4. `fix/r2-github-token-log` + `fix/r2-deploy-credentials` (seguridad)
5. `fix/r1-csrf` (60+ forms, requiere más tiempo)
6. `fix/r1-csp` (una línea en application.properties)
7. Issues P0/P1 de funcionalidad nueva (#737-#742)
8. Refactors grandes R5 (XL)
9. Resto de bugs P3

---

*Documento generado siguiendo ADEV.md y agent workflows.*
