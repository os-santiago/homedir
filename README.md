# eventflow

Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

## 2.1.1 – Persistente con soporte de Eventos, Oradores y Charlas

Esta versión elimina completamente la sincronización con repositorios Git introducida en versiones experimentales posteriores a v1.0.0.

### Cambios clave
- Eliminada toda dependencia de JGit
- Removida funcionalidad de sincronización automática
- Panel de administración simplificado
- Mejora en estabilidad y compatibilidad con compilación nativa

> **Nota:** Las versiones `v1.1.x` fueron experimentales y no deben usarse en producción.

This demo uses Google Sign-In (OAuth 2.0) through the Quarkus OIDC extension. Configure the application by providing the following properties:

```
quarkus.oidc.provider=google
quarkus.oidc.client-id=<CLIENT_ID>
quarkus.oidc.credentials.secret=<CLIENT_SECRET>
quarkus.oidc.application-type=web-app
quarkus.oidc.authentication.redirect-path=/private
quarkus.oidc.authentication.scopes=openid profile email
quarkus.oidc.logout.post-logout-path=/
quarkus.oidc.user-info-required=false
quarkus.oidc.authentication.user-info-required=false
quarkus.oidc.authentication.id-token-required=true
quarkus.oidc.token.principal-claim=id_token
```

The `provider=google` setting enables automatic discovery of all Google OAuth2 endpoints as well as JWKS. Set the client id and secret obtained from the Google Cloud console. After starting the application you can navigate to `/private` to trigger the login flow.

After authenticating you will be redirected to `/private/profile` where the application displays your profile information (name, given and family names, email, and sub) extracted from the ID token.

Ensure `https://eventflow.opensourcesantiago.io/private` is registered as an authorized redirect URI in the Google OAuth2 client configuration if running in production.

You can also configure these values using environment variables. The included `application.properties` expects `OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET` along with the rest of the OIDC URLs, as shown in `deployment/google-oauth-secret.yaml`.

When deploying through GitHub Actions, the workflow populates these values from repository secrets and creates the `google-oauth` secret in the cluster. The manifest in `deployment/google-oauth-secret.yaml` is only a template and is not applied directly during deployment.

## Admin access

Endpoints under `/private/admin` are restricted to authenticated users whose
email address is present in the comma separated list defined by the
`ADMIN_LIST` configuration property or environment variable. Example:

```
ADMIN_LIST=sergio.canales.e@gmail.com,alice@example.org
```

Only users included in this list can create, edit or delete events and their
associated scenarios and talks.

## Importing events from JSON

The administration UI provides an option to import events using a JSON file.
Use the form under `/private/admin/events` to upload a file named `file`
with the `application/json` MIME type. The application validates the content
and will respond with `409 Conflict` if an event with the same ID already
exists or `400 Bad Request` when the JSON is invalid. A successful import
redirects back to the event list displaying a confirmation banner.

## Troubleshooting

- **Error 401: invalid_client**
  This indicates that the OAuth client credentials are incorrect. Verify that `OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET` (or the values in `google-oauth-secret.yaml`) match the client configuration in the Google Cloud console and that the redirect URI is registered correctly.
- **The application supports RP-Initiated Logout but the OpenID Provider does not advertise the end_session_endpoint**
  Google does not publish an RP logout endpoint. Ensure Quarkus' built-in logout is disabled by leaving `quarkus.oidc.logout.path` empty and using the provided `/logout` endpoint instead.

## Supply chain: SBOM & Vulnerabilities

The build generates Software Bill of Materials (SBOM) for dependencies and container images and scans the image for known vulnerabilities.

### Local commands

```bash
# Build and generate dependency SBOM
mvn -f quarkus-app/pom.xml -DskipTests verify
# Generate image SBOM
syft oci:${REGISTRY}/${IMAGE_NAME}:<tag> -o cyclonedx-json > sbom-image.cdx.json
# Scan image
grype oci:${REGISTRY}/${IMAGE_NAME}:<tag> --fail-on High
```

### CI

- `target/bom.json` and `sbom-image.cdx.json` are uploaded as workflow artifacts (Actions → Artifacts).
- Vulnerability gating is warning-only unless `vars.SECURITY_GATING` is set to `enforcing`.
  When enforcing, pushes to `main` fail on **Critical** findings.
- Images are signed with [Cosign](https://github.com/sigstore/cosign) using keyless signatures when `vars.SIGN_KEYLESS` is `true`, or a private key when `COSIGN_PRIVATE_KEY`/`COSIGN_PASSWORD` secrets are provided.
- The image SBOM can be attached to the image when `vars.SIGN_ATTACH_SBOM` is `true`.

Required variables and secrets:

- `REGISTRY` – container registry (defaults to `ghcr.io`)
- `IMAGE_NAME` – repository path of the image (defaults to `owner/repo`)
- `SECURITY_GATING` – `permissive` (default) or `enforcing`
- `SIGN_KEYLESS=true` – enable keyless signing
- `SIGN_ATTACH_SBOM=true` – attach SBOM to the image (optional)
- `COSIGN_PRIVATE_KEY` and `COSIGN_PASSWORD` – key pair for signing (optional)

For coordinated vulnerability disclosure, see [SECURITY.md](SECURITY.md).

### CI digests and promotion

- Pull Requests build the native image destined for production and push it to the registry.
- The immutable image digest (`REGISTRY/IMAGE_NAME@sha256:...`) is printed in the job summary and stored in the `security-reports` artifact along with SBOM and vulnerability reports.
- Severity checks run in permissive mode by default; switch `vars.SECURITY_GATING` to `enforcing` to fail on findings.
- When a PR is merged, the workflow resolves that same digest, retags it for `main` without rebuilding, signs it, and deploys using the digest.
