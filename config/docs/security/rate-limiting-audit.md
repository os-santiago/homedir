# Rate Limiting Coverage Audit

**Document Version:** 1.0  
**Date:** 2026-06-24  
**Issue:** #859 - [AppSec] Revision integral de Rate Limiting: cobertura y umbrales  
**Auditor:** Automated security review  
**Source Code Reference:** `quarkus-app/src/main/java/com/scanales/homedir/security/RateLimitingFilter.java`

## Executive Summary

The Homedir application implements a **per-IP fixed-window rate limiter** via `RateLimitingFilter.java`. Current coverage protects authentication paths, logout, and general API routes with granular controls for community content endpoints. However, critical gaps exist:

- **6 critical unprotected endpoints** including OAuth callbacks and admin APIs
- **Inconsistent path-based protection** (protects `/api/*` but not `/admin/*`)
- **Missing specialized buckets** for high-risk operations (uploads, exports, bulk operations)
- **Generic thresholds** not calibrated to operation risk (120 req/60s for all non-auth APIs)

**Risk Assessment:** MEDIUM-HIGH. Unprotected OAuth callbacks and admin endpoints create vectors for credential stuffing, token exhaustion, and resource-based DoS attacks.

---

## 1. Current Rate Limiting Inventory

### 1.1 Active Buckets (RateLimitingFilter.java:42-89)

| Bucket Name | Path Pattern | Limit (req/window) | Window (seconds) | Algorithm | Source Code |
|-------------|--------------|-------------------|------------------|-----------|-------------|
| **auth** | `/login`, `/auth`, `/j_security_check` | 30 | 60 | Fixed Window | `RateLimitingFilter.java:42,68,233` |
| **logout** | `/logout` | 30 | 60 | Fixed Window | `RateLimitingFilter.java:43,72,230` |
| **api** | `/api/*`, `/private/*`, `/public/*` | 120 | 60 | Fixed Window | `RateLimitingFilter.java:76,243` |
| **api-community-content** | `/api/community/content*` (READ) | 1800 | 60 | Fixed Window + Adaptive | `RateLimitingFilter.java:84,236` |
| **api-community-content** | `/api/community/content*` (WRITE) | 600 | 60 | Fixed Window + Adaptive | `RateLimitingFilter.java:88,236` |

### 1.2 Configuration Parameters

| Parameter | Default | Description | Source |
|-----------|---------|-------------|--------|
| `rate.limit.enabled` | `true` | Global enable/disable | Line 60 |
| `rate.limit.window-seconds` | `60` | Default time window for all buckets | Line 64 |
| `rate.limit.auth.limit` | `30` | Login/auth endpoint threshold | Line 68 |
| `rate.limit.logout.limit` | `30` | Logout endpoint threshold | Line 72 |
| `rate.limit.api.limit` | `120` | Generic API endpoint threshold | Line 76 |
| `rate.limit.api.community-content.read.limit` | `1800` | Community content GET requests | Line 84 |
| `rate.limit.api.community-content.write.limit` | `600` | Community content POST/PUT/DELETE | Line 88 |
| `rate.limit.api.community-content.adaptive.enabled` | `true` | Enable adaptive limits for community content | Line 94 |
| `rate.limit.api.community-content.adaptive.per-fingerprint-bonus` | `120` | Bonus requests per unique fingerprint | Line 100 |
| `rate.limit.api.community-content.adaptive.max-fingerprints` | `20` | Maximum tracked fingerprints per IP | Line 106 |
| `rate.limit.api.community-content.adaptive.max-limit` | `2400` | Ceiling for adaptive limit escalation | Line 123 |
| `rate.limit.max-entries` | `10000` | Counter cache size before cleanup | Line 112 |
| `rate.limit.cleanup-ttl-minutes` | `5` | Stale counter TTL | Line 115 |

### 1.3 Excluded Paths (Never Rate Limited)

| Path Pattern | Reason | Source |
|--------------|--------|--------|
| `/`, `/health`, `/metrics` | Infrastructure/monitoring endpoints | Line 45 |
| `/css/*`, `/js/*`, `/images/*`, `/static/*`, `/img/*` | Static assets | Line 46 |
| `/ws/*` | WebSocket connections | Line 47 |
| SSE endpoints (Accept: `text/event-stream`) | Server-Sent Events | Line 221 |

