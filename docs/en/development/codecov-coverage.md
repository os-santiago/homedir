# Code Coverage with Codecov

This document explains how code coverage is measured and published for HomeDir.

## Overview

Coverage is measured with JaCoCo inside the `coverage` Maven profile of the Quarkus
application (`quarkus-app/pom.xml`). The `tests_cov` job of
`.github/workflows/pr-quality-suite.yml` runs the test suite with that profile and
uploads the report to Codecov. The README shows a live coverage badge fed by Codecov.

## How it works

1. The `tests_cov` job runs `./mvnw verify -B -Pcoverage`.
2. JaCoCo generates the report at `quarkus-app/target/site/jacoco/jacoco.xml`.
3. The `codecov/codecov-action` uploads that file to Codecov.
4. Codecov renders the badge in the README and provides per-PR coverage comments.

## Coverage gates

JaCoCo enforces minimum ratios during `verify`:

- Line coverage: `0.60` (60%)
- Branch coverage: `0.40` (40%)

These values are defined in the `check` execution of the `coverage` profile in
`quarkus-app/pom.xml`. A PR that drops below these thresholds fails the `tests_cov`
gate before any coverage is reported to Codecov.

## Required setup (repository admin)

The following steps require repository `Admin` permissions:

1. Create a Codecov account for the `os-santiago` organization and authorize the
   `homedir` repository.
2. Add a repository secret named `CODECOV_TOKEN` in
   `Settings > Secrets and variables > Actions`, using the repository token Codecov
   provides.
3. Verify the first PR run appears in the Codecov dashboard.

Until the secret is configured, the upload step is non-blocking
(`fail_ci_if_error: false`), so CI still passes but the badge shows no data.

## Roles

- Coverage data is public once the repository is public on Codecov.
- Only members with `Admin` permission can change repository secrets. If you need
  access, ask a current admin to grant you the `Admin` role on `os-santiago/homedir`.

## How to check locally

```bash
cd quarkus-app
./mvnw verify -Pcoverage
```

The HTML report is available at `target/site/jacoco/index.html`.
