# Governance Documentation

This directory contains policies, procedures, and guidelines for project governance, code review, and quality assurance.

## Core Policies

### [PR Review Policy](./PR_REVIEW_POLICY.md)
Mandatory review requirements, conversation resolution rules, and stale review handling.

**Key Points**:
- All conversations must be resolved before merge
- Approval requirements vary by risk level (1 for low-risk, 2+ for medium/high, 3 for critical)
- Stale reviews require re-approval after significant changes
- Code owner approval required for Medium+ risk changes

**When to Use**: Before merging any PR, review this policy to ensure compliance.

---

### [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)
Criteria for when issues are ready to work on and when they are considered complete.

**Key Points**:
- DoR: Issue must have clear scope, acceptance criteria, and no duplicates
- DoD: Changes must be reviewed, tested, documented, and pass CI/CD
- All closed issues must link to merged PR(s)

**When to Use**: 
- Before starting work on an issue (verify DoR)
- Before closing an issue (verify DoD)

---

### [Review Exception Protocol](./REVIEW_EXCEPTION_PROTOCOL.md)
Process for deviating from standard review requirements in emergency or special circumstances.

**Key Points**:
- 4 exception types: Reduced Review, Conversation Bypass, Stale Review Override, Break-Glass
- All exceptions require approval and logging
- Emergency merges must document rollback plan
- Post-merge review required for break-glass exceptions

**When to Use**: 
- Production emergencies requiring immediate merge
- Critical security patches
- Automated dependency updates
- Any deviation from standard review process

---

## Templates and Checklists

### [Merge Safety Checklist](./templates/merge_safety_checklist.md)
Pre-merge verification checklist to ensure all review and quality requirements are met.

**When to Use**: Before clicking "Merge" button on any PR.

---

### [Break-Glass Runbook](./templates/break_glass_runbook.md)
Emergency procedure for bypassing normal governance controls during critical incidents.

**When to Use**: Production outages, security emergencies, compliance violations requiring immediate action.

---

### [Post-Incident Review Template](./templates/post_incident_review.md)
Template for documenting incidents and extracting lessons learned.

**When to Use**: After any production incident, break-glass event, or significant issue.

---

## Audit and Compliance

### [Exception Log](./break_glass_exceptions.log)
Historical record of all governance exceptions and emergency merges.

**Purpose**: Audit trail for compliance, trend analysis, and process improvement.

---

## Quick Reference

### Before Starting Work
1. ✅ Verify issue meets [Definition of Ready](./DEFINITION_OF_READY_DONE.md#definition-of-ready-dor)
2. ✅ Review acceptance criteria
3. ✅ Check for dependencies and blockers

### Before Requesting Review
1. ✅ Self-review changes
2. ✅ Run all tests locally
3. ✅ Update documentation
4. ✅ Write clear PR description linking issue

### Before Merging PR
1. ✅ Use [Merge Safety Checklist](./templates/merge_safety_checklist.md)
2. ✅ Verify all conversations resolved
3. ✅ Confirm minimum approvals obtained
4. ✅ Check for stale reviews
5. ✅ Ensure CI/CD passes

### Emergency Situations
1. 🚨 Follow [Break-Glass Runbook](./templates/break_glass_runbook.md)
2. 🚨 Document in [Exception Log](./break_glass_exceptions.log)
3. 🚨 Schedule post-incident review

---

## Review Approval Requirements (Quick Reference)

| Change Type | Min Approvals | Code Owner Required | Additional Requirements |
|-------------|---------------|---------------------|------------------------|
| Docs/typos | 1 | No | - |
| Features | 2 | ≥1 Code Owner | - |
| Breaking changes | 2 | All Code Owners | Security review if applicable |
| Auth/crypto | 3 | All Code Owners | Security team approval |

---

## Common Scenarios

### Scenario: Reviewer is on vacation, need to merge quickly
**Solution**: 
- Request exception Type 2 (Conversation Bypass)
- Get Technical Lead approval
- Create follow-up issue to address deferred concerns
- See [Review Exception Protocol](./REVIEW_EXCEPTION_PROTOCOL.md#type-2-conversation-resolution-bypass)

### Scenario: Fixed typo after approval, need to re-request review?
**Solution**:
- If <20 lines changed: Request Type 3 exception (Stale Review Override)
- If >20 lines: Request re-review from approvers
- See [PR Review Policy](./PR_REVIEW_POLICY.md#stale-review-policy)

### Scenario: Production is down, need emergency merge
**Solution**:
1. Follow [Break-Glass Runbook](./templates/break_glass_runbook.md)
2. Get On-Call Technical Lead approval
3. Document rollback plan in PR
4. Log exception immediately
5. Schedule post-merge review within 24h

### Scenario: Dependabot PR with passing tests
**Solution**:
- Pre-approved for automated bot PRs
- Verify all tests pass
- 1 approval sufficient
- Merge without exception needed
- See [Review Exception Protocol](./REVIEW_EXCEPTION_PROTOCOL.md#automated-dependency-updates)

---

## Policy Ownership and Updates

- **Owner**: Technical Lead / Maintainer
- **Review Frequency**: Quarterly or post-incident
- **Update Process**: PR to governance docs with team review

### Proposing Policy Changes

1. Open issue with `governance` label
2. Describe problem with current policy
3. Propose specific change with rationale
4. Tag technical leads for review
5. Submit PR after consensus reached

---

## Training and Onboarding

New contributors should review in order:

1. ✅ [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)
2. ✅ [PR Review Policy](./PR_REVIEW_POLICY.md)
3. ✅ [Merge Safety Checklist](./templates/merge_safety_checklist.md)
4. ⚠️ [Review Exception Protocol](./REVIEW_EXCEPTION_PROTOCOL.md) (for merge access)
5. 🚨 [Break-Glass Runbook](./templates/break_glass_runbook.md) (for on-call rotation)

---

## Escalation Contacts

- **Code Review Questions**: Ask in PR comments or #engineering-general
- **Policy Interpretation**: Technical Lead
- **Emergency Approval**: On-Call Technical Lead (#oncall channel)
- **Policy Changes**: Open issue with `governance` label

---

## Related Documentation

- Repository: `CONTRIBUTING.md` - Contribution guidelines
- Repository: `CODEOWNERS` - Code ownership definitions
- Security: `docs/security/` - Security policies and procedures
- Incidents: `docs/incidents/` - Post-incident reviews

---

## Metrics and Auditing

We track:
- PR merge time (from open to merge)
- Review exception frequency
- Conversation resolution rate
- Stale review rate
- Time to re-approval

**Audit Schedule**:
- Weekly: Sample 10% of merged PRs
- Monthly: Full audit of High/Critical merges
- Quarterly: Policy effectiveness review

---

## Document History

| Date | Change | Author |
|------|--------|--------|
| 2026-06-24 | Initial governance documentation | Claude (Issue #849) |

---

## Feedback

Have suggestions for improving these policies? 
- Open an issue with the `governance` label
- Propose changes via PR
- Discuss in #engineering-general
