# eventflow

Smart event management platform: spaces, activities, speakers, attendees, and personalized planning.

This demo uses Google Sign-In (OAuth 2.0) through Quarkus OIDC. Provide your OAuth credentials via the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables.

To avoid the `invalid_client` error from Google, make sure the OAuth client is configured with the correct redirect URI. Quarkus expects `https://eventflow.opensourcesantiago.io/q/oidc/redirect` by default. Add this URI to your OAuth client settings and update the `google-oauth` secret or the environment variables with your actual client ID and secret.
