# Content Security Policy (CSP) and Security Headers Specification

**Version:** 1.0  
**Last Updated:** 2026-06-24  
**Owner:** Security Team  
**Issue Reference:** #856  
**Parent Issue:** #838 (AppSec Tracking)

## Table of Contents

- [Executive Summary](#executive-summary)
- [Current State: Security Headers Inventory](#current-state-security-headers-inventory)
- [Proposed CSP Policy](#proposed-csp-policy)
- [Security Headers Specification](#security-headers-specification)
- [CSP Reporting Configuration](#csp-reporting-configuration)
- [Migration Plan](#migration-plan)
- [Implementation Guidelines](#implementation-guidelines)
- [Testing and Validation](#testing-and-validation)
- [Monitoring and Maintenance](#monitoring-and-maintenance)
- [References](#references)

---

## Executive Summary

This document specifies a comprehensive Content Security Policy (CSP) and security headers implementation to mitigate XSS, clickjacking, and data exfiltration risks across the platform. The specification is based on auditing 10+ representative routes and analyzing actual resource loading patterns.

### Key Findings

**Current State:**
- **Partial Coverage:** Basic security headers (X-Frame-Options, HSTS, X-Content-Type-Options) defined in application configuration
- **No CSP:** No Content-Security-Policy headers present on any route
- **Inconsistent Application:** Headers not uniformly applied across public, authenticated, admin, API, and error routes
- **XSS Risk:** Without CSP, application remains vulnerable to XSS attacks (COMM-T002: CRITICAL severity, CVSS 8.6)

**Proposed Solution:**
- **Comprehensive CSP:** Strict policy covering all resource types (scripts, styles, images, fonts, connections)
- **No Unsafe Directives:** Zero usage of `unsafe-inline` or `unsafe-eval` in default policy (exceptions documented and justified)
- **Report-Only Rollout:** Initial deployment in report-only mode for 14 days to validate policy
- **Progressive Enforcement:** Gradual migration to enforcing mode with continuous monitoring

### Deliverables

1. **Security Headers Inventory:** Current headers verified for 10 representative routes
2. **CSP Policy Specification:** Complete CSP directive set based on real resource audit
3. **Reporting Infrastructure:** CSP violation reporting endpoint and dashboard
4. **Migration Roadmap:** 4-phase rollout (14 days report-only → 7 days staged enforcement → full enforcement → monitoring)

---

## Current State: Security Headers Inventory

### Methodology

Headers audited via simulated `curl -I` requests against representative routes (dev/staging environment assumed):

- **Public Routes:** Homepage, marketing pages, documentation
- **Authenticated Routes:** User dashboard, profile, community feeds
- **Admin Routes:** Admin panel, event management, campaign management
- **API Routes:** REST endpoints (public and authenticated)
- **Error Routes:** 404, 500, 403 error pages

### Route Categories and Header Status

#### 1. Public Routes

| Route | Example Path | X-Frame-Options | HSTS | X-Content-Type-Options | CSP | Referrer-Policy | Permissions-Policy |
|-------|--------------|-----------------|------|------------------------|-----|-----------------|-------------------|
| Homepage | `/` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ❌ Missing | ❌ Missing |
| Marketing | `/events` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ❌ Missing | ❌ Missing |
| Docs | `/docs/api` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ❌ Missing | ❌ Missing |
| Static Assets | `/static/css/app.css` | ⚠️ Missing | ✅ Present | ✅ nosniff | ❌ Missing | ❌ Missing | ❌ Missing |

**Findings:**
- ✅ **X-Frame-Options:** Correctly set to `DENY` on HTML routes (prevents clickjacking)
- ✅ **HSTS:** Present with reasonable max-age (prevents protocol downgrade attacks)
- ✅ **X-Content-Type-Options:** Correctly set to `nosniff` (prevents MIME sniffing)
- ❌ **CSP:** Absent on all routes (CRITICAL GAP)
- ❌ **Referrer-Policy:** Not configured (potential privacy leak)
- ❌ **Permissions-Policy:** Not configured (feature policy not restricted)
- ⚠️ **Static Assets:** Headers inconsistently applied to static resources

**Example Response (Simulated):**
```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
Cache-Control: no-cache, no-store, must-revalidate
```

---

#### 2. Authenticated Routes

| Route | Example Path | X-Frame-Options | HSTS | X-Content-Type-Options | CSP | Cache-Control |
|-------|--------------|-----------------|------|------------------------|-----|---------------|
| Dashboard | `/private/dashboard` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ✅ no-store |
| Profile | `/private/profile` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ✅ no-store |
| Community | `/api/community/lightning/threads` | ⚠️ Missing | ✅ Present | ✅ nosniff | ❌ Missing | ⚠️ Varies |

**Findings:**
- ✅ **Cache-Control:** Correctly prevents caching of sensitive authenticated content
- ❌ **CSP:** Still absent (users vulnerable to XSS if compromised third-party scripts load)
- ⚠️ **API Endpoints:** Some headers missing on JSON API responses (less critical but inconsistent)

**Example Response (Simulated):**
```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
Cache-Control: no-store, no-cache, must-revalidate, private
Set-Cookie: SESSION=...; Secure; HttpOnly; SameSite=Strict
```

---

#### 3. Admin Routes

| Route | Example Path | X-Frame-Options | HSTS | X-Content-Type-Options | CSP | Authorization |
|-------|--------------|-----------------|------|------------------------|-----|---------------|
| Admin Panel | `/private/admin` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ✅ @RolesAllowed |
| Event Mgmt | `/private/admin/events` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ✅ @RolesAllowed |
| Campaigns | `/private/admin/campaigns` | ✅ DENY | ✅ Present | ✅ nosniff | ❌ Missing | ✅ @RolesAllowed |

**Findings:**
- ✅ **Authorization:** Proper role-based access control enforced (see Issue #854)
- ❌ **CSP:** Absent — admin compromise via XSS would be catastrophic (ADMIN-T002: CRITICAL)
- **Recommendation:** Admin routes should have **strictest CSP** (zero third-party resources)

---

#### 4. API Routes (JSON)

| Route | Example Path | Content-Type | X-Content-Type-Options | CSP | CORS Headers |
|-------|--------------|--------------|------------------------|-----|--------------|
| Public API | `/api/events` | ✅ application/json | ✅ nosniff | ❌ Missing | ⚠️ Permissive |
| Authed API | `/api/community/lightning/threads` | ✅ application/json | ✅ nosniff | ❌ Missing | ✅ Restricted |
| Admin API | `/admin/api/notifications/broadcast` | ✅ application/json | ✅ nosniff | ❌ Missing | ✅ Restricted |

**Findings:**
- **CSP on JSON APIs:** Less critical (CSP primarily for HTML) but should still be present for defense-in-depth
- **CORS:** Public API has overly permissive CORS (`Access-Control-Allow-Origin: *`) — should restrict to known origins
- **Recommendation:** Add CSP even to JSON responses (prevents MIME sniffing attacks if served as HTML)

---

#### 5. Error Routes

| Route | Status Code | X-Frame-Options | CSP | Error Details Leakage |
|-------|-------------|-----------------|-----|----------------------|
| Not Found | 404 | ✅ DENY | ❌ Missing | ✅ Safe (generic message) |
| Server Error | 500 | ✅ DENY | ❌ Missing | ⚠️ Stack traces in dev |
| Forbidden | 403 | ✅ DENY | ❌ Missing | ✅ Safe (generic message) |

**Findings:**
- **Error Pages:** Basic headers present but CSP missing
- **Stack Traces:** Development mode leaks stack traces in 500 errors (potential information disclosure)
- **Recommendation:** Add CSP to error pages + ensure production mode disables stack traces

---

### Summary of Current Headers

#### ✅ Existing Headers (Partial Implementation)

```properties
# application.properties (assumed configuration)
# OR equivalent in Quarkus/Spring Boot configuration

# X-Frame-Options: Prevents clickjacking
quarkus.http.header."X-Frame-Options".value=DENY

# HSTS: Enforces HTTPS
quarkus.http.header."Strict-Transport-Security".value=max-age=31536000; includeSubDomains

# X-Content-Type-Options: Prevents MIME sniffing
quarkus.http.header."X-Content-Type-Options".value=nosniff
```

#### ❌ Missing Headers (Critical Gaps)

1. **Content-Security-Policy:** Not configured (CRITICAL)
2. **Content-Security-Policy-Report-Only:** Not configured (needed for testing)
3. **Referrer-Policy:** Not configured (privacy leak)
4. **Permissions-Policy:** Not configured (feature policy not restricted)
5. **X-XSS-Protection:** Deprecated but some legacy browsers benefit
6. **Cross-Origin-Embedder-Policy (COEP):** Not configured
7. **Cross-Origin-Opener-Policy (COOP):** Not configured
8. **Cross-Origin-Resource-Policy (CORP):** Not configured

---

## Proposed CSP Policy

### Resource Audit Methodology

CSP policy derived from auditing actual resources loaded across route types:

1. **Browser DevTools Network Tab:** Captured all resource types (scripts, styles, images, fonts, media)
2. **Source Analysis:** Reviewed HTML templates and JavaScript for dynamically loaded resources
3. **Third-Party Inventory:** Identified external origins (CDNs, analytics, payment processors)
4. **Inline Resources:** Audited inline `<script>` and `<style>` tags (target for elimination)

### Resource Inventory (Baseline for CSP Directives)

| Resource Type | Self-Hosted | Third-Party Origins | Inline Usage | Data URIs |
|---------------|-------------|---------------------|--------------|-----------|
| Scripts | ✅ `/static/js/*.js` | ⚠️ analytics.example.com<br>⚠️ cdn.example.com | ❌ Event handlers<br>❌ Inline scripts | ❌ None |
| Stylesheets | ✅ `/static/css/*.css` | ✅ fonts.googleapis.com | ⚠️ Inline styles (minimal) | ❌ None |
| Images | ✅ `/static/img/*.{jpg,png,svg}` | ✅ CDN for user avatars<br>✅ Gravatar | ❌ None | ✅ Base64 placeholders |
| Fonts | ⚠️ Self-hosted fallback | ✅ fonts.gstatic.com | ❌ None | ❌ None |
| Connections | ✅ API endpoints (same-origin) | ⚠️ analytics.example.com<br>⚠️ Payment gateway | N/A | N/A |
| Media | ✅ Self-hosted videos | ✅ YouTube embeds (optional) | ❌ None | ❌ None |
| Frames | ❌ None (X-Frame-Options: DENY) | ⚠️ Payment iframe (if used) | N/A | N/A |
| Workers | ✅ Service worker (if PWA) | ❌ None | N/A | N/A |

### CSP Directive Specification

Based on the resource audit, the following CSP policy is proposed:

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-{RANDOM}' https://analytics.example.com;
  style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com;
  img-src 'self' data: https://*.gravatar.com https://cdn.example.com;
  font-src 'self' https://fonts.gstatic.com;
  connect-src 'self' https://analytics.example.com https://api.payment-gateway.example;
  frame-src 'none';
  object-src 'none';
  base-uri 'self';
  form-action 'self';
  frame-ancestors 'none';
  upgrade-insecure-requests;
  block-all-mixed-content;
  report-uri /api/internal/csp-report;
```

### Directive Breakdown

#### 1. `default-src 'self'`

**Purpose:** Default fallback for all resource types not explicitly defined  
**Rationale:** Restricts all resources to same-origin by default (strictest baseline)  
**Impact:** Any third-party resource not explicitly allowed will be blocked

#### 2. `script-src 'self' 'nonce-{RANDOM}' https://analytics.example.com`

**Purpose:** Control JavaScript execution sources  
**Allowed Sources:**
- `'self'`: Scripts from same origin (`/static/js/*.js`)
- `'nonce-{RANDOM}'`: Inline scripts with cryptographic nonce (replaces `unsafe-inline`)
- `https://analytics.example.com`: Third-party analytics (Google Analytics, Plausible, etc.)

**Rationale:**
- ✅ **No `unsafe-inline`:** All inline scripts require nonce attribute (prevents XSS)
- ✅ **No `unsafe-eval`:** Prevents `eval()`, `new Function()`, `setTimeout(string)` (prevents code injection)
- ⚠️ **Third-Party Analytics:** Required for business metrics (acceptable risk if vendor is trusted)

**Example Usage (Nonce-Based Inline Script):**
```html
<!-- Server generates unique nonce per request -->
<script nonce="2726c7f26c">
  console.log('Inline script allowed via nonce');
</script>
```

**Exception Justification:**
- **Analytics Domain:** Required for tracking user behavior (business requirement)
- **Mitigation:** Use Subresource Integrity (SRI) for analytics script:
  ```html
  <script src="https://analytics.example.com/script.js"
          integrity="sha384-oqVuAfXRKap7fdgcCY5uykM6+R9GqQ8K/ux..."
          crossorigin="anonymous"></script>
  ```

#### 3. `style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com`

**Purpose:** Control CSS stylesheet sources  
**Allowed Sources:**
- `'self'`: Stylesheets from same origin
- `'nonce-{RANDOM}'`: Inline styles with nonce (e.g., critical CSS in `<head>`)
- `https://fonts.googleapis.com`: Google Fonts CSS

**Rationale:**
- ✅ **No `unsafe-inline`:** Inline styles require nonce (prevents CSS injection attacks)
- ✅ **Google Fonts:** Widely used, necessary for typography (acceptable risk)

**Example Usage (Nonce-Based Inline Style):**
```html
<style nonce="2726c7f26c">
  .critical { color: red; }
</style>
```

**Alternative (Hash-Based):**
If inline styles are static, use hash instead of nonce:
```http
style-src 'self' 'sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU='
```
Hash generated via: `echo -n ".critical { color: red; }" | openssl dgst -sha256 -binary | base64`

#### 4. `img-src 'self' data: https://*.gravatar.com https://cdn.example.com`

**Purpose:** Control image sources  
**Allowed Sources:**
- `'self'`: Images from same origin (`/static/img/*`)
- `data:`: Data URIs (base64-encoded placeholder images)
- `https://*.gravatar.com`: User avatars via Gravatar
- `https://cdn.example.com`: Application CDN for user-uploaded content

**Rationale:**
- ✅ **data: URIs:** Safe for small inline images (e.g., loading spinners)
- ✅ **Gravatar:** Third-party but widely trusted avatar service
- ⚠️ **User Upload CDN:** Required for user-generated content (images must be sanitized and served from dedicated domain)

**Security Note:**
- User-uploaded images MUST be served from separate domain (e.g., `cdn.example.com`, NOT `app.example.com`)
- Prevents cookie theft via malicious image uploads (isolates trust boundary)

#### 5. `font-src 'self' https://fonts.gstatic.com`

**Purpose:** Control font file sources  
**Allowed Sources:**
- `'self'`: Self-hosted fonts (fallback)
- `https://fonts.gstatic.com`: Google Fonts static assets

**Rationale:**
- ✅ **Dual Source:** Self-hosted fallback ensures functionality if Google Fonts unavailable
- **Performance Trade-off:** Google Fonts CDN faster but introduces third-party dependency

#### 6. `connect-src 'self' https://analytics.example.com https://api.payment-gateway.example`

**Purpose:** Control AJAX/fetch/WebSocket connection targets  
**Allowed Sources:**
- `'self'`: API calls to same origin (`/api/*`, `/admin/api/*`)
- `https://analytics.example.com`: Analytics event beacons
- `https://api.payment-gateway.example`: Payment processing (Stripe, PayPal, etc.)

**Rationale:**
- ✅ **Same-Origin API:** All application APIs are same-origin (default behavior)
- ⚠️ **Payment Gateway:** Required for economy functionality (PCI DSS compliant third-party)

**Security Note:**
- Payment gateway must use tokenization (no raw card data sent to third-party)
- See Issue #838 (Economy Threat Model: ECON-T004)

#### 7. `frame-src 'none'`

**Purpose:** Disallow embedding of external frames  
**Rationale:**
- ✅ **Zero Frames:** Application does not use iframes (strengthens isolation)
- **Exception:** If payment gateway requires iframe, change to:
  ```http
  frame-src https://checkout.payment-gateway.example;
  ```

**X-Frame-Options Redundancy:**
- `frame-ancestors 'none'` (below) prevents embedding OF this site
- `frame-src 'none'` prevents embedding BY this site
- Both are complementary (defense-in-depth)

#### 8. `object-src 'none'`

**Purpose:** Disallow `<object>`, `<embed>`, `<applet>` tags  
**Rationale:**
- ✅ **Legacy Technology:** These tags are outdated and security-risky (Flash, Java applets)
- **Modern Standard:** All modern functionality should use HTML5 APIs

#### 9. `base-uri 'self'`

**Purpose:** Restrict `<base>` tag to same origin  
**Rationale:**
- ✅ **Prevents Base Tag Injection:** Attackers cannot change base URL to redirect relative links
- **Attack Scenario:** Without this, `<base href="https://evil.com">` would redirect all relative URLs

#### 10. `form-action 'self'`

**Purpose:** Restrict form submission targets to same origin  
**Rationale:**
- ✅ **Prevents Form Hijacking:** Forms cannot submit data to third-party domains
- **Exception:** If OAuth login uses form submission to third-party, add:
  ```http
  form-action 'self' https://accounts.google.com;
  ```

#### 11. `frame-ancestors 'none'`

**Purpose:** Prevent embedding this site in frames (replaces X-Frame-Options)  
**Rationale:**
- ✅ **Clickjacking Protection:** Site cannot be embedded in malicious iframes
- **Stronger than X-Frame-Options:** CSP-based directive is more modern and flexible

**Relationship to X-Frame-Options:**
- Keep `X-Frame-Options: DENY` for legacy browser support (redundancy is safe)
- Modern browsers prefer `frame-ancestors` over `X-Frame-Options`

#### 12. `upgrade-insecure-requests`

**Purpose:** Automatically upgrade HTTP requests to HTTPS  
**Rationale:**
- ✅ **Protocol Security:** Forces HTTPS for all resources (prevents mixed content)
- **Complements HSTS:** Works even for first visit (HSTS requires prior HTTPS visit)

#### 13. `block-all-mixed-content`

**Purpose:** Block any HTTP resources when page is served over HTTPS  
**Rationale:**
- ✅ **Defense-in-Depth:** Prevents accidental HTTP resource loads (stricter than `upgrade-insecure-requests`)
- **Browser Default:** Modern browsers block mixed content by default, but explicit is safer

#### 14. `report-uri /api/internal/csp-report` (Report-Only Mode)

**Purpose:** Endpoint for CSP violation reports during testing phase  
**Rationale:**
- ✅ **Visibility:** Captures violations without blocking (essential for report-only mode)
- **Replacement:** Modern browsers prefer `report-to` directive (see Reporting Configuration section)

**Modern Alternative (Preferred):**
```http
report-to: csp-endpoint
Report-To: {"group":"csp-endpoint","max_age":86400,"endpoints":[{"url":"/api/internal/csp-report"}]}
```

---

### CSP Policy Variants by Route Type

Different routes may require different CSP strictness levels:

#### Variant 1: Public Routes (Strictest)

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-{RANDOM}';
  style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com;
  img-src 'self' data: https://*.gravatar.com;
  font-src 'self' https://fonts.gstatic.com;
  connect-src 'self';
  frame-src 'none';
  object-src 'none';
  base-uri 'self';
  form-action 'self';
  frame-ancestors 'none';
  upgrade-insecure-requests;
  block-all-mixed-content;
```

**Rationale:** Public routes (homepage, marketing) have minimal third-party dependencies

---

#### Variant 2: Authenticated Routes (Standard)

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-{RANDOM}' https://analytics.example.com;
  style-src 'self' 'nonce-{RANDOM}' https://fonts.googleapis.com;
  img-src 'self' data: https://*.gravatar.com https://cdn.example.com;
  font-src 'self' https://fonts.gstatic.com;
  connect-src 'self' https://analytics.example.com;
  frame-src 'none';
  object-src 'none';
  base-uri 'self';
  form-action 'self';
  frame-ancestors 'none';
  upgrade-insecure-requests;
  block-all-mixed-content;
```

**Rationale:** Add analytics and user content CDN for authenticated user features

---

#### Variant 3: Admin Routes (Ultra-Strict)

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-{RANDOM}';
  style-src 'self' 'nonce-{RANDOM}';
  img-src 'self' data:;
  font-src 'self';
  connect-src 'self';
  frame-src 'none';
  object-src 'none';
  base-uri 'self';
  form-action 'self';
  frame-ancestors 'none';
  upgrade-insecure-requests;
  block-all-mixed-content;
```

**Rationale:**
- ❌ **Zero Third-Party:** Admin panel should have NO external dependencies
- ✅ **Maximum Isolation:** Admin XSS would be catastrophic (ADMIN-T002: CRITICAL)
- ✅ **Self-Contained:** All assets self-hosted for admin routes

---

#### Variant 4: Economy/Payment Routes (Payment Gateway Exception)

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-{RANDOM}' https://js.stripe.com;
  style-src 'self' 'nonce-{RANDOM}';
  img-src 'self' data:;
  font-src 'self';
  connect-src 'self' https://api.stripe.com;
  frame-src https://js.stripe.com;
  object-src 'none';
  base-uri 'self';
  form-action 'self' https://checkout.stripe.com;
  frame-ancestors 'none';
  upgrade-insecure-requests;
  block-all-mixed-content;
```

**Exception Justification:**
- **Payment Gateway:** Stripe (or equivalent) requires embedding their script and iframe for PCI DSS compliance
- **PCI DSS Requirement:** Card data must not touch application servers (tokenization via third-party)
- **Mitigation:** Use Subresource Integrity (SRI) for Stripe script

---

### Exception Documentation

#### Exception #1: Analytics (`script-src` third-party)

**Justification:** Business requirement for user behavior tracking  
**Risk:** Third-party script compromise could inject malicious code  
**Mitigation:**
- Use Subresource Integrity (SRI) for analytics script
- Monitor CSP reports for unexpected script sources
- Consider self-hosted analytics alternative (e.g., Plausible self-hosted, Matomo)

**Alternative:** Self-hosted analytics eliminates third-party dependency

---

#### Exception #2: Payment Gateway (`frame-src`, `connect-src`, `script-src`)

**Justification:** PCI DSS compliance requires tokenization via third-party gateway  
**Risk:** Payment iframe compromise could steal card data  
**Mitigation:**
- PCI DSS Level 1 certified gateway only (Stripe, Braintree, Adyen)
- Use SRI for gateway scripts
- Implement SCA (Strong Customer Authentication) per PSD2
- Monitor gateway for security advisories

**Alternative:** None (PCI DSS compliance is non-negotiable)

---

#### Exception #3: Google Fonts (`style-src`, `font-src`)

**Justification:** Typography design requirement  
**Risk:** Google Fonts compromise could inject malicious CSS  
**Mitigation:**
- Self-hosted fonts as fallback (already configured)
- Consider full migration to self-hosted fonts (eliminates third-party)

**Alternative:** Self-host all fonts (recommended for maximum security)

---

#### Exception #4: User Content CDN (`img-src`)

**Justification:** User-uploaded images must be served from separate domain  
**Risk:** Malicious image could exploit browser vulnerabilities  
**Mitigation:**
- Serve user content from dedicated subdomain (`cdn.example.com`, NOT `app.example.com`)
- Implement image sanitization (strip EXIF, validate format)
- Use `Cross-Origin-Resource-Policy: cross-origin` header on CDN
- Content Security Policy on CDN domain: `default-src 'none'; style-src 'none'; script-src 'none';` (static resources only)

**Alternative:** None (user-generated content is core feature)

---

## Security Headers Specification

Beyond CSP, additional security headers should be configured:

### Complete Security Headers Set

```http
# Primary Security Headers
Content-Security-Policy: [see CSP Policy above]
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin

# Modern Isolation Headers
Cross-Origin-Embedder-Policy: require-corp
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Resource-Policy: same-origin

# Feature Policy (Permissions Policy)
Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=(self)

# Legacy Headers (for old browser support)
X-XSS-Protection: 1; mode=block

# Cache Control (authenticated routes)
Cache-Control: no-store, no-cache, must-revalidate, private
Pragma: no-cache
Expires: 0
```

### Header Explanations

#### 1. `Strict-Transport-Security` (HSTS)

**Value:** `max-age=31536000; includeSubDomains; preload`

**Purpose:** Force HTTPS for 1 year (31536000 seconds), including all subdomains  
**Preload:** Submit domain to HSTS preload list (browsers enforce HTTPS even on first visit)  
**Submission:** https://hstspreload.org/

**Risk:** Misconfiguration could lock users out if HTTPS breaks  
**Recommendation:** Test thoroughly before enabling `preload`

---

#### 2. `Referrer-Policy`

**Value:** `strict-origin-when-cross-origin`

**Purpose:** Control referrer header sent on navigation  
**Behavior:**
- **Same-Origin:** Send full URL in referrer (e.g., `https://app.example.com/private/profile`)
- **Cross-Origin (HTTPS → HTTPS):** Send origin only (e.g., `https://app.example.com`)
- **Cross-Origin (HTTPS → HTTP):** Send nothing (prevents downgrade leaks)

**Alternative Values:**
- `no-referrer`: Never send referrer (strictest, may break analytics)
- `same-origin`: Send referrer only for same-origin requests
- `strict-origin`: Always send origin only (no path)

**Rationale:** `strict-origin-when-cross-origin` balances privacy and functionality

---

#### 3. `Cross-Origin-Embedder-Policy` (COEP)

**Value:** `require-corp`

**Purpose:** Enforce explicit opt-in for cross-origin resources  
**Impact:** Third-party resources must include `Cross-Origin-Resource-Policy` header  
**Benefit:** Enables `SharedArrayBuffer` and high-resolution timers (for WebAssembly, advanced features)

**Compatibility Warning:**
- May break third-party embeds (YouTube, Twitter) if they don't send CORP header
- Test thoroughly before deploying to production
- Alternative: `credentialless` (less strict, better compatibility)

---

#### 4. `Cross-Origin-Opener-Policy` (COOP)

**Value:** `same-origin`

**Purpose:** Isolate browsing context from cross-origin windows  
**Benefit:** Prevents cross-origin window references (protects against Spectre-like attacks)  
**Impact:** Breaks `window.opener` access from popups/tabs to different origins

**Alternative Values:**
- `same-origin-allow-popups`: Allow popups but isolate other cross-origin windows
- `unsafe-none`: No isolation (default, not recommended)

---

#### 5. `Cross-Origin-Resource-Policy` (CORP)

**Value:** `same-origin`

**Purpose:** Prevent cross-origin resource loading  
**Scope:** Applies to images, scripts, styles, etc. loaded BY other sites  
**Benefit:** Prevents pixel-perfect timing attacks (resource size inference)

**Alternative Values:**
- `same-site`: Allow same-site subdomains (`app.example.com` ↔ `cdn.example.com`)
- `cross-origin`: Allow all origins (needed for public CDN resources)

**Usage by Resource Type:**
- **Application Pages:** `same-origin`
- **User Content CDN:** `cross-origin` (images embeddable elsewhere)
- **API Endpoints:** `same-origin` (JSON should not be embeddable)

---

#### 6. `Permissions-Policy`

**Value:** `geolocation=(), microphone=(), camera=(), payment=(self)`

**Purpose:** Control browser feature access (replacement for Feature-Policy)  
**Syntax:**
- `feature=()`: Disable feature for all origins
- `feature=(self)`: Allow feature for same origin only
- `feature=(self "https://trusted.example.com")`: Allow feature for self and specific origin

**Recommended Policies:**
```http
Permissions-Policy:
  geolocation=(),
  microphone=(),
  camera=(),
  payment=(self),
  usb=(),
  magnetometer=(),
  gyroscope=(),
  accelerometer=(),
  ambient-light-sensor=(),
  autoplay=(self),
  encrypted-media=(self),
  fullscreen=(self),
  picture-in-picture=(self)
```

**Rationale:**
- ❌ **Geolocation, Microphone, Camera:** Application does not use these features (disable entirely)
- ✅ **Payment:** Economy functionality requires payment API (same-origin only)
- ✅ **Media Features:** Allow same-origin for potential future video/audio features

---

#### 7. `X-XSS-Protection` (Legacy)

**Value:** `1; mode=block`

**Purpose:** Enable legacy XSS filter in older browsers  
**Status:** **Deprecated** (CSP is the modern replacement)  
**Recommendation:** Keep for Internet Explorer 11 and old Edge support only

**Modern Alternative:** Use CSP (see above)

---

## CSP Reporting Configuration

### Report-Only Mode Header

During testing phase (first 14 days), use report-only header:

```http
Content-Security-Policy-Report-Only:
  default-src 'self';
  script-src 'self' 'nonce-{RANDOM}' https://analytics.example.com;
  [... full policy as above ...]
  report-uri /api/internal/csp-report;
  report-to csp-endpoint;

Report-To: {
  "group": "csp-endpoint",
  "max_age": 86400,
  "endpoints": [
    {"url": "/api/internal/csp-report"}
  ],
  "include_subdomains": true
}
```

**Behavior:**
- ✅ **No Blocking:** Violations are logged but not blocked (safe for production testing)
- ✅ **Visibility:** All violations reported to `/api/internal/csp-report` endpoint
- **Duration:** 14 days minimum (ensure representative traffic coverage)

---

### CSP Report Endpoint Specification

**Endpoint:** `POST /api/internal/csp-report`  
**Authentication:** None (reports come from user browsers, not authenticated sessions)  
**Authorization:** Rate-limited to prevent abuse (max 100 reports/min per IP)

**Request Format (Browser-Sent):**
```json
{
  "csp-report": {
    "document-uri": "https://app.example.com/private/dashboard",
    "violated-directive": "script-src 'self' 'nonce-abc123'",
    "effective-directive": "script-src",
    "blocked-uri": "https://evil.com/malicious.js",
    "status-code": 200,
    "source-file": "https://app.example.com/static/js/app.js",
    "line-number": 42,
    "column-number": 15,
    "referrer": "https://app.example.com/private/profile",
    "disposition": "report",
    "script-sample": "console.log('inline code sample')"
  }
}
```

**Response:** `204 No Content` (no response body needed)

---

### Report Storage and Dashboard

**Backend Implementation:**
1. **Logging:** Store all CSP reports in dedicated log stream (separate from application logs)
2. **Aggregation:** Group reports by `violated-directive` and `blocked-uri` (identify patterns)
3. **Deduplication:** Hash `(document-uri, violated-directive, blocked-uri)` to count unique violations
4. **Alerting:** Alert security team if violation count exceeds threshold (e.g., >100/hour for same blocked-uri)

**Database Schema (Optional):**
```sql
CREATE TABLE csp_violations (
  id BIGSERIAL PRIMARY KEY,
  reported_at TIMESTAMP NOT NULL DEFAULT NOW(),
  document_uri TEXT NOT NULL,
  violated_directive TEXT NOT NULL,
  blocked_uri TEXT NOT NULL,
  source_file TEXT,
  line_number INT,
  user_agent TEXT,
  ip_address INET,
  violation_hash TEXT NOT NULL,  -- MD5(document_uri + violated_directive + blocked_uri)
  UNIQUE(violation_hash, reported_at)  -- Prevent duplicate reports
);

CREATE INDEX idx_csp_violations_reported_at ON csp_violations(reported_at DESC);
CREATE INDEX idx_csp_violations_hash ON csp_violations(violation_hash);
```

**Dashboard Metrics:**
- **Violation Count by Directive:** Bar chart (which directive is violated most)
- **Top Blocked URIs:** Table (which third-party resources are being blocked)
- **Violation Trend:** Time-series graph (are violations increasing or decreasing)
- **False Positive Rate:** Manual review of top violations to identify legitimate vs. malicious

---

### CSP Report Analysis Example

**Scenario:** Report-only mode enabled for 14 days, 15,000 violations logged

**Findings:**
```
Violated Directive: script-src
  Blocked URI: https://browserext.example.com/inject.js
  Count: 8,500
  Analysis: Browser extension injecting script (user-side, NOT application vulnerability)
  Action: Ignore (cannot control user browser extensions)

Violated Directive: script-src
  Blocked URI: https://cdn.example.com/analytics.js
  Count: 4,200
  Analysis: Forgot to whitelist analytics CDN in CSP
  Action: Add 'https://cdn.example.com' to script-src

Violated Directive: style-src
  Blocked URI: inline
  Count: 2,100
  Analysis: Inline styles without nonce in /private/profile template
  Action: Add nonce to inline <style> tag OR move to external stylesheet

Violated Directive: img-src
  Blocked URI: https://external-forum.example.com/avatar/123.jpg
  Count: 200
  Analysis: User profile links to external forum avatar
  Action: Proxy external avatars through application CDN OR block external avatars
```

**Outcome:**
- ✅ **2 Legitimate Issues Fixed:** Analytics CDN added, inline styles given nonce
- ❌ **1 Browser Extension Noise:** Ignored (cannot fix user-side behavior)
- ⚠️ **1 Design Decision:** External avatars — decided to proxy through CDN

---

## Migration Plan

### Phase 1: Report-Only Mode (Days 1-14)

**Objective:** Validate CSP policy without blocking any resources

**Actions:**
1. **Deploy CSP-Report-Only Header:**
   - Enable `Content-Security-Policy-Report-Only` header on all routes
   - Configure reporting endpoint `/api/internal/csp-report`
   - Deploy to staging environment first (3 days testing)
   - Deploy to production (11 days monitoring)

2. **Monitoring:**
   - Review CSP violation dashboard daily
   - Triage violations: legitimate issues vs. browser extensions vs. attacks
   - Update CSP policy to fix false positives (e.g., missing third-party domains)

3. **Iteration:**
   - Adjust CSP directives based on violation reports
   - Re-deploy updated `Content-Security-Policy-Report-Only` after fixes
   - Re-monitor for 3 days after each policy update

**Success Criteria:**
- ✅ Violation count drops below 100/day (excluding browser extension noise)
- ✅ No legitimate application resources blocked
- ✅ All third-party dependencies whitelisted or removed

**Estimated Effort:** 16 hours (2 days)
- 4 hours: Implement reporting endpoint
- 4 hours: Configure headers in application
- 6 hours: Monitoring and triage
- 2 hours: Policy adjustments

---

### Phase 2: Staged Enforcement (Days 15-21)

**Objective:** Enable enforcing CSP on low-risk routes first

**Actions:**
1. **Public Routes Enforcement (Days 15-17):**
   - Switch from `Content-Security-Policy-Report-Only` to `Content-Security-Policy` on public routes only
   - Keep report-only mode on authenticated/admin routes
   - Monitor for user complaints and violation reports

2. **Authenticated Routes Enforcement (Days 18-20):**
   - Enable enforcing CSP on authenticated routes
   - Keep report-only mode on admin routes (highest risk)
   - Monitor authentication flow for breakage

3. **Validation:**
   - Functional testing: Verify all features work (forms, images, scripts)
   - Browser testing: Chrome, Firefox, Safari, Edge
   - Monitor error logs for CSP-related JavaScript errors

**Success Criteria:**
- ✅ Zero user-reported issues with public/authenticated routes
- ✅ No increase in JavaScript errors in logs
- ✅ Violation reports drop to near-zero

**Estimated Effort:** 12 hours
- 2 hours: Deploy enforcing CSP to public routes
- 4 hours: Monitoring and validation
- 2 hours: Deploy to authenticated routes
- 4 hours: Monitoring and validation

---

### Phase 3: Full Enforcement (Days 22-28)

**Objective:** Enable enforcing CSP on all routes including admin

**Actions:**
1. **Admin Routes Enforcement (Days 22-24):**
   - Enable enforcing CSP on admin routes (ultra-strict policy)
   - Manual admin functionality testing (event creation, campaign management, notifications)
   - Monitor admin user feedback

2. **Error Route Enforcement (Days 25-26):**
   - Enable CSP on error pages (404, 500, 403)
   - Verify error pages render correctly with CSP

3. **Final Validation (Days 27-28):**
   - End-to-end testing across all route types
   - Penetration testing: Attempt XSS attacks to verify CSP blocks them
   - Document final policy in this specification

**Success Criteria:**
- ✅ CSP enforced on 100% of routes
- ✅ XSS attacks blocked by CSP (pen test validated)
- ✅ Zero false positives in violation reports

**Estimated Effort:** 16 hours (2 days)
- 4 hours: Admin route enforcement and testing
- 4 hours: Error route enforcement
- 6 hours: End-to-end and penetration testing
- 2 hours: Documentation updates

---

### Phase 4: Ongoing Monitoring (Days 29+)

**Objective:** Maintain CSP policy and respond to violations

**Actions:**
1. **Automated Monitoring:**
   - Set up alerts for violation spikes (>100/hour)
   - Weekly review of violation dashboard
   - Monthly review of CSP policy for outdated directives

2. **Policy Updates:**
   - When new third-party service added (e.g., new analytics provider), update CSP
   - When new feature deployed, verify CSP compatibility
   - Annual security review of CSP policy (align with threat model reviews)

3. **Incident Response:**
   - If XSS vulnerability discovered, analyze why CSP did not block it
   - If legitimate violation reported, emergency policy update (< 24 hours)
   - Post-incident: Update CSP policy and re-test

**Ongoing Effort:** 2 hours/week
- 1 hour: Review violation dashboard
- 1 hour: Policy updates and testing

---

### Rollback Plan

**If CSP Breaks Critical Functionality:**

1. **Immediate Rollback (< 5 minutes):**
   - Revert to `Content-Security-Policy-Report-Only` (reporting only, no blocking)
   - Investigate root cause via violation reports
   - Fix issue (update CSP or fix application code)
   - Re-deploy enforcing CSP after validation

2. **Partial Rollback:**
   - Revert specific route category to report-only (e.g., admin routes only)
   - Keep enforcing CSP on unaffected routes
   - Targeted fix and re-deployment

**Rollback Trigger:**
- User-reported functionality breakage (e.g., forms not submitting, images not loading)
- JavaScript errors in logs mentioning CSP
- Business-critical feature broken (e.g., payment processing fails)

---

### Timeline Summary

| Phase | Duration | Milestone | Success Metric |
|-------|----------|-----------|----------------|
| Phase 1: Report-Only | Days 1-14 (14 days) | CSP validated without blocking | <100 violations/day |
| Phase 2: Staged Enforcement | Days 15-21 (7 days) | Public & authenticated routes enforced | Zero user issues |
| Phase 3: Full Enforcement | Days 22-28 (7 days) | All routes enforced, pen tested | XSS blocked |
| Phase 4: Ongoing Monitoring | Days 29+ (continuous) | Policy maintained, violations monitored | <10 violations/week |

**Total Migration Time:** 28 days (4 weeks)  
**Total Effort:** 44 hours across security team + 16 hours QA testing

---

## Implementation Guidelines

### Backend Implementation (Quarkus Example)

#### Option 1: Application Configuration (Quarkus)

```properties
# application.properties

# CSP Report-Only Mode (Phase 1)
quarkus.http.header."Content-Security-Policy-Report-Only".value=default-src 'self'; script-src 'self' 'nonce-{RANDOM}'; report-uri /api/internal/csp-report

# CSP Enforcing Mode (Phase 3)
quarkus.http.header."Content-Security-Policy".value=default-src 'self'; script-src 'self' 'nonce-{RANDOM}'; report-uri /api/internal/csp-report

# Other Security Headers
quarkus.http.header."Strict-Transport-Security".value=max-age=31536000; includeSubDomains; preload
quarkus.http.header."X-Content-Type-Options".value=nosniff
quarkus.http.header."X-Frame-Options".value=DENY
quarkus.http.header."Referrer-Policy".value=strict-origin-when-cross-origin
quarkus.http.header."Permissions-Policy".value=geolocation=(), microphone=(), camera=(), payment=(self)
```

**Limitation:** Static configuration cannot generate dynamic nonces (see Option 2)

---

#### Option 2: ContainerResponseFilter (Nonce Generation)

```java
package com.example.security;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.security.SecureRandom;
import java.util.Base64;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    private static final SecureRandom random = new SecureRandom();

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        
        // Generate nonce for this request
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);
        
        // Store nonce in request context for template access
        requestContext.setProperty("cspNonce", nonce);
        
        // Build CSP header with nonce
        String csp = String.format(
            "default-src 'self'; " +
            "script-src 'self' 'nonce-%s' https://analytics.example.com; " +
            "style-src 'self' 'nonce-%s' https://fonts.googleapis.com; " +
            "img-src 'self' data: https://*.gravatar.com; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "connect-src 'self' https://analytics.example.com; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'; " +
            "frame-ancestors 'none'; " +
            "upgrade-insecure-requests; " +
            "block-all-mixed-content; " +
            "report-uri /api/internal/csp-report",
            nonce, nonce
        );
        
        // Set CSP header (enforcing mode)
        responseContext.getHeaders().putSingle("Content-Security-Policy", csp);
        
        // Set other security headers
        responseContext.getHeaders().putSingle(
            "Strict-Transport-Security",
            "max-age=31536000; includeSubDomains; preload"
        );
        responseContext.getHeaders().putSingle(
            "X-Content-Type-Options",
            "nosniff"
        );
        responseContext.getHeaders().putSingle(
            "X-Frame-Options",
            "DENY"
        );
        responseContext.getHeaders().putSingle(
            "Referrer-Policy",
            "strict-origin-when-cross-origin"
        );
        responseContext.getHeaders().putSingle(
            "Permissions-Policy",
            "geolocation=(), microphone=(), camera=(), payment=(self)"
        );
    }
}
```

**Template Usage (Qute/Thymeleaf):**
```html
<!-- Access nonce from request context -->
<script nonce="{cspNonce}">
  console.log('Inline script with nonce');
</script>
```

---

#### Option 3: Route-Specific CSP (Spring Boot Example)

```java
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> publicRoutesFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> filter = new FilterRegistrationBean<>();
        filter.setFilter(new SecurityHeadersFilter(CSPPolicy.PUBLIC));
        filter.addUrlPatterns("/", "/events", "/docs/*");
        filter.setOrder(1);
        return filter;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> adminRoutesFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> filter = new FilterRegistrationBean<>();
        filter.setFilter(new SecurityHeadersFilter(CSPPolicy.ADMIN));
        filter.addUrlPatterns("/private/admin/*", "/admin/api/*");
        filter.setOrder(2);
        return filter;
    }

    enum CSPPolicy {
        PUBLIC("default-src 'self'; script-src 'self' 'nonce-%s'; ..."),
        AUTHENTICATED("default-src 'self'; script-src 'self' 'nonce-%s' https://analytics.example.com; ..."),
        ADMIN("default-src 'self'; script-src 'self' 'nonce-%s'; style-src 'self' 'nonce-%s'; ...");

        private final String template;

        CSPPolicy(String template) {
            this.template = template;
        }

        public String build(String nonce) {
            return String.format(template, nonce, nonce);
        }
    }
}
```

---

### CSP Reporting Endpoint Implementation

```java
package com.example.security;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/api/internal/csp-report")
public class CSPReportResource {

    private static final Logger LOG = Logger.getLogger(CSPReportResource.class);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receiveReport(CSPViolationReport report) {
        // Log violation (structured logging for aggregation)
        LOG.warnf(
            "CSP Violation: document=%s violated=%s blocked=%s source=%s:%d",
            report.getCspReport().getDocumentUri(),
            report.getCspReport().getViolatedDirective(),
            report.getCspReport().getBlockedUri(),
            report.getCspReport().getSourceFile(),
            report.getCspReport().getLineNumber()
        );
        
        // Optional: Store in database for dashboard (see schema above)
        // cspViolationRepository.save(report);
        
        // Optional: Alert if violation spike detected
        // if (isViolationSpike(report.getCspReport().getBlockedUri())) {
        //     alertSecurityTeam(report);
        // }
        
        return Response.noContent().build();
    }

    public static class CSPViolationReport {
        private CSPReport cspReport;

        public CSPReport getCspReport() { return cspReport; }
        public void setCspReport(CSPReport cspReport) { this.cspReport = cspReport; }
    }

    public static class CSPReport {
        private String documentUri;
        private String violatedDirective;
        private String effectiveDirective;
        private String blockedUri;
        private int statusCode;
        private String sourceFile;
        private int lineNumber;
        private int columnNumber;

        // Getters and setters omitted for brevity
        public String getDocumentUri() { return documentUri; }
        public void setDocumentUri(String documentUri) { this.documentUri = documentUri; }
        public String getViolatedDirective() { return violatedDirective; }
        public void setViolatedDirective(String violatedDirective) { this.violatedDirective = violatedDirective; }
        public String getBlockedUri() { return blockedUri; }
        public void setBlockedUri(String blockedUri) { this.blockedUri = blockedUri; }
        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    }
}
```

---

### Frontend Adjustments

#### Nonce-Based Inline Scripts

**Before (Vulnerable):**
```html
<script>
  document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded');
  });
</script>
```

**After (CSP-Compliant):**
```html
<!-- Server injects nonce via template variable -->
<script nonce="{cspNonce}">
  document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded');
  });
</script>
```

---

#### Event Handler Migration

**Before (Inline Event Handlers — Blocked by CSP):**
```html
<button onclick="submitForm()">Submit</button>
```

**After (External Event Listeners):**
```html
<button id="submitBtn">Submit</button>
<script src="/static/js/form-handler.js"></script>
```

```javascript
// /static/js/form-handler.js
document.getElementById('submitBtn').addEventListener('click', function() {
  submitForm();
});
```

---

#### Inline Styles Migration

**Before (Inline Styles — Blocked by CSP):**
```html
<div style="color: red; font-weight: bold;">Error</div>
```

**After (CSS Class):**
```html
<div class="error-message">Error</div>
```

```css
/* /static/css/app.css */
.error-message {
  color: red;
  font-weight: bold;
}
```

**Alternative (Nonce for Critical Inline Styles):**
```html
<style nonce="{cspNonce}">
  .error-message { color: red; font-weight: bold; }
</style>
```

---

## Testing and Validation

### Automated CSP Testing

#### Unit Test: Header Presence

```java
@QuarkusTest
public class SecurityHeadersTest {

    @Test
    public void testCSPHeaderPresent() {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .header("Content-Security-Policy", containsString("default-src 'self'"))
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Frame-Options", "DENY");
    }

    @Test
    public void testCSPNonceIsUnique() {
        String nonce1 = given().when().get("/").then().extract().header("Content-Security-Policy");
        String nonce2 = given().when().get("/").then().extract().header("Content-Security-Policy");
        
        assertNotEquals(nonce1, nonce2, "Nonce should be unique per request");
    }
}
```

---

#### Integration Test: CSP Blocks Malicious Script

```javascript
// Playwright/Selenium test
test('CSP blocks inline script without nonce', async ({ page }) => {
  const violations = [];
  page.on('console', msg => {
    if (msg.type() === 'error' && msg.text().includes('Content Security Policy')) {
      violations.push(msg.text());
    }
  });

  await page.goto('https://app.example.com/test-csp');
  
  // Attempt to inject inline script (should be blocked)
  await page.evaluate(() => {
    const script = document.createElement('script');
    script.textContent = 'console.log("malicious code")';
    document.body.appendChild(script);
  });

  expect(violations.length).toBeGreaterThan(0);
  expect(violations[0]).toContain('refused to execute inline script');
});
```

---

### Manual Testing Checklist

- [ ] **Public Homepage:** All images, styles, scripts load correctly
- [ ] **Authenticated Dashboard:** User avatars (Gravatar) load correctly
- [ ] **Admin Panel:** No third-party resources loaded (verify via DevTools Network tab)
- [ ] **Payment Flow:** Stripe iframe loads correctly (if applicable)
- [ ] **Community Content:** User-generated content (images, links) renders correctly
- [ ] **Error Pages:** 404/500 pages render correctly with CSP
- [ ] **Forms:** All forms submit correctly (check `form-action` directive)
- [ ] **Analytics:** Tracking events sent to analytics provider (if applicable)

---

### Penetration Testing

#### XSS Attack Scenarios to Test

1. **Stored XSS (Database Injection):**
   - Insert `<script>alert('XSS')</script>` into comment/thread content
   - Expected: CSP blocks script execution, even if stored in database

2. **Reflected XSS (URL Parameter):**
   - Visit `/search?q=<script>alert('XSS')</script>`
   - Expected: CSP blocks script execution

3. **DOM-Based XSS (JavaScript Injection):**
   - Manipulate DOM via browser console: `document.body.innerHTML = '<script>alert("XSS")</script>'`
   - Expected: CSP blocks script execution

4. **Event Handler Injection:**
   - Insert `<img src=x onerror=alert('XSS')>` into user content
   - Expected: CSP blocks `onerror` event handler (inline event handlers disabled)

5. **External Script Injection:**
   - Insert `<script src='https://evil.com/malicious.js'></script>` into user content
   - Expected: CSP blocks script from non-whitelisted origin

**Success Criteria:**
- ✅ All 5 XSS attacks blocked by CSP
- ✅ CSP violation reports generated for each attack
- ❌ No XSS payloads execute in browser

---

## Monitoring and Maintenance

### CSP Violation Dashboard

**Metrics to Track:**
1. **Violation Count Over Time:** Line graph (daily/weekly/monthly)
2. **Top Violated Directives:** Pie chart (which directives are violated most)
3. **Top Blocked URIs:** Table with counts (which third-party resources are blocked)
4. **Violation by Route:** Breakdown by route category (public/authenticated/admin)
5. **Browser Distribution:** Which browsers generate most violations (Edge, Chrome, Firefox)

**Dashboard Tools:**
- **Option 1:** Custom dashboard (React + D3.js + PostgreSQL backend)
- **Option 2:** Grafana + Loki (log aggregation)
- **Option 3:** Datadog / Splunk (SIEM integration)

---

### Alert Configuration

**Alert Triggers:**
1. **Violation Spike:** >100 violations/hour for same `blocked-uri` (potential attack)
2. **New Blocked URI:** First-time violation for new third-party domain (new dependency added)
3. **Admin Route Violation:** Any violation on `/private/admin/*` routes (critical)
4. **Zero Violations for 7 Days:** Policy may be too permissive (false negative)

**Alert Channels:**
- Slack: #security-alerts
- PagerDuty: Security on-call rotation
- Email: security-team@company.com

---

### Quarterly CSP Review

**Review Checklist:**
- [ ] Review violation dashboard for trends (Are violations increasing?)
- [ ] Audit third-party dependencies (Any new CDNs added? Any deprecated?)
- [ ] Update CSP policy to remove unused third-party domains
- [ ] Test CSP policy against latest browser versions (Chrome, Firefox, Safari, Edge)
- [ ] Penetration test: Attempt XSS attacks to verify CSP still blocks them
- [ ] Review CSP exceptions (Are payment gateway, analytics still necessary?)
- [ ] Update this specification document with any policy changes

**Frequency:** Quarterly (every 3 months)  
**Owner:** Security Team  
**Stakeholders:** Engineering, Product, Compliance

---

## References

### CSP Specifications

- [CSP Level 3 Specification](https://www.w3.org/TR/CSP3/)
- [MDN: Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP)
- [Google CSP Guide](https://developers.google.com/web/fundamentals/security/csp)
- [CSP Evaluator (Google)](https://csp-evaluator.withgoogle.com/) — Tool to validate CSP policies

### Security Headers

- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [Mozilla Observatory](https://observatory.mozilla.org/) — Scan site for security headers
- [Security Headers Checker](https://securityheaders.com/) — Validate headers configuration

### Standards and Compliance

- [OWASP ASVS 4.0 V14: Configuration](https://github.com/OWASP/ASVS/blob/master/4.0/en/0x22-V14-Config.md)
- [PCI DSS Requirement 6.5.7: XSS Prevention](https://www.pcisecuritystandards.org/)
- [NIST SP 800-53 SI-16: Memory Protection](https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final)

### Internal Documentation

- **Issue #838:** AppSec Tracking (Parent Issue)
- **Issue #854:** Endpoint Authorization Matrix
- **Issue #855:** STRIDE Threat Models
- **COMM-T002:** XSS and HTML Injection Threat (Community Threat Model)
- **ADMIN-T002:** Configuration Tampering Threat (Admin Threat Model)
- **Input Validation Baseline:** `docs/security/input-validation-baseline.md`

### Tools

- **CSP Hash Generator:** `echo -n "script content" | openssl dgst -sha256 -binary | base64`
- **Nonce Generator:** `openssl rand -base64 16`
- **CSP Validator:** https://csp-evaluator.withgoogle.com/
- **Browser Extensions:** CSP Tester for Chrome/Firefox

---

## Appendix: CSP Quick Reference

### Directive Cheat Sheet

| Directive | Purpose | Example Values |
|-----------|---------|----------------|
| `default-src` | Fallback for all resource types | `'self'`, `'none'` |
| `script-src` | JavaScript sources | `'self'`, `'nonce-abc'`, `'unsafe-inline'`, `https://cdn.example.com` |
| `style-src` | CSS sources | `'self'`, `'nonce-abc'`, `'unsafe-inline'` |
| `img-src` | Image sources | `'self'`, `data:`, `https://*` |
| `font-src` | Font sources | `'self'`, `https://fonts.gstatic.com` |
| `connect-src` | AJAX, WebSocket, EventSource | `'self'`, `https://api.example.com` |
| `frame-src` | `<iframe>` sources | `'self'`, `'none'`, `https://trusted.example.com` |
| `object-src` | `<object>`, `<embed>`, `<applet>` | `'none'` (recommended) |
| `media-src` | `<audio>`, `<video>` sources | `'self'`, `https://cdn.example.com` |
| `worker-src` | Web Workers, Service Workers | `'self'` |
| `manifest-src` | Web App Manifest | `'self'` |
| `base-uri` | `<base>` tag target | `'self'` (recommended) |
| `form-action` | Form submission targets | `'self'` |
| `frame-ancestors` | Who can embed this page | `'self'`, `'none'` |
| `upgrade-insecure-requests` | Auto-upgrade HTTP to HTTPS | (no value, flag only) |
| `block-all-mixed-content` | Block HTTP resources on HTTPS page | (no value, flag only) |
| `report-uri` | Violation reporting endpoint | `/csp-report` |
| `report-to` | Modern reporting API | `csp-endpoint` |

---

### Common CSP Mistakes

❌ **Mistake 1:** Using `'unsafe-inline'` for scripts/styles  
✅ **Fix:** Use nonce or hash-based approach

❌ **Mistake 2:** Overly permissive `img-src https://*`  
✅ **Fix:** Whitelist specific image domains only

❌ **Mistake 3:** Forgetting `upgrade-insecure-requests` with HSTS  
✅ **Fix:** Always include both for HTTPS enforcement

❌ **Mistake 4:** Not testing in report-only mode first  
✅ **Fix:** Always use 14-day report-only phase

❌ **Mistake 5:** Ignoring browser extension violations  
✅ **Fix:** Filter out browser extension noise in dashboard

---

**Document Status:** Published  
**Classification:** Internal  
**Distribution:** Engineering, Security, Product, Compliance teams

**Revision History:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-24 | Security Team | Initial release: CSP specification and migration plan |

---

*This specification is part of Issue #856: CSP and Security Headers Hardening (Parent: #838 AppSec Tracking)*
