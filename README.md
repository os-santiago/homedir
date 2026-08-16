# Homedir
<!-- Deployed: 2026-08-15 -->
> **DevRel, OpenSource, InnerSource Community Platform**


![Maven Central](https://img.shields.io/maven-central/v/com.scanales/homedir?style=for-the-badge)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge&logo=apache&logoColor=white)](LICENSE)
[![Contributors](https://img.shields.io/github/contributors/os-santiago/homedir?style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/graphs/contributors)
[![GitHub Stars](https://img.shields.io/github/stars/os-santiago/homedir?style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/stargazers)
[![PR Validation](https://img.shields.io/github/actions/workflow/status/os-santiago/homedir/pr-check.yml?style=for-the-badge&label=PR%20Validation&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/actions/workflows/pr-check.yml)
[![PR CI Build](https://img.shields.io/github/actions/workflow/status/os-santiago/homedir/pr-ci-build-native-sbom.yml?style=for-the-badge&label=PR%20CI%20Build&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/actions/workflows/pr-ci-build-native-sbom.yml)
[![PR Quality](https://img.shields.io/github/actions/workflow/status/os-santiago/homedir/pr-quality-suite.yml?style=for-the-badge&label=PR%20Quality&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/actions/workflows/pr-quality-suite.yml)
[![Coverage](https://img.shields.io/codecov/c/github/os-santiago/homedir?style=for-the-badge&logo=codecov&logoColor=white)](https://codecov.io/gh/os-santiago/homedir)
[![Language](https://img.shields.io/github/languages/top/os-santiago/homedir?style=for-the-badge&logo=java&logoColor=white&color=ED8B00)](https://github.com/os-santiago/homedir)
[![Last Commit](https://img.shields.io/github/last-commit/os-santiago/homedir?style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/commits/main)
[![Version](https://img.shields.io/github/v/release/os-santiago/homedir?label=Version&style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir/releases)
[![Maven Version](https://img.shields.io/badge/version-3.630.0-blue?style=for-the-badge&logo=apache-maven&logoColor=white)](https://github.com/os-santiago/homedir/releases)
[![Discord](https://img.shields.io/badge/Discord-Join%20the%20chat-5865F2?logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/3eawzc9ybc)
[![Repo Size](https://img.shields.io/github/repo-size/os-santiago/homedir?style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir)
[![Project Status: Active](https://img.shields.io/badge/status-active-brightgreen?style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir)

<p align="center">
  <a href="https://coderabbit.ai" target="_blank">
    <img src="https://img.shields.io/coderabbit/prs/github/os-santiago/homedir?utm_source=oss&utm_medium=github&utm_campaign=os-santiago%2Fhomedir&labelColor=171717&color=FF570A&link=https%3A%2F%2Fcoderabbit.ai&label=CodeRabbit+Pull+Request+Reviews&style=for-the-badge" alt="CodeRabbit Pull Request Reviews">
  </a>
</p>

Homedir is a Quarkus-based community platform for DevRel/Open Source initiatives, combining Community Picks, Events, Projects, CFP workflows, GitHub Trending repositories, and contributor identity integrations (GitHub/Discord/Google login) in a single product.

## Repository Layout
- `quarkus-app/`: main Quarkus application (backend + templates + static assets).
- `docs/`: product and technical documentation (EN/ES).
- `config/`: governance documentation (labels, branch protection, priorities).
- `platform/`: VPS deployment scripts, env template, systemd/nginx assets.
- `scripts/`: shared CI/ops scripts (versioning, tests, i18n validation).
- `deployment/`: deployment and orchestration assets.
- `container/`: container build assets.
- `modules/`: optional modular prototypes/proposals.
- `src/`: support tooling (e.g., Python worker).
- `tests/`: Playwright E2E and JS unit tests.
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

### 2) Run in a container (no JDK required)
If you prefer not to install JDK 21 and Maven on your machine, `scripts/dev-container.sh`
runs the same developer mode inside a disposable container. Edit files with your usual
editor on the host; Quarkus live reload picks the changes up.

Prerequisites:
- Docker (or Podman with a `docker` alias)

```bash
scripts/dev-container.sh              # dev mode with live reload on http://localhost:8080
scripts/dev-container.sh test         # run the test suite
scripts/dev-container.sh clean package
```

The dependency cache is kept in `.tmp/m2` inside the repository (git-ignored) rather than
in `~/.m2`, so nothing is written outside the project. To remove every trace:

```bash
rm -rf .tmp/m2 .tmp/maven-home quarkus-app/target
docker rmi maven:3.9-eclipse-temurin-21
```

Override the image or port with `DEV_CONTAINER_IMAGE` and `DEV_CONTAINER_PORT`.

### 3) Run using public Quay images
Public image repository:
- `quay.io/sergio_canales_e/homedir`

Tags published by release pipeline:
- `latest`
- semantic tags like `3.630.0` (see [Releases](https://github.com/os-santiago/homedir/releases))

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
  quay.io/sergio_canales_e/homedir:3.630.0
```

### 4) Build your own image from source
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
Canonical documentation language: **English**.
Spanish documentation is maintained as a mirror (full translation or stub).

- [English Documentation](docs/en/README.md)
- [Documentación en Español](docs/es/README.md)
- [Documentation Language Policy](docs/en/development/documentation-language-policy.md)
- [Release Stage Gates (Alpha -> Beta -> RC -> GA)](docs/en/development/release-stage-gates.md)

Platform deployment notes:
- [platform/README.md](platform/README.md)
- [Production-Safe Delivery Playbook](docs/en/development/production-safe-delivery-playbook.md)

## Contributing

We welcome contributions! This project is built on **voluntary contributions** and serves as a **learning and practice space** for the community.

Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on how to get started, our development workflow, and contribution guidelines.

For information about project governance and how to become a maintainer, see [GOVERNANCE.md](GOVERNANCE.md).

### 🏆 Bounty Hunters Program

Homedir includes a **Bounty Hunters** reputation system that rewards contributors for creating valuable issues and resolving them through pull requests.

> **📚 Learning & Practice Space:** This is an open source project built on **voluntary contributions** as a learning and practice opportunity for the community. The Bounty Hunters program is a **meritocracy and gamification system** to recognize and celebrate contributors, not a payment or monetary reward program.

**How it works:**
- **Create valuable issues** → Get points when validated by admins
- **Resolve issues** → Earn points when your PR is merged
- **Level up** → Unlock exclusive frames and recognition

**Eligible Labels & Points:**
| Label | Points | Type |
|-------|--------|------|
| `bug-impact-low` | 5 pts | Bug fixes |
| `bug-impact-medium` | 15 pts | Bug fixes |
| `bug-impact-high` | 30 pts | Bug fixes |
| `feature-request` | 20 pts | Features |
| `documentation-improvement` | 10 pts | Documentation |
| `platform-maintenance` | 15 pts | Maintenance |

**Progression Levels:**
- 🥉 **Novice** (50+ pts) → Basic frame
- 🥈 **Experienced** (150+ pts) → Enhanced frame
- 🥇 **Professional** (400+ pts) → Professional frame
- 💎 **Ultimate** (800+ pts) → Ultimate frame
- ⭐ **Transcendental** (1500+ pts) → Transcendental frame

**API Endpoints:**
```
GET /api/bounty-hunters/leaderboard?limit=50
GET /api/bounty-hunters/profile/{userId}
GET /api/bounty-hunters/config/labels
GET /api/bounty-hunters/config/levels
```

**Want to participate?**
1. Look for issues with bounty labels (e.g., #1374)
2. Submit quality issues for validation
3. Resolve labeled issues via PRs
4. Track your progress via the API

**Note:** Dashboard UI is coming soon! See [#1375](https://github.com/os-santiago/homedir/issues/1375) to contribute to the frontend.

## Team

### 👥 Maintainers

The project is maintained by the [@os-santiago/core-devs](https://github.com/orgs/os-santiago/teams/core-devs) team:

| Maintainer | GitHub | Role |
|------------|--------|------|
| Sergio Canales | [@scanales-stack](https://github.com/scanales-stack) | Lead Maintainer |

**Interested in becoming a maintainer?** Check our [Governance Guidelines](GOVERNANCE.md) to learn about the path to maintainer status.

**Want to show your support?** If you're a member of the organization, make your membership [public](https://github.com/orgs/os-santiago/people) to display the Open Source Santiago badge on your profile!

### ✨ Contributors

Thanks to all our amazing contributors who make this project possible!

[![Contributors](https://contrib.rocks/image?repo=os-santiago/homedir)](https://github.com/os-santiago/homedir/graphs/contributors)

We follow the [All Contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind are welcome and recognized!

## Code of Conduct
We are committed to fostering a welcoming and inclusive community. Please read our [Code of Conduct](CODE_OF_CONDUCT.md) to understand the standards of behavior we expect from all community members.

## Security
We take security seriously. Please see our [Security Policy](SECURITY.md) for responsible disclosure guidelines and security contact information.

---
*Homedir: Where code finds its home.*<!-- Test comment -->
<!-- Last AI-SDLC local test: 2026-08-01 -->
