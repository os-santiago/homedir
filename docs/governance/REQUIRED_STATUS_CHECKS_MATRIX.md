# Required Status Checks Matrix

**Parent Issue:** #838 (Auditoria integral de calidad)  
**Related Issue:** #848 (Matriz canonica de required status checks por tipo de cambio)  
**Version:** 1.0  
**Status:** Active  
**Last Updated:** 2026-06-23  
**Owner:** Main Governance Working Group

---

## Executive Summary

This document defines the canonical mapping between change types and required CI/CD status checks before merge to `main`. It establishes which checks are **universally required** for all PRs, and which **additional checks** apply based on change risk profile.

**Purpose:**
- Ensure consistent enforcement across all PRs
- Prevent incomplete validation for high-risk changes
- Provide clear guidance to contributors and reviewers
- Enable automated branch protection rule configuration

---

## Universal Required Checks (All PRs)

These checks apply to **every PR** targeting `main`, regardless of change type:

| Workflow | Job Name | Enforcement | Purpose | Advisory/Blocking |
|----------|----------|-------------|---------|-------------------|
| **PR Validation** | `build-and-test` | Required | Build verification, unit tests, smoke tests | **Blocking** |
| **Quality Gates** | `sbom-and-security-scan` | Required | SBOM generation, container vulnerability scan | **Advisory** (see SECURITY_GATING var) |
| **Security Advisory** | `dependency-review` | Required | Dependency vulnerability scan (high+ severity) | **Advisory** |
| **Security Advisory** | `codeql-advisory` | Required | Static analysis for security vulnerabilities | **Advisory** |
| **I18n Validation** | `i18n-check` | Required (if i18n changes) | Translation completeness and format validation | **Blocking** (if triggered) |

**Notes:**
- `SECURITY_GATING` environment variable controls security workflow enforcement mode:
  - `permissive` (default): security findings generate warnings, do not block merge
  - `enforcing`: security findings fail the workflow and block merge
- Path filters apply: docs-only changes skip `build-and-test` and `quality-gates`

---

## Change-Type Specific Required Checks

Additional checks required based on the nature of the change:

### Backend Changes

**Scope:** Java code, Quarkus services, API endpoints, database schema, persistence layer

| Change Type | Additional Required Checks | Enforcement | Notes |
|-------------|---------------------------|-------------|-------|
| **Backend code** | `build-and-test` (already universal) | Blocking | Includes JUnit tests, integration tests |
| **API endpoints** | Load testing (if configured) | Advisory | Triggered by workflow_dispatch or labels |
| **Database schema** | Manual migration review | Manual | Reviewer must verify migration safety |
| **Security-sensitive** | `codeql-advisory` (already universal) | Advisory | Auth, crypto, input validation |

**File Path Patterns:**
- `quarkus-app/src/main/java/**`
- `quarkus-app/src/main/resources/db/**`
- `quarkus-app/pom.xml`

---

### Frontend Changes

**Scope:** HTML templates, CSS, JavaScript, UI components, layouts

| Change Type | Additional Required Checks | Enforcement | Notes |
|-------------|---------------------------|-------------|-------|
| **Frontend code** | `build-and-test` (already universal) | Blocking | Includes template validation |
| **I18n strings** | `i18n-validation` | Blocking | Completeness check for ES/EN/PT |
| **CSS/layout** | Manual visual review | Manual | Reviewer must verify responsive behavior |

**File Path Patterns:**
- `quarkus-app/src/main/resources/templates/**`
- `quarkus-app/src/main/resources/META-INF/resources/**`
- `quarkus-app/src/main/resources/i18n/**`

---

### Security-Sensitive Changes

**Scope:** Authentication, authorization, cryptography, secrets management, input validation

