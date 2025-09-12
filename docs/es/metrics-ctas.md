# Ctas metrics

## Definitions and formulas
- ** ctas (range) **: Sum of clicks on releases, report ISSUE and KO-FI within the selected range.
- ** Daily average (by cta and total) ** = `sum_en_rango / nº_días_contemplados_en_el_rango`.
- ** Simple deviation ** About total daily within the range (population method).
- ** Picos **:
  - *Option A (by default) *: Top-3 total range of the range.
  - *Option B (configurable) *: Total ≥ average + 2 × dev.est.
- ** Time zone **: The event is used to group per day.

## Presentation rules
- Table ordered by descending date.
- Badges "peak" with tooltip "peak according to rule configured for this range."
- Placeholders and messages of "without data to export in this range." When appropriate.

## Links
-URLS parameterizable by `links.releases-url`,` links.issan-ull` and `links.donate-url`.
- Recommended Security: `target =" _ Blank "` + `rel =" noopener "`.

## Privacy and performance
- PII is not exposed; only added data.
- Snapshot/metric cache is reused to avoid charging times.