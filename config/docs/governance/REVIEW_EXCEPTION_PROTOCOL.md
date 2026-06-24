# Review Exception Protocol

## Purpose

This protocol defines when and how to deviate from standard PR review requirements while maintaining audit trails and minimizing risk.

## When Exceptions Are Justified

### Emergency Scenarios

Immediate exceptions allowed for:

1. **Production Outages**
   - Service completely unavailable
   - Data loss or corruption in progress
   - Security breach actively exploited

2. **Critical Security Patches**
   - Zero-day vulnerability with active exploits
   - Credential leak requiring immediate rotation
   - Emergency security advisory from dependency maintainer

3. **Compliance Violations**
   - Legal requirement with hard deadline
   - Regulatory finding requiring immediate fix
   - Data privacy incident requiring immediate remediation

### Non-Emergency Exceptions

Pre-approved exceptions for:

1. **Automated Dependency Updates**
   - Bot-generated PRs (Dependabot, Renovate)
   - All automated tests pass
   - Security-only updates
   - Minor/patch version bumps with no breaking changes

2. **Documentation-Only Changes**
   - Typo fixes
   - Link updates
   - Formatting improvements
   - No code changes

3. **Revert Operations**
   - Reverting a recently merged PR
   - Addressing regression or incident
   - Original PR followed proper review process

## Exception Types

### Type 1: Reduced Review Count

**When**: Non-critical changes that need expedited merge

**Requirements**:
- Minimum 1 approval (instead of required 2+)
- All conversations still must be resolved
- CI/CD must pass
- Documented justification required

**Example**: Documentation update during incident response

### Type 2: Conversation Resolution Bypass

**When**: Reviewer unavailable and change is time-critical

**Requirements**:
- Author documents why each conversation cannot wait
- Technical lead approval required
- Follow-up issue created to address deferred concerns
- Original reviewer tagged in follow-up issue

**Example**: Reviewer on vacation, critical bug fix needed

### Type 3: Stale Review Override

**When**: Changes after approval were cosmetic/trivial and reviewer unavailable

**Requirements**:
- Changes are purely cosmetic (typos, formatting, comments, log messages)
- Changes <50 lines in reviewed files (aligns with stale review threshold)
- No new files added
- No dependency changes
- No logic or behavior changes
- Author certifies changes address only minor feedback or formatting
- Document specific changes made

**Example**: Fixing typo in comment after approval, reformatting code style

### Type 4: Full Review Bypass (Break-Glass)

**When**: Production emergency requiring immediate merge

**Requirements**:
- On-call technical lead approval
- Full break-glass protocol followed
- Post-merge review scheduled within 24 hours
- Incident report required
- Rollback plan documented

**Example**: Production database connection leak causing outage

## Exception Request Process

### 1. Document Justification

In PR description, add section:

```markdown
## Review Exception Request

**Exception Type**: [Type 1/2/3/4]

**Justification**: 
[Clear explanation of why standard review process cannot be followed]

**Business Impact if Delayed**:
[Specific impact: revenue loss, customer impact, security risk, etc.]

**Risk Assessment**:
- Change size: [lines changed]
- Affected systems: [list]
- Rollback difficulty: [easy/moderate/hard]
- Testing coverage: [%]

**Mitigation**:
[Steps taken to reduce risk despite reduced review]

**Approver**: @[technical-lead-username]
```

### 2. Obtain Approval

| Exception Type | Required Approver | Response Time SLA |
|----------------|-------------------|-------------------|
| Type 1: Reduced Review | Code Owner | 2 hours |
| Type 2: Conversation Bypass | Technical Lead | 4 hours |
| Type 3: Stale Review | Code Owner | 1 hour |
| Type 4: Break-Glass | On-Call Technical Lead | 15 minutes |

### 3. Log Exception

Add entry to `docs/governance/break_glass_exceptions.log`:

```
[YYYY-MM-DD HH:MM UTC] EXCEPTION Type-N: PR #NNNN
Title: [PR title]
Approver: [Name] ([Role])
Justification: [Brief reason]
Impact: [Production/staging/none]
Post-merge actions: [List]
```

### 4. Execute Merge

- Merge using GitHub UI or CLI
- Add comment: "Merged under exception protocol Type-N. Exception logged."
- If Type 4 (Break-Glass): Follow full break-glass runbook