| Change Type | Additional Required Checks | Enforcement | Notes |
|-------------|---------------------------|-------------|-------|
| **Auth/AuthZ** | `codeql-advisory` (already universal) | Advisory | SAST for security patterns |
| **Secrets/credentials** | `secret-scanning` (via TruffleHog in sbom scan) | Advisory | Detects leaked secrets |
| **Input validation** | Manual security review | Manual | Reviewer must verify XSS/injection prevention |
| **Crypto changes** | Manual security review | Manual | Cryptographic algorithm review required |

**Recommended (future):**
- **DAST scanning** for API endpoints with user input (see #856, #857)
- **Penetration testing** for major auth changes

**File Path Patterns:**
- `**/auth/**`, `**/security/**`
- `**/crypto/**`, `**/secrets/**`
- API endpoint files with `@Consumes`, `@QueryParam`, form input

---

### Infrastructure & CI/CD Changes

**Scope:** GitHub Actions workflows, deployment scripts, Docker configuration, release automation

| Change Type | Additional Required Checks | Enforcement | Notes |
|-------------|---------------------------|-------------|-------|
| **Workflow changes** | Manual review by infra owner | Manual | Reviewer must verify workflow safety |
| **Deployment scripts** | Manual review + dry-run test | Manual | Must not deploy to production in PR |
| **Docker/container** | `sbom-and-security-scan` (already universal) | Advisory | Container vulnerability scan |
| **Release workflow** | `pipeline-health` check | Blocking | Verify release pipeline is healthy before merge |

**File Path Patterns:**
- `.github/workflows/**`
- `scripts/ci/**`, `scripts/deploy/**`
- `Dockerfile`, `docker-compose.yml`
- `pom.xml` (if modifying build plugins)

---

### Documentation & Non-Code Changes

**Scope:** Markdown docs, README, guides, runbooks, comments

| Change Type | Additional Required Checks | Enforcement | Notes |
|-------------|---------------------------|-------------|-------|
| **Docs-only** | None (workflows skipped via path filter) | N/A | Skips `build-and-test`, `quality-gates` |
| **README / GOVERNANCE** | Manual review for accuracy | Manual | High-visibility docs require extra care |

**File Path Patterns:**
- `docs/**/*.md`
- `README.md`, `CONTRIBUTING.md`, `GOVERNANCE.md`
- `**/*.md` (general markdown)

**Path Filter Exclusion:**
- PRs changing only `docs/**` or `**.md` skip build and security scans (per workflow `paths-ignore`)

---

### Release & Hotfix Changes

**Scope:** Version bumps, release notes, emergency production fixes

| Change Type | Additional Required Checks | Enforcement | Notes |
|-------------|---------------------------|-------------|-------|
| **Production release** | `production-release` workflow | Blocking | Automated release workflow must succeed |
| **Hotfix to main** | All universal checks + expedited review | Blocking | No shortcuts, but higher reviewer priority |
| **Version bump** | `update-docs-on-release` | Advisory | Auto-updates docs with release info |

**Special Cases:**
- **Emergency hotfix:** All checks still apply, but may be reviewed concurrently with deployment
- **Release automation:** Uses `release.yml` workflow, includes version tagging, changelog generation

---

## Enforcement Modes

### Blocking vs Advisory

- **Blocking:** Workflow failure prevents PR merge. Must be resolved or waived.
- **Advisory:** Workflow failure generates warnings but does not block merge. Findings must be acknowledged and triaged.

### Current Advisory-Mode Workflows (as of 2026-06-23)

| Workflow | Mode | Rationale | Migration Plan |
|----------|------|-----------|----------------|
| `quality-gates.yml` / `sbom-and-security-scan` | Advisory (permissive) | Reducing false-positive noise during migration | See #860 for enforcement hardening plan |
| `security-advisory.yml` / `dependency-review` | Advisory | High rate of transitive dependency false positives | See #860 for threshold tuning |
| `security-advisory.yml` / `codeql-advisory` | Advisory | SAST findings require manual triage | See #860 for security review workflow |

**Note:** See **#860** for the comprehensive plan to migrate security gates from advisory to enforcing mode.

---

## Branch Protection Rule Configuration

This matrix maps to GitHub branch protection settings for `main`:

### Required Status Checks (recommended configuration)

```yaml
# .github/branch-protection-config.yml (conceptual)
branches:
  main:
    required_status_checks:
      strict: true  # Require branches to be up-to-date before merging
      checks:
        # Universal (always required)
        - "PR Validation / build-and-test"
        - "Quality Gates / sbom-and-security-scan"
        - "Security Advisory / dependency-review"
        - "Security Advisory / codeql-advisory"
        
        # Conditional (triggered by path filters)
        - "I18n Validation / i18n-check"
        
        # Future enforcing-mode checks (see #860)
        # - "Quality Gates / sbom-and-security-scan" (enforcing)
        # - "Security Advisory / dependency-review" (enforcing for severity >= moderate)
    
    required_approving_review_count: 1  # Minimum reviewers
    require_code_owner_reviews: true     # For CODEOWNERS-designated areas
    dismiss_stale_reviews: true
    require_linear_history: false        # Allow merge commits for release workflow
```

**Current State (2026-06-23):**
- Branch protection is enabled on `main`
- Specific required checks TBD (to be configured based on this matrix)

**Action Item:** Update branch protection settings via GitHub UI or API to match this matrix.

---

## Mapping Workflows to Workflow Files

| Workflow Name (in matrix) | Workflow File | Jobs |
|---------------------------|---------------|------|
| **PR Validation** | `pr-check.yml` | `build-and-test` |
| **Quality Gates** | `quality-gates.yml` | `sbom-and-security-scan` |
| **Security Advisory** | `security-advisory.yml` | `dependency-review`, `codeql-advisory`, `secret-scanning` |
| **I18n Validation** | `i18n-validation.yml` | `i18n-check` |
| **Pipeline Health** | `pipeline-health.yml` | `pipeline-health-check` |
| **Production Release** | `release.yml` | `release` |
| **CFP Go-Live Resilience** | `cfp-go-live-resilience.yml` | `resilience-test` (manual trigger) |

**Deprecated:**
- `full_release_cycle.yml` - marked DEPRECATED, use `release.yml` instead

---

## Change Risk Classification

### Low Risk

**Examples:** Documentation, typo fixes, comment updates, non-functional refactoring  
**Required Checks:** Minimal (docs-only PRs skip most workflows)  
**Review:** 1 approving reviewer  

### Medium Risk

**Examples:** Feature additions, non-security bug fixes, frontend changes, test additions  
**Required Checks:** Universal checks (build, SBOM, dependency review, CodeQL)  
**Review:** 1 approving reviewer + domain owner if CODEOWNERS match  

### High Risk

**Examples:** Security-sensitive changes, database schema, auth/authz, deployment scripts, API breaking changes  
**Required Checks:** Universal + domain-specific + manual security review  
**Review:** 2 approving reviewers, including security owner for auth/crypto changes  

### Critical Risk (Production Hotfix)

**Examples:** Emergency production fixes, data loss prevention, security incident response  
**Required Checks:** All checks (no exceptions), expedited but not skipped  
**Review:** 1 approving reviewer (may be concurrent with deployment), post-deploy validation required  

---

## Exception & Waiver Process

### When Checks Can Be Waived

Security and quality checks should **rarely** be waived. Valid reasons:

1. **False positive** confirmed by security reviewer (document in waiver)
2. **Transitive dependency** with no upgrade path and no exploit in our usage
3. **Emergency hotfix** where delay risk exceeds finding risk (requires post-deploy remediation plan)
4. **Workflow infrastructure issue** preventing check completion (must file incident ticket)

### Waiver Approval Authority

| Check Type | Waiver Approver | Documentation Required |
|------------|----------------|------------------------|
| Security Advisory findings | Security owner + 1 maintainer | Waiver document in `docs/security/waivers/` |
| Quality Gates findings | Maintainer | Comment in PR with justification |
| Build failure | Domain owner | Must fix or explain why build is not applicable |

**Waiver Template:** See `docs/security/waivers/TEMPLATE.md`

**Waiver Expiration:** All waivers expire after 90 days and must be re-evaluated.

---

## Reviewer Guidance

When reviewing a PR, verify:

1. ✅ **All universal checks have passed or been waived**
2. ✅ **Change-type specific checks are triggered and passed**
3. ✅ **Advisory-mode findings are triaged** (comment with decision: accept, fix, waive)
4. ✅ **High-risk changes have appropriate additional reviews**
5. ✅ **Waiver requests include proper documentation and approval**

### Checklist by Change Type

**Backend changes:**
- [ ] Unit tests added/updated for new code paths
- [ ] Integration tests verify API contract
- [ ] Database migrations tested in staging
- [ ] Error handling covers edge cases

**Frontend changes:**
- [ ] Visual review completed (screenshots or live demo)
- [ ] Responsive behavior verified (desktop + mobile)
- [ ] I18n strings complete for all supported locales

**Security-sensitive changes:**
- [ ] Input validation prevents injection attacks
- [ ] Authentication/authorization logic is correct
- [ ] Secrets are not hardcoded or logged
- [ ] Cryptographic algorithms are current best practice

**Infrastructure changes:**
- [ ] Workflow changes tested via `workflow_dispatch` dry-run
- [ ] Deployment scripts do not execute in PR context
- [ ] Container images scanned for vulnerabilities

---

## Continuous Improvement

### Metrics to Track

Monitor these metrics quarterly to validate enforcement effectiveness:

| Metric | Target | Current (2026-Q2) | Tracking Method |
|--------|--------|-------------------|-----------------|
| % PRs with all required checks passing | ≥95% | TBD | GitHub API: check runs per PR |
| % security findings remediated within SLA | ≥90% | TBD | Manual review of advisory findings |
| Mean time to check completion | ≤15 min | TBD | Workflow run duration analysis |
| % waivers requiring re-approval | ≤5% | TBD | Waiver expiration tracking |

### Quarterly Review Process

Every quarter, the Main Governance Working Group will:

1. Review this matrix for accuracy vs actual workflows
2. Assess enforcement effectiveness using metrics above
3. Update check requirements based on incident post-mortems
4. Migrate additional checks from advisory to enforcing mode (per #860 plan)

---

## Related Documentation

- [Issue #838](https://github.com/os-santiago/homedir/issues/838) - Parent audit issue
- [Issue #848](https://github.com/os-santiago/homedir/issues/848) - Required status checks matrix (this document)
- [Issue #860](https://github.com/os-santiago/homedir/issues/860) - Security gate enforcement hardening plan
- [SEVERITY_PRIORITY_CONTRACT.md](./SEVERITY_PRIORITY_CONTRACT.md) - Severity classification for security findings
- [SLA_ESCALATION.md](./SLA_ESCALATION.md) - Response time SLAs for findings
- [TRIAGE_RUNBOOK.md](./TRIAGE_RUNBOOK.md) - PR triage process
- [Waiver Template](../security/waivers/TEMPLATE.md) - How to request check waivers

---

## Approval & Change History

| Version | Date | Changes | Approved By |
|---------|------|---------|-------------|
| 1.0 | 2026-06-23 | Initial matrix defining universal and change-type specific required checks | Pending approval |

**Approvals Required:**
- [ ] Main Governance Working Group Lead
- [ ] Security Owner
- [ ] Infrastructure Owner

---

**Maintainers:** Update this document when workflows are added, removed, or enforcement modes change.  
**Contributors:** Refer to this matrix when creating PRs to understand which checks will be required.  
**Reviewers:** Use this matrix as the source of truth for required check enforcement.
