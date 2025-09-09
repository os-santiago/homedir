# eventflow

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Quality Gate](https://github.com/scanalesespinoza/eventflow/actions/workflows/quality.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/quality.yml)
[![PR Quality — Architecture Rules](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-architecture-rules.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-architecture-rules.yml)
[![PR Quality — Static Analysis](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-static-analysis.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-static-analysis.yml)
[![PR Quality — Dependencies](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-deps-hygiene.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-deps-hygiene.yml)
[![PR Quality — Tests & Coverage](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-tests-coverage.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-tests-coverage.yml)
[![PR Quality — Suite](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-quality-suite.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-quality-suite.yml)


Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

Also available in [Español](README.es.md).

See the [documentation](docs/README.md) for more guides.

## Arquitectura

La estrategia de persistencia de EventFlow está documentada en:
 - [Opciones de persistencia](docs/es/architecture/persistence-options.md)
 - [ADR 2025-09-07: Persistencia centralizada](docs/es/architecture/ADR-2025-09-07-persistence-service-centralized.md)


Latest stable release: **v2.2.0**.

## Features
- Manage events, speakers, scenarios, and talks
- Sign in with Google using Quarkus OIDC
- Admin area protected by `ADMIN_LIST`
- Import events from JSON
- In-app notifications for talk status changes
- Supply chain security with SBOM generation, image signing and vulnerability scanning

## Quick start
Run the application in dev mode:

```bash
mvn -f quarkus-app/pom.xml quarkus:dev
```

Then browse to `http://localhost:8080`.

### Google OAuth 2.0 setup
Configure these properties in `application.properties` or environment variables:

```
quarkus.oidc.provider=google
quarkus.oidc.client-id=<CLIENT_ID>
quarkus.oidc.credentials.secret=<CLIENT_SECRET>
quarkus.oidc.authentication.redirect-path=/private
quarkus.oidc.authentication.scopes=openid profile email
quarkus.oidc.logout.post-logout-path=/
```

Register `https://eventflow.opensourcesantiago.io/private` as an authorized redirect URI for production deployments.

### Admin access
Only emails listed in `ADMIN_LIST` can create or edit events:

```
ADMIN_LIST=sergio.canales.e@gmail.com,alice@example.org
```

### Importing events
Upload a JSON file named `file` at `/private/admin/events` to import events. Duplicate IDs return `409 Conflict`; invalid JSON returns `400 Bad Request`.

## Supply chain
The build produces SBOMs for dependencies and container images and scans images for known vulnerabilities. CI publishes artifacts like `target/bom.json` and `sbom-image.cdx.json`, and images can be signed with Cosign.

## Community
Project supported by the OpenSource Santiago community. Join our [Discord server](https://discord.gg/3eawzc9ybc).

For coordinated vulnerability disclosure, see [SECURITY.md](SECURITY.md).
