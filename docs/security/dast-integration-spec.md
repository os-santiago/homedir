# DAST Integration Specification

**Parent Issue:** [#838 - IA Governance](https://github.com/os-santiago/homedir/issues/838)  
**Issue:** [#858 - DAST/Fuzzing Integration](https://github.com/os-santiago/homedir/issues/858)

## Executive Summary

This specification defines the integration of Dynamic Application Security Testing (DAST) into the homedir CI/CD pipeline. It establishes a phased rollout strategy starting with passive scanning (advisory), progressing to active scanning (enforcing), and culminating in fuzzing-based testing for high-risk endpoints. The specification prioritizes endpoints based on the authorization matrix (issue #854) and input validation baseline (issue #857), with clear thresholds for blocking vs. advisory findings.

**Key Decisions:**
- **Tool**: OWASP ZAP (Zed Attack Proxy) via GitHub Action
- **Phases**: 3 phases over 12 weeks (passive → active → fuzzing)
- **Scope**: 255 endpoints analyzed, 20+ critical endpoints prioritized
- **Thresholds**: Phase-specific limits (0 Critical in all phases, 3→1→0 High across phases)
- **Enforcement**: Advisory weeks 1-4, enforcing weeks 5+

---

## Tool Selection: OWASP ZAP

### Rationale

| Criterion | OWASP ZAP | Alternatives (Burp Suite, Nuclei) |
|-----------|-----------|-----------------------------------|
| **Cost** | ✅ Free, open source | ❌ Burp Suite Pro: $399/year/user |
| **CI Integration** | ✅ Official GitHub Action ([zaproxy/action-baseline](https://github.com/zaproxy/action-baseline)) | ⚠️ Burp: Manual setup, Nuclei: template-based (less comprehensive) |
| **Automation** | ✅ CLI-driven, Docker image, API mode | ✅ Burp CLI available, Nuclei CLI native |
| **Scan Types** | ✅ Passive, active, AJAX spider, fuzzing | ✅ Burp: Active + passive, Nuclei: template-based only |
| **Reporting** | ✅ HTML, JSON, Markdown, XML | ✅ Burp: Multiple formats, Nuclei: JSON/Markdown |
| **False Positive Rate** | ⚠️ Moderate (requires tuning) | ✅ Burp: Lower FP rate, Nuclei: depends on templates |
| **Community Support** | ✅ Large OWASP community, active maintenance | ✅ Burp: Commercial support, Nuclei: growing community |
| **Baseline Scanning** | ✅ Purpose-built action for PR checks | ❌ Burp: Requires custom scripting |

**Decision**: **OWASP ZAP** due to zero cost, official GitHub Action support, comprehensive scan types, and strong OWASP community backing.

**Tradeoffs**:
- Higher false positive rate than Burp Suite Pro (mitigated via configuration tuning in Phase 2)
- Requires initial configuration effort (rules, context, authentication)

---

## Integration Approach

### GitHub Action Workflow

**File**: `.github/workflows/dast-scan.yml`

```yaml
name: DAST Security Scan

on:
  schedule:
    # Phase 1: Weekly on Sundays at 2 AM UTC
    - cron: '0 2 * * 0'
  workflow_dispatch:
    inputs:
      phase:
        description: 'Scan phase (baseline, active, fuzzing)'
        required: true
        default: 'baseline'
        type: choice
        options:
          - baseline
          - active
          - fuzzing
      enforce:
        description: 'Block on findings (true = enforcing, false = advisory)'
        required: true
        default: 'false'
        type: boolean

jobs:
  zap-scan:
    runs-on: ubuntu-latest
    permissions:
      security-events: write
      contents: read
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Start application (Docker Compose)
        run: |
          docker compose -f docker-compose.test.yml up -d
          timeout 60s bash -c 'until curl -f http://localhost:8080/health; do sleep 2; done'

      - name: ZAP Baseline Scan (Phase 1)
        if: github.event.inputs.phase == 'baseline' || github.event_name == 'schedule'
        uses: zaproxy/action-baseline@v0.12.0
        with:
          target: 'http://localhost:8080'
          rules_file_name: '.zap/rules-baseline.tsv'
          cmd_options: '-a -j -T 30 -d'
          fail_action: ${{ github.event.inputs.enforce == 'true' }}

      - name: ZAP Full Scan (Phase 2)
        if: github.event.inputs.phase == 'active'
        uses: zaproxy/action-full-scan@v0.10.0
        with:
          target: 'http://localhost:8080'
          rules_file_name: '.zap/rules-active.tsv'
          cmd_options: '-j -T 60 -d -z "-config spider.maxDuration=10"'
          fail_action: ${{ github.event.inputs.enforce == 'true' }}

      - name: ZAP API Scan with Fuzzing (Phase 3)
        if: github.event.inputs.phase == 'fuzzing'
        uses: zaproxy/action-api-scan@v0.7.0
        with:
          target: 'http://localhost:8080'
          format: openapi
          api_spec: 'docs/api/openapi.yaml'
          rules_file_name: '.zap/rules-fuzzing.tsv'
          cmd_options: '-j -T 90 -d -z "-config fuzz.threads=5"'
          fail_action: ${{ github.event.inputs.enforce == 'true' }}

      - name: Upload ZAP Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: zap-report-${{ github.event.inputs.phase || 'baseline' }}
          path: zap-report.*

      - name: Upload to Security Tab
        if: always()
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: zap-sarif.json
```

---

### Docker Compose Test Environment

**File**: `docker-compose.test.yml`

```yaml
version: '3.9'
services:
  homedir-app:
    build:
      context: .
      dockerfile: Dockerfile.test
    ports:
      - "8080:8080"
    environment:
      QUARKUS_PROFILE: test
      SECURITY_GATING: permissive
      DATABASE_URL: jdbc:h2:mem:testdb
      DISABLE_AUTH: "false"  # IMPORTANT: Keep auth enabled for DAST
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 5s
      timeout: 3s
      retries: 12
```

**Rationale**:
- H2 in-memory database (fast, ephemeral, no state leakage between runs)
- `DISABLE_AUTH: false` ensures authentication flows are tested
- Health check prevents ZAP from scanning before app is ready

---

## Phased Rollout Plan

### Phase 1: Passive Baseline Scan (Weeks 1-4)

**Objective**: Establish baseline, identify low-hanging fruit, tune false positives

**Scan Mode**: **Passive + Spider** (no active attacks)
- ZAP spider crawls all reachable endpoints
- Passive rules analyze requests/responses (headers, cookies, response content)
- No mutation of requests (read-only)

**Scope**:
- **Public endpoints** (155 total, no authentication required)
- **Authentication endpoints** (`/auth/*`, `/api/auth/*`)
- **Health/metrics endpoints** (`/health`, `/metrics`, `/ready`)

**Excluded**:
- Admin endpoints (protected by `@RolesAllowed("admin")`)
- Internal insights endpoints (will be authenticated first, see #854)
- Destructive endpoints (DELETE, POST to write operations)

**Thresholds**:
- **Critical**: 0 allowed (block immediately)
- **High**: ≤ 3 allowed (advisory, manual review required)
- **Medium**: ≤ 10 allowed (advisory, prioritize fixes)
- **Low**: Unlimited (log only, review in sprint retrospective)

**Schedule**: Weekly on Sundays 2 AM UTC (low-traffic window)

**Enforcement**: **Advisory only** (does not block PRs/deployments)

**Success Criteria**:
- ✅ False positive rate < 20% (measured by manual review of first 3 runs)
- ✅ Scan completes in < 10 minutes (spider + passive rules)
- ✅ Zero Critical findings (or all triaged as false positives)
- ✅ ≤ 3 High findings (or remediation plan documented)

---

### Phase 2: Active Scan on High-Risk Endpoints (Weeks 5-8)

**Objective**: Detect injection vulnerabilities (XSS, SQLi, SSTI, CSRF) via active probing

**Scan Mode**: **Active + AJAX Spider**
- ZAP sends malicious payloads to test for vulnerabilities
- AJAX spider executes JavaScript to discover SPA routes
- Includes all passive rules from Phase 1

**Scope**:
- **User write endpoints** (87 ESCRITURA_USUARIO endpoints from #854 matrix)
  - `/api/community/lightning/threads` (POST)
  - `/api/community/lightning/threads/{id}/comments` (POST)
  - `/api/events/{eventId}/cfp/submissions` (POST)
  - `/api/economy/purchase` (POST)
  - `/api/community/submissions` (POST)
  - `/private/profile/speaker` (POST)
  - (See full list in #857 Input Validation Baseline)

**Thresholds** (enforcing):
- **Critical**: 0 allowed (block deployment)
- **High**: ≤ 1 allowed (manual security review + waiver required)
- **Medium**: ≤ 5 allowed (remediation plan required, does not block)
- **Low**: ≤ 20 allowed (log only)

**Schedule**: 
- **Weekly**: Sundays 2 AM UTC (full scan, ~30-45 minutes)
- **PR-triggered** (optional, week 7+): Lightweight active scan on changed endpoints only

**Enforcement**: **Enforcing** (blocks if thresholds exceeded)

**Success Criteria**:
- ✅ Zero Critical findings
- ✅ ≤ 1 High finding (or waiver documented)
- ✅ Scan completes in < 45 minutes
- ✅ False positive rate < 10% (improved from Phase 1 via tuning)

---

### Phase 3: Fuzzing on Critical Endpoints (Weeks 9-12)

**Objective**: Discover edge cases, input validation bypasses, and crash-inducing payloads via fuzzing

**Scan Mode**: **API Scan + Fuzzing**
- ZAP API scan uses OpenAPI spec (`docs/api/openapi.yaml`) to generate requests
- Fuzzing module mutates parameters with boundary values, special characters, oversized inputs
- Targeted fuzzing on high-risk fields (identified in #857 Input Validation Baseline)

**Scope**:
- **Critical unprotected endpoints** (5 CRITICAL_ADMIN_UNPROTECTED from #854)
- **High-risk user write endpoints** (subset of Phase 2 scope)
- **Admin write endpoints** (5 ESCRITURA_ADMIN)

**Thresholds** (enforcing):
- **Critical**: 0 allowed (block immediately, alert oncall)
- **High**: 0 allowed (block deployment, security review required)
- **Medium**: ≤ 2 allowed (remediation plan required, does not block)
- **Low**: ≤ 10 allowed (log only)

**Schedule**: Weekly on Saturdays 11 PM UTC (low-traffic, longer runtime ~60-90 minutes)

**Enforcement**: **Enforcing** (blocks if thresholds exceeded)

**Success Criteria**:
- ✅ Zero Critical findings
- ✅ Zero High findings
- ✅ Zero application crashes (HTTP 500, OOM, etc.)
- ✅ Scan completes in < 90 minutes
- ✅ ≤ 2 Medium findings (or all triaged)

---

## Threshold Matrix

Summary of thresholds across all phases:

| Severity | Phase 1 (Passive) | Phase 2 (Active) | Phase 3 (Fuzzing) | Enforcement |
|----------|-------------------|------------------|-------------------|-------------|
| **Critical** | 0 | 0 | 0 | Always block, alert oncall |
| **High** | ≤ 3 (advisory) | ≤ 1 (enforcing) | 0 (enforcing) | Manual review + waiver |
| **Medium** | ≤ 10 (advisory) | ≤ 5 (enforcing) | ≤ 2 (enforcing) | Remediation plan required |
| **Low** | Unlimited (log) | ≤ 20 (log) | ≤ 10 (log) | Log only, review in sprint |

---

## False Positive Handling

### Tuning Process

1. Review findings after each scan run
2. Classify: True Positive (TP), False Positive (FP), or Uncertain
3. Document false positives in `.zap/false-positives.md`
4. Update rule files to ignore false positives
5. Verify suppression on next scan run

**Target False Positive Rate**:
- Phase 1: < 20% (initial tuning)
- Phase 2: < 10% (refined rules)
- Phase 3: < 5% (mature configuration)

---

## Dependency Integration

### Issue #857: Input Validation Baseline

**Dependency**: Phase 2 active scanning relies on input validation being implemented first.

**Integration Points**:
1. ZAP active scan payloads test the same field types validated in #857
2. Validation failures should return **400 Bad Request** (expected, not a vulnerability)
3. ZAP interprets 500 as "unhandled exception" (potential vulnerability)

**Validation Status Check** (prerequisite for Phase 2):
```bash
# Verify validation coverage before enabling Phase 2 active scanning
grep -r "@Valid\|@NotNull\|@NotBlank\|@Size\|@Email\|@URL" quarkus-app/src/main/java/ | wc -l
# Expected: 50+ validation annotations (15 endpoints × 3-4 fields avg)
```

---

### Issue #859: Rate Limiting Audit

**Dependency**: Rate limiting prevents DAST from overwhelming the application.

**Integration Points**:
1. **ZAP scan rate configuration**: Max 5 concurrent requests, 100ms delay
2. **Test user rate limit exemption**: 10x rate limits in test environment

---

## Timeline

| Week | Phase | Activity | Deliverable |
|------|-------|----------|-------------|
| 1-4 | Phase 1 | Configure baseline scanning, tune false positives | Baseline workflow running weekly |
| 5-8 | Phase 2 | Active scanning with authentication, verify thresholds | Active scan enforcing mode |
| 9-12 | Phase 3 | API fuzzing, crash detection, final validation | Full DAST coverage operational |

**Milestones**:
- **Week 4**: Advisory baseline scanning operational
- **Week 8**: Enforcing active scanning operational
- **Week 12**: Fuzzing operational, full DAST coverage

---

## Success Metrics

### Coverage Metrics
- **Endpoint Coverage**: 100% of public + authenticated endpoints by Phase 2
- **Vulnerability Detection Rate**: 95% detection (validated via OWASP Benchmark)

### Quality Metrics
- **False Positive Rate**: Phase 1 < 20%, Phase 2 < 10%, Phase 3 < 5%
- **Mean Time to Remediation (MTTR)**: Critical ≤ 4h, High ≤ 24h, Medium ≤ 1 week

### Performance Metrics
- **Scan Runtime**: Baseline < 10 min, Active < 45 min, Fuzzing < 90 min
- **Application Performance Impact**: < 50% CPU/memory increase during scanning

---

## Open Questions / Future Work

### 1. Should we integrate DAST into PR checks?

**Recommendation**: 
- **Phase 2**: Optional lightweight scan on changed endpoints only (< 5 min)
- **Phase 3**: Required only for PRs touching security-sensitive endpoints

**Action**: Revisit after Phase 2 week 7

---

### 2. Should we use ZAP Automation Framework instead of Actions?

**Recommendation**: Stick with official GitHub Actions for Phase 1-2, migrate to Automation Framework in Phase 3 if needed

---

### 3. How do we handle authentication expiration during long scans?

**Recommendation**: Increase session timeout in test environment to 2 hours (Option A for simplicity)

---

### 4. Should we scan production or staging?

**Recommendation**: 
- **Phases 1-3**: Scan test environment only
- **Phase 4 (future)**: Optional passive-only scan of production

---

## References

- [OWASP ZAP Documentation](https://www.zaproxy.org/docs/)
- [ZAP GitHub Actions](https://github.com/zaproxy/action-baseline)
- [OWASP Benchmark Project](https://owasp.org/www-project-benchmark/)
- Issue #838: Parent AppSec Tracking Issue
- Issue #854: Endpoint Authorization Matrix
- Issue #857: Input Validation Baseline (dependency)
- Issue #859: Rate Limiting Audit (dependency)

---

**Document Version**: 1.0  
**Last Updated**: 2026-06-23  
**Author**: Claude Code (Sonnet 4.5)  
**Reviewers**: [Pending Security Lead Review]  
**Status**: Draft (awaiting approval to begin Phase 1)
