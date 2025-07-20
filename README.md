# eventflow

Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

This demo uses Google Sign-In (OAuth 2.0) through the Quarkus OIDC extension. Configure the application by providing the following properties:

```
quarkus.oidc.provider=google
quarkus.oidc.client-id=<CLIENT_ID>
quarkus.oidc.credentials.secret=<CLIENT_SECRET>
quarkus.oidc.application-type=web-app
quarkus.oidc.authentication.redirect-path=/private
quarkus.oidc.authentication.scopes=openid profile email
quarkus.oidc.logout.post-logout-path=/
```

The `provider=google` setting enables automatic discovery of all Google OAuth2 endpoints as well as JWKS. Set the client id and secret obtained from the Google Cloud console. After starting the application you can navigate to `/private` to trigger the login flow.

Ensure `https://eventflow.opensourcesantiago.io/private` is registered as an authorized redirect URI in the Google OAuth2 client configuration if running in production.

You can also configure these values using environment variables. The included `application.properties` expects `OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET` along with the rest of the OIDC URLs, as shown in `deployment/google-oauth-secret.yaml`.

When deploying through GitHub Actions, the workflow populates these values from repository secrets and creates the `google-oauth` secret in the cluster. The manifest in `deployment/google-oauth-secret.yaml` is only a template and is not applied directly during deployment.

## Troubleshooting

- **Error 401: invalid_client**
  This indicates that the OAuth client credentials are incorrect. Verify that `OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET` (or the values in `google-oauth-secret.yaml`) match the client configuration in the Google Cloud console and that the redirect URI is registered correctly.
- **The application supports RP-Initiated Logout but the OpenID Provider does not advertise the end_session_endpoint**
  Google does not publish an RP logout endpoint. Ensure Quarkus' built-in logout is disabled by leaving `quarkus.oidc.logout.path` empty and using the provided `/logout` endpoint instead.
