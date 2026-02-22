# Mapa de navegación de Homedir

Este documento describe el mapa de navegación actual de Homedir y una propuesta inicial de navegación objetivo para la nueva UI basada en maqueta.

## 1. Navegación actual

### 1.1 Ubicación de assets

- **Templates Qute:** `quarkus-app/src/main/resources/templates` (incluye `layout/base.html` y `_layout.qute.html` como contenedores comunes).
- **Recursos estáticos:** `quarkus-app/src/main/resources/META-INF/resources` (CSS/JS e imágenes servidas directamente por Quarkus).

### 1.2 Inventario de templates Qute

| Template (ruta) | Uso aparente |
| --- | --- |
| `HomeResource/home.html` | Home pública con héroe, lista de eventos futuros/pasados y panel "now". |
| `EventsDirectoryResource/eventos.html` | Directorio público de eventos con métricas de próximos/pasados. |
| `EventResource/detail.html` | Detalle de evento individual con agenda, escenarios y enlaces externos. |
| `ScenarioResource/detail.html` | Listado de charlas por escenario dentro de un evento. |
| `TalkResource/detail.html` | Ficha de charla (uso tanto en `/talk/{id}` como en `/event/{eventId}/talk/{talkId}`). |
| `SpeakerResource/detail.html` | Perfil de ponente con charlas asociadas. |
| `ProjectsResource/proyectos.html` | Listado de módulos/proyectos de Homedir. |
| `CommunityResource/community.html` | Directorio de comunidad con formulario de búsqueda y alta. |
| `LoginPage/ingresar.html` | Pantalla de ingreso con opciones local/GitHub. |
| `PublicResource/publicPage.html` | Página pública mínima de inicio de sesión (legacy OAuth). |
| `PrivateResource/privatePage.html` | Página privada básica con claims del usuario autenticado. |
| `ProfileResource/profile.html` | Panel privado de perfil y agenda personal. |
| `AdminResource/admin.html` y `AdminResource/guide.html` | Acceso principal y guía para administradores. |
| `AdminEventResource/list.html` y `AdminEventResource/edit.html` | Gestión de eventos (listado, creación/edición). |
| `AdminSpeakerResource/list.html` | Gestión de ponentes y charlas. |
| `AdminCapacityResource/index.html` | Ajuste de capacidad y aforo para eventos. |
| `AdminBackupResource/index.html` | Consola de backups/importación de datos. |
| `AdminMetricsResource/index.html`, `guide.html`, `talks.html`, `registrations.html`, `registrants.html` | Vistas de métricas, guías y reportes de registros. |
| `admin/notifications.qute.html` y `admin/notifications_sim.qute.html` | Paneles para enviar o simular notificaciones globales. |
| `notifications/center.html` | Centro público de notificaciones. |
| `partials/project-header.html`, `_now-box.qute.html`, `fragments/toasts.qute.html`, `fragments/notifications-bell.html` | Partials/fragmentos compartidos. |

### 1.3 Rutas principales

