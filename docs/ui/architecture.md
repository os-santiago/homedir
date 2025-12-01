# Arquitectura de la UI de Homedir (v2)

Este documento describe cómo está organizada la interfaz de usuario de Homedir en su versión v2 (layout Qute + estilos `hd-*`).

## 1. Estructura de templates

- **Layout principal**
  - `quarkus-app/src/main/resources/templates/layout/main.html`
  - Define `<head>`, `<body>`, navegación, `<main>` y `<footer>`.
  - Expone los bloques `{#insert title}` y `{#insert body}` para cada página.

- **Fragmentos compartidos**
  - Navegación: `quarkus-app/src/main/resources/templates/fragments/nav.html`
  - Footer: `quarkus-app/src/main/resources/templates/fragments/footer.html`

- **Páginas públicas (v2)**
  - Home: `quarkus-app/src/main/resources/templates/HomeResource/home.html` (ruta `/`).
  - Eventos: `quarkus-app/src/main/resources/templates/pages/eventos.html` (ruta `/eventos`).
  - Comunidad: `quarkus-app/src/main/resources/templates/pages/comunidad.html` (ruta `/comunidad`, v2 en construcción). El recurso actual usa `templates/CommunityResource/community.html` mientras se completa la migración.
  - Documentación: `quarkus-app/src/main/resources/templates/pages/docs.html` (ruta `/docs`).
  - Contacto: `quarkus-app/src/main/resources/templates/pages/contacto.html` (ruta `/contacto`).

- **Vistas privadas**
  - Paneles y herramientas administrativas viven bajo `quarkus-app/src/main/resources/templates/admin/**` y los templates específicos por recurso (`Admin*Resource`).
  - Secciones privadas generales usan `quarkus-app/src/main/resources/templates/PrivateResource/privatePage.html`.
  - Otros recursos privados siguen la convención `templates/<Recurso>/<vista>.html` (por ejemplo, `templates/ProfileResource/profile.html`).

Todas las páginas públicas de la UI v2 deben extender el layout principal mediante `{#include layout/main}` o `{#extends layout/main}`:

```html
{#extends layout/main}

{#title}Título · Homedir{/title}

{#body}
  <!-- contenido específico de cada vista -->
{/body}
```

Los fragmentos se incluyen con `{#include fragments/nav /}` y `{#include fragments/footer /}` desde el layout.

## 2. Estilos y design tokens

- **CSS principal**: `quarkus-app/src/main/resources/META-INF/resources/css/homedir.css`.
- **Variables CSS**: definidas en `:root` como `--hd-color-primary`, `--hd-color-secondary`, `--hd-color-bg`, `--hd-color-surface`, `--hd-color-text-primary`, `--hd-color-text-secondary`, `--hd-color-accent` (ver `docs/ui/design-tokens.md`).
- **Clases y componentes `hd-*`**:
  - Layout y contenedores: `.hd-body`, `.hd-main`, `.hd-page`, `.hd-section`, `.hd-hero`, `.hd-section-title`.
  - Navegación y footer: `.hd-nav`, `.hd-nav-menu`, `.hd-nav-link`, `.hd-footer`.
  - Tarjetas y grids: `.hd-card`, `.hd-section-grid`, `.hd-card-title`, `.hd-card-text`.
  - Formularios: `.hd-form`, `.hd-form-field`, `.hd-form-label`, `.hd-form-input`, `.hd-form-actions`.
  - Tablas y listados: `.hd-table`, `.hd-table-row`, `.hd-table-cell`.
  - Variantes auxiliares: `.hd-btn`, `.hd-btn-primary`, `.hd-btn-secondary`, badges y pills específicas por sección.
- **TODO**: los valores exactos de colores, tipografías y espaciados deben alinearse con la maqueta de Canva (ver `docs/ui/design-tokens.md`).

## 3. Feature flag de UI

- Propiedad: `homedir.ui.v2.enabled` en `quarkus-app/src/main/resources/application.properties`.
- Propósito: habilitar/deshabilitar la UI v2 sin cambiar rutas. Actualmente el valor `false` usa los mismos templates porque no existe una versión mínima, pero queda listo para introducir un fallback futuro.
- Ejemplo de uso en controladores (Home):

```java
@ConfigProperty(name = "homedir.ui.v2.enabled", defaultValue = "true")
boolean uiV2Enabled;

if (uiV2Enabled) {
  return Templates.home(...);
}
// TODO: definir template de fallback si en el futuro se desea una versión mínima
return Templates.home(...);
```

Aplica el mismo patrón a otras rutas públicas (`/eventos`, `/docs`, `/contacto`, etc.) cuando se requiera alternar entre versiones.

## 4. Convenciones para nuevas vistas

- Extiende siempre `layout/main` y reutiliza los fragmentos de navegación y footer.
- Usa clases `hd-*` existentes; si creas variantes nuevas, documenta el uso en `docs/ui/architecture.md` y en los estilos.
- Mantén los textos en español hasta definir una estrategia de i18n.
- Evita estilos inline: centraliza los cambios en `homedir.css` y respeta los tokens definidos.
- Marca con `TODO` los textos o imágenes que deban alinearse con la maqueta de Canva antes de un release.