### 1.4 Client Identification (RateLimitingFilter.java:249-263)

Rate limits are enforced per-IP using the first available header:
1. `X-Real-IP` (if trusted proxy enabled)
2. `X-Forwarded-For` (first IP in chain, if trusted proxy enabled)
3. `CF-Connecting-IP` (Cloudflare, if trusted proxy enabled)
4. Fallback: `"unknown"` (single global bucket if no proxy headers)

**Security Note:** Trusted proxy mode requires `rate.limit.trusted-proxies ≠ "none"`. In untrusted mode, all clients share the `"unknown"` key, making the limiter ineffective.

### 1.5 Adaptive Limiting (Community Content Only)

The `api-community-content` bucket implements **fingerprint-based adaptive limits** (lines 299-333):
- Base limit: 600-1800 req/60s (write/read)
- Fingerprint factors: session cookie + User-Agent + Accept-Language + Accept + HTTP method
- Bonus: +120 req/60s per unique fingerprint (max 20 fingerprints)
- Maximum effective limit: 2400 req/60s

**Rationale:** Legitimate content curators interact with diverse resources (different sessions/tools), while scrapers/bots reuse identical request patterns.

---

## 2. Endpoint Coverage Gap Analysis

### 2.1 Critical Gaps (Immediate Risk)

#### CRITICAL-1: OAuth/OIDC Callback Endpoints (Brute Force, Token Exhaustion)

| Endpoint | HTTP Method | Class | Security Annotation | Current Protection | Risk |
|----------|-------------|-------|---------------------|-------------------|------|
| `/login/callback` | GET | `OidcLoginCallbackResource` | `@PermitAll` | **NONE** | Session fixation, token replay attacks |
| `/auth/github/callback` | GET | `GithubAuthResource` | `@PermitAll` | **NONE** | OAuth state manipulation, code exhaustion |
| `/auth/discord/callback` | GET | `DiscordAuthResource` | `@PermitAll` | **NONE** | OAuth abuse, credential stuffing |
| `/auth/session` | GET/POST | `SessionResource` | Mixed | **NONE** | Session enumeration, refresh token abuse |
| `/auth/session/refresh` | POST | `SessionResource` | `@Authenticated` | **NONE** | Token cycling attacks |

**Why Unprotected:** Filter's `AUTH_PATHS` only covers `/login`, `/auth`, `/j_security_check` (line 42). OAuth callbacks like `/login/callback` and `/auth/github/callback` do not match these exact strings.

**Impact:** Attackers can:
- Exhaust OAuth authorization codes via repeated callback requests
- Brute-force session tokens via `/auth/session` enumeration
- Perform credential stuffing via callback replay without rate limits

#### CRITICAL-2: Admin API Endpoints (Resource Exhaustion, Data Exfiltration)

| Endpoint | HTTP Method | Class | Current Protection | Risk |
|----------|-------------|-------|-------------------|------|
| `/admin/api/notifications/broadcast` | POST | `AdminNotificationResource` | **NONE** | Global notification spam, queue saturation |
| `/admin/api/notifications/sim/execute` | POST | `NotificationSimulationResource` | **NONE** | Simulation resource abuse |
| `/admin/notifications/*` | GET/POST | `AdminNotificationPageResource` | **NONE** | Automated scraping, template DoS |

**Why Unprotected:** Filter's `resolveBucket()` checks `/api/*`, `/private/*`, `/public/*` (line 243) but NOT `/admin/*`. Admin endpoints fall through with `return null` (line 246), skipping rate limiting entirely.

**Impact:** 
- Notification broadcast spam saturates message queues
- Simulation endpoints trigger expensive operations without throttling
- Admin pages vulnerable to automated scraping

### 2.2 High-Risk Gaps (Operation-Specific Vulnerabilities)

#### HIGH-1: File Upload Endpoints (Protected but Under-Throttled)

| Endpoint | HTTP Method | Current Limit | Recommended Limit | Justification |
|----------|-------------|---------------|-------------------|---------------|
| `/private/admin/backup/upload` | POST | 120 req/60s (api bucket) | **5 req/60s** | Large file DoS, malicious archive uploads |
| `/private/admin/events/import` | POST | 120 req/60s (api bucket) | **10 req/60s** | Payload bombs, JSON schema validation abuse |

