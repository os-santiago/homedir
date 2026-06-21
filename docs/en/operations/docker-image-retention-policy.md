# Docker Image Retention Policy

## Registries

- **Primary**: Quay.io (`quay.io/os-santiago/homedir`)
- **Fallback**: GitHub Container Registry (`ghcr.io/os-santiago/homedir`)

## Retention Rules

| Tag Pattern | Retention | Rationale |
|-------------|-----------|-----------|
| `latest` | Keep indefinitely | Current production image |
| `vMAJOR.MINOR.PATCH` (semver tags) | Keep last 10 releases | Rollback support (2 weeks of releases at ~5/week) |
| `vMAJOR.MINOR.PATCH-buildTIMESTAMP` (CI build tags) | Keep last 5 | Debugging recent builds only |
| `sha-<commit>` (commit SHA tags) | Delete after 30 days | Temporary debug references |

## Cleanup Strategy

- **Quay.io**: Auto-prune via Quay UI or API — set retention policy in Quay repository settings to keep 10 most recent tags.
- **GHCR**: Use `gh api` with pagination to list tags older than 30d and delete orphaned `sha-*` tags.

## Out of Scope

- Local Docker build cache on developer machines (developer responsibility)
- CI runner disk cleanup (handled by GitHub Actions runner ephemeral nature)