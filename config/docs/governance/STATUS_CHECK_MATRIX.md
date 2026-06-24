# Canonical Status Check Matrix

## Purpose

This document defines the required status checks for merging pull requests to `main`, organized by change type and risk category. It ensures consistent enforcement of quality gates while avoiding redundant checks for low-risk changes.

## Universal Required Checks (All PRs)

The following checks are **required** for all PRs regardless of change type:

| Check Name | Workflow | Type | Blocking |
|------------|----------|------|----------|
| PR Quality — Suite / style | PR Quality Suite | Code Style | Yes |
| PR Quality — Suite / static | PR Quality Suite | Static Analysis | Yes |
| PR Quality — Suite / arch | PR Quality Suite | Architecture | Yes |
| PR Quality — Suite / tests_cov | PR Quality Suite | Test Coverage | Yes |
| PR Quality — Suite / deps | PR Quality Suite | Dependencies | Yes |
| PR CI (Build, Native, SBOM/Scan) / sbom | PR Validation | SBOM Generation | Yes |

**Source**: `ruleset-main.json` `required_status_checks` section

## Additional Checks by Change Type

### Backend / API Changes

Changes to backend code, APIs, database schemas, or business logic.

| Check Name | Workflow | Type | Blocking |
|------------|----------|------|----------|
| Build & Verify | PR Validation | Build | Yes |
| SAST - CodeQL Analysis (java-kotlin) | Quality Gates | Security | Yes |
| CodeQL Java (Advisory) | Security Advisory | Security | Advisory |
| Load Test (High-Impact Services) | PR Validation | Performance | Yes |
| Secret Scanning | Quality Gates | Security | Yes |

### Frontend / UI Changes

Changes to templates, CSS, JavaScript, or user-facing components.

| Check Name | Workflow | Type | Blocking |
|------------|----------|------|----------|
| Build & Verify | PR Validation | Build | Yes |
| CodeQL | CodeQL Analysis | Security | Yes |

### Security / Infrastructure Changes

Changes to workflows, deployment configs, security policies, or infrastructure.

| Check Name | Workflow | Type | Blocking |
|------------|----------|------|----------|
| Dependency Review | Quality Gates | Security | Yes |
| Dependency Review (Advisory) | Security Advisory | Security | Advisory |
| SBOM & Security Scan | Quality Gates | Security | Yes |
| grype | Grype Scan | Vulnerability | Yes |
| Secret Scanning | Quality Gates | Security | Yes |

### Documentation-Only Changes

Changes to `*.md` files, docs directories, or comments only.

**Required checks**: Universal checks only (style, static, arch, tests_cov, deps, sbom)

**Rationale**: Documentation changes have minimal runtime risk and don't require full security/performance validation.

### Release / Deploy Changes

Changes to release workflows, version bumps, or deployment automation.

| Check Name | Workflow | Type | Blocking |
|------------|----------|------|----------|
| All Universal Checks | - | - | Yes |
| Build & Verify | PR Validation | Build | Yes |
| SBOM & Security Scan | Quality Gates | Security | Yes |
| Dependency Review | Quality Gates | Security | Yes |
| Quality Gate Summary | Quality Gates | Summary | Yes |

## Check Status Types

### Blocking (Enforcing)
- **Must pass** before PR can be merged to `main`
- Configured in `ruleset-main.json` under `required_status_checks`
- Merge button is disabled if any blocking check fails

### Advisory (Non-Blocking)
- **Should pass** but does not prevent merge
- Serves as early warning for potential issues
- Reviewers should evaluate advisory failures before approving
- Typically security or experimental checks

## Workflow Mapping

Current workflows and their primary focus:

| Workflow Name | Primary Focus | When Triggered |
|--------------|---------------|----------------|
| PR Validation | Build, Load Test, SBOM | On PR to main |
| Quality Gates | Security (SAST, Secrets, Deps, SBOM) | On PR to main |
| Security Advisory | Advisory security checks (CodeQL, Deps) | On PR to main |
| CodeQL Analysis | Code security scanning | On PR to main |
| Grype Scan | Vulnerability scanning | On PR to main |

## Change Type Detection

**Automated detection** (future enhancement):
- File path patterns (`.java`, `.html`, `.yml`, `docs/`)
- Modified directories (`apps/`, `config/`, `scripts/ci/`)
- Label-based (`security`, `frontend`, `backend`)

**Current process** (manual):
1. Reviewer inspects changed files
2. Verifies appropriate checks have run
3. Uses this matrix to confirm completeness
4. Approves only when all required checks pass

## Exception Process

If a PR cannot pass all required checks:

1. **Document the reason** in PR description
2. **Obtain maintainer approval** via PR review
3. **Create follow-up issue** to address skipped check
4. **Use bypass actor** (repository collaborator with bypass_mode: pull_request) only for emergency fixes

**Bypass actors** (from `ruleset-main.json`):
- `scanalesespinoza` (RepositoryCollaborator, bypass_mode: pull_request)

## Minimum Required Configuration

From `ruleset-main.json`, the following rules are **always enforced**:

1. **Deletion protection**: Cannot delete main branch
2. **Non-fast-forward protection**: Prevents force pushes
3. **Required status checks**: 6 universal checks (see Universal Required Checks)
4. **Conventional Commits**: Commit messages must match pattern `^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\[.*\])?(\(.*\))?(!)?: .*`
5. **Pull request required**: Direct commits to main blocked (except bypass actors)

## Usage Guidelines

### For PR Authors
1. Consult this matrix when opening a PR
2. Verify all required checks are configured to run
3. Wait for all blocking checks to pass before requesting review
4. Address advisory check failures when feasible

### For Reviewers
1. Check that all required checks (universal + change-type-specific) have run
2. Verify all blocking checks passed
3. Evaluate advisory check failures for security/quality concerns
4. Do not approve if required checks are missing or failed (unless exception approved)

### For Maintainers
1. Update this matrix when adding/removing workflows
2. Keep `ruleset-main.json` synchronized with Universal Required Checks
3. Document any bypass approvals in PR comments
4. Review matrix quarterly for coverage gaps

## Related Documents

- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) - Issue and PR completion criteria
- `ruleset-main.json` - GitHub repository ruleset configuration
- [Emergency Break-Glass Runbook](#) - Exception procedures for production incidents

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-06-24 | Claude (via WOS) | Initial version for issue #848 |

---

**Maintained by**: Platform Engineering
**Review frequency**: Quarterly or when workflows change
**Last reviewed**: 2026-06-24
