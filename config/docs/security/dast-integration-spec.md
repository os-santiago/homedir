# DAST Integration Specification

## Executive Summary

This specification defines the phased integration of Dynamic Application Security Testing (DAST) into the homedir CI/CD pipeline to detect injection vulnerabilities (XSS, SQL, SSTI), abusive configurations, and business logic flaws before production deployment.

## Tool Selection: OWASP ZAP

### Justification
- **GitHub Action Support**: Official `zaproxy/action-baseline@v0.12.0` and `zaproxy/action-full-scan@v0.10.0` actions available
- **Docker Integration**: Stable `ghcr.io/zaproxy/zaproxy:stable` image for custom workflows
- **API Mode**: REST API (`zap-api-scan`) and CLI (`zap-cli`) for programmatic control
- **Community Support**: OWASP project with active maintenance, extensive documentation
- **Cost**: Free and open-source (vs commercial alternatives like Burp Suite Enterprise)
- **CI/CD Native**: Designed for automation with exit codes, JSON/XML reports, and baseline filtering

### Alternative Considered
- **Nuclei**: Fast but template-based (less dynamic crawling)
- **Arachni**: Discontinued, no active maintenance since 2017
- **Burp Suite Enterprise**: Commercial, higher cost, overkill for current scale

## Integration Mode

### Phase 1-2: GitHub Action (Recommended)
```yaml
# .github/workflows/dast-scan.yml
name: DAST Security Scan
on:
  schedule:
    - cron: '0 2 * * 1'  # Weekly Monday 2 AM UTC
  workflow_dispatch:       # Manual trigger for ad-hoc scans

jobs:
  zap-scan:
    runs-on: ubuntu-latest
    steps:
      - name: ZAP Baseline Scan
        uses: zaproxy/action-baseline@v0.12.0
        with:
          target: 'https://staging.homedir.example.com'
          rules_file_name: '.zap/rules.tsv'
          cmd_options: '-a -j -l WARN'
```

### Phase 3: Docker + Custom Script
For fuzzing and advanced scenarios:
```bash
docker run -v $(pwd):/zap/wrk/:rw \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-api-scan.py -t https://api.homedir.example.com/openapi.json \
  -f openapi -r zap-report.html -J zap-report.json
```

## Phased Rollout

### Phase 1: Public and Authentication Endpoints (Weeks 1-4)

**Scope**:
- Public endpoints: `/`, `/about`, `/community-content` (GET)
- Authentication endpoints: `/api/auth/login`, `/api/auth/register`, `/api/auth/logout`, `/api/auth/password-reset`
- Session management: `/api/auth/session`, `/api/auth/refresh-token`

**Scan Mode**: **Passive Scan** (baseline)
- ZAP crawls target, observes traffic, reports issues without active probing
- Low risk of service disruption or data corruption
- Detects: missing security headers, cookie flags, information disclosure

**Configuration**:
```yaml
# .zap/rules-phase1.tsv
10010	IGNORE	# Cookie No HttpOnly Flag - staging uses HttpOnly
10011	WARN	# Cookie Without Secure Flag
10015	FAIL	# Incomplete or No Cache-control Header Set
10017	FAIL	# Cross-Domain JavaScript Source File Inclusion
10021	FAIL	# X-Content-Type-Options Header Missing
10023	FAIL	# Information Disclosure - Debug Error Messages
10027	FAIL	# Information Disclosure - Suspicious Comments
10038	FAIL	# Content Security Policy (CSP) Header Not Set
10054	FAIL	# Cookie Without SameSite Attribute
10096	WARN	# Timestamp Disclosure
```

**Thresholds**:
- **Critical**: 0 allowed → **BLOCK** (fail workflow)
- **High**: 3 allowed → **BLOCK**
- **Medium**: 10 allowed → **WARN** (advisory, do not block)
- **Low**: Unlimited → **INFO**

**Schedule**: Weekly (Monday 2 AM UTC, post-deploy window)

**Acceptance Criteria**:
- Scan completes in < 10 minutes
- Zero false positives in baseline rules (tune `.zap/rules.tsv` until stable)
- Reports uploaded to GitHub Actions artifacts

---

### Phase 2: Admin and Write API Endpoints (Weeks 5-8)

**Scope**:
- Admin endpoints: `/api/admin/*` (all methods)
- Write API endpoints: `/api/cfp-submission` (POST/PUT/PATCH), `/api/community-content` (POST/PUT/DELETE)
- Batch operations: `/api/bulk/*`

