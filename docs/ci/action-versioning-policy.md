# GitHub Actions Version Management Policy

## Purpose
This document establishes the canonical versions for all GitHub Actions used across workflows in this repository, ensuring consistency, maintainability, and supply chain security.

## Current Inventory

| Workflow | Line | Action | Current | Target |
|----------|------|--------|---------|--------|
| cfp-go-live-resilience.yml | 89 | actions/checkout | v6 | v6 |
| cfp-go-live-resilience.yml | 100 | webfactory/ssh-agent | v0.9.0 | v0.9.0 |
| cfp-go-live-resilience.yml | 298 | actions/checkout | v6 | v6 |
| cfp-go-live-resilience.yml | 301 | webfactory/ssh-agent | v0.9.0 | v0.9.0 |
| cfp-go-live-resilience.yml | 359 | actions/upload-artifact | v7 | v7 |
| full_release_cycle.yml | 16 | actions/checkout | v6 | v6 |
| full_release_cycle.yml | 18 | actions/setup-java | v5 | v5 |
| full_release_cycle.yml | 30 | actions/checkout | v6 | v6 |
| full_release_cycle.yml | 32 | actions/setup-java | v5 | v5 |
| full_release_cycle.yml | 39 | actions/upload-artifact | v7 | v7 |
| full_release_cycle.yml | 49 | actions/checkout | v6 | v6 |
| i18n-validation.yml | 20 | actions/checkout | **v4** | v6 |
| i18n-validation.yml | 23 | actions/setup-python | **v5** | v6 |
| pipeline-health.yml | 26 | actions/checkout | v6 | v6 |
| pipeline-health.yml | 29 | actions/setup-python | v6 | v6 |
| pipeline-health.yml | 62 | actions/upload-artifact | v7 | v7 |
| pr-check.yml | 23 | actions/checkout | v6 | v6 |
| pr-check.yml | 26 | actions/setup-java | v5 | v5 |
| quality-gates.yml | 34 | actions/checkout | v6 | v6 |
| quality-gates.yml | 37 | actions/setup-java | v5 | v5 |
| quality-gates.yml | 58 | anchore/sbom-action | v0 | v0 |
| quality-gates.yml | 66 | anchore/scan-action | v6 | v6 |
| quality-gates.yml | 76 | github/codeql-action/upload-sarif | **v3** | v4 |
| quality-gates.yml | 82 | actions/upload-artifact | **v4** | v7 |
| quality-gates.yml | 111 | actions/checkout | v6 | v6 |
| quality-gates.yml | 114 | actions/dependency-review-action | v4 | v4 |
| quality-gates.yml | 137 | actions/checkout | v6 | v6 |
| quality-gates.yml | 140 | github/codeql-action/init | **v3** | v4 |
| quality-gates.yml | 146 | actions/setup-java | v5 | v5 |
| quality-gates.yml | 159 | github/codeql-action/analyze | **v3** | v4 |
| quality-gates.yml | 169 | actions/checkout | v6 | v6 |
| quality-gates.yml | 174 | trufflesecurity/trufflehog | main | main |
| release.yml | 49 | actions/checkout | v6 | v6 |
| release.yml | 54 | actions/setup-java | v5 | v5 |
| release.yml | 70 | mathieudutour/github-tag-action | v6.2 | v6.2 |
| release.yml | 262 | webfactory/ssh-agent | v0.9.0 | v0.9.0 |
| security-advisory.yml | 28 | actions/checkout | v6 | v6 |
| security-advisory.yml | 32 | actions/dependency-review-action | v4 | v4 |
| security-advisory.yml | 60 | actions/checkout | v6 | v6 |
| security-advisory.yml | 64 | github/codeql-action/init | v4 | v4 |
| security-advisory.yml | 72 | actions/setup-java | v5 | v5 |
| security-advisory.yml | 81 | github/codeql-action/autobuild | v4 | v4 |
| security-advisory.yml | 87 | github/codeql-action/analyze | v4 | v4 |

**Bold** indicates current version differs from target.

## Standard Versions Table

| Action | Version | Rationale |
|--------|---------|-----------|
| actions/checkout | v6 | Latest stable |
| actions/setup-java | v5 | Latest with improved caching |
| actions/setup-python | v6 | Latest with enhanced tooling |
| actions/upload-artifact | v7 | Latest with performance improvements |
| actions/dependency-review-action | v4 | Enhanced vulnerability detection |
| github/codeql-action/init | v4 | Latest CodeQL |
| github/codeql-action/analyze | v4 | Must match init version |
| github/codeql-action/autobuild | v4 | Must match init version |
| github/codeql-action/upload-sarif | v4 | Must match CodeQL family |
| anchore/sbom-action | v0 | Stable version |
| anchore/scan-action | v6 | Latest with improved scanning |
| mathieudutour/github-tag-action | v6.2 | Pinned for stability |
| webfactory/ssh-agent | v0.9.0 | Pinned for SSH stability |
| trufflesecurity/trufflehog | main | No versioned releases |

## Update Policy

### Frequency
- Quarterly review (Jan, Apr, Jul, Oct)
- Critical security updates within 48 hours
- Major version migrations in dedicated sprints

### Responsible
- Primary: CI/CD Team Lead
- Reviewers: SecOps + Senior Dev
- Approval: Tech Lead

### Process
1. Discovery: Run verification script, review advisories
2. Planning: Create issue, document breaking changes
3. Implementation: Branch, update docs and workflows
4. Validation: Smoke tests, monitor 5 runs
5. Deployment: PR with 2 approvals, monitor 24h

### Exceptions
- Pinned patches for critical actions
- Temporary regressions (document inline with issue link)
- Branch tracking for tools without releases

## Verification

Run before commits:
```bash
./scripts/verify-action-versions.sh
```

## Current Discrepancies (2026-06-22)

1. i18n-validation.yml: checkout v4→v6, setup-python v5→v6
2. quality-gates.yml: codeql v3→v4 (3 instances), upload-artifact v4→v7

**Action Required**: Separate PR to remediate.

## References
- Parent Issue: #838
- This Policy: #862

**Version**: 1.0 | **Updated**: 2026-06-22 | **Next Review**: 2026-07-01
