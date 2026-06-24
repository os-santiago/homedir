# Branch Protection Continuous Audit Specification

## Purpose

This document defines continuous automated auditing of branch protection rules to detect configuration drift and ensure `main` branch protection remains compliant with the [canonical baseline](#baseline-requirements).

## Problem Statement

Branch protection rules can drift due to:
- Manual configuration changes via GitHub UI
- API-based modifications without review
- GitHub feature updates changing default behaviors
- Repository transfer or ownership changes

**Risk**: Silent drift allows vulnerable configurations (e.g., disabled required checks, bypass without audit) to persist undetected.

**Solution**: Automated periodic verification via GitHub API with alerting on deviations.

## Audit Scope

### What is Audited

The audit verifies the following configuration for the `main` branch:

| Control | Expected State | API Field | Severity if Missing |
|---------|---------------|-----------|---------------------|
| **Deletion protection** | Enabled | `ruleset.rules[].type = "deletion"` | **Critical** |
| **Force push protection** | Enabled | `ruleset.rules[].type = "non_fast_forward"` | **Critical** |
| **Required status checks** | 6 universal checks configured | `ruleset.rules[].parameters.required_status_checks` | **High** |
| **Pull request required** | Enabled (except bypass actors) | `ruleset.rules[].type = "pull_request"` | **Critical** |
| **Conversation resolution** | Required | `ruleset.rules[].parameters.required_conversation_resolution = true` | **High** |
| **Required approvals** | ≥1 approval | `ruleset.rules[].parameters.required_approving_review_count ≥ 1` | **High** |
| **Commit message pattern** | Conventional Commits regex | `ruleset.rules[].parameters.operator = "starts_with"` | **Medium** |
| **Bypass actors** | Only authorized users | `ruleset.bypass_actors[].actor_id` matches allowlist | **Critical** |
| **Bypass mode** | `pull_request` only (not `always`) | `ruleset.bypass_actors[].bypass_mode = "pull_request"` | **High** |

### Out of Scope

- **Workflow YAML correctness** (separate SAST/linting check)
- **Secret scanning configuration** (covered by security audit)
- **User permissions** (covered by access review)

## Audit Frequency

| Audit Type | Frequency | Trigger | Purpose |
|------------|-----------|---------|---------|
| **Scheduled** | Daily at 09:00 UTC | Cron (`0 9 * * *`) | Detect slow drift |
| **On-demand** | Manual workflow dispatch | Maintainer request | Pre/post-change verification |
| **Post-change** | On push to `.github/ruleset-*.json` | GitHub Actions `paths:` filter | Immediate validation after config change |

## Audit Implementation

### GitHub API Calls

The audit script queries:

```bash
# Get repository ruleset for main branch
gh api repos/{owner}/{repo}/rulesets --jq '.[] | select(.name == "main branch protection")'

# Get specific ruleset details
gh api repos/{owner}/{repo}/rulesets/{ruleset_id}
```

### Audit Script Specification

**Location**: `scripts/ci/audit-branch-protection.sh`

**Inputs** (environment variables):
- `GITHUB_REPOSITORY` (e.g., `os-santiago/homedir`)
- `GITHUB_TOKEN` (scoped for `read:org`, `repo`)
- `BYPASS_ALLOWLIST` (comma-separated GitHub usernames)

**Outputs**:
- **Exit code**: 0 if compliant, 1 if drift detected, 2 if audit failed
- **JSON report**: `audit-report.json` with findings
- **Markdown summary**: `audit-summary.md` for GitHub Actions summary

**Algorithm**:
1. Fetch ruleset for `main` via GitHub API
2. Parse JSON response and extract `rules[]` and `bypass_actors[]`
3. For each control in audit scope:
   - Compare actual state vs expected state
   - If mismatch: record finding with severity
4. Generate JSON report and markdown summary
5. Exit with appropriate code

### Expected Output Format

**JSON Report** (`audit-report.json`):
```json
{
  "audit_timestamp": "2026-06-24T09:00:00Z",
  "repository": "os-santiago/homedir",
  "branch": "main",
  "status": "DRIFT_DETECTED",
  "findings": [
    {
      "control": "required_status_checks",
      "severity": "HIGH",
      "expected": ["PR Quality — Suite / style", "PR Quality — Suite / static", ...],
      "actual": ["PR Quality — Suite / style"],
      "missing": ["PR Quality — Suite / static", ...],
      "recommendation": "Add missing required checks to ruleset-main.json"
    }
  ],
  "summary": {
    "total_controls": 9,
    "compliant": 6,
    "drift": 3,
    "critical_findings": 0,
    "high_findings": 1,
    "medium_findings": 2
  }
}
```

**Markdown Summary** (`audit-summary.md`):
```markdown
## Branch Protection Audit Summary

**Status**: 🔴 DRIFT DETECTED
**Repository**: os-santiago/homedir
**Branch**: main
**Timestamp**: 2026-06-24 09:00:00 UTC

### Findings

| Control | Severity | Status | Details |
|---------|----------|--------|---------|
| Required Status Checks | HIGH | ❌ Drift | Missing 5 of 6 universal checks |
| Deletion Protection | CRITICAL | ✅ Compliant | Enabled |
| Force Push Protection | CRITICAL | ✅ Compliant | Enabled |
| Pull Request Required | CRITICAL | ✅ Compliant | Enabled |
| Conversation Resolution | HIGH | ❌ Drift | Currently disabled |

### Recommendations

1. **HIGH**: Add missing required status checks to `ruleset-main.json`
2. **HIGH**: Enable `required_conversation_resolution` in ruleset

### Compliance Score

**6/9 controls compliant (66.7%)**
```

## Alerting and Escalation

### Alert Channels

| Severity | Alert Target | Response SLA | Auto-Escalation |
|----------|-------------|--------------|-----------------|
| **Critical** | Slack `#security-alerts` + PagerDuty | 1 hour | Escalate to on-call after 2 hours |
| **High** | Slack `#platform-alerts` | 4 hours | Escalate to maintainer after 8 hours |
| **Medium** | GitHub Issue (auto-created) | 24 hours | Escalate to sprint backlog if >3 days |

### Alert Message Format

**Slack message** (critical/high findings):
```
🔴 Branch Protection Drift Detected

Repository: os-santiago/homedir
Branch: main
Findings: 3 drift(s) detected (1 HIGH, 2 MEDIUM)

Critical Issues:
- None

High Issues:
- Required status checks missing (5 of 6 not configured)

View full report: https://github.com/os-santiago/homedir/actions/runs/{run_id}
```

**Auto-created GitHub Issue** (medium findings):
```markdown
## Branch Protection Drift Detected

**Audit timestamp**: 2026-06-24T09:00:00Z
**Severity**: Medium
**Controls affected**: 2

### Findings

1. **Commit message pattern**: Expected Conventional Commits regex not enforced
2. **Bypass mode**: Bypass actor has `always` mode instead of `pull_request`

### Recommended Actions

- [ ] Review findings in [audit report](https://github.com/os-santiago/homedir/actions/runs/{run_id})
- [ ] Update `ruleset-main.json` to correct drift
- [ ] Re-run audit to verify compliance

**Auto-generated by branch protection audit workflow**
```

## Compliance KPIs

### Monthly Governance Debt Metric

**Formula**:
```
Compliance Score = (Compliant Controls / Total Controls) × 100%
Governance Debt = (Critical Findings × 10) + (High Findings × 3) + (Medium Findings × 1)
```

**Target**:
- **Compliance Score**: ≥95% (8.5/9 controls or better)
- **Governance Debt**: ≤5 points

**Reporting**:
- Monthly summary published to `docs/governance/audit-history/YYYY-MM.md`
- Trend chart tracking compliance score over time
- Escalation to leadership if score <90% for 2 consecutive months

### Audit History Tracking

Each audit run appends to monthly log:

**File**: `docs/governance/audit-history/2026-06.md`

```markdown
| Date | Status | Compliance Score | Critical | High | Medium | Report |
|------|--------|-----------------|----------|------|--------|--------|
| 2026-06-24 | DRIFT | 66.7% | 0 | 1 | 2 | [Report](https://github.com/.../runs/123) |
| 2026-06-23 | COMPLIANT | 100% | 0 | 0 | 0 | [Report](https://github.com/.../runs/122) |
```

## Remediation Workflow

When drift is detected:

### 1. Immediate Response (Critical/High)

1. **Triage** (within SLA):
   - Review audit report to understand drift
   - Determine if drift is intentional (e.g., approved temporary bypass) or accidental
2. **Investigate**:
   - Check GitHub audit log for who/when configuration changed
   - Verify if change was approved via PR to `ruleset-*.json`
3. **Remediate**:
   - **If accidental**: Revert to compliant state via PR to `ruleset-main.json`
   - **If intentional**: Document exception in `gate_exceptions.log`, create follow-up issue to remove exception

### 2. Drift Resolution Process

1. Create PR to update `ruleset-main.json` with compliant configuration
2. PR must include:
   - Explanation of drift cause
   - Reference to audit report
   - Verification that change restores compliance
3. Merge PR (triggers post-change audit)
4. Re-run audit to confirm compliance restored
5. Update monthly audit log with remediation

### 3. Exception Handling

If drift is **intentional** (e.g., temporary bypass for emergency):

1. Document in `config/docs/governance/gate_exceptions.log`:
   ```
   2026-06-24 | Drift: required_status_checks reduced | Emergency release bypass | Restoring by 2026-06-26 | @maintainer
   ```
2. Create follow-up issue to restore compliant state
3. Set SLA for remediation (critical: 24h, high: 3d, medium: 7d)
4. Alert maintainer if SLA approaching

## Baseline Requirements

### Canonical Ruleset Configuration

The audit compares against this baseline (from `ruleset-main.json`):

```json
{
  "name": "main branch protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {"include": ["refs/heads/main"], "exclude": []}
  },
  "rules": [
    {"type": "deletion"},
    {"type": "non_fast_forward"},
    {
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": [
          {"context": "PR Quality — Suite / style"},
          {"context": "PR Quality — Suite / static"},
          {"context": "PR Quality — Suite / arch"},
          {"context": "PR Quality — Suite / tests_cov"},
          {"context": "PR Quality — Suite / deps"},
          {"context": "PR CI (Build, Native, SBOM/Scan) / sbom"}
        ],
        "strict_required_status_checks_policy": true
      }
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 1,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true
      }
    },
    {
      "type": "commit_message_pattern",
      "parameters": {
        "operator": "starts_with",
        "pattern": "^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\\[.*\\])?(\\(.*\\))?(!)?: .*"
      }
    }
  ],
  "bypass_actors": [
    {
      "actor_id": <GitHub_user_ID>,
      "actor_type": "RepositoryRole",
      "bypass_mode": "pull_request"
    }
  ]
}
```

### Allowed Bypass Actors

Only the following users/teams are authorized as bypass actors:

| Actor | GitHub Username | Actor Type | Bypass Mode | Justification |
|-------|----------------|------------|-------------|---------------|
| Repository Owner | `scanalesespinoza` | RepositoryRole | `pull_request` | Emergency hotfix authority |

**Drift detection**: If any other actor appears in `bypass_actors[]`, audit raises **CRITICAL** finding.

## Rollout Plan

### Phase 1: Advisory (Weeks 1-2)

- Deploy audit script
- Run daily audits
- Generate reports (no alerting)
- Establish baseline compliance score
- **Goal**: Validate audit logic, fix false positives

### Phase 2: Alerting (Weeks 3-4)

- Enable Slack alerting for CRITICAL findings only
- Manual review of HIGH/MEDIUM findings
- Tune alert thresholds based on noise
- **Goal**: Validate alert routing and SLA feasibility

### Phase 3: Enforcing (Week 5+)

- Enable full alerting (CRITICAL/HIGH/MEDIUM)
- Auto-create GitHub issues for MEDIUM findings
- Enforce monthly compliance KPI
- **Goal**: Full continuous audit enforcement

## Maintenance

### Quarterly Review

Every quarter, maintainers must:

1. Review audit findings from past 3 months
2. Identify recurring drift patterns
3. Update audit controls if new protections added
4. Verify bypass actor allowlist still accurate
5. Update this specification if GitHub API changes

### Audit Script Updates

When updating `scripts/ci/audit-branch-protection.sh`:

1. Create PR with changes to script
2. Run audit in dry-run mode against test repository
3. Verify JSON/markdown output format unchanged (backward compatibility)
4. Document changes in script comments and this specification

## Related Documents

- [Status Check Matrix](./STATUS_CHECK_MATRIX.md) - Defines required checks audited
- [Conversation Resolution Policy](./CONVERSATION_RESOLUTION_POLICY.md) - Review requirements audited
- [Emergency Break-Glass Runbook](./EMERGENCY_BREAK_GLASS_RUNBOOK.md) - Bypass procedures
- `ruleset-main.json` - Canonical configuration baseline

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-06-24 | Claude (via WOS) | Initial specification for issue #853 |

---

**Maintained by**: Platform Engineering
**Review frequency**: Quarterly
**Last reviewed**: 2026-06-24
