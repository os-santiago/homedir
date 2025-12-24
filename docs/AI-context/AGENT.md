# AGENT NOTES

- gh cli session already authenticated for this repo.
- SSH key for VPS is available/loaded for connections.
- Keep updating this file with any new reminders.
- This file is ignored locally and should not be committed.
- Recommendations/next steps from user must be executed/implemented.
- Every repo change must go up as a PR; track status through merge and fixes.
- For gh runs waits: use 2-minute sleeps up to 15 minutes max.
- Verify PR reaches deploy and latest built image is deployed to production (VPS).
- Use terminal/VPS history to learn frequent commands/actions and common errors to avoid.
- Local sandbox blocks creating pipes/subprocess stdio: Maven Surefire cannot fork (CreatePipe access denied) and Mockito inline agent attach fails. Quarkus @QuarkusTest needs forking, so Java tests can't run here.
- Pytest temp dirs under AppData/.tmp/tmp_py were created with restricted ACL by the sandbox; cleanup/removal hits Access denied. Configure TMP/TEMP to a writable spot outside the sandbox before running pytest.
- When fixing build failures:
  1. Replicate the workflow commands locally: run `./mvnw clean package -Pnative "-Dquarkus.native.container-build=true"` inside `quarkus-app`, using the provided Podman builder image (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`). Keep an eye on podman processes (`podman ps`)-stop/remove leftovers before retrying.
  2. Focus on failing tests or templates logged in the GitHub run, reproduce them locally with targeted `-Dtest=` runs, inspect `target/surefire-reports`, and only push after the suite passes.
  3. Ensure home page Qute data (nav/footer flags, stats) matches the expectations of authenticated/UI tests before pushing; add boolean defaults or relevant template keys if necessary.
  4. Record the exact commands and outcomes here so future agents know what succeeded before pushing.
- Template helpers under `AppTemplateExtensions` may throw when `Arc.container()` is unavailable (native builds hit this during layout rendering); guard every helper that retrieves `SecurityIdentity` or `EventService` so the UI can render even when the container is not ready.
- Once the template fix is in place, rerun `./mvnw clean package -Pnative "-Dquarkus.native.container-build=true"` locally to match the workflow and prevent broken builds from being pushed.
- Production deployments should export `NOTIFICATIONS_USER_HASH_SALT` (>=16 chars) for stable notification hashing; the native image now logs a warning and generates a random salt when it is missing, but the hash will rotate on every restart unless a fixed secret is supplied.
- Latest expectation: run ./mvnw clean package -Pnative "-Dquarkus.native.container-build=true" inside quarkus-app before pushing changes so the native workflow matches the GitHub run.
- When rerunning the native build, check for stuck Podman builder containers and remove them before cleaning the target directory so the clean step can delete native-artifacts.
- Después de corregir y validar que compila y pasan pruebas, enviar siempre los cambios al origen.
- **Workflow Mandatorio para Producción**:
  1. Implementar cambios.
  2. Crear commit.
  3. Crear PR y configurar auto-merge para CI/CD hasta producción.
  4. Usar `gh cli` (sesión válida disponible) para crear PR y monitorear hasta el push de la imagen.
  5. Monitorear endpoint [https://int.opensourcesantiago.io](https://int.opensourcesantiago.io) para ver cuando la imagen de contenedor es descargada y ejecutada en prod.
  6. Verificar finalmente en [https://homedir.opensourcesantiago.io/about](https://homedir.opensourcesantiago.io/about) para confirmar que el commit ya está productivo.
  7. **Guía Detallada**: Ver `.agent/workflows/production_fix_cycle.md` para el paso a paso de diagnótico, implementación robusta y verificación.

- **Robustez en Templates (UI)**:
  - Evitar depender exclusivamente de objetos globales (e.g., `userSession`) que pueden fallar en resolución de beans.
  - Usar siempre variables pasadas por el Controlador (`userAuthenticated`, `userName`) como fallback para ocultar/mostrar elementos críticos de UI (Login, Avatars).
