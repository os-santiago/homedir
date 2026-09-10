# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Homedir (`com.scanales:homedir`, repo `os-santiago/homedir`) is a single-module Quarkus 3.26 / Java 21 server-rendered web platform for a DevRel / open-source community: events and CFP, talks and speakers, volunteers, community picks, GitHub trending, gamification (quests, achievements, economy, reputation, bounty hunters) and contributor identity (Google OIDC plus GitHub/Discord account linking). Everything user-facing is Qute HTML rendered server-side; the JS under `META-INF/resources/js/` is progressive enhancement, not a SPA.

## Commands

All Maven commands run against `quarkus-app/pom.xml`. The wrapper is `quarkus-app/mvnw` (`.cmd` on Windows).

```bash
mvn -f quarkus-app/pom.xml quarkus:dev        # dev mode on http://localhost:8080, live reload
scripts/test-fast.sh                          # unit tests only  (.ps1 variant exists)
scripts/test-all.sh                           # verify + coverage (.ps1 variant exists)
scripts/dev-container.sh                      # same dev mode in Docker, no local JDK needed
```

Single test / single method:

```bash
cd quarkus-app && ./mvnw test -Dtest=TalkStateEvaluatorTest
cd quarkus-app && ./mvnw test -Dtest=TalkStateEvaluatorTest#evaluatesPinnedClock
```

The exact gates CI runs, reproducible locally from `quarkus-app/`:

```bash
./mvnw spotless:apply                         # google-java-format; spotless:check gates PRs
./mvnw compile -Dmaven.compiler.showWarnings=true
./mvnw enforcer:enforce                       # Java 21, dependency convergence
./mvnw verify -Pcoverage                      # jacoco: 60% line, 40% branch (build fails below)
./mvnw dependency:analyze
./scripts/ci/pr_preflight.sh                  # from repo root: what pr-check.yml actually runs
python3 scripts/validate_i18n.py              # from repo root, gates every PR touching i18n
```

Non-Java tests:

```bash
node --test tests/js/utils-format-date.test.js   # browser JS unit tests
pytest tests/                                    # repo tooling / governance tests
bash scripts/ci/e2e-playwright.sh                # full-stack Playwright: boots Quarkus, runs tests/e2e, stops it
```

Dev-mode login (OIDC is disabled under the `dev` profile, embedded users instead): `user@example.com / userpass`, `admin@example.org / adminpass`.

## Architecture

**Route packages are the access-control boundary, and the trailing underscore is deliberate.**
- `public_/` — anonymous pages and public APIs. Methods carry `@PermitAll`.
- `private_/` — everything under `/private/*`, gated to authenticated users by `quarkus.http.auth.permission.private` in `application.properties`. Admin resources live here and rely on `AdminRoleAugmentor`, which promotes principals listed in `ADMIN_LIST` to the `admin` role.
- `localhost/` — `LocalhostAdminApiResource`: full admin surface, bound to loopback connections plus a `LOCALHOST_ADMIN_TOKEN` bearer token, so it is only reachable by someone with SSH on the box. See `docs/LOCALHOST_ADMIN_API.md`.
- `security/` — CSP (`CspService`, `CspHeaderFilter`, `CspReportResource`), `RateLimitingFilter`, `RedirectSanitizer`, session-expiry handling for HTML vs API responses.

**Persistence is JSON files on disk, not a relational database.** `service/PersistenceService` owns it: one file per aggregate under `${homedir.data.dir}` (`./data` in dev), atomic write-then-rename, all writes queued on a single background thread so request threads never block on I/O, with checksums and retry. Domain snapshot types (`ReputationStateSnapshot`, `EconomyStateSnapshot`, `ChallengeStateSnapshot`, ...) are serialized wholesale. The one exception is community voting, which uses an embedded H2 file DB at `community.votes.db.path`. There is no JPA, no Flyway, no PostgreSQL — `ADEV-LOCAL.md` and `docs/en/architecture/persistence-service.md` describe an aspirational design that is not what the code does. Changing a persisted record's shape is a migration problem: `quarkus.jackson.fail-on-unknown-properties=true`, so removing a field breaks reads of existing data.