**Scan Mode**: **Active Scan** (controlled)
- ZAP actively probes endpoints with malicious payloads (SQLi, XSS, command injection)
- Requires authentication token and test database/environment
- Higher risk of triggering rate limits or corrupting test data

**Configuration**:
```yaml
# .zap/context-phase2.xml (authentication context)
<context>
  <name>homedir-api</name>
  <authentication>
    <type>script</type>
    <script>scripts/zap-auth.js</script>
  </authentication>
  <users>
    <user>
      <name>test-admin</name>
      <credentials>
        <username>zap-test@example.com</username>
        <password>${ZAP_TEST_PASSWORD}</password>
      </credentials>
    </user>
  </users>
</context>
```

**Attack Vectors** (enabled in active scan):
- SQL Injection (40018, 40019, 40020)
- Cross-Site Scripting (40012, 40014, 40016, 40017)
- Path Traversal (6, 7)
- Remote OS Command Injection (90020)
- Server-Side Template Injection (90035)
- XML External Entity (XXE) (90023)

**Thresholds**:
- **Critical**: 0 allowed → **BLOCK**
- **High**: 1 allowed → **BLOCK** (stricter than Phase 1)
- **Medium**: 5 allowed → **WARN**
- **Low**: Unlimited → **INFO**

**Schedule**: Weekly (same as Phase 1, separate job)

**Dependencies**:
- **#857** (input validation baseline): Prioritize endpoints with insufficient validation
- **#859** (rate limiting audit): Configure ZAP to respect rate limits (`-z "-config scanner.threadPerHost=1"`)

**Acceptance Criteria**:
- Scan completes in < 30 minutes
- Test environment has isolated database (no production data)
- Authentication token rotates per scan (ephemeral `ZAP_TEST_PASSWORD` from GitHub Secrets)
- Active scan policy excludes destructive tests (e.g., no DELETE `/api/admin/users/*`)

---

### Phase 3: Fuzzing and Advanced Testing (Weeks 9-12)

**Scope**:
- All Phase 1 + 2 endpoints
- **Plus**: OpenAPI-driven fuzzing of parameter edge cases (boundary values, type confusion, oversized inputs)

**Scan Mode**: **API Scan + Fuzzing**
- ZAP imports OpenAPI spec (`/openapi.json` or `/swagger.json`)
- Fuzzes parameters: strings (max length +1, special chars), integers (MIN_INT, MAX_INT, overflow), arrays (empty, 10k items)
- Detects: unhandled exceptions, 500 errors, resource exhaustion

**Configuration**:
```yaml
# .github/workflows/dast-fuzz.yml
- name: ZAP API Fuzz Scan
  uses: zaproxy/action-api-scan@v0.7.0
  with:
    target: 'https://staging-api.homedir.example.com/openapi.json'
    format: 'openapi'
    cmd_options: '-z "-config api.maxdepth=5" -z "-config api.fuzz.enabled=true"'
    fail_action: true
```

**Fuzzing Targets**:
- String fields: 0 chars, 1 char, 255 chars, 10k chars, null byte, Unicode BOM, SQL keywords
- Integer fields: -1, 0, 1, 2^31-1, 2^31, 2^63-1
- Array fields: [], [null], [item] * 10000
- Enum fields: valid values, invalid values, empty string, null

**Thresholds**:
- **Critical**: 0 allowed → **BLOCK**
- **High**: 0 allowed → **BLOCK** (strictest)
- **Medium**: 3 allowed → **BLOCK**
- **Low**: 10 allowed → **WARN**

**Schedule**: Weekly (Monday 2 AM UTC, runs after Phase 1+2 scans complete)

**Acceptance Criteria**:
- Scan completes in < 45 minutes
- OpenAPI spec is up-to-date (validated in pre-scan step)
- Fuzzing detects at least 1 unhandled edge case (acceptance test: introduce a known vulnerability, verify ZAP catches it)
- No rate limit exhaustion (ZAP throttled to 1 req/sec for fuzz scans)

---

## Rollout Timeline