**Why Insufficient:** Generic `api` bucket (120 req/60s) does not account for resource cost of file I/O, decompression, and validation.

#### HIGH-2: Data Export Endpoints (Protected but Under-Throttled)

| Endpoint | HTTP Method | Current Limit | Recommended Limit | Justification |
|----------|-------------|---------------|-------------------|---------------|
| `/api/events/{id}/cfp/submissions/export.csv` | GET | 120 req/60s | **20 req/60s** | CSV generation CPU/memory cost |
| `/api/private/admin/insights/initiatives/export.csv` | GET | 120 req/60s | **20 req/60s** | Report generation resource exhaustion |
| `/private/admin/backup/download` | GET | 120 req/60s | **10 req/60s** | Large file transfer, disk I/O saturation |

**Why Insufficient:** Export operations involve database queries, serialization, and streaming large files—significantly more expensive than typical API reads.

### 2.3 Medium-Risk Gaps (General Coverage)

#### MEDIUM-1: Non-Namespaced Page Endpoints

Endpoints not matching `/api/*`, `/private/*`, `/public/*` patterns receive **NO protection**:

| Path Pattern | Example Endpoints | Risk Level | Current Protection |
|--------------|-------------------|------------|-------------------|
| `/quests/*` | Quest board pages | MEDIUM | **NONE** |
| `/notifications/*` | User notifications | MEDIUM | **NONE** |
| `/trending` | GitHub trending page | MEDIUM | **NONE** |
| `/legacy` | Legacy redirects | LOW | **NONE** |
| `/whoami` | Diagnostic endpoint | LOW | **NONE** (but requires admin auth) |
| `/profile` | Profile redirect | LOW | **NONE** |

**Impact:** High-traffic public pages (e.g., `/trending`, `/quests`) lack throttling, enabling scraping and resource exhaustion.

### 2.4 Coverage Summary

| Risk Level | Unprotected Endpoints | Protected but Under-Throttled | Total Gaps |
|------------|----------------------|------------------------------|------------|
| **CRITICAL** | 6 (OAuth, admin APIs) | 0 | 6 |
| **HIGH** | 0 | 5 (uploads, exports) | 5 |
| **MEDIUM** | 8+ (page endpoints) | 0 | 8+ |
| **LOW** | 3 (diagnostics) | 0 | 3 |
| **TOTAL** | **17+** | **5** | **22+** |

**Percentage Protected:** Approximately **70%** of REST endpoints have some rate limiting, but only **40%** have risk-appropriate thresholds.

---

## 3. Proposed Canonical Bucket Strategy

### 3.1 Risk-Based Bucket Taxonomy

| Bucket Name | Risk Level | Description | Proposed Limit | Proposed Window | Justification |
|-------------|-----------|-------------|----------------|-----------------|---------------|
| **auth-critical** | CRITICAL | Login, registration, password reset, OAuth callbacks | **20 req/60s** | 60s | Prevent credential stuffing while allowing legitimate retries (5 failed attempts = 15s lockout) |
| **auth-session** | CRITICAL | Session creation, refresh, validation | **60 req/60s** | 60s | Higher than auth-critical (no password hashing cost), but low enough to prevent token enumeration |
| **admin-write** | HIGH | Admin data modification, broadcast, batch operations | **10 req/60s** | 60s | Admin operations are infrequent and often expensive; conservative limit prevents abuse |
| **admin-read** | HIGH | Admin dashboards, reports (non-export) | **60 req/60s** | 60s | Allow rapid dashboard navigation while preventing scraping |
| **upload** | HIGH | File uploads, bulk imports | **5 req/60s** | 60s | Account for I/O cost, decompression, validation overhead |
| **export** | HIGH | Data exports (CSV, JSON), backups | **20 req/60s** | 60s | Balance legitimate use (re-downloading) vs. exfiltration risk |
| **api-write** | MEDIUM | Authenticated write operations (non-admin) | **120 req/60s** | 60s | Current generic API limit is reasonable for standard writes |
| **api-read** | MEDIUM | Authenticated read operations | **300 req/60s** | 60s | Reads are cheaper than writes; allow higher throughput |
| **public-read** | LOW | Unauthenticated public pages, profiles | **300 req/60s** | 60s | Balance legitimate browsing vs. scraping (5 pages/sec is aggressive) |
| **community-content-read** | LOW | Community content GET (current adaptive logic) | **1800 req/60s** (base) | 60s | Retain current high limit for curated content consumption |
| **community-content-write** | MEDIUM | Community content POST/PUT/DELETE | **600 req/60s** (base) | 60s | Retain current adaptive logic for content curation |

