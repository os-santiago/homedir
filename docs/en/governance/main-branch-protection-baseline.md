# Main Branch Protection Baseline

**Status**: Approved  
**Version**: 1.1  
**Last Updated**: 2026-06-23  
**Owner**: Platform Team  
**Related Issues**: #847, #850

## Purpose

This document defines the mandatory baseline configuration for `main` branch protection to ensure production stability, code quality, and controlled change management.

## Mandatory Protection Settings

### 1. Required Status Checks

**Configuration**:
- Require status checks to pass before merging: **ENABLED**
- Require branches to be up to date before merging: **ENABLED**

**Required Checks** (minimum):
- `pr-validation / Quality Gates` - Ensures code quality standards
- `pr-validation / Test Suite` - All tests must pass
- `pr-validation / Security Scan` - No critical/high vulnerabilities

**Rationale**: Prevents broken or untested code from reaching production.

### 2. Conversation Resolution

**Configuration**:
- Require conversation resolution before merging: **ENABLED**

**Rationale**: Ensures all review feedback is addressed before merge.

### 3. Force Push & Deletion Protection

**Configuration**:
- Allow force pushes: **DISABLED**
- Allow deletions: **DISABLED**

**Rationale**: Protects commit history integrity and prevents accidental data loss.

### 4. Administrator Enforcement

**Configuration**:
- Enforce all configured restrictions for administrators: **ENABLED**

**Current Status**: ✅ Already enabled  
**Rationale**: No one bypasses protection rules, ensuring consistent quality gates.

### 5. Linear History

**Configuration**:
- Require linear history: **MANDATORY** (strictly enforced)

**Current Status**: ⚠️ Requires enablement  
**Rationale**: Ensures clean audit trail, simplifies incident tracing, and enables reliable rollbacks. When enabled, only squash and rebase merges are allowed (merge commits are prohibited).

**See Also**: [Merge Strategy Policy](./merge-strategy-policy.md) for complete merge strategy documentation and operational guidelines.

### 6. Pull Request Requirements

**Configuration**:
- Require a pull request before merging: **ENABLED**
- Require at least 1 approval: **ENABLED**
- Dismiss stale pull request approvals when new commits are pushed: **ENABLED**

**Rationale**: All changes undergo code review before merge.

## Current Baseline Gaps

Based on API inspection (\`gh api repos/:owner/:repo/branches/main/protection\`):

| Setting | Required | Current Status | Gap |
|---------|----------|----------------|-----|
| Enforce admins | ✅ ENABLED | ✅ ENABLED | ✅ None |
| Allow force pushes | ❌ DISABLED | ✅ DISABLED | ✅ None |
| Allow deletions | ❌ DISABLED | ✅ DISABLED | ✅ None |
| Required conversation resolution | ✅ ENABLED | ❌ DISABLED | ⚠️ **MUST ENABLE** |
| Required linear history | ✅ ENABLED | ❌ DISABLED | ⚠️ **MUST ENABLE** |
| Required status checks | ✅ ENABLED | 🔍 Unknown | 🔍 Needs verification |
| Required pull request reviews | ✅ ENABLED | 🔍 Unknown | 🔍 Needs verification |

## Emergency Exception Protocol

### When to Use

Emergency exceptions are permitted ONLY in the following scenarios:
1. **Production outage** with customer impact (SEV-1 incident)
2. **Critical security vulnerability** requiring immediate hotfix
3. **Data loss risk** requiring immediate intervention

### Exception Process

1. **Pre-approval**:
   - Incident Commander approval required
   - Document incident ticket number
   - Estimate downtime/impact

2. **Temporary bypass**:
   - Disable specific protection setting via GitHub UI
   - Apply hotfix following normal commit standards
   - Maximum bypass window: **2 hours**

3. **Post-remediation**:
   - Re-enable all protection settings within 1 hour of merge
   - Create follow-up PR with full validation within 24 hours
   - Document exception in post-mortem

4. **Audit trail**:
   - Log exception event in \`docs/en/governance/emergency-exception-log.md\`
   - Include: timestamp, approver, reason, ticket, duration

### Prohibited Actions

Even during emergencies, the following are **NEVER** permitted:
- Disabling \`enforce_admins\`
- Enabling \`allow_force_pushes\` or \`allow_deletions\`
- Bypassing security scans for non-security fixes
- Committing unsigned commits (if signing is enforced)

## Compliance Verification Checklist

Use this checklist to verify baseline compliance:

### Automated Verification (via API)

\`\`\`bash
# Fetch current protection settings
gh api repos/:owner/:repo/branches/main/protection > protection.json

# Verify each setting
jq '.enforce_admins.enabled' protection.json  # Must be true
jq '.allow_force_pushes.enabled' protection.json  # Must be false
jq '.allow_deletions.enabled' protection.json  # Must be false
jq '.required_conversation_resolution.enabled' protection.json  # Must be true
jq '.required_status_checks.strict' protection.json  # Must be true
jq '.required_pull_request_reviews.required_approving_review_count' protection.json  # Must be >= 1
\`\`\`

### Manual Verification

- [ ] Attempt force push to \`main\` → Should be **rejected**
- [ ] Attempt to delete \`main\` → Should be **rejected**
- [ ] Create PR with failing tests → Merge button should be **disabled**
- [ ] Create PR with unresolved conversation → Merge button should be **disabled**
- [ ] Create PR without approval → Merge button should be **disabled**

### Remediation for Non-Compliance

If any check fails:

1. **Immediate action**: Apply the required setting via GitHub UI or API
2. **Verification**: Re-run the automated verification script
3. **Documentation**: Update this baseline if new settings are added
4. **Notification**: Alert platform team of compliance drift

## Setting Application (via API)

To apply this baseline programmatically:

\`\`\`bash
# Enable conversation resolution
gh api -X PATCH repos/:owner/:repo/branches/main/protection \
  -f required_conversation_resolution[enabled]=true

# Enforce status checks
gh api -X PATCH repos/:owner/:repo/branches/main/protection/required_status_checks \
  -f strict=true \
  -f contexts[]="pr-validation / Quality Gates" \
  -f contexts[]="pr-validation / Test Suite" \
  -f contexts[]="pr-validation / Security Scan"

# Enforce pull request reviews
gh api -X PATCH repos/:owner/:repo/branches/main/protection/required_pull_request_reviews \
  -f required_approving_review_count=1 \
  -f dismiss_stale_reviews=true
\`\`\`

## Review Schedule

This baseline must be reviewed:
- **Quarterly**: Verify settings are still applied
- **After major incidents**: Assess if baseline prevented/allowed the issue
- **When new CI checks are added**: Update required status checks list

## References

- [GitHub Branch Protection Documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
- [Merge Strategy Policy](./merge-strategy-policy.md) - Canonical merge strategy and linear history requirements
- Issue #850: [Main Governance] Linear history rule and canonical merge strategy
- Issue #847: [Main Governance] Baseline de proteccion obligatoria para rama main
- Issue #838: Parent governance audit issue

## Changelog

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-06-23 | 1.1 | Linear history changed from recommended to mandatory | Platform Team |
| 2026-06-22 | 1.0 | Initial baseline definition | Platform Team |
