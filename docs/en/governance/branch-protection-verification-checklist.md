# Branch Protection Verification Checklist

**Purpose**: Quarterly verification that \`main\` branch protection baseline is correctly applied.

**Last Verified**: [YYYY-MM-DD]  
**Verified By**: [Name]  
**Status**: [PASS / FAIL / PARTIAL]

## Automated Checks

Run the verification script and record results:

\`\`\`bash
# Download current protection settings
gh api repos/:owner/:repo/branches/main/protection > /tmp/protection.json

# Run all checks
./scripts/verify-branch-protection.sh
\`\`\`

### Protection Settings Verification

| Setting | Expected | Actual | Status | Notes |
|---------|----------|--------|--------|-------|
| \`enforce_admins.enabled\` | \`true\` | | ⬜ | Admins must follow rules |
| \`allow_force_pushes.enabled\` | \`false\` | | ⬜ | Force push prohibited |
| \`allow_deletions.enabled\` | \`false\` | | ⬜ | Branch deletion prohibited |
| \`required_conversation_resolution.enabled\` | \`true\` | | ⬜ | All comments must be resolved |
| \`required_status_checks.strict\` | \`true\` | | ⬜ | Branch must be up-to-date |
| \`required_pull_request_reviews.required_approving_review_count\` | \`>= 1\` | | ⬜ | At least 1 approval required |
| \`required_pull_request_reviews.dismiss_stale_reviews\` | \`true\` | | ⬜ | New commits dismiss approvals |

### Required Status Checks

| Check Name | Configured | Status | Notes |
|------------|------------|--------|-------|
| \`pr-validation / Quality Gates\` | | ⬜ | |
| \`pr-validation / Test Suite\` | | ⬜ | |
| \`pr-validation / Security Scan\` | | ⬜ | |

## Manual Verification Tests

Perform these manual tests to verify enforcement:

### Test 1: Force Push Rejection

\`\`\`bash
# Attempt force push to main (should fail)
git checkout main
git commit --allow-empty -m "test: force push verification"
git push origin main --force
\`\`\`

- [ ] **Expected**: Remote rejected (protected branch hook)
- [ ] **Actual**: _______________
- [ ] **Pass/Fail**: ___

### Test 2: Branch Deletion Rejection

\`\`\`bash
# Attempt to delete main branch (should fail)
git push origin --delete main
\`\`\`

- [ ] **Expected**: Remote rejected (protected branch)
- [ ] **Actual**: _______________
- [ ] **Pass/Fail**: ___

### Test 3: Unresolved Conversation Block

1. Create test PR with 1 file change
2. Request reviewer to add a comment (do not resolve)
3. Attempt to merge

- [ ] **Expected**: Merge button disabled with message "Conversations must be resolved"
- [ ] **Actual**: _______________
- [ ] **Pass/Fail**: ___

### Test 4: Failing Status Check Block

1. Create test PR that intentionally breaks a test
2. Wait for CI to complete
3. Attempt to merge

- [ ] **Expected**: Merge button disabled with message "Required checks have not passed"
- [ ] **Actual**: _______________
- [ ] **Pass/Fail**: ___

### Test 5: Missing Approval Block

1. Create test PR (do not request reviews)
2. Attempt to merge without approval

- [ ] **Expected**: Merge button disabled with message "Review required"
- [ ] **Actual**: _______________
- [ ] **Pass/Fail**: ___

### Test 6: Stale Approval Dismissal

1. Create test PR and get approval
2. Push a new commit to the same PR
3. Check approval status

- [ ] **Expected**: Previous approval is dismissed
- [ ] **Actual**: _______________
- [ ] **Pass/Fail**: ___

## Remediation Steps (If Failures Detected)

### Failed Check: [Setting Name]

**Issue**: _______________ 
**Impact**: _______________  
**Remediation**:

\`\`\`bash
# Apply fix via API or GitHub UI
gh api -X PATCH repos/:owner/:repo/branches/main/protection \
  -f [setting_name]=[correct_value]
\`\`\`

**Re-verification**:
- [ ] Setting applied
- [ ] Manual test passed
- [ ] API verification passed

## Sign-off

- [ ] All automated checks passed
- [ ] All manual tests passed
- [ ] Deviations documented and approved
- [ ] Remediation completed (if applicable)

**Verified By**: _______________ (Name, Date)  
**Approved By**: _______________ (Platform Lead, Date)

## Next Review Date

Scheduled: **[YYYY-MM-DD]** (3 months from last verification)
