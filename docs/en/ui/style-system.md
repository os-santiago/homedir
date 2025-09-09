# Style system

A mobile styles were introduced with tokens and reusable utilities.

## Tokens

| Name | Value |
| ------ | ----- |
| `--Sp-1` | 4px |
| `--SP-2` | 8px |
| `--SP-3` | 12px |
| `--SP-4` | 16px |
| `--SP-6` | 24px |
| `--SP-8` | 32px |
| `--Z-DopDown` | 1000 |
| `--Z-STicky` | 1100 |
| `--Z-modal` | 1200 |
| `-Motion-Fast` | 120ms |
| `-Motion-Base` | 200ms |
| `-Motion-Gentle` | 280ms |
| `-Motion-Modal` | 320ms |
| `-Ease-Standard` | `Cubic-Bezier (0.2,0,0,1)` |

## Utilities

-`.U-P-*` / `.U-MB-*` for spacing.
-`.u-elev-*` for shadows.
-`
- `.Link-*` for accessible links with tactile targets ≥ 44px.
- `.Animate-Press` for micro-interactions.

External link example:

`` html
<a class = "link-external link-tap" href = "https://example.com" target = "_ blank" rel = "noopener"> doc ↗ </a>
``

## Before / after

| View | Before | After |
| ------ | ------ | -------- |
| Metrics admin | ! [Before] (admin-metrics-before.png) | ! [After] (admin-metrics-after.png) |

## Quick guide

- Include `Styles.CSS` next to HTML.
-Respect `Prefers-Deduced-Motion` for users who request it.
- Avoid horizontal overflows using `.Container` and truncated utilities.