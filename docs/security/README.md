# Security Documentation

This directory contains security analysis and audit reports for the Quarkus application.

## Issue #854 - Endpoint Authorization Audit

### Files

1. **endpoint-authorization-matrix.yaml** - Complete authorization matrix
   - 255 REST endpoints analyzed from 74 Resource.java files
   - Includes route, HTTP method, security annotation, risk level, source file, and method name
   - Sorted by risk level (critical first)
   - Contains detailed statistics in YAML comments

2. **endpoint-authorization-summary.md** - Executive summary
   - Overview of findings
   - Risk level distribution
   - Critical security issues highlighted
   - Security annotation distribution
   - HTTP method distribution
   - Actionable recommendations

### Risk Classification

- **CRITICAL_ADMIN_UNPROTECTED** (5 endpoints): Admin/private paths without proper security annotations
- **DATOS_SENSIBLES** (4 endpoints): Paths with sensitive data (password, token, secret, credential, payment)
- **ESCRITURA_ADMIN** (5 endpoints): Admin write operations with proper authorization
- **ESCRITURA_USUARIO** (87 endpoints): User write operations (POST/PUT/PATCH/DELETE)
- **LECTURA_AUTENTICADA** (76 endpoints): Authenticated read operations
- **LECTURA_PUBLICA** (78 endpoints): Public read operations

### Key Findings

- **5 Critical Issues**: Admin/private paths without security annotations
  - `/api/internal/insights/events` (POST)
  - `/api/internal/insights/initiatives/start` (POST)
  - `/private/github/callback` (GET)
  - `/private/github/connect` (GET)
  - `/private/github/start` (GET)

- **35 Endpoints with NONE**: No explicit security annotation
  - Requires review to add `@PermitAll`, `@Authenticated`, or `@RolesAllowed`

- **Security Annotation Distribution**:
  - `@Authenticated`: 160 (62.7%)
  - `@PermitAll`: 50 (19.6%)
  - `NONE`: 35 (13.7%)
  - `@RolesAllowed("admin")`: 7 (2.7%)
  - `@RolesAllowed({"admin", "admin-view"})`: 3 (1.2%)

### Usage

```bash
# View summary
cat docs/security/endpoint-authorization-summary.md

# Query the YAML matrix
# Find all critical endpoints
yq '.endpoints[] | select(.risk == "CRITICAL_ADMIN_UNPROTECTED")' docs/security/endpoint-authorization-matrix.yaml

# Find all endpoints with no security
yq '.endpoints[] | select(.security == "NONE")' docs/security/endpoint-authorization-matrix.yaml

# Find all POST endpoints
yq '.endpoints[] | select(.method == "POST")' docs/security/endpoint-authorization-matrix.yaml

# Count by risk level
yq '.endpoints | group_by(.risk) | map({risk: .[0].risk, count: length})' docs/security/endpoint-authorization-matrix.yaml
```

### Next Steps

1. Review and fix the 5 critical issues immediately
2. Add explicit security annotations to all 35 endpoints with NONE
3. Verify OAuth callback security
4. Consider implementing automated security annotation checks in CI/CD
5. Add tests to ensure all new endpoints have explicit security annotations

---

*Generated: 2026-06-22*
*Issue: #854*
