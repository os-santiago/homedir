# HomeDir CSS Style Guide

Conventions for writing and naming CSS in the HomeDir codebase.

## Design tokens

- All colors, fonts, spacing, and other reusable values MUST be defined as CSS
  custom properties in `css/tokens.css`.
- Components MUST consume tokens via `var(--token-name)` — never hardcode a
  color, font, or spacing value in component styles.
- The palette is intentionally shared across the retro theme (`homedir.css`,
  `retro-theme.css`) and feature stylesheets (`notifications.css`, etc.).
- `styles.css` keeps its own `:root` for its independent modern token set; do
  not redefine those names in feature sheets.

## Naming conventions

### Custom properties (`--token-name`)

- Lowercase, `kebab-case`, descriptive: `--toast-bg`, `--notif-badge-bg`.
- Semantic intent first: `--toast-bg`, not `--gray-333`.
- RGB channel aliases (`--white-rgb`) exist so `rgba(var(--white-rgb), 0.7)`
  can be used for translucent tones.

### Classes

- Use `kebab-case` for multi-word class names: `.notifications-bell`, `.filter-btn`.
- BEM-lite: block prefix for related parts: `.ef-toast`, `.ef-toast__title`,
  `.ef-toast__actions`.
- Utility classes from `styles.css` (`.U-P-*`, `.u-elev-*`, `.container`)
  may be composed in markup but must not be reimplemented in feature CSS.

## Rules

- Do NOT use `!important`. Use a higher-specificity selector instead.
- Prefer `:focus-visible` for focus rings; ensure keyboard targets are ≥ 44×44px.
- Respect `prefers-reduced-motion` for all animations/transitions.
- Keep selector specificity low (single class or element + class where possible).
- Inline `style="..."` attributes are forbidden in templates; use classes.

## Minimum contrast ratios (WCAG AA)

All text and interactive elements MUST meet [WCAG 2.1 Level AA](https://www.w3.org/WAI/WCAG21/quickref/) contrast requirements. The ratios below are the absolute minimums — aim higher when feasible.

### Text contrast (WCAG 1.4.3)

| Element type | Minimum ratio | Notes |
|---|---|---|
| Normal text (< 18pt / < 14pt bold) | **4.5:1** | Body copy, labels, nav links |
| Large text (≥ 18pt / ≥ 14pt bold) | **3.0:1** | Headings, section titles |
| Incidental / decorative text | Exempt | Logos, disabled-state text |

### Non-text contrast (WCAG 1.4.11)

| Element type | Minimum ratio | Notes |
|---|---|---|
| UI component boundaries (inputs, buttons, cards) | **3.0:1** | Against adjacent background |
| Focus indicators (outlines, box-shadows) | **3.0:1** | Against adjacent colors |
| State indicators (selected, active, error) | **3.0:1** | Must not rely on color alone — pair with icon or text |

### Focus indicator requirements (WCAG 2.4.7)

1. Every interactive element (`a`, `button`, `input`, `select`, `textarea`, `[tabindex]`) MUST have a visible `:focus-visible` indicator.
2. The indicator MUST have ≥ 3:1 contrast against its adjacent background.
3. The indicator MUST be at least 2px thick (outline or box-shadow ring).
4. `outline: none` is FORBIDDEN on `:focus-visible` without an equivalent visible indicator (e.g., a 2px+ box-shadow ring with sufficient contrast).
5. The global baseline in `homedir.css` provides `outline: 2px solid var(--color-accent); outline-offset: 2px;` — component-specific styles may override with a tailored indicator of equal or greater visibility.

### Color + meaning (WCAG 1.4.1)

- Error states MUST use color + icon or text (never color alone).
- Success/warning states MUST include an icon or text label alongside the color cue.
- `--color-error` (`#ff4444`) and `--color-success` are defined in `:root`; pair them with `.material-symbols-outlined` icons or explicit text.

### Palette reference (dark theme)

| Token | Value | Typical use |
|---|---|---|
| `--color-accent` | `#ffd700` (gold) | Focus rings, active states, highlights — 15.6:1 on `rgba(0,0,0,0.6)` |
| `--color-text-main` | `#ffffff` (white) | Primary text — 21:1 on `rgba(0,0,0,0.6)` |
| `--color-text-muted` | `rgba(255,255,255,0.9)` | Secondary text — verify ≥ 4.5:1 on actual background |
| `--color-error` | `#ff4444` | Error states — pair with icon |
| `--color-success` | `#78ffa0` (mint) | Success states — pair with icon |

> **Note**: Contrast ratios for translucent backgrounds (e.g., `rgba(0,0,0,0.6)`) depend on the composited result over the page gradient. When in doubt, test with a contrast checker against the rendered (not raw) background.