### 3.2 Bucket-to-Endpoint Mapping (Proposed Implementation)

#### CRITICAL: auth-critical (20 req/60s)
```
/login
/auth
/j_security_check
/login/callback          # NEW
/auth/github/callback    # NEW
/auth/discord/callback   # NEW
/auth/google/callback    # NEW (if exists)
```

#### CRITICAL: auth-session (60 req/60s)
```
/auth/session            # NEW
/auth/session/refresh    # NEW
/auth/whoami             # NEW (move from unprotected)
```

#### HIGH: admin-write (10 req/60s)
```
POST /admin/api/notifications/broadcast      # NEW
POST /admin/api/notifications/sim/execute    # NEW
POST /private/admin/**/import                # TIGHTEN (currently 120)
POST /private/admin/**/upload                # TIGHTEN (currently 120)
DELETE /private/admin/**                     # NEW
```

#### HIGH: admin-read (60 req/60s)
```
GET /admin/**            # NEW
GET /private/admin/**    # TIGHTEN (currently 120)
```

#### HIGH: upload (5 req/60s)
```
POST /private/admin/backup/upload
POST /private/admin/events/import
POST /api/**/upload      # Any future upload endpoints
```

#### HIGH: export (20 req/60s)
```
GET /api/**/export.csv
GET /api/**/export.json
GET /private/admin/backup/download
GET /api/private/admin/insights/initiatives/export.csv
```

#### MEDIUM: api-write (120 req/60s) [Current default]
```
POST /api/**             # Exclude paths in upload/export/community-content
PUT /api/**
DELETE /api/**
PATCH /api/**
POST /private/**         # Exclude admin-write paths
PUT /private/**
DELETE /private/**
```

#### MEDIUM: api-read (300 req/60s) [New, elevated from 120]
```
GET /api/**              # Exclude export paths
GET /private/**          # Exclude admin-read paths
```

#### LOW: public-read (300 req/60s)
```
GET /public/**           # Already protected at 120, elevate to 300
GET /quests/**           # NEW
GET /notifications/**    # NEW
GET /trending            # NEW
GET /legacy              # NEW
GET /profile             # NEW
GET /u/{username}        # NEW
GET /speaker/{id}        # NEW
```

#### MEDIUM/LOW: community-content (retain current adaptive logic)
```
GET /api/community/content/**    # 1800 base + adaptive
POST /api/community/content/**   # 600 base + adaptive
PUT /api/community/content/**
DELETE /api/community/content/**
```

### 3.3 Implementation Priority

| Priority | Bucket(s) | Rationale | Estimated Effort |
|----------|----------|-----------|------------------|
| **P0** (Critical) | `auth-critical`, `auth-session`, `admin-write` | Close immediate attack vectors (OAuth abuse, admin spam) | 2-4 hours |
| **P1** (High) | `upload`, `export` | Prevent resource-based DoS on expensive operations | 2-3 hours |
| **P2** (Medium) | `admin-read`, `api-read`, `public-read` | Improve general coverage and scraping resistance | 3-4 hours |
| **P3** (Polish) | Observability, alerting, dynamic tuning | Monitor effectiveness, tune thresholds based on traffic | Ongoing |

---

## 4. Recommended Baseline Thresholds

### 4.1 Threshold Calibration Methodology

Thresholds balance **legitimate use patterns** vs. **abuse prevention**:

1. **Auth endpoints (20 req/60s):** 
   - Legitimate user: 1-3 login attempts/minute (password typos, MFA retries)
   - Attacker: 100+ req/min (credential stuffing, brute force)
   - **20 req/60s** allows 5 failed logins with 12s spacing, then blocks for 60s

2. **Session endpoints (60 req/60s):**
   - Legitimate user: 1-5 session checks/minute (SPA token refreshes, page navigations)
   - Attacker: 50+ req/min (token enumeration)
   - **60 req/60s** allows frequent legitimate checks while blocking enumeration

