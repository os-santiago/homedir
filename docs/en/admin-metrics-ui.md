# Admin Metrics UI

This page summarizes the layout tokens and breakpoints used in the mobile-first Admin → Métricas screen.

## Breakpoints
- ≥1200px: four-column grid
- 900–1199px: three-column grid
- 600–899px: two-column grid
- <600px: single-column stacked cards

## Tokens
- Uses existing site variables for colors (`--color-primary`, `--color-bg`, `--color-light`).
- Chips, selects and buttons share `var(--space-sm)` padding and `var(--radius-md)` radius for consistent tap targets.
- Cards keep 12px radius and soft shadow.
- Grid gap between cards: `1rem`.

## States
- Filter toolbar is sticky to top and supports chip focus/hover/active styles.
- Each card can display empty content messages when there is no data.

## Screenshots
- Before: ![before](ui/admin-metrics-before.png)
- After: ![after](ui/admin-metrics-after.png)