**Rendering.** Qute templates live in `quarkus-app/src/main/resources/templates/<ResourceClassName>/<method>.qute.html` for type-safe `@CheckedTemplate` usage, with shared pieces in `layout/`, `partials/` and `fragments/`. `view/GlobalTemplateExtensions` publishes `{appVersion}` and `{userSession}` as `@TemplateGlobal` to every template, so templates can branch on auth state without the resource passing it in. `util/AppTemplateExtensions` adds value-level extension methods.

**i18n is enforced by CI and there are exactly two bundles**: `messages/i18n.properties` (English, the default and fallback) and `messages/i18n_es.properties`. A flat `i18n_en.properties` was deliberately removed as redundant — do not recreate it; `scripts/validate_i18n.py` fails the build if it reappears, if the two bundles' key sets diverge, if a template references a missing `{i18n:key}`, or if a key breaks the lowercase dotted naming rule. Java-side access goes through `config/AppMessages.java` (a ~8.8k-line `@MessageBundle` interface — one method per key, expect to append to it). `ADEV-LOCAL.md` mentions pt-BR; that is stale, only EN and ES exist.

**Feature flags are plain config properties**, boolean and default-off, e.g. `reputation.engine.enabled`, `reputation.hub.ui.enabled`, `homedir.ui.v2.enabled`. New surfaces ship hidden behind one of these and are enabled progressively in production rather than staged in a separate environment.

**Background work** is Quarkus `@Scheduled` inside services (`TrendingService` scrapes GitHub trending with JDK `HttpClient` and regex, no scraping library; `CommunitySyncService` pulls `members.yaml` from the `os-santiago.github.io` repo). Live notifications go over a WebSocket owned by `notifications/NotificationSocketService`, with `NotificationStore` / `NotificationRepository` persisting through `PersistenceService`.

## Repository layout beyond `quarkus-app/`

`scripts/` CI and ops shell/Python (several have `.ps1` twins for Windows) · `tests/` Playwright E2E, browser JS unit tests, and pytest suites that assert on repo governance · `tools/` satellite tooling (community-curator, discord-bot, homedir-cli, load-test) · `platform/` VPS deploy assets (nginx, systemd, ansible) · `config/docs/governance/` the governance docs that `AGENTS.md` points at · `src/wos/` a Python "workspace OS" CLI, unrelated to the Java app.

## Conventions that will bite you

- **Read `AGENTS.md` before opening any issue or PR.** It is the binding contract: mandatory `Closes #N`, exactly one `pr:risk-*` label, `pr:` state labels are automation-owned and must never be applied by hand, branch naming `feat|fix|docs/issue-XXX-description`, conventional commits signed off with `git commit -s`, and English for every PR title, body, commit and code comment. When closing several issues, repeat the keyword on its own line per issue — `Closes #10, #11` only closes the first.
- `mvn` from the repo root does nothing; there is no root POM. Always `-f quarkus-app/pom.xml` or `cd quarkus-app`.
- Run `./mvnw spotless:apply` before committing Java. Formatting is a hard PR gate and google-java-format will reflow anything you hand-format.
- Any user-facing string added to a template, a Java message or JS needs both bundles updated in the same change, or `i18n-validation.yml` fails.
- Escape user-controlled data in JS with `window.HomeDirUtils.escapeHtml`; never `innerHTML` with raw values. `tests/js/dom-injection-xss.test.js` exists because this regressed before.
- The `hooks/pre-commit` gitleaks hook is not installed automatically — wire it up with `git config core.hooksPath hooks` if you want secret scanning locally.
- Do not bump the version by hand. `release.yml` computes the next version from conventional commits, rewrites `<revision>` in `quarkus-app/pom.xml`, commits and tags. The `<revision>` value sitting in the POM lags the actual released version between releases, which is expected. `scripts/set_version.sh` predates this and also seds a `quarkus.application.version` property that no longer exists in `application.properties`.
