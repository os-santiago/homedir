# Release Process

This project uses a **Manual Trigger / Auto-Versioning** release strategy.
- **Source of Truth**: Git Tags (calculated by the workflow based on Conventional Commits).
- **Versioning**: CI-Friendly Maven Versioning (`${revision}`).

## How to Release
Releases are triggered manually by administrators.

1.  Go to the **Actions** tab in GitHub.
2.  Select the **Production Release** workflow.
3.  Click **Run workflow**.
4.  (Optional) Select a release level (Patch, Minor, Major), or leave empty to let the system calculate it based on commit history (feat=minor, fix=patch).

The workflow will:
1.  Calculate the next version (e.g., `v3.305.1`).
2.  Creating and push the Git Tag.
3.  Build the Maven artifact with version `3.305.1`.
4.  Build and push the Docker image `ghcr.io/os-santiago/homedir:3.305.1`.

## Local Development
The project uses the Maven `${revision}` property. 
- **Default**: Defined in `pom.xml` properties (e.g., `3.304.1-SNAPSHOT`).
- **Override**: You can build with a specific version locally:
    ```bash
    ./mvnw package -Drevision=1.0.0-custom
    ```
