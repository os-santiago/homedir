# GitHub Actions Version Management Policy

## Purpose
This document establishes the canonical versions for all GitHub Actions used across workflows in this repository, ensuring consistency, maintainability, and supply chain security.

## Current Inventory

> Inventory regenerated on **2026-08-11** from the actual workflow files. Workflows use **SHA pinning with a version comment** (e.g., `@<sha> # v6.5.1`); the comment is the effective version. Version tags shown for actions pinned by tag.

| Workflow | Line | Action | Version |
|----------|------|--------|---------|
| cfp-go-live-resilience.yml | 89 | actions/checkout | v6 |
| cfp-go-live-resilience.yml | 100 | webfactory/ssh-agent | v0.9.0 |
| cfp-go-live-resilience.yml | 298 | actions/checkout | v6 |
| cfp-go-live-resilience.yml | 301 | webfactory/ssh-agent | v0.9.0 |
| cfp-go-live-resilience.yml | 359 | actions/upload-artifact | v7 |
| deploy-worker.yml | 40 | actions/checkout | v6 |
| i18n-validation.yml | 26 | actions/checkout | v4 |
| i18n-validation.yml | 29 | actions/setup-python | v5 |
| issue-metadata-validation.yml | 23 | actions/checkout | v4 |
| issue-metadata-validation.yml | 26 | actions/setup-python | v5 |
| issue-metadata-validation.yml | 45 | actions/github-script | v7 |
| issue-metadata-validation.yml | 62 | actions/github-script | v7 |
| issue-metadata-validation.yml | 76 | actions/github-script | v7 |
| pipeline-health.yml | 26 | actions/checkout | v6 |
| pipeline-health.yml | 29 | actions/setup-python | v6 |
| pipeline-health.yml | 62 | actions/upload-artifact | v7 |
| pr-check.yml | 27 | actions/checkout | v6.5.1 |
| pr-check.yml | 30 | actions/setup-java | v5.4.0 |
| pr-check.yml | 144 | actions/checkout | v6.5.1 |
| pr-check.yml | 147 | actions/setup-java | v5.4.0 |
| pr-check.yml | 154 | actions/setup-node | v4.2.0 |
| pr-check.yml | 161 | actions/cache | v4.2.3 |
| pr-check.yml | 173 | actions/upload-artifact | v4.6.2 |
| pr-check.yml | 182 | actions/upload-artifact | v4.6.2 |
| pr-check.yml | 196 | actions/checkout | v6.5.1 |
| pr-check.yml | 201 | actions/setup-java | v5.4.0 |
| pr-ci-build-native-sbom.yml | 24 | actions/checkout | v6.5.1 |
| pr-ci-build-native-sbom.yml | 27 | actions/setup-java | v5.4.0 |
| pr-ci-build-native-sbom.yml | 38 | actions/upload-artifact | v4.6.2 |
| pr-quality-suite.yml | 24 | actions/checkout | v6.5.1 |
| pr-quality-suite.yml | 27 | actions/setup-java | v5.4.0 |
| pr-quality-suite.yml | 44 | actions/checkout | v6.5.1 |
| pr-quality-suite.yml | 47 | actions/setup-java | v5.4.0 |
| pr-quality-suite.yml | 64 | actions/checkout | v6.5.1 |
| pr-quality-suite.yml | 67 | actions/setup-java | v5.4.0 |
| pr-quality-suite.yml | 84 | actions/checkout | v6.5.1 |
| pr-quality-suite.yml | 87 | actions/setup-java | v5.4.0 |
| pr-quality-suite.yml | 98 | codecov/codecov-action | v7.0.0 |
| pr-quality-suite.yml | 111 | actions/checkout | v6.5.1 |
| pr-quality-suite.yml | 114 | actions/setup-java | v5.4.0 |
| quality-gates.yml | 34 | actions/checkout | v6.5.1 |
| quality-gates.yml | 37 | actions/setup-java | v5.4.0 |
| quality-gates.yml | 58 | anchore/sbom-action | v0.24.0 |
| quality-gates.yml | 65 | anchore/scan-action | v6.5.1 |
| quality-gates.yml | 94 | actions/checkout | v6.5.1 |
| quality-gates.yml | 97 | actions/dependency-review-action | v4.9.0 |
| quality-gates.yml | 120 | actions/checkout | v6.5.1 |
| quality-gates.yml | 123 | github/codeql-action/init | v3.36.2 |
| quality-gates.yml | 129 | actions/setup-java | v5.4.0 |
| quality-gates.yml | 142 | github/codeql-action/analyze | v3.36.2 |
| quality-gates.yml | 152 | actions/checkout | v6.5.1 |
| quality-gates.yml | 157 | trufflesecurity/trufflehog | main |
| release.yml | 54 | actions/checkout | v6 |
| release.yml | 100 | actions/setup-java | v5 |
| release.yml | 115 | mathieudutour/github-tag-action | v6.2 |
| release.yml | 425 | webfactory/ssh-agent | v0.9.0 |
| security-advisory.yml | 28 | actions/checkout | v6.5.1 |
| security-advisory.yml | 32 | actions/dependency-review-action | v4.9.0 |
| security-advisory.yml | 60 | actions/checkout | v6.5.1 |
| security-advisory.yml | 64 | github/codeql-action/init | v3.36.2 |
| security-advisory.yml | 72 | actions/setup-java | v5.4.0 |
| security-advisory.yml | 81 | github/codeql-action/autobuild | v3.36.2 |
| security-advisory.yml | 87 | github/codeql-action/analyze | v3.36.2 |
| spotless-apply.yml | 22 | actions/checkout | v6.0.0 |
| spotless-apply.yml | 28 | actions/setup-java | v5.4.0 |
| update-docs-on-release.yml | 20 | actions/checkout | v6 |

## Notes

- **full_release_cycle.yml is DEPRECATED**: its workflow only prints an "obsolete" notice. It is excluded from the inventory above and should be removed (see #1363).
- **CodeQL**: the policy previously targeted `v4`; the actual pinned version in `security-advisory.yml` and `quality-gates.yml` is **v3.36.2** (`github/codeql-action/*@8272c...`).
- `quality-gates.yml` no longer uses `github/codeql-action/upload-sarif` or `actions/upload-artifact`; those rows were removed.

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

## Current Discrepancies (2026-08-11)

1. i18n-validation.yml: checkout v4→v6, setup-python v5→v6
2. issue-metadata-validation.yml: checkout v4→v6, setup-python v5→v6
3. quality-gates.yml + security-advisory.yml: CodeQL pinned at v3.36.2 (policy target v4)
4. pr-check.yml / pr-ci-build-native-sbom.yml / pr-quality-suite.yml: `actions/upload-artifact` pinned at v4.6.2 (policy target v7)

**Action Required**: Separate PRs to remediate; tracked under #1363.

## References
- Parent Issue: #838
- This Policy: #862

**Version**: 1.1 | **Updated**: 2026-08-11 | **Next Review**: 2026-10-01