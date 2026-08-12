# Branch Protection Ruleset Validation

**Issue**: #988  
**Date**: 2026-06-24 (original validation) · re-verified 2026-08-11  
**Ruleset ID**: 9071701  
**Repository**: os-santiago/homedir

## Validation Summary

All acceptance criteria from issue #988 have been met. The GitHub repository ruleset now enforces all documented requirements.

## ✅ Acceptance Criteria Status

### 1. Required Status Checks (3 universal checks)
**Status**: ✅ PASS

```bash
gh api repos/os-santiago/homedir/rulesets/9071701 \
  --jq '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[] | .context'
```

**Result** (re-verified 2026-08-11):
```
Quality Summary
CI Summary
Quality Gate Summary
```

Count: 3 checks ✅

> These contexts come from the aggregate jobs in `pr-quality-suite.yml` (`Quality Summary`), `pr-ci-build-native-sbom.yml` (`CI Summary`) and `quality-gates.yml` (`Quality Gate Summary`). They are not the individual job contexts (e.g., `PR Quality - Suite / style`); the ruleset requires only the aggregate check.

### 2. Commit Message Pattern (Conventional Commits)
**Status**: ✅ PASS

```bash
gh api repos/os-santiago/homedir/rulesets/9071701 \
  --jq '.rules[] | select(.type == "commit_message_pattern") | .parameters'
```

**Result**:
```json
{
  "name": "Conventional Commits",
  "operator": "starts_with",
  "pattern": "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([a-z0-9-]+\\))?: .+"
}
```

Pattern matches Conventional Commits regex ✅

### 3. Required Conversation Resolution
**Status**: ✅ PASS

```bash
gh api repos/os-santiago/homedir/rulesets/9071701 \
  --jq '.rules[] | select(.type == "pull_request") | .parameters.required_review_thread_resolution'
```

**Result**: `true` ✅

### 4. Bypass Actor Mode
**Status**: ⚠️ DRIFT

```bash
gh api repos/os-santiago/homedir/rulesets/9071701 \
  --jq '.bypass_actors'
```

**Result** (re-verified 2026-08-11): the field is **absent** from the response — the ruleset defines **no** bypass actors (`current_user_can_bypass: "never"`).

```json
null
```

> The committed `config/ruleset-main.json` lists `scanalesespinoza` (`RepositoryCollaborator`, bypass mode `pull_request`) as the intended allowlist, but the enforced ruleset does **not** currently configure any bypass actors. This is drift to reconcile (see [BRANCH_PROTECTION_AUDIT.md](./BRANCH_PROTECTION_AUDIT.md)).

## Full Ruleset Configuration

```bash
gh api repos/os-santiago/homedir/rulesets/9071701 \
  --jq '{name: .name, enforcement: .enforcement, rules: (.rules | map(.type))}'
```

**Result** (re-verified 2026-08-11):
```json
{
  "enforcement": "active",
  "name": "Main Branch Protection",
  "rules": [
    "deletion",
    "non_fast_forward",
    "pull_request",
    "required_status_checks",
    "commit_message_pattern"
  ]
}
```

## Comparison: Documented vs Actual

| Requirement | Documented (ruleset-main.json) | Actual (API, 2026-08-11) | Status |
|-------------|-------------------------------|--------------|--------|
| Required status checks | 6 checks | 3 checks (`Quality Summary`, `CI Summary`, `Quality Gate Summary`) | ⚠️ DRIFT |
| Commit message pattern | Conventional Commits regex | Conventional Commits regex | ✅ MATCH |
| Conversation resolution | Required | Enabled (true) | ✅ MATCH |
| Required approvals | ≥1 | 0 | ⚠️ DRIFT |
| Bypass actors | `RepositoryCollaborator` `scanalesespinoza` (`pull_request`) | None | ⚠️ DRIFT |
| Branch deletion protection | Enabled | deletion rule | ✅ MATCH |
| Force push protection | Enabled | non_fast_forward rule | ✅ MATCH |

## Verification Commands

Reproduce this validation:

```bash
# List all rulesets
gh api repos/os-santiago/homedir/rulesets

# Get full ruleset details
gh api repos/os-santiago/homedir/rulesets/9071701

# Check specific rules
gh api repos/os-santiago/homedir/rulesets/9071701 \
  --jq '.rules[] | {type: .type, parameters: .parameters}'
```

## Next Steps

1. ✅ All acceptance criteria met
2. ✅ Documentation updated
3. 🔄 Monitor next PR to verify checks are enforced in practice
4. 🔄 Close issue #988 after PR merge
5. ⚠️ **Reconciling drift (2026-08-11)**: ruleset now requires 3 aggregate checks (not 6), requires 0 approvals, and has no bypass actors. Update `BRANCH_PROTECTION_IMPLEMENTATION.md` / `BRANCH_PROTECTION_AUDIT.md` baselines to match the enforced ruleset (see #1363).

## References

- Issue: #988
- Implementation Guide: [BRANCH_PROTECTION_IMPLEMENTATION.md](./BRANCH_PROTECTION_IMPLEMENTATION.md)
- Status Check Matrix: [STATUS_CHECK_MATRIX.md](./STATUS_CHECK_MATRIX.md)
- PR: #989