3. **Admin writes (10 req/60s):**
   - Legitimate admin: 1-5 write ops/minute (event creation, configuration changes)
   - Attacker: Broadcast spam, batch abuse
   - **10 req/60s** accommodates realistic admin workflows (1 action every 6s)

4. **Uploads (5 req/60s):**
   - Legitimate user: 1-2 uploads/minute (backup restoration, batch import with retries)
   - Attacker: Payload bomb saturation
   - **5 req/60s** allows retries while preventing rapid-fire abuse

5. **Exports (20 req/60s):**
   - Legitimate user: 3-10 exports/minute (downloading reports, re-exporting after format changes)
   - Attacker: Data exfiltration via repeated downloads
   - **20 req/60s** balances legitimate workflows (report generation pipeline) vs. bulk theft

6. **API reads (300 req/60s):**
   - Legitimate SPA: 30-100 reads/minute (pagination, real-time updates, dashboard refreshes)
   - Scraper: 1000+ req/min (aggressive crawling)
   - **300 req/60s** allows smooth UX (5 reads/sec) while blocking industrial scraping

7. **Public pages (300 req/60s):**
   - Legitimate browser: 10-50 pages/minute (rapid navigation, browser prefetch)
   - Scraper: 200+ pages/min
   - **300 req/60s** allows power users (e.g., Ctrl+Click multiple tabs) while blocking bots

### 4.2 Threshold Decision Matrix

| Operation Type | Computation Cost | I/O Cost | Abuse Risk | Legitimate Frequency | Proposed Limit |
|----------------|-----------------|----------|------------|---------------------|----------------|
| Password auth | HIGH (bcrypt) | LOW | CRITICAL (credential stuffing) | LOW (1-3/min) | **20** |
| Token refresh | MEDIUM (JWT validation) | LOW | HIGH (enumeration) | MEDIUM (5-10/min) | **60** |
| Admin broadcast | LOW (CPU) | HIGH (queue) | CRITICAL (spam) | VERY LOW (1/min) | **10** |
| File upload | MEDIUM | VERY HIGH (disk) | HIGH (DoS) | VERY LOW (<1/min) | **5** |
| CSV export | HIGH (DB query) | HIGH (streaming) | MEDIUM (exfiltration) | LOW (2-5/min) | **20** |
| API write | MEDIUM (DB write) | MEDIUM | MEDIUM (data corruption) | MEDIUM (10-20/min) | **120** |
| API read | LOW (DB read) | LOW (cache hit) | LOW (scraping) | HIGH (30-100/min) | **300** |
| Public page | LOW (template render) | MEDIUM (cache miss) | LOW (scraping) | HIGH (10-50/min) | **300** |

### 4.3 Adaptive Threshold Considerations

**Retain adaptive logic for community-content bucket:**
- Current implementation (lines 299-333) is well-designed for content curation workflows
- Fingerprint diversity is a strong signal of legitimate curator behavior
- **No changes recommended** to adaptive parameters

**Do NOT implement adaptive logic for other buckets:**
- Auth endpoints: Adaptive limits weaken brute-force protection (attackers can manipulate fingerprints)
- Admin endpoints: Low legitimate traffic volume makes adaptation unnecessary
- Uploads/exports: Operation cost is deterministic; adaptation provides no benefit

---

## 5. Implementation Checklist

### 5.1 Code Changes (RateLimitingFilter.java)

- [ ] **Line 42-44:** Expand `AUTH_PATHS` to include callback routes:
  ```java
  private static final Set<String> AUTH_PATHS = Set.of(
    "/login", "/auth", "/j_security_check",
    "/login/callback", "/auth/github/callback", "/auth/discord/callback"  // ADD
  );
  ```

