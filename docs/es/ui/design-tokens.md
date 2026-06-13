# Design tokens de Homedir (UI v2)

> Este documento refleja los tokens realmente definidos en `styles.css` y
> `homedir.css`. Verifica siempre la fuente antes de usar un token que
> no figure aquí.

## 1. Colores

| Token                  | `styles.css`          | `homedir.css` (retro)  | Propósito                     |
|------------------------|-----------------------|------------------------|-------------------------------|
| `--color-bg`           | `#0b1021`             | `#0b0e1a`              | Fondo principal               |
| `--color-surface`      | `#0f172a`             | `#131627`             | Superficie (cards, paneles)   |
| `--color-surface-2`    | `#111827`             | —                      | Superficie secundaria         |
| `--color-card`         | `rgba(255,255,255,0.04)` | —                  | Fondo de cards                |
| `--color-border`       | `rgba(255,255,255,0.08)` | —                  | Bordes                        |
| `--color-primary`      | `#2563eb`             | `#667eea`             | Color primario                |
| `--color-accent`       | `#22d3ee` (cyan)      | `#ffd700` (gold)      | Color de acento               |
| `--color-highlight`    | `#f59e0b`            | —                      | Realce (alertas, badges)      |
| `--color-dark`         | `#0f172a`             | —                      | Fondo oscuro alternativo      |
| `--color-text`         | `#e5e7eb`            | `#e2e8f0`             | Texto principal               |
| `--color-muted`        | `#cbd5e1`            | `#a0aec0`             | Texto secundario / muted      |
| `--color-light`        | `#ffffff`            | —                      | Texto sobre fondos oscuros    |

> `homedir.css` sobrescribe varios tokens en `:root` (cargado después de `styles.css`).
> Si no se listan valores en `homedir.css`, se heredan de `styles.css`.

## 2. Tipografía

- **Fuente principal**: `Inter` (sans-serif).
- **Fuente secundaria** (retro): `Press Start 2P` / `Courier Prime`.
- **Fuente mono**: `JetBrains Mono` (terminal).
- Tamaños base:

| Token                  | Valor                           |
|------------------------|----------------------------------|
| `--font-size-base`     | `clamp(1rem, 0.94rem + 0.3vw, 1.1rem)` |
| `--font-size-h1`       | `clamp(2rem, 1.6rem + 2vw, 2.8rem)`   |
| `--font-size-h2`       | `clamp(1.5rem, 1.2rem + 1.5vw, 2.1rem)`|
| `--line-height-base`   | `1.6`                           |
| `--line-height-heading`| `1.2`                           |

## 3. Layout y espaciado

| Token                  | Valor       | Propósito                     |
|------------------------|-------------|-------------------------------|
| `--container-max`      | `72rem`     | Ancho máximo de contenedor    |
| `--space-xs`           | `0.25rem`   | Espaciado extra pequeño       |
| `--space-sm`           | `0.5rem`    | Espaciado pequeño             |
| `--space-md`           | `1rem`      | Espaciado base                |
| `--space-lg`           | `1.5rem`    | Espaciado grande              |
| `--space-xl`           | `2rem`      | Espaciado extra grande        |
| `--radius-sm`          | `6px`       | Bordes pequeños               |
| `--radius-md`          | `12px`      | Bordes base (cards, botones)  |
| `--radius-lg`          | `16px`      | Bordes grandes                |
| `--radius-xl`          | `20px`      | Bordes extra grandes          |
| `--shadow-sm`          | `0 4px 10px rgba(0,0,0,0.12)` | Sombras sutiles     |
| `--shadow-md`          | `0 10px 30px rgba(0,0,0,0.18)` | Sombras base        |
| `--shadow-lg`          | `0 20px 60px rgba(0,0,0,0.28)` | Sombras elevadas    |

Además existe una escala móvil (`--sp-1` a `--sp-8`) usada mayormente en
componentes responsivos.

## 4. Z-index

| Token                  | Valor | Propósito                     |
|------------------------|-------|-------------------------------|
| `--z-dropdown`         | `1000`| Dropdowns                     |
| `--z-sticky`           | `1100`| Elementos sticky              |
| `--z-modal`            | `1200`| Modales                       |
| `--z-tooltip`          | `1300`| Tooltips                      |
| `--z-toast`            | `1400`| Toasts                        |
| `--z-loading`          | `1500`| Overlays de carga             |

## 5. Convenciones de uso

- Sin CDN de Tailwind. El CSS es vanilla con variables.
- Los SVG decorativos deben llevar `aria-hidden="true"`.
- Los textos visibles al usuario se externalizan vía i18n (`{i18n:key}`).
- Las URLs de imágenes OG se configuran via `homedir.og-image.default-url`
  en `application.properties` y se exponen con `app:defaultOgImage()`.

## 6. Cómo agregar un token nuevo

1. Definir la variable en `styles.css` (`:root`) y, si aplica al tema retro,
   sobrescribirla en `homedir.css`.
2. Agregar la documentación en esta tabla.
3. Usar la variable en lugar de un valor hardcodeado.
