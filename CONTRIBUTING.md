# Release Process

This project uses a **CI-Friendly Versioning** strategy.
Git tags are the single source of truth for versions to ensure consistency between code, git, and docker images.

## How to Release

1.  **Commit Code**: Ensure your changes are merged to `main`.
2.  **Tag**: Create a SemVer tag (must start with `v`, e.g., `v3.305.0`):
    ```bash
    git tag v3.305.0
    git push origin v3.305.0
    ```
3.  **Automated Build**: GitHub Actions will trigger:
    - A Maven build injecting the version `3.305.0` (overriding `${revision}`).
    - A Docker build tagging the image with `3.305.0` and `latest`.
    - A push to `ghcr.io`.

## Local Development

The project uses the Maven `${revision}` property. 
- **Default**: Defined in `pom.xml` properties (e.g., `3.304.1-SNAPSHOT`).
- **Override**: You can build with a specific version locally:
    ```bash
    ./mvnw package -Drevision=1.0.0-custom
    ```

## CI/CD Configuration
The workflow is defined in `.github/workflows/deploy.yml`. It uses:
- `mvn package -Drevision=${VERSION}`
- `ghcr.io` as the container registry.
