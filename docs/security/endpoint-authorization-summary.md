# Endpoint Authorization Audit Summary
Generated: 2026-06-22 02:56:25
Issue: #854

## Overview
- **Total Endpoints Analyzed**: 255
- **Total Resource Files**: 74

## Risk Level Distribution

| Risk Level | Count | Percentage |
|-----------|-------|------------|
| CRITICAL_ADMIN_UNPROTECTED | 5 | 2.0% |
| DATOS_SENSIBLES | 4 | 1.6% |
| ESCRITURA_ADMIN | 5 | 2.0% |
| ESCRITURA_USUARIO | 87 | 34.1% |
| LECTURA_AUTENTICADA | 76 | 29.8% |
| LECTURA_PUBLICA | 78 | 30.6% |

## Critical Security Issues

### 1. CRITICAL_ADMIN_UNPROTECTED (5 endpoints)

These are admin/private paths **without proper security annotations**:

- **POST /api/internal/insights/events**
  - Security: `NONE`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/private_/InternalInsightsIngestResource.java`
  - Method: `appendEvent`

- **POST /api/internal/insights/initiatives/start**
  - Security: `NONE`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/private_/InternalInsightsIngestResource.java`
  - Method: `startInitiative`

- **GET /private/github/callback**
  - Security: `NONE`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/private_/GithubLinkResource.java`
  - Method: `callback`

- **GET /private/github/connect**
  - Security: `NONE`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/private_/GithubLinkResource.java`
  - Method: `connect`

- **GET /private/github/start**
  - Security: `NONE`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/private_/GithubLinkResource.java`
  - Method: `start`

### 2. DATOS_SENSIBLES (4 endpoints)

These endpoints handle sensitive data (auth, tokens, credentials):

- **GET /auth/discord/callback**
  - Security: `@PermitAll`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/public_/DiscordAuthResource.java`

- **GET /auth/github/callback**
  - Security: `@PermitAll`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/public_/GithubAuthResource.java`

- **GET /auth/session/auth/session**
  - Security: `@Authenticated`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/security/SessionResource.java`

- **POST /auth/session/refresh**
  - Security: `@Authenticated`
  - File: `quarkus-app/src/main/java/com/scanales/homedir/security/SessionResource.java`

## Security Annotation Distribution

| Security Annotation | Count | Percentage |
|-------------------|-------|------------|
| `@Authenticated` | 160 | 62.7% |
| `@PermitAll` | 50 | 19.6% |
| `@RolesAllowed("admin")` | 7 | 2.7% |
| `@RolesAllowed({"admin", "admin-view"})` | 3 | 1.2% |
| `NONE` | 35 | 13.7% |

## HTTP Method Distribution

| HTTP Method | Count | Percentage |
|------------|-------|------------|
| DELETE | 7 | 2.7% |
| GET | 155 | 60.8% |
| POST | 71 | 27.8% |
| PUT | 22 | 8.6% |

## Recommendations

1. **Immediate Action Required** - Fix the 5 CRITICAL_ADMIN_UNPROTECTED endpoints:
   - Add `@RolesAllowed("admin")` or `@Authenticated` to InternalInsightsIngestResource endpoints
   - Add proper security annotations to GithubLinkResource endpoints

2. **Review Required** - 35 endpoints have NO security annotation (NONE):
   - Review each to determine if public access is intended
   - Add explicit `@PermitAll` if public access is intentional
   - Add `@Authenticated` or `@RolesAllowed` if protection is needed

3. **Sensitive Data Endpoints** - Review the 4 DATOS_SENSIBLES endpoints:
   - Ensure OAuth callbacks are properly validated
   - Consider additional security measures for auth endpoints

4. **Write Operations** - 87 user write operations detected:
   - Verify all have proper authorization checks
   - Review for CSRF protection

## Next Steps

1. Review the full matrix: `docs/security/endpoint-authorization-matrix.yaml`
2. Fix critical issues identified above
3. Add security annotations to all 35 endpoints with NONE
4. Consider implementing a policy to require explicit security annotations on all endpoints
5. Add automated tests to verify security annotations are present

---

*Full endpoint details available in `docs/security/endpoint-authorization-matrix.yaml`*