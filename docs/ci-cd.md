# CI/CD Pipeline

Two GitHub Actions workflows orchestrate the build and deployment pipeline and ensure a single immutable image digest flows from pull request to production.

## Pull Requests – `.github/workflows/pr-ci.yml`

- **Build and test**: `./mvnw -B -ntp test package` runs inside `quarkus-app`.
- **Build native image**: the native runner is packaged once and tagged for the commit and pull request.
- **SBOM / vulnerability scan**: Syft and Grype generate reports, uploaded as the `pr-security-reports` artifact and to code scanning. The job summary shows the exact image reference used for later promotion.
- **Optional signing**: if Cosign keys are present, the same image digest is signed.

The repository variable `SECURITY_GATING` toggles scan enforcement:

- `permissive` (default) – scan failures do not block the workflow.
- `enforcing` – scan failures cause the job to fail.

## Merge to `main` – `.github/workflows/main-deploy.yml`

The deploy workflow resolves the pull request image digest and promotes it without rebuilding. It may tag the digest for traceability and then authenticates to GKE to apply manifests and roll out the exact image by digest.

## Reports and artifact identity

- **Security reports**: artifact `pr-security-reports` and code scanning results.
- **Image identity**: written to the PR workflow summary and stored in `image-ref.txt` within the artifact.

Switch to mandatory gating by setting repository variable `SECURITY_GATING` to `enforcing` in repository settings.