| Week | Phase | Mode | Schedule | Enforcement |
|------|-------|------|----------|-------------|
| 1-4  | Phase 1 | Passive (baseline) | Weekly (advisory) | **WARN** only (no PR block) |
| 5-8  | Phase 2 | Active (controlled) | Weekly (advisory) | **WARN** only |
| 9-12 | Phase 3 | API Fuzz | Weekly (advisory) | **WARN** only |
| 13+  | **Enforcement Gate** | All modes | Weekly + PR trigger | **BLOCK** on threshold violation |

**Transition to PR Gate (Week 13)**:
```yaml
on:
  pull_request:
    branches: [main]
    paths:
      - 'src/**'
      - 'api/**'
  schedule:
    - cron: '0 2 * * 1'
```

**Enforcement Logic**:
- PR scans run **baseline + active** on changed endpoints only (delta scan)
- Weekly scans run **full suite** (baseline + active + fuzz)
- PR blocks merge if Critical/High findings exceed thresholds
- Weekly scans create GitHub Issues for non-blocking findings (Medium/Low)

---

## Technical Dependencies

### Infrastructure
- **Staging Environment**: `https://staging.homedir.example.com` (required for safe active scanning)
- **Test Database**: Isolated Postgres instance, seeded with synthetic data
- **GitHub Secrets**:
  - `ZAP_TEST_PASSWORD`: Ephemeral admin password (rotated weekly)
  - `STAGING_URL`: Staging base URL
  - `STAGING_API_KEY`: API key for authenticated scans (scoped to test tenant)

### Code Dependencies
- **#857** (input validation baseline): Endpoint inventory to prioritize Phase 1/2 scopes
- **#859** (rate limiting audit): Rate limit buckets to configure ZAP throttling
- **OpenAPI Spec**: `/openapi.json` endpoint (required for Phase 3 fuzzing)

### Workflow Files
```
.github/workflows/
├── dast-baseline.yml      # Phase 1: Passive scan
├── dast-active.yml        # Phase 2: Active scan
├── dast-fuzz.yml          # Phase 3: API fuzzing
└── dast-pr-gate.yml       # Week 13+: PR enforcement

.zap/
├── rules-phase1.tsv       # Baseline scan rules
├── rules-phase2.tsv       # Active scan rules
├── rules-phase3.tsv       # Fuzzing rules
├── context-phase2.xml     # Auth context for active scans
└── scripts/
    └── zap-auth.js        # Authentication script (login, session mgmt)
```

---

## Threshold Rationale

| Phase | Critical | High | Medium | Low | Rationale |
|-------|----------|------|--------|-----|-----------|
| 1 | 0 | 3 | 10 | ∞ | Passive scan should have minimal noise; allow some High findings during tuning |
| 2 | 0 | 1 | 5 | ∞ | Active scan on write endpoints → stricter High threshold (1 SQLi = incident) |
| 3 | 0 | 0 | 3 | 10 | Fuzzing should catch all injection vectors → zero tolerance for Critical/High |

**Threshold Adjustment**:
- Review findings weekly in first 4 weeks
- If false positive rate > 20%, update `.zap/rules.tsv` to suppress specific scan IDs
- If true positive rate < 5%, increase active scan aggression (`-z "-config scanner.level=HIGH"`)

---

## Success Metrics

### Phase Completion Criteria
- **Phase 1**: 4 consecutive weekly scans with < 5% false positive rate
- **Phase 2**: 4 consecutive weekly scans with 0 Critical/High findings
- **Phase 3**: API fuzzing detects ≥ 1 unhandled edge case per endpoint category (string/int/array)

### Long-Term KPIs (Week 13+)
- **PR Gate Adoption**: 100% of PRs touching `src/**` or `api/**` run DAST scan
- **Remediation SLA**: Critical findings fixed within 24h, High within 7 days
- **False Positive Rate**: < 10% of total findings
- **Coverage**: ≥ 80% of write endpoints scanned (tracked via OpenAPI spec)

---

## Rollback Plan

### Week 1-12 (Advisory Mode)
- No rollback needed (scans are non-blocking)
- Disable scheduled workflow if scan runtime > 1 hour (cost concern)

### Week 13+ (Enforcement Mode)
- **Trigger**: > 50% of PRs blocked by false positives
- **Action**:
  1. Disable PR gate (`on: pull_request` → commented out)
  2. Revert to weekly advisory scans only
  3. Re-tune `.zap/rules.tsv` for 2 weeks
  4. Re-enable PR gate with updated thresholds

---

## Open Questions / Future Work