- [ ] **Line 228-247:** Refactor `resolveBucket()` to implement new bucket logic:
  ```java
  private Bucket resolveBucket(String path, String method) {
    String m = method == null ? "GET" : method.trim().toUpperCase();
    
    // P0: Auth endpoints
    if (AUTH_PATHS.contains(path)) return new Bucket("auth-critical", 20, windowSeconds);
    if (path.startsWith("/auth/session")) return new Bucket("auth-session", 60, windowSeconds);
    if (LOGOUT_PATHS.contains(path)) return new Bucket("logout", 30, windowSeconds);
    
    // P0: Admin writes
    if (path.startsWith("/admin/api") && !m.equals("GET")) return new Bucket("admin-write", 10, windowSeconds);
    if (path.startsWith("/private/admin") && Set.of("POST","PUT","DELETE","PATCH").contains(m)) {
      if (path.contains("/upload") || path.contains("/import")) return new Bucket("upload", 5, windowSeconds);
      return new Bucket("admin-write", 10, windowSeconds);
    }
    
    // P1: Uploads and exports
    if (path.contains("/upload") && !m.equals("GET")) return new Bucket("upload", 5, windowSeconds);
    if (path.contains("/export") || path.contains("/download")) return new Bucket("export", 20, windowSeconds);
    
    // P0: Admin reads
    if (path.startsWith("/admin") || path.startsWith("/private/admin")) return new Bucket("admin-read", 60, windowSeconds);
    
    // Existing community content logic (UNCHANGED)
    if (path.startsWith(COMMUNITY_CONTENT_API_PREFIX)) {
      int limit = m.equals("GET") ? communityContentApiReadLimit : communityContentApiWriteLimit;
      return new Bucket("api-community-content", limit, windowSeconds);
    }
    
    // P2: API differentiation (read vs write)
    if (path.startsWith("/api") || path.startsWith("/private")) {
      int limit = m.equals("GET") ? 300 : 120;  // Elevate reads to 300
      String bucketName = m.equals("GET") ? "api-read" : "api-write";
      return new Bucket(bucketName, limit, windowSeconds);
    }
    
    // P2: Public pages (new coverage)
    if (path.startsWith("/public") || path.startsWith("/quests") || path.startsWith("/notifications") 
        || path.equals("/trending") || path.equals("/legacy") || path.equals("/profile") 
        || path.matches("/u/.*") || path.matches("/speaker/.*")) {
      return new Bucket("public-read", 300, windowSeconds);
    }
    
    return null;  // Skip rate limiting for unmatched paths
  }
  ```

- [ ] **Configuration:** Add new limit properties to `application.properties`:
  ```properties
  rate.limit.auth-critical.limit=20
  rate.limit.auth-session.limit=60
  rate.limit.admin-write.limit=10
  rate.limit.admin-read.limit=60
  rate.limit.upload.limit=5
  rate.limit.export.limit=20
  rate.limit.api-read.limit=300
  rate.limit.public-read.limit=300
  ```

### 5.2 Testing Requirements

- [ ] **Unit tests:** Verify bucket resolution logic for all new paths
- [ ] **Integration tests:** Confirm 429 responses after threshold breach for each bucket
- [ ] **Load tests:** Validate performance impact of expanded coverage (expect <5ms overhead per request)
- [ ] **Negative tests:** Confirm authenticated users are NOT bypassing rate limits (verify client key extraction works with real proxy headers)

### 5.3 Deployment Considerations

- [ ] **Gradual rollout:** Deploy P0 buckets first (auth, admin), monitor for false positives
- [ ] **Observability:** Add metrics for new buckets (`checkedByBucket`, `throttledByBucket` already exist, line 132-133)
- [ ] **Alerting:** Set up alerts for abnormal throttling rates (e.g., >10% of auth requests blocked = potential attack OR misconfigured limit)
- [ ] **Documentation:** Update API docs with rate limit headers (`Retry-After`, `X-RateLimit-Remaining`)

### 5.4 Validation Criteria (Acceptance)

- [ ] All CRITICAL gaps (OAuth callbacks, admin APIs) are protected with appropriate limits
- [ ] Upload/export endpoints have specialized throttling (not generic API limits)
- [ ] No false positives reported in production logs (legitimate users NOT blocked)
- [ ] Monitoring dashboard shows per-bucket throttling rates
- [ ] Security team approves threshold values for sensitive buckets (auth, admin)

---

## 6. Appendix

### 6.1 Current vs. Proposed Coverage Comparison

