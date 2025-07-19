# eventflow

Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

This demo uses Google Sign-In (OAuth 2.0) through the Quarkus OIDC extension.
Configure the application by providing the following properties:

```
quarkus.oidc.provider=google
quarkus.oidc.client-id=<CLIENT_ID>
quarkus.oidc.credentials.secret=<CLIENT_SECRET>
quarkus.oidc.application-type=web-app
quarkus.oidc.authentication.redirect-path=/private
quarkus.oidc.authentication.scopes=openid profile email
quarkus.oidc.logout.post-logout-path=/
```

The `provider=google` setting enables automatic discovery of all Google OAuth2
endpoints as well as JWKS. Set the client id and secret obtained from the
Google Cloud console. After starting the application you can navigate to
`/private` to trigger the login flow.

Ensure `https://eventflow.opensourcesantiago.io/private` is registered as an
authorized redirect URI in the Google OAuth2 client configuration if running in
production.
