# Homedir Deployment and Monitoring

## Platform
- **Production URL**: `https://homedir.opensourcesantiago.io`
- **Internal Monitor**: `https://int.opensourcesantiago.io`
- **Infrastructure**: Debian-based VPS with Podman/Docker.

## CI/CD Pipeline
- **GitHub Actions**:
  - `pr-check.yml`: Runs Maven tests and Spotless check.
  - `release.yml`: Builds the native image and triggers deployment.
- **Workflow**: Automated via `gh pr merge --auto`.

## Production Secrets
- Mandatory: `NOTIFICATIONS_USER_HASH_SALT` (>=16 chars) for stable notification IDs.
- If missing, the native app generates a random salt (will change salt on every restart).

## Monitoring and Logs
- Inspect logs via `gh run watch` or SSH to the VPS at `service homedir logs`.
- Check `/about` (or similar) to confirm the git ID of the active version.
