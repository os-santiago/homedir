# Homedir Expert Agent

Expert assistant for the Homedir platform. Structured for automated CI/CD flow, native image validation, and VPS deployment monitoring.

## 🏗️ Architecture and Stack
- **Backend (Main)**: Quarkus (Java 21) in `quarkus-app/`. Port 8080.
- **Backend (Core)**: Go-based event processing (Edge features).
- **Automation**: Python-based scripts in `scripts/` (knowledge management, crawling, testing).
- **Deployment**: Native binaries built via Podman on a VPS.

## 🛠️ Operational Workflow

### 1. Development and Fixes
- **Branching**: All changes must use specific branches (`fix/`, `feature/`, `refactor/`).
- **Commits**: Follow conventional commits (checked by `.commitlintrc.yml`).
- **Local Testing**:
  - Java: `./mvnw -f quarkus-app/pom.xml -T 1C -Dquarkus.devservices.enabled=false test` (use `scripts/test-fast.sh`).
  - Python: `python -m pytest tests`.
- **Sandbox Limits**: Java tests cannot run in the Antigravity sandbox due to forking issues (Maven/Mockito). Use native builds or push to PR for full suite validation.

### 2. Native Image Validation (Mandatory for Fixes)
Before pushing critical fixes:
1. Navigate to `quarkus-app/`.
2. Run native package: `./mvnw clean package -Pnative "-Dquarkus.native.container-build=true"`.
3. Use the Podman builder: `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`.
4. Check for stuck Podman containers if build fails.

### 3. CI/CD & Production
- **PR Flow**: Create PR via `gh pr create` -> Enable automerge `gh pr merge --auto`.
- **Monitoring**: 
  - Trace the "Release Pipeline" in GitHub Actions.
  - Monitor production: `https://int.opensourcesantiago.io` (container pull) and `https://homedir.opensourcesantiago.io/about` (active version).
- **VPS SSH**: Keys are available for direct access if troubleshooting deployment is needed.

## ⚠️ Robustness Rules (UI/Templates)
- **UI fallbacks**: Do NOT depend on global `userSession` bean alone (can fail in native). Use controller-passed variables (`userAuthenticated`, `userName`) and `{#if}` guards.
- **Template Helpers**: `AppTemplateExtensions` must be guarded against `Arc.container()` unavailability during initial native render.

## 🧪 Quick Test Commands
| Target | Command | Script |
|---|---|---|
| Java Fast | `mvn test -DskipITs=true` | `scripts/test-fast.sh` |
| Java Full | `mvn verify -Pcoverage` | `scripts/test-all.sh` |
| Python | `pytest tests/` | - |
