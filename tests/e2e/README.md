# Homedir E2E Tests (Playwright)

End-to-end tests that exercise the running Quarkus application in a **real
Chromium browser**, covering flows that unit/REST tests cannot validate:
Qute template rendering with JS bundles, navigation between pages, the global
notifications WebSocket, and the local dev login flow.

## Prerequisites

- JDK 21
- Maven (or the included wrapper)
- Node.js 20+ with npm

## Run locally

1. Start Quarkus in dev mode (from the repository root):

   ```bash
   cd quarkus-app
   ./mvnw quarkus:dev
   ```

   This runs the `dev` profile: OIDC is disabled and local form auth uses the
   embedded users (`user@example.com / userpass`, `admin@example.org / adminpass`).

2. In another terminal, install and run the E2E suite:

   ```bash
   cd tests/e2e
   npm ci
   npx playwright install chromium
   npm run test:e2e
   ```

The `E2E_BASE_URL` env var overrides the app URL (default `http://localhost:8080`).

## What is covered

| Spec | Scenario |
|------|----------|
| `login.spec.ts` | Dev login flow: valid credentials land on `/profile`, invalid credentials are rejected |
| `homepage.spec.ts` | Homepage renders with auth bootstrap + JS bundle; static assets served; navigation between public pages |
| `notifications-ws.spec.ts` | Global notifications WebSocket opens and completes the `hello` handshake |

## Full-stack runner

`scripts/ci/e2e-playwright.sh` starts Quarkus in dev mode, waits for
`/q/health/ready`, installs the Playwright browser, runs the suite, and stops
Quarkus. This is the entrypoint used by the CI job.

## CI

The `e2e` job in `.github/workflows/pr-check.yml` runs the full-stack runner on
every PR. A Playwright HTML report is uploaded as a build artifact.

## Maintainer notes

- Only Chromium is exercised in this first iteration (no cross-browser,
  visual regression, or mobile viewport tests).
- These tests complement — they do not replace — the existing
  `@QuarkusTest` REST tests.
- The tests rely on the `dev` profile embedded users; keep credentials in
  `application.properties` (`%dev.*`) in sync with `login.spec.ts`.
