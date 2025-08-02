# eventflow

Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

This demo uses Google Sign-In (OAuth 2.0) through the Quarkus OIDC extension. OAuth support is disabled by default so the application can run without credentials. Configure the application by providing the following properties:

```
quarkus.oidc.provider=google
quarkus.oidc.enabled=true
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

You can also configure these values using environment variables. The included `application.properties` expects `OIDC_ENABLED=true`, `OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET` along with the rest of the OIDC URLs, as shown in `deployment/google-oauth-secret.yaml`.

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

## Map navigation and talk status

- Events can define a `mapImageUrl` pointing to the general venue map while each scenario may specify a `highlightedMapImageUrl` to highlight its position. If not set, the application builds a default path under `/img/events/{eventId}`. For best results, use 800x600 px images.
- Scenario pages display this map when available and show "Imagen de ubicación no disponible." otherwise. Talk details and the profile page provide a "🧭 ¿Cómo llegar?" link directing to the scenario page.
- The profile page lists all registered talks in a table showing a dynamic status column indicating whether each talk is on time, about to start, in progress or finished.
