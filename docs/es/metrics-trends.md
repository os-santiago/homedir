# Dashboard of metrics - trends

## Trend definition
- ** δ% = (current- base) / base × 100 ** when `base ≥ min-basline`.
- Comparative windows (same time zone of the event):
  - ** Today ** → compared to*yesterday*.
  - ** last 7 days ** → compared to the*7 days before*.
  - ** last 30 days ** → compared to the*30 days before*.
  - ** All the event ** → disabled trend (`n/a`).

## Limit case rules
- `Base <Min-Baseline` → Show` sample low` (no percentage).
- `Base = 0` and` current> 0` → Badge `new +n` (absolute δ).
- `Current = 0` and` base ≥ min-basoline` → `▼ 100%`.
- Rounding: 1 decimal if `| δ | <10%`, integers otherwise. Limit `" <0.1%"` and without `−0%`.

## Tables and growth ranking
- Each row of the Top 10 tables includes a Badge δ with the previous rules.
- Additional table: ** TOP 5 GROWTH (RANGE) ** For*records to my talks*.
  - Columns: `Talk · Event · Records · δ`.
  - Main order for δ descending absolute; Takes for δ% and then name.
  - Placeholder: `Insufficient data to calculate trends in this range.

## Copys / UX
- Badges: `Nuevo`,` Sample low`, `n/a (the entire event)`.
- Tooltip: `compared to {previous period}`.
- `ARIA-LABEL` Examples:` X% rose about the previous period ', `` ``.

## Qa / Validations
- Testing matrix with examples of `base = 0`,` base <min-basline`, falls and growths.
- Confirm that the δ respect the selected range and the time zone of the event.