# Experience SLO and Regression Guardrails

This document defines lightweight experience SLO targets for the public pages with the highest traffic.

## Goals

- Keep first render stable and predictable for users.
- Prevent known regressions before merge (missing auth bootstrap, missing CSS, legacy asset references).
- Keep HTML payload size controlled to reduce visual jank and avoid heavy startup work.

## Current SLO Targets

| Page | HTML budget (bytes) | Availability target |
| --- | ---: | --- |
| `/` (Home) | 120,000 | 99.9% |
| `/comunidad` (Community) | 240,000 | 99.9% |
| `/comunidad/board/discord-users` | 200,000 | 99.9% |

Notes:

- Budgets are for server-side HTML payload only. Assets are validated separately.
- These are conservative limits meant to catch accidental bloat, not to optimize for synthetic benchmark scores.

## CI Smoke Coverage

`PublicExperienceSmokeTest` validates:

- Key public pages return `200`.
- `window.userAuthenticated` bootstrap is present.
- Legacy broken reference patterns are absent (e.g. `canva-theme-v2.css`).
- Critical assets return `200` and non-empty payload:
  - `/css/homedir.css`
  - `/css/retro-theme.css`
  - `/js/homedir.js`
  - `/js/app.js`

## How to run locally

From `quarkus-app/`:

```bash
./mvnw -Dtest=PublicExperienceSmokeTest test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd "-Dtest=PublicExperienceSmokeTest" test
```
