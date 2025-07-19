# eventflow

Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

This demo uses Google Sign-In (OAuth 2.0) through Quarkus OIDC. Provide your OAuth credentials via the following environment variables:

```
OIDC_CLIENT_ID
OIDC_CLIENT_SECRET
OIDC_AUTH_SERVER_URL
OIDC_AUTH_URI
OIDC_TOKEN_URI
OIDC_JWKS_URI
OIDC_REDIRECT_URI
```

The Quarkus application reads these variables at startup to configure its
OpenID Connect client. Ensure they are available at runtime, for example by
creating the `google-oauth` secret and passing it to the container.

Set `https://eventflow.opensourcesantiago.io/callback` as an authorized redirect URI
for the Google OAuth2 client. Quarkus will redirect the browser to this path
after the user authenticates.
Also add `https://eventflow.opensourcesantiago.io` to the list of authorized JavaScript origins so that the login page can initiate the OAuth flow from the browser.
