# Content Security Policy (CSP) and Security Headers Specification

**Version:** 1.0  
**Last Updated:** 2026-06-23  
**Owner:** Security Team  
**Issue Reference:** #856  
**Parent Issue:** #838

## Executive Summary

This document establishes a comprehensive Content Security Policy (CSP) and security headers specification for the homedir platform. It provides an inventory of current security headers across representative endpoints, proposes a strict CSP policy without unsafe directives, defines a report-only monitoring phase, and outlines a phased migration plan to enforcing mode.

**Key Objectives:**
- Prevent XSS attacks through strict CSP directives
- Eliminate clickjacking risks via X-Frame-Options
- Prevent MIME-type sniffing attacks
- Enable HSTS for HTTPS enforcement
- Establish monitoring infrastructure before enforcement

---

## Table of Contents

- [Current Security Headers Inventory](#current-security-headers-inventory)
- [Proposed CSP Policy](#proposed-csp-policy)
- [Report-Only Mode Configuration](#report-only-mode-configuration)
- [Migration Plan](#migration-plan)
- [Compliance and Standards](#compliance-and-standards)
- [References](#references)

---

## Current Security Headers Inventory

### Audit Methodology

Security headers were audited against 10+ representative routes across different endpoint types using the following methodology:

```bash
# Audit script for security headers
curl -I -H "Accept: application/json" https://dev.homedir.local/endpoint
```

**Endpoint Categories Audited:**
1. **Public Endpoints**: Landing page, documentation, public API
2. **Authenticated Endpoints**: User dashboard, profile, settings
3. **Admin Endpoints**: Admin panel, user management, configuration
4. **API Endpoints**: REST API resources (public and authenticated)
5. **Error Pages**: 404, 500, 403 error handlers

### Current Headers by Endpoint Type

#### 1. Public Endpoints

**Representative Routes:**
- `GET /` (Landing page)
- `GET /api/public/health` (Health check)
- `GET /docs` (Public documentation)

**Current Headers:**
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: [NOT PRESENT]
X-XSS-Protection: [NOT PRESENT - deprecated]
Referrer-Policy: [NOT PRESENT]
Permissions-Policy: [NOT PRESENT]
```

**Analysis:**
- ✅ Basic anti-clickjacking protection (X-Frame-Options)
- ✅ HSTS enabled with 1-year max-age
- ✅ MIME-sniffing protection
- ❌ No CSP defined (critical gap)
- ⚠️ No Referrer-Policy (privacy concern)
- ⚠️ No Permissions-Policy (feature control missing)

---

#### 2. Authenticated Endpoints

**Representative Routes:**
- `GET /dashboard` (User dashboard)
- `GET /api/user/profile` (User profile API)
- `POST /api/user/settings` (User settings update)

**Current Headers:**
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
Set-Cookie: session=...; HttpOnly; Secure; SameSite=Strict
Content-Security-Policy: [NOT PRESENT]
Cache-Control: private, no-cache, no-store, must-revalidate
```

**Analysis:**
- ✅ Secure session cookie attributes (HttpOnly, Secure, SameSite=Strict)
- ✅ Proper cache control for authenticated content
- ✅ HSTS and anti-clickjacking
- ❌ No CSP (XSS vulnerability remains)

---

#### 3. Admin Endpoints

**Representative Routes:**
- `GET /admin` (Admin panel)
- `GET /admin/users` (User management)
- `POST /admin/config` (Configuration API)

**Current Headers:**
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Set-Cookie: admin_session=...; HttpOnly; Secure; SameSite=Strict
Content-Security-Policy: [NOT PRESENT]
Cache-Control: private, no-cache, no-store, must-revalidate
X-Content-Type-Options: nosniff
```

**Analysis:**
- ✅ HSTS preload enabled (stricter than public endpoints)
- ✅ Strong session cookie security
- ✅ No-cache headers prevent sensitive data caching
- ❌ No CSP despite high-privilege context (critical)

---

#### 4. API Endpoints

**Representative Routes:**
- `GET /api/public/cfp` (Public CFP listing)
- `GET /api/authenticated/submissions` (User submissions)
- `POST /api/admin/users` (Admin user creation)

**Current Headers:**
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
Content-Security-Policy: [NOT PRESENT]
```

**Analysis:**
- ✅ Correct Content-Type with charset
- ✅ No-store cache control for API responses
- ⚠️ CSP less critical for pure API endpoints (no HTML/JS execution)
- ℹ️ API endpoints may use `default-src 'none'` CSP for defense-in-depth

---

#### 5. Error Pages

**Representative Routes:**
- `404 /nonexistent` (Not Found)
- `500 /error` (Internal Server Error - simulated)
- `403 /forbidden` (Forbidden)

**Current Headers:**
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: [NOT PRESENT]
```

**Analysis:**
- ✅ Consistent security headers on error pages
- ❌ Error pages vulnerable to XSS if error messages reflect user input
- 🔴 **Critical:** Error handlers must sanitize all reflected input

---

### Inventory Summary

| Header | Public | Auth | Admin | API | Error |
|--------|--------|------|-------|-----|-------|
| **X-Frame-Options** | ✅ DENY | ✅ DENY | ✅ DENY | ✅ DENY | ✅ DENY |
| **X-Content-Type-Options** | ✅ nosniff | ✅ nosniff | ✅ nosniff | ✅ nosniff | ✅ nosniff |
| **Strict-Transport-Security** | ✅ 1yr | ✅ 1yr | ✅ 1yr+preload | ✅ 1yr | ✅ 1yr |
| **Content-Security-Policy** | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Referrer-Policy** | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Permissions-Policy** | ❌ | ❌ | ❌ | ❌ | ❌ |

**Critical Gaps:**
1. **No CSP across all endpoints** (XSS vulnerability)
2. **No Referrer-Policy** (privacy leak risk)
3. **No Permissions-Policy** (unnecessary browser features enabled)

---

## Proposed CSP Policy

### CSP Design Principles

1. **No `unsafe-inline` or `unsafe-eval`** in default policy
2. **Nonce-based script/style execution** for inline code
3. **Strict `default-src 'none'`** with explicit allowlisting
4. **Separate policies for HTML pages vs. API endpoints**
5. **Report violations to monitoring endpoint** before enforcement

---

### CSP Policy for HTML Pages

#### Recommended Policy (Strict)

```
Content-Security-Policy:
  default-src 'none';
  script-src 'self' 'nonce-{RANDOM}' https://cdn.homedir.io;
  style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com;
  font-src 'self' https://fonts.gstatic.com;
  img-src 'self' https://cdn.homedir.io https://avatars.githubusercontent.com data:;
  connect-src 'self' https://api.homedir.io;
  frame-ancestors 'none';
  base-uri 'self';
  form-action 'self';
  upgrade-insecure-requests;
  block-all-mixed-content;
  report-uri /api/csp-reports;
  report-to csp-endpoint
```

#### Directive Breakdown

| Directive | Value | Rationale |
|-----------|-------|-----------|
| `default-src` | `'none'` | Deny-by-default; explicit allowlisting required |
| `script-src` | `'self' 'nonce-{RANDOM}' https://cdn.homedir.io` | - `'self'`: Allow scripts from same origin<br>- `'nonce-{RANDOM}'`: Runtime nonce for inline scripts (replaces unsafe-inline)<br>- CDN: Trusted third-party scripts (e.g., analytics) |
| `style-src` | `'self' 'nonce-{RANDOM}' https://fonts.googleapis.com` | - Nonce for inline styles<br>- Google Fonts CSS |
| `font-src` | `'self' https://fonts.gstatic.com` | Web fonts from Google Fonts CDN |
| `img-src` | `'self' https://cdn.homedir.io https://avatars.githubusercontent.com data:` | - Same-origin images<br>- CDN images<br>- GitHub avatars (user profile pics)<br>- Data URIs for inline SVGs/icons |
| `connect-src` | `'self' https://api.homedir.io` | API endpoints for AJAX/Fetch requests |
| `frame-ancestors` | `'none'` | Prevent embedding (replaces X-Frame-Options) |
| `base-uri` | `'self'` | Prevent `<base>` tag hijacking |
| `form-action` | `'self'` | Prevent form submission to external domains |
| `upgrade-insecure-requests` | (directive) | Auto-upgrade HTTP to HTTPS |
| `block-all-mixed-content` | (directive) | Block HTTP resources on HTTPS pages |
| `report-uri` | `/api/csp-reports` | Legacy reporting endpoint (deprecated but widely supported) |
| `report-to` | `csp-endpoint` | Modern Reporting API endpoint |

---

### Nonce Implementation

**Backend (Quarkus/Jakarta):**

```java
@ApplicationScoped
public class CSPNonceProvider {
    public String generateNonce() {
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }
}

@ServerResponseFilter
public void addCSPHeader(@Context ContainerRequestContext requestContext,
                         @Context ContainerResponseContext responseContext) {
    String nonce = cspNonceProvider.generateNonce();
    requestContext.setProperty("cspNonce", nonce);
    
    String csp = String.format(
        "default-src 'none'; script-src 'self' 'nonce-%s' https://cdn.homedir.io; ...",
        nonce
    );
    responseContext.getHeaders().add("Content-Security-Policy-Report-Only", csp);
}
```

**Frontend (React/Template):**

```html
<!-- Inline script with nonce -->
<script nonce="${cspNonce}">
  console.log('This script is allowed by CSP nonce');
</script>

<!-- External script (no nonce needed) -->
<script src="/js/app.js"></script>
```

---

### CSP Policy for API Endpoints

**Recommended Policy (API-only):**

```
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
```

**Rationale:**
- API endpoints return JSON, not HTML (no script/style execution)
- `default-src 'none'` prevents any resource loading
- `frame-ancestors 'none'` prevents embedding (defense-in-depth)
- Minimal policy reduces overhead for high-traffic API routes

---

### Exception Handling: `unsafe-inline` Justification

**Scenario Requiring `unsafe-inline`:**

If the application uses legacy inline event handlers (`onclick`, `onerror`) or inline `<style>` tags that cannot be refactored to use nonces in Phase 1, a temporary exception is permitted:

```
Content-Security-Policy-Report-Only:
  default-src 'none';
  script-src 'self' 'unsafe-inline' https://cdn.homedir.io;
  style-src 'self' 'unsafe-inline';
  ...
  report-uri /api/csp-reports
```

**Justification Requirements:**
1. **Document specific use case** (e.g., "Admin panel uses inline event handlers in 12 legacy components")
2. **Create refactoring ticket** to migrate to nonce-based approach
3. **Set deadline** for removing `unsafe-inline` (e.g., "Remove by 2026-09-01")
4. **Monitor violations** to identify affected code paths

**Acceptance Criteria for Exception:**
- Exception limited to specific routes (not global)
- Refactoring plan documented and scheduled
- Temporary exception reviewed monthly

**IMPORTANT:** `unsafe-eval` is NEVER permitted (blocks all dynamic code evaluation, breaks React DevTools, etc.). If legacy code requires `eval()`, refactor BEFORE enabling CSP.

---

## Report-Only Mode Configuration

### Phase 1: Monitoring (Report-Only)

Before enforcing CSP, deploy in **report-only mode** to collect violation data without breaking functionality.

#### Report-Only Header

```
Content-Security-Policy-Report-Only:
  default-src 'none';
  script-src 'self' 'nonce-{RANDOM}' https://cdn.homedir.io;
  ...
  report-uri /api/csp-reports;
  report-to csp-endpoint
```

**Key Difference:** `Content-Security-Policy-Report-Only` header reports violations but does NOT block resources.

---

### CSP Reporting Endpoint

#### Backend Implementation (Quarkus)

**Endpoint:** `POST /api/csp-reports`

```java
@Path("/api/csp-reports")
@ApplicationScoped
public class CSPReportResource {
    
    @Inject
    Logger log;
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response receiveReport(CSPViolationReport report) {
        // Log violation for monitoring
        log.warn("CSP Violation: document-uri={}, violated-directive={}, blocked-uri={}",
                 report.getCspReport().getDocumentUri(),
                 report.getCspReport().getViolatedDirective(),
                 report.getCspReport().getBlockedUri());
        
        // Store in metrics/monitoring system
        metricsService.recordCSPViolation(report);
        
        return Response.noContent().build();
    }
}

// DTOs
public class CSPViolationReport {
    @JsonProperty("csp-report")
    private CSPReport cspReport;
    
    // Getters/setters
}

public class CSPReport {
    @JsonProperty("document-uri")
    private String documentUri;
    
    @JsonProperty("violated-directive")
    private String violatedDirective;
    
    @JsonProperty("blocked-uri")
    private String blockedUri;
    
    @JsonProperty("original-policy")
    private String originalPolicy;
    
    // Additional fields per CSP spec
}
```

---

### Reporting API (Modern)

**Header Configuration:**

```
Report-To: {"group":"csp-endpoint","max_age":10886400,"endpoints":[{"url":"/api/csp-reports"}]}
Content-Security-Policy-Report-Only: ...; report-to csp-endpoint
```

**Note:** Modern browsers use `report-to` directive; older browsers fallback to `report-uri`. Include both for compatibility.

---

### Monitoring Dashboard

**Metrics to Track:**
1. **Violation count by directive** (script-src, style-src, img-src, etc.)
2. **Violation count by page/route** (identify problematic endpoints)
3. **Violation count by blocked URI** (identify unexpected third-party resources)
4. **Violation count by user-agent** (browser compatibility issues)

**Example Grafana Query (Prometheus):**

```promql
sum(rate(csp_violations_total[5m])) by (violated_directive, document_uri)
```

**Alerting Thresholds:**
- **Warning:** >100 violations/hour (potential policy too strict)
- **Critical:** >1000 violations/hour (policy misconfiguration)

---

## Migration Plan

### Overview

CSP rollout follows a phased approach to ensure zero production impact:

1. **Phase 1:** Report-only mode in staging (14 days)
2. **Phase 2:** Report-only mode in production (30 days)
3. **Phase 3:** Enforcement in staging (7 days)
4. **Phase 4:** Gradual enforcement in production (14 days)

---

### Phase 1: Report-Only in Staging (Days 1-14)

**Objective:** Validate CSP policy completeness in staging environment.

**Actions:**
1. Deploy `Content-Security-Policy-Report-Only` header to staging
2. Configure CSP reporting endpoint (`/api/csp-reports`)
3. Enable monitoring dashboard
4. Test all user workflows (auth, admin, CFP submission, etc.)
5. Review violation reports daily

**Success Criteria:**
- ✅ Zero violations for core user workflows
- ✅ All violations from known third-party resources (documented exceptions)
- ✅ Reporting endpoint receiving and logging violations correctly

**Rollback Criteria:**
- ❌ >500 violations/hour (policy too restrictive)
- ❌ Critical workflow broken (e.g., authentication fails)

**Timeline:** 14 days  
**Owner:** Security Team + QA

---

### Phase 2: Report-Only in Production (Days 15-44)

**Objective:** Collect real-world violation data from production traffic.

**Actions:**
1. Deploy `Content-Security-Policy-Report-Only` to production (all routes)
2. Monitor violations for 30 days
3. Analyze top violated directives and blocked URIs
4. Refactor code to eliminate violations (nonce migration, remove inline handlers)
5. Update CSP policy based on findings

**Success Criteria:**
- ✅ <50 violations/day after first week (steady state)
- ✅ All violations documented with action plan (allow or refactor)
- ✅ No security incidents related to XSS during monitoring period

**Monitoring Focus:**
- **Week 1-2:** High violation count expected (initial discovery)
- **Week 3-4:** Declining violation count as code is refactored
- **Week 4-5:** Stable baseline (<50/day)

**Go/No-Go Decision (Day 44):**
- **GO:** Violations <50/day, all critical paths validated
- **NO-GO:** Violations >100/day, critical functionality affected → Extend Phase 2 by 14 days

**Timeline:** 30 days  
**Owner:** Security Team + DevOps

---

### Phase 3: Enforcement in Staging (Days 45-51)

**Objective:** Validate enforcing CSP in staging without production risk.

**Actions:**
1. Replace `Content-Security-Policy-Report-Only` with `Content-Security-Policy` (enforcing)
2. Run full regression test suite
3. Manual QA testing of all workflows
4. Load testing with CSP enforcement enabled

**Success Criteria:**
- ✅ All automated tests pass
- ✅ Manual QA finds no CSP-related issues
- ✅ Load testing shows <5% performance degradation (CSP header overhead)

**Rollback Criteria:**
- ❌ Any critical functionality broken
- ❌ >10% performance degradation

**Timeline:** 7 days  
**Owner:** QA + Security Team

---

### Phase 4: Gradual Enforcement in Production (Days 52-65)

**Objective:** Roll out enforcing CSP to production in stages.

#### Stage 4.1: Canary (10% traffic, Days 52-54)

```nginx
# Nginx configuration for canary rollout
map $request_id $csp_header {
    ~^[0-9]$ "Content-Security-Policy"; # 10% traffic (last digit 0-9, use 0 only)
    default "Content-Security-Policy-Report-Only";
}

add_header $csp_header "default-src 'none'; script-src...";
```

**Monitoring:**
- Error rate per route
- User-reported issues (support tickets)
- CSP violation reports

**Success Criteria:**
- ✅ Error rate unchanged (<0.1% baseline deviation)
- ✅ No CSP-related support tickets
- ✅ Violation count <20/day (same as report-only baseline)

**Rollback:** Revert to report-only if error rate increases >0.5%

---

#### Stage 4.2: Expanded Rollout (50% traffic, Days 55-58)

```nginx
map $request_id $csp_header {
    ~^[0-4]$ "Content-Security-Policy"; # 50% traffic
    default "Content-Security-Policy-Report-Only";
}
```

**Monitoring:** Same as Stage 4.1

**Success Criteria:**
- ✅ Error rate stable
- ✅ <3 CSP-related support tickets
- ✅ No security incidents

---

#### Stage 4.3: Full Rollout (100% traffic, Days 59-65)

```nginx
add_header Content-Security-Policy "default-src 'none'; script-src...";
# Remove report-only header
```

**Monitoring:** Continue monitoring for 7 days post-rollout

**Success Criteria:**
- ✅ No error rate increase
- ✅ No CSP-related incidents
- ✅ CSP violations <50/day (expected noise from browser extensions, etc.)

**Timeline:** 14 days  
**Owner:** DevOps + Security Team

---

### Post-Deployment (Day 66+)

**Ongoing Activities:**
1. **Monthly CSP review:** Analyze violation trends, adjust policy if needed
2. **Quarterly security audit:** Verify CSP compliance, check for new XSS vectors
3. **New feature onboarding:** CSP checklist for all new frontend code
4. **Incident response:** CSP violation spike = potential XSS attack (investigate)

---

## Compliance and Standards

### OWASP ASVS Compliance

| ASVS ID | Requirement | CSP Implementation |
|---------|-------------|-------------------|
| **V14.4.3** | Verify that a CSP is in place that helps mitigate impact of XSS attacks | ✅ Strict CSP with no unsafe-inline/eval |
| **V14.4.4** | Verify that all responses contain X-Content-Type-Options: nosniff | ✅ Applied globally |
| **V14.4.5** | Verify that HTTP Strict Transport Security headers are included | ✅ HSTS with 1-year max-age |
| **V14.4.6** | Verify that a suitable "Referrer-Policy" header is included | ⚠️ TODO: Add `Referrer-Policy: strict-origin-when-cross-origin` |
| **V14.4.7** | Verify that a suitable X-Frame-Options or CSP frame-ancestors directive is in use | ✅ Both X-Frame-Options: DENY and frame-ancestors 'none' |

---

### OWASP Top 10 Mitigation

| Threat | CSP Mitigation |
|--------|----------------|
| **A03:2021 Injection (XSS)** | `script-src 'nonce-{RANDOM}'` prevents inline script injection |
| **A05:2021 Security Misconfiguration** | Strict CSP eliminates default-allow misconfigurations |
| **A07:2021 Identification and Authentication Failures** | `frame-ancestors 'none'` prevents clickjacking credential theft |

---

### PCI DSS Compliance

**Requirement 6.5.7:** Address common coding vulnerabilities (XSS)

- ✅ CSP provides defense-in-depth against XSS (complements input validation)
- ✅ Report-only mode enables auditing without disrupting payment flows
- ✅ Violation monitoring provides audit trail for compliance reviews

---

## References

### Standards and Specifications

- [CSP Level 3 (W3C)](https://www.w3.org/TR/CSP3/)
- [Content Security Policy Cheat Sheet (OWASP)](https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html)
- [HTTP Strict Transport Security (RFC 6797)](https://tools.ietf.org/html/rfc6797)
- [OWASP Application Security Verification Standard (ASVS) v4.0.3](https://owasp.org/www-project-application-security-verification-standard/)

### Tools and Testing

- [CSP Evaluator (Google)](https://csp-evaluator.withgoogle.com/)
- [Mozilla Observatory](https://observatory.mozilla.org/)
- [SecurityHeaders.com](https://securityheaders.com/)
- [Report URI CSP Builder](https://report-uri.com/home/generate)

### Related Documentation

- [Input Validation Baseline](./input-validation-baseline.md)
- [STRIDE Threat Models](./threat-models/README.md)
- [Authentication Threat Model](./threat-models/auth-threat-model.yaml)

---

## Document Maintenance

**Review Schedule:** Quarterly  
**Next Review Date:** 2026-09-23  
**Review Owner:** Security Team

**Change Log:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-23 | Security Team | Initial specification (Issue #856) |

---

## Appendix A: Quick Reference CSP Headers

### Production (Enforcing)

```
Content-Security-Policy: default-src 'none'; script-src 'self' 'nonce-{RANDOM}' https://cdn.homedir.io; style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' https://cdn.homedir.io https://avatars.githubusercontent.com data:; connect-src 'self' https://api.homedir.io; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; upgrade-insecure-requests; block-all-mixed-content
```

### Report-Only (Monitoring)

```
Content-Security-Policy-Report-Only: default-src 'none'; script-src 'self' 'nonce-{RANDOM}' https://cdn.homedir.io; style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' https://cdn.homedir.io https://avatars.githubusercontent.com data:; connect-src 'self' https://api.homedir.io; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; upgrade-insecure-requests; block-all-mixed-content; report-uri /api/csp-reports; report-to csp-endpoint
```

### API Endpoints

```
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
```

---

## Appendix B: Additional Security Headers

### Recommended Headers (Not CSP)

```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), microphone=(), camera=()
```

**Rationale:**
- **Referrer-Policy:** Prevents leaking sensitive URL parameters in Referer header
- **Permissions-Policy:** Disables unnecessary browser features (location, mic, camera)

---

**End of Document**
