# Admin Metrics UI

This page summarizes the layout tokens and breakpoints used in the redesigned Admin → Métricas screen.

## Breakpoints
- ≥1200px: three column grid
- 768–1199px: two column grid
- <768px: single column stacked cards

## Tokens
- Uses existing site variables for colors (`--color-primary`, `--color-bg`, `--color-light`).
- Cards have 12px radius and soft shadow.
- Spacing between cards: `1rem` grid gap.

## States
- Filter toolbar is sticky to top and supports chip focus/hover/active styles.
- Each card can display empty content messages when there is no data.