### 5. Post-Merge Actions

Within 24 hours:

- [ ] Create follow-up issue for deferred review items
- [ ] Schedule post-merge review (Type 4 only)
- [ ] Update exception log with outcomes
- [ ] Document lessons learned
- [ ] Update monitoring/alerting if incident-related

## Approval Authority Matrix

| Role | Type 1 | Type 2 | Type 3 | Type 4 |
|------|--------|--------|--------|--------|
| Code Owner | ✅ | ⚠️ | ✅ | ❌ |
| Technical Lead | ✅ | ✅ | ✅ | ⚠️ |
| On-Call Lead | ✅ | ✅ | ✅ | ✅ |
| Maintainer | ✅ | ✅ | ✅ | ✅ |

✅ = Can approve independently  
⚠️ = Can approve if designated on-call  
❌ = Cannot approve

## Escalation Path

If approver unavailable:

1. **Contact next in chain**:
   - Code Owner → Technical Lead → On-Call Lead → Maintainer

2. **Use team communication channels**:
   - Slack: #engineering-alerts (emergency)
   - Slack: #engineering-general (non-emergency)
   - On-call pager (production outage only)

3. **Document escalation attempts**:
   - Who contacted, when, method
   - Responses received
   - Decision made

## Rollback Plan Requirements

Every exception merge must document:

```markdown
## Rollback Plan

**Rollback Method**: [git revert / redeploy previous version / feature flag disable]

**Estimated Rollback Time**: [minutes]

**Rollback Command**:
```bash
# Exact commands to rollback
git revert <commit-sha>
# OR
kubectl rollout undo deployment/<name>
```

**Rollback Verification**:
- [ ] [How to verify rollback successful]
- [ ] [Metrics to monitor]
- [ ] [User-facing changes reverted]

**Data Consistency**:
- [ ] No migration executed
- [ ] OR migration is reversible
- [ ] OR data backup taken before merge
```

## Audit and Monitoring

### Real-Time Monitoring

Exception merges trigger:
- Slack notification to #engineering-alerts
- Log entry automatically created
- Incident timeline updated (if applicable)
- Post-merge review reminder scheduled

### Audit Requirements

Weekly audit of exceptions:
- [ ] All exceptions had proper justification
- [ ] Required approvals obtained
- [ ] Post-merge actions completed
- [ ] Exception log accurate and complete
- [ ] Lessons learned documented

### Metrics Tracked

- Exception count by type (weekly)
- Average time from exception to post-merge review
- Exception-to-incident ratio
- Most common exception justifications
- Approval response times vs SLA

### Trend Analysis

Monthly review:
- Are exceptions increasing? (Process problem)
- Which type most common? (Optimize that path)
- Same approver bottleneck? (Add backup)
- Recurring justifications? (Fix root cause)

## Anti-Patterns to Avoid

❌ **Don't**:
- Use exceptions for poor planning ("we're behind schedule")
- Bypass review to avoid addressing feedback
- Request exceptions habitually (sign of broken process)
- Approve your own exception request
- Skip logging because "it's just a small change"
- Merge without rollback plan documented

✅ **Do**:
- Plan ahead to avoid needing exceptions
- Address review feedback properly
- Fix processes that generate repeated exceptions
- Always get independent approval
- Log every exception, no matter how small
- Always document rollback plan

## Training Requirements

All engineers with merge access must:
- [ ] Read this protocol before first merge
- [ ] Complete exception protocol training
- [ ] Practice break-glass scenario in staging
- [ ] Acknowledge understanding of approval authority

## Protocol Review and Updates

- **Owner**: Technical Lead
- **Review Frequency**: Quarterly or after each Type 4 exception
- **Update Process**: PR with team review and approval

## References

- [PR Review Policy](./PR_REVIEW_POLICY.md)
- [Break-Glass Runbook](./templates/break_glass_runbook.md)
- [Merge Safety Checklist](./templates/merge_safety_checklist.md)
- [Exception Log](./break_glass_exceptions.log)
- [Definition of Done](./DEFINITION_OF_READY_DONE.md)

## Contact

- **On-Call Technical Lead**: Check #oncall channel for current rotation
- **Emergency Escalation**: Use PagerDuty or emergency contact list
- **Policy Questions**: engineering-leads@[organization]