| Endpoint Category | Current Limit | Proposed Limit | Change Reason |
|-------------------|---------------|----------------|---------------|
| `/login`, `/auth` | 30 req/60s | **20 req/60s** | Tighten brute-force protection |
| `/login/callback` | **NONE** | **20 req/60s** | Close OAuth abuse vector |
| `/auth/session*` | **NONE** | **60 req/60s** | Prevent token enumeration |
| `/admin/api/**` (write) | **NONE** | **10 req/60s** | Stop notification spam |
| `/admin/**` (read) | **NONE** | **60 req/60s** | Prevent dashboard scraping |
| `**/upload` | 120 req/60s | **5 req/60s** | Account for file I/O cost |
| `**/export.csv` | 120 req/60s | **20 req/60s** | Balance export workflows vs. exfiltration |
| `/api/**` (read) | 120 req/60s | **300 req/60s** | Improve UX for read-heavy SPAs |
| `/public/**` | 120 req/60s | **300 req/60s** | Allow faster browsing while blocking scrapers |
| `/quests`, `/trending`, etc. | **NONE** | **300 req/60s** | Add general page protection |

### 6.2 Attack Scenarios Mitigated

#### Scenario 1: Credential Stuffing via OAuth Callbacks
**Before:** Attacker sends 10,000 requests/min to `/auth/github/callback` with stolen GitHub codes  
**After:** Limited to 20 req/60s (99.97% blocked), attacker gets max 20 attempts before 60s lockout

#### Scenario 2: Admin Notification Spam
**Before:** Attacker calls `/admin/api/notifications/broadcast` 1000 times/min, saturating message queue  
**After:** Limited to 10 req/60s, maximum 10 broadcasts/min (queue can process at this rate)

#### Scenario 3: Data Exfiltration via CSV Export
**Before:** Scraper downloads `/api/events/*/cfp/submissions/export.csv` for 500 events in 5 minutes  
**After:** Limited to 20 exports/60s, exfiltration slowed by 96% (takes 25 minutes instead of 5)

#### Scenario 4: Upload-Based DoS
**Before:** Attacker uploads 120 malicious archives/min to `/private/admin/backup/upload`, exhausting disk I/O  
**After:** Limited to 5 uploads/60s, system has time to validate and reject malicious files

### 6.3 False Positive Risk Assessment

| Bucket | False Positive Risk | Mitigation |
|--------|---------------------|------------|
| **auth-critical (20 req/60s)** | LOW | Legitimate users rarely exceed 5 failed logins/min; OAuth callbacks are single-use |
| **auth-session (60 req/60s)** | LOW | SPAs typically refresh tokens every 5-15 min, not every second |
| **admin-write (10 req/60s)** | VERY LOW | Admin batch operations (e.g., creating 20 events) should use dedicated bulk APIs, not rapid individual POSTs |
| **upload (5 req/60s)** | LOW | 5 uploads/min is generous for manual operations; automated tools should implement backoff |
| **export (20 req/60s)** | MEDIUM | Power users may hit this during bulk report downloads; monitor for complaints and adjust to 30 if needed |
| **api-read (300 req/60s)** | VERY LOW | 5 reads/sec accommodates aggressive SPAs; only custom data pipelines would exceed this |
| **public-read (300 req/60s)** | VERY LOW | 5 pages/sec is faster than human browsing; only automated tools would trigger this |

**Recommendation:** Implement gradual rollout with monitoring. If false positive rate exceeds 0.1% for any bucket, increase limit by 50% and re-evaluate after 7 days.

### 6.4 References

- **Source Code:** `quarkus-app/src/main/java/com/scanales/homedir/security/RateLimitingFilter.java`
- **Related Issue:** #854 (Endpoint authorization matrix) - provides security context for risk classification
- **OWASP Guidance:** [Rate Limiting Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html#rate-limiting)
- **JAX-RS Filters:** [Jakarta EE Container Request Filters](https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1#filters_and_interceptors)
- **Fixed Window Algorithm:** Simple, memory-efficient, but allows bursts at window boundaries (acceptable trade-off for this application)

### 6.5 Maintenance Plan

- **Quarterly Review:** Analyze `throttledByBucket` metrics to identify over/under-throttled buckets
- **Threshold Tuning:** Adjust limits based on:
  - False positive reports (increase limit)
  - Security incidents (decrease limit)
  - Traffic growth (proportional increase)
- **Bucket Expansion:** Add specialized buckets as new high-risk features are deployed (e.g., webhooks, batch APIs)
- **Algorithm Upgrade:** Consider upgrading to **sliding window** or **token bucket** algorithm if burst traffic becomes problematic (requires more memory but smoother rate enforcement)

---

**END OF AUDIT DOCUMENT**
