# Security Fix for Issue #730: Hardcoded Credentials Cleanup

## Summary

This document describes the security improvements made to address issue #730, which identified multiple instances of hardcoded credentials and sensitive data logging vulnerabilities.

## Changes Made

### 1. Security Utility for Redacting Sensitive Data

**File**: `quarkus-app/src/main/java/com/scanales/homedir/util/SecurityUtils.java`

Created a comprehensive utility class for redacting sensitive information in logs:
- Redacts tokens (access_token, id_token, refresh_token, etc.)
- Redacts OAuth parameters (code, state, authorization codes)
- Redacts credentials, passwords, and secrets
- Provides safe token preview showing only last 4 characters
- Parameter-aware redaction for key-value pairs

### 2. GitHub Service - Token Logging (Lines 121, 127)

**File**: `quarkus-app/src/main/java/com/scanales/homedir/service/GithubService.java`

- Applied `SecurityUtils.redactSensitiveData()` to all response body logging in `exchangeCode()` method
- Prevents access tokens and other OAuth credentials from appearing in logs

### 3. Community Sync Service - Token Preview (Line 184)

**File**: `quarkus-app/src/main/java/com/scanales/homedir/service/CommunitySyncService.java`

- Replaced manual token preview logic with `SecurityUtils.redactTokenPreview()`
- Ensures consistent and secure token redaction across the application

### 4. Deploy Script - Hardcoded Credentials Removed

**File**: `quarkus-app/deploy_with_limits.sh`

**CRITICAL**: Removed hardcoded credentials:
- `HOST="root@72.60.141.165"` - now uses `$DEPLOY_SSH_HOST` environment variable
- `KEY="C:\Users\sergi\.ssh\id_ed25519_codex"` - now uses `$DEPLOY_SSH_KEY` environment variable

**SECURITY ACTION REQUIRED**:
The exposed SSH key `id_ed25519_codex` has been committed to the repository and must be considered compromised. You must:
1. Generate a new SSH key pair
2. Add the new public key to the deployment server
3. Configure the new private key in GitHub Secrets as `DEPLOY_SSH_PRIVATE_KEY`
4. Revoke/remove the old `id_ed25519_codex` key from the server
5. Set `DEPLOY_SSH_HOST` environment variable or GitHub variable

### 5. GitHub Actions Workflow - MITM Vulnerability Fixed

**File**: `.github/workflows/release.yml`

**Security improvements**:
- Removed `StrictHostKeyChecking=no` fallback (lines 254-256, 282-286)
- Removed `UserKnownHostsFile=/dev/null` option
- Now requires proper SSH host key validation via:
  - `DEPLOY_SSH_KNOWN_HOSTS` secret (recommended), or
  - `ssh-keyscan` with fallback to `accept-new` (less secure but acceptable)
- Prevents man-in-the-middle attacks during deployment

**Configuration Required**:
Add the SSH host key to GitHub Secrets:
```bash
# On your local machine, get the host key:
ssh-keyscan -p 22 72.60.141.165

# Add the output to GitHub Secrets as DEPLOY_SSH_KNOWN_HOSTS
```

### 6. Dev Login Page - Assessment

**File**: `quarkus-app/src/main/resources/templates/dev-login.html`

**Security Assessment**:
- Credentials in dev-login.html are for **local development only** (dev profile)
- These are example/dummy credentials (admin@example.org, user@example.com)
- Not actual production secrets - they only work with embedded authentication
- **Decision**: Keep as-is, since:
  - They're already documented in application.properties (%dev profile)
  - They only function in local dev mode (production uses OIDC)
  - Externalizing would add complexity without security benefit
  - The page itself warns users these are "Local Development" credentials

### 7. OAuth Logging Filters - Sensitive Parameter Filtering

**File**: `quarkus-app/src/main/java/com/scanales/oauth/logging/OidcCallbackLoggingFilter.java`

**Security improvements**:
- Added parameter filtering using `SecurityUtils.redactIfSensitive()`
- Redacts sensitive OAuth parameters before logging:
  - `access_token`
  - `id_token`
  - `refresh_token`
  - `code`
  - `state`
  - `authorization`
  - Any other sensitive parameters

## Testing

Created comprehensive unit tests for the security utility:
- **File**: `quarkus-app/src/test/java/com/scanales/homedir/util/SecurityUtilsTest.java`
- 19 test cases covering all redaction scenarios
- All tests passing

## Configuration Required

### GitHub Secrets to Add

1. **DEPLOY_SSH_PRIVATE_KEY** (required)
   - New SSH private key for deployment
   - Generate with: `ssh-keygen -t ed25519 -C "github-actions-homedir"`

2. **DEPLOY_SSH_KNOWN_HOSTS** (recommended)
   - SSH host key fingerprint
   - Get with: `ssh-keyscan -p 22 <your-host>`

### GitHub Variables to Set

1. **DEPLOY_SSH_HOST**
   - Format: `user@hostname` (e.g., `root@72.60.141.165`)

2. **DEPLOY_SSH_KEY** (for deploy_with_limits.sh)
   - Path to SSH key file in the runner environment

### Environment Variables (if using deploy_with_limits.sh)

```bash
export DEPLOY_SSH_HOST="root@72.60.141.165"
export DEPLOY_SSH_KEY="/path/to/new/ssh/key"
```

## Security Best Practices Applied

1. **Principle of Least Privilege**: Credentials are now injected only where needed
2. **Defense in Depth**: Multiple layers of redaction (utility, filters, logging)
3. **Secret Rotation**: Documentation for rotating compromised SSH key
4. **Secure Defaults**: Removed insecure fallback options (StrictHostKeyChecking=no)
5. **Separation of Concerns**: Configuration separated from code
6. **Audit Trail**: Clear logging with sensitive data redacted

## Impact Assessment

- **Breaking Changes**: None (backward compatible with environment variable fallbacks)
- **Required Actions**: SSH key rotation and GitHub Secrets configuration
- **Risk Reduction**: Eliminates hardcoded credentials and prevents credential leakage in logs

## Verification Steps

1. Run tests: `./mvnw test -Dtest=SecurityUtilsTest`
2. Verify no hardcoded credentials in codebase: `git grep -i "password\|secret\|key.*=" | grep -v "\.md"`
3. Check OAuth logs during local testing to ensure sensitive params are redacted
4. Deploy with new SSH configuration to verify GitHub Actions workflow

## References

- Issue #730: Secretos - limpieza de credenciales hardcodeadas
- OWASP: Hardcoded Passwords
- CWE-798: Use of Hard-coded Credentials
- CWE-532: Insertion of Sensitive Information into Log File
