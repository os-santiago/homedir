# Homedir - Claude Development Notes

## AI-SDLC Migration (2026-08-01)

AI-SDLC components have been **migrated to a separate repository**: https://github.com/os-santiago/homedir-ai-sdlc

### Deprecated Paths (Removal Scheduled: 2026-08-14)

**Java Classes** (all marked `@Deprecated(since = "3.403.1", forRemoval = true)`):
- `quarkus-app/src/main/java/com/scanales/homedir/sdlc/SdlcApiResource.java`
- `quarkus-app/src/main/java/com/scanales/homedir/sdlc/SdlcDashboardResource.java`
- `quarkus-app/src/main/java/com/scanales/homedir/sdlc/SdlcDashboardSnapshot.java`
- `quarkus-app/src/main/java/com/scanales/homedir/sdlc/SdlcObservabilityService.java`

**Frontend Assets**:
- `quarkus-app/src/main/resources/templates/sdlc/dashboard/`
- `quarkus-app/src/main/resources/META-INF/resources/sdlc/dashboard/`

**Scripts**:
- `platform/scripts/homedir-sdlc-*.sh`
- `platform/scripts/sdlc-*.sh`
- `platform/scripts/policy-*.sh`

**Configuration**:
- `platform/config/autonomous-decision-policy.yaml`
- `platform/systemd/user/homedir-sdlc-worker.*`
- `platform/ansible/playbooks/sdlc-runner.yml`

**CI/CD**:
- `.github/workflows/build-sdlc-worker-image.yml`
- `.github/workflows/deploy-worker.yml`

### Important Notes

⚠️ **DO NOT make changes to deprecated paths** - they exist only for rollback purposes during the migration period.

✅ **For AI-SDLC work**, use the new repository: https://github.com/os-santiago/homedir-ai-sdlc

**Automatic Cleanup**: A GitHub Actions workflow (`.github/workflows/cleanup-sdlc.yml`) will automatically remove all deprecated code on 2026-08-14.

### Migration Reason

AI-SDLC modifications were causing issues in the main Homedir application. Separating the systems allows independent evolution without coupling.

### References

- **Migration Plan**: https://github.com/os-santiago/homedir-ai-sdlc/blob/main/MIGRATION-NOTES.md
- **New Repo**: https://github.com/os-santiago/homedir-ai-sdlc
- **Deployment Docs**: https://github.com/os-santiago/homedir-ai-sdlc/blob/main/docs/deployment/

---

## Development Guidelines

This file can be extended with other Claude-specific development notes for the Homedir project.
