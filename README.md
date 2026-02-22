# Homedir
> **DevRel, OpenSource, InnerSource Community Platform**

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge&logo=apache&logoColor=white)](LICENSE)
[![PR Validation](https://img.shields.io/github/actions/workflow/status/os-santiago/homedir/pr-check.yml?style=for-the-badge&label=PR%20Validation&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/actions/workflows/pr-check.yml)
[![Version](https://img.shields.io/github/v/release/os-santiago/homedir?label=Version&style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/releases)
[![Discord](https://img.shields.io/badge/Discord-Join%20the%20chat-5865F2?logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/3eawzc9ybc)

Homedir is a Quarkus-based community platform for DevRel/Open Source initiatives, combining Community Picks, Events, Projects, CFP workflows, and contributor identity integrations (GitHub/Discord/Google login) in a single product.

## Repository Layout
- `quarkus-app/`: main Quarkus application (backend + templates + static assets).
- `docs/`: product and technical documentation (EN/ES).
- `platform/`: VPS deployment scripts, env template, systemd/nginx assets.
- `tools/community-curator/`: curated content generation and deployment tooling.

## Getting Started

### 1) Run locally with Quarkus (Developer Mode)
Prerequisites:
- JDK 21
- Maven 3.9+ (or use the included Maven Wrapper)

```bash
git clone https://github.com/os-santiago/homedir.git
cd homedir
mvn -f quarkus-app/pom.xml quarkus:dev
```

Open `http://localhost:8080`.

For local auth in dev mode, you can use:
- `user@example.com / userpass`
- `admin@example.org / adminpass`

### 2) Run using public Quay images
Public image repository:
- `quay.io/sergio_canales_e/homedir`

Tags published by release pipeline:
- `latest`
- semantic tags like `3.360.1` (see [Releases](https://github.com/os-santiago/homedir/releases))

Create an env file from `platform/env.example` and fill required values (do not commit secrets), then run:

```bash
docker run --rm --name homedir \
  -p 8080:8080 \
  --env-file ./.env.homedir \
  -v "$(pwd)/.data:/work/data" \
  quay.io/sergio_canales_e/homedir:latest
```

You can pin a specific release tag:

```bash
docker run --rm --name homedir \
  -p 8080:8080 \
  --env-file ./.env.homedir \
  -v "$(pwd)/.data:/work/data" \
  quay.io/sergio_canales_e/homedir:3.360.1
```

### 3) Build your own image from source
Build a JVM image with the project Dockerfile:

```bash
docker build -f quarkus-app/src/main/docker/Dockerfile.jvm \
  -t homedir:local \
  quarkus-app
```

Run it:

```bash
docker run --rm --name homedir-local \
  -p 8080:8080 \
  --env-file ./.env.homedir \
  -v "$(pwd)/.data:/work/data" \
  homedir:local
```

Push your own image to Quay:

```bash
docker login quay.io
docker tag homedir:local quay.io/<your-namespace>/homedir:<tag>
docker push quay.io/<your-namespace>/homedir:<tag>
```

## Documentation
Please select your language to view the documentation:

Por favor selecciona tu idioma para ver la documentación:

## [English Documentation](docs/en/README.md)

## [Documentación en Español](docs/es/README.md)

Platform deployment notes:
- [platform/README.md](platform/README.md)
- [Production-Safe Delivery Playbook](docs/development/production-safe-delivery-playbook.md)

---
*Homedir: Where code finds its home.*