| Ruta | Tipo | Template asociado (si aplica) | Descripción breve |
| --- | --- | --- | --- |
| `/` | Pública | `HomeResource/home.html` | Home con próximos/pasados y bloque "now". |
| `/events` | Pública (redirect) | – | Redirige permanentemente a `/`. |
| `/eventos` | Pública | `EventsDirectoryResource/eventos.html` | Listado de eventos con métricas. |
| `/event/{id}` | Pública | `EventResource/detail.html` | Detalle de evento y agenda. |
| `/event/{eventId}/scenario/{id}` | Pública | `ScenarioResource/detail.html` | Vista de charlas por escenario. |
| `/talk/{id}` | Pública | `TalkResource/detail.html` | Detalle de charla independiente del evento. |
| `/event/{eventId}/talk/{talkId}` | Pública | `TalkResource/detail.html` | Detalle de charla contextual al evento. |
| `/speaker/{id}` | Pública | `SpeakerResource/detail.html` | Perfil de ponente y charlas asociadas. |
| `/comunidad` | Pública (con flujo de alta autenticado) | `CommunityResource/community.html` | Directorio y registro de comunidad. |
| `/comunidad/unirse` | Privada (POST) | `CommunityResource/community.html` | Alta en comunidad (redirecciona con estado). |
| `/proyectos` | Pública | `ProjectsResource/proyectos.html` | Catálogo de proyectos de Homedir. |
| `/ingresar` | Pública | `LoginPage/ingresar.html` | Pantalla de login (local/GitHub). |
| `/public` | Pública | `PublicResource/publicPage.html` | Landing pública de login (legacy OAuth). |
| `/legacy` | Pública con cookie | `META-INF/resources/private.html` | Página legacy protegida por cookie `user`. |
| `/private` | Privada (auth) | `PrivateResource/privatePage.html` | Página privada básica con datos del token. |
| `/private/profile` | Privada (auth) | `ProfileResource/profile.html` | Perfil, agenda personal y vinculación GitHub. |
| `/private/admin` | Privada (admin) | `AdminResource/admin.html` | Panel principal de administración. |
| `/private/admin/guide` | Privada (admin) | `AdminResource/guide.html` | Guía de uso para admins. |
| `/private/admin/events` + `/new`, `/{id}/edit` | Privada (admin) | `AdminEventResource/list.html` / `edit.html` | Gestión de eventos (listado y creación/edición). |
| `/private/admin/speakers` | Privada (admin) | `AdminSpeakerResource/list.html` | Gestión de ponentes y charlas. |
| `/private/admin/capacity` | Privada (admin) | `AdminCapacityResource/index.html` | Gestión de aforo y capacidad. |
| `/private/admin/backup` | Privada (admin) | `AdminBackupResource/index.html` | Descarga/subida de backups. |
| `/private/admin/metrics` | Privada (admin) | `AdminMetricsResource/index.html` | Dashboard de métricas (con derivados `guide`, `talks`, `registrations`, `registrants`). |
| `/admin/notifications` | Privada (roles admin) | `admin/notifications.qute.html` | Broadcast de notificaciones globales. |
| `/admin/notifications/sim` | Privada (roles admin) | `admin/notifications_sim.qute.html` | Simulación de notificaciones. |
| `/notifications/center` | Pública | `notifications/center.html` | Centro de notificaciones web. |

### 1.4 Notas sobre autenticación y flujo

- Las rutas públicas sirven HTML sin autenticación; `/comunidad` permite navegar anónimo pero el alta requiere login y perfil GitHub vinculado.
- Las rutas bajo `/private/**` y `/private/admin/**` requieren autenticación; las de admin aplican validación adicional (`AdminUtils` o roles `admin`).
- El layout principal usado por la mayoría de vistas es `layout/base.html`, que incluye navegación, breadcrumbs, pie de página y scripts comunes.
- Existen endpoints adicionales de API/servicios (notificaciones, métricas, CTAs) que no renderizan HTML y no se incluyen aquí.

## 2. Navegación objetivo (borrador)

> Esta sección es una propuesta preliminar para alinear el sitio con la maqueta. Los nombres y rutas exactos pueden cambiar una vez que se revise la maqueta en detalle.

- Home (`/`)
  - Sección "Qué es Homedir" (`/#que-es`)
  - Sección "Cómo funciona" (`/#como-funciona`)
  - Sección "Para quién es" (`/#para-quien-es`)
  - Sección "Comunidad" (`/#comunidad`)
- Página "Eventos" (`/eventos`)
- Página "Comunidad" (`/comunidad`)
- Página "Proyectos" (`/proyectos`)
- Página "Contacto" (`/contacto` - por definir)
- Ruta de login: `/ingresar`
- Área privada: `/private/**` (perfil, agenda y administración)

## 3. Diferencias y preguntas abiertas

- El diseño actual tiene menú básico y layout heredado; el rediseño deberá homogenizar navegación pública/privada y estilos.
- La maqueta podría agregar páginas nuevas (ej. contacto, docs, comunidad extendida) o secciones intra-home; falta confirmar estructura final.
- Validar si rutas admin fuera de `/private/admin` (ej. `/admin/notifications`) deben reubicarse o integrarse en un layout unificado.
- Definir si el directorio de comunidad y los listados de eventos/talks requieren paginación o filtros adicionales para el nuevo look & feel.
