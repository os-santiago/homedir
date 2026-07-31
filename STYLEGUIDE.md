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