1. **Authenticated Fuzzing**: Should Phase 3 fuzz endpoints requiring multi-factor auth (MFA)?
   - **Recommendation**: Defer to Phase 4 (out of scope for initial rollout); MFA bypass is rare in DAST
2. **Mobile API**: Does homedir have a mobile app API (`/api/mobile/*`)?
   - **Action**: Confirm with product team; add to Phase 2 scope if exists
3. **GraphQL Support**: ZAP has experimental GraphQL support (plugin)
   - **Action**: Evaluate if homedir uses GraphQL; defer to Phase 4 if yes
4. **SAST Integration**: Combine DAST with SAST (e.g., Semgrep) for defense-in-depth
   - **Action**: Track in separate issue (out of scope for DAST spec)

---

## Appendix: Example ZAP Report

```json
{
  "site": [
    {
      "@name": "https://staging.homedir.example.com",
      "alerts": [
        {
          "pluginid": "10038",
          "alert": "Content Security Policy (CSP) Header Not Set",
          "name": "Content Security Policy (CSP) Header Not Set",
          "riskcode": "2",
          "confidence": "3",
          "riskdesc": "Medium (High)",
          "desc": "Content Security Policy (CSP) is an added layer of security...",
          "instances": [
            {
              "uri": "https://staging.homedir.example.com/",
              "method": "GET",
              "evidence": ""
            }
          ],
          "count": "1",
          "solution": "Ensure that your web server, application server, load balancer, etc. is configured to set the Content-Security-Policy header.",
          "reference": "https://owasp.org/www-project-secure-headers/",
          "cweid": "693",
          "wascid": "15"
        }
      ]
    }
  ]
}
```

---

## Implementation Checklist

**Pre-Rollout (Week 0)**:
- [ ] Provision staging environment with isolated database
- [ ] Create `zap-test@example.com` test user with admin role
- [ ] Generate `ZAP_TEST_PASSWORD` and store in GitHub Secrets
- [ ] Generate `STAGING_API_KEY` and store in GitHub Secrets
- [ ] Create `.zap/` directory structure in repo root
- [ ] Implement ZAP authentication script (`.zap/scripts/zap-auth.js`)

**Phase 1 (Weeks 1-4)**:
- [ ] Create `.github/workflows/dast-baseline.yml`
- [ ] Create `.zap/rules-phase1.tsv` with baseline rules
- [ ] Run first scan manually (`workflow_dispatch`)
- [ ] Review findings, tune rules to < 5% false positive rate
- [ ] Enable weekly schedule

**Phase 2 (Weeks 5-8)**:
- [ ] Create `.github/workflows/dast-active.yml`
- [ ] Create `.zap/context-phase2.xml` with auth context
- [ ] Confirm #857 and #859 dependencies are resolved
- [ ] Run first active scan manually
- [ ] Review findings, tune scan policy
- [ ] Enable weekly schedule

**Phase 3 (Weeks 9-12)**:
- [ ] Validate OpenAPI spec is up-to-date (`/openapi.json`)
- [ ] Create `.github/workflows/dast-fuzz.yml`
- [ ] Configure ZAP fuzzing parameters
- [ ] Run first fuzz scan manually
- [ ] Review findings, adjust thresholds
- [ ] Enable weekly schedule

**Enforcement Gate (Week 13)**:
- [ ] Create `.github/workflows/dast-pr-gate.yml`
- [ ] Test PR gate on feature branch
- [ ] Document remediation SLAs in CONTRIBUTING.md
- [ ] Announce enforcement to team (1 week notice)
- [ ] Enable PR gate on `main` branch

---

## References

- [OWASP ZAP Documentation](https://www.zaproxy.org/docs/)
- [ZAP GitHub Actions](https://github.com/zaproxy/action-baseline)
- [ZAP Docker Images](https://github.com/zaproxy/zaproxy/pkgs/container/zaproxy)
- [ZAP API Documentation](https://www.zaproxy.org/docs/api/)
- [OWASP DAST Best Practices](https://owasp.org/www-community/Vulnerability_Scanning_Tools)
- Issue #857: Input Validation Baseline
- Issue #859: Rate Limiting Audit
- Issue #838: Parent AppSec Tracking Issue

---

**Document Version**: 1.0  
**Last Updated**: 2026-06-23  
**Author**: Claude Code (Sonnet 4.5)  
**Reviewers**: [Pending]  
**Status**: Draft (awaiting review)
