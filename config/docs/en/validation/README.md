# Validation Documentation

This directory contains validation tests and documentation for various homedir integrations and workflows.

## Available Validations

### Webhook Integration Test (Issue #864)

**Purpose:**  
Validates the end-to-end flow from GitHub webhook events to WOS (Workspace OS) delegation and Discord notifications.

**Files:**
- [webhook-wos-discord-validation.md](./webhook-wos-discord-validation.md) - Complete validation documentation
- [../../scripts/test-webhook-integration.sh](../../scripts/test-webhook-integration.sh) - Automated test script

**Quick Test:**
```bash
# Run from repository root
bash config/scripts/test-webhook-integration.sh
```

**Current Status:**
- Webhook: ✅ OPERATIONAL (HTTP 200 responses)
- WOS: ✅ CONFIGURED
- Discord: ⚠️ PENDING VERIFICATION

## Contributing

Follow ADEV guidelines when adding new validations:
- Atomic commits, one issue per PR
- Use Conventional Commits format
- Link to issues with "Closes #NNN"
