# Sistema de estilos

Se introdujo un template de estilos móvil con tokens y utilidades reutilizables.

## Tokens

| Nombre | Valor |
| ------ | ----- |
| `--sp-1` | 4px |
| `--sp-2` | 8px |
| `--sp-3` | 12px |
| `--sp-4` | 16px |
| `--sp-6` | 24px |
| `--sp-8` | 32px |
| `--z-dropdown` | 1000 |
| `--z-sticky` | 1100 |
| `--z-modal` | 1200 |
| `--motion-fast` | 120ms |
| `--motion-base` | 200ms |
| `--motion-gentle` | 280ms |
| `--motion-modal` | 320ms |
| `--ease-standard` | `cubic-bezier(0.2,0,0,1)` |

## Utilidades

- `.u-p-*` / `.u-mb-*` para espaciado.
- `.u-elev-*` para sombras.
- `.u-truncate`, `.u-clamp-2` para truncado.
- `.link-*` para enlaces accesibles con targets táctiles ≥ 44px.
- `.animate-press` para micro‑interacciones.

Ejemplo de enlace externo:

```html
<a class="link link-external link-tap" href="https://example.com" target="_blank" rel="noopener">Doc ↗</a>
```

## Antes / Después

| Vista | Antes | Después |
|-------|-------|---------|
| Métricas admin | ![Antes](admin-metrics-before.png) | ![Después](admin-metrics-after.png) |

## Guía rápida

- Incluir `styles.css` junto al HTML.
- Respetar `prefers-reduced-motion` para usuarios que lo soliciten.
- Evitar desbordes horizontales usando `.container` y utilidades de truncado.
