# Emergency Break-Glass Runbook

**Document Status**: Active  
**Last Updated**: 2026-06-24  
**Owner**: Engineering Leadership  
**Relates To**: Issue #852, Parent Issue #838

## Purpose

This runbook defines the controlled emergency bypass process for production incidents requiring immediate code changes to `main` branch. It ensures that exceptional circumstances do not compromise traceability, accountability, or post-incident learning.

---

## When to Use Break-Glass

### Qualifying Emergencies

Break-glass is **ONLY** authorized for:

| Emergency Type | Criteria | Example |
|---------------|----------|---------|
| **P0 Production Outage** | Complete service unavailability affecting >50% of users | Database connection pool exhausted, all requests timing out |
| **Active Security Exploit** | Confirmed exploit in progress, actively being used | CVE being exploited in the wild, attacker gaining unauthorized access |
| **Data Loss Risk** | Imminent or ongoing data corruption/loss | Migration bug deleting user data, replication lag causing corruption |
| **Broken Main Branch** | Main branch completely broken, blocking all development | Bad merge broke build, no PRs can merge, all CI failing |

### Non-Qualifying Scenarios

Do **NOT** use break-glass for:

- ❌ Feature requests (even from executives)
- ❌ Performance degradation (<50% throughput drop)
- ❌ Non-critical bugs affecting small user subset
- ❌ Missed deadlines or "urgent" business asks
- ❌ Failed status checks on otherwise safe code
- ❌ Reviewer unavailability during normal business hours

**Rule of Thumb**: If you can wait 2 hours for normal review process, it's not a break-glass scenario.

---

## Break-Glass Authorization

### Required Approvals

| Incident Severity | Required Approvers | Response Time SLA |
|------------------|-------------------|-------------------|
| **P0 - Complete Outage** | 1x Engineering Lead OR VP Engineering | <15 minutes |
| **Active Security Exploit** | 1x Security Lead + 1x Engineering Lead | <30 minutes |
| **Data Loss** | 1x Engineering Lead + 1x DBA/Data Owner | <30 minutes |
| **Broken Main** | 1x CI Owner OR Engineering Manager | <1 hour |

**Approval must be explicit and in writing** (PR comment, Slack thread, or incident ticket).

### Temporal Window

Break-glass approval is valid for:
- **4 hours** from approval timestamp
- **Single merge only** (one hotfix PR per approval)
- **Must re-request** if new incident or different fix needed

After 4 hours, approval expires and standard process resumes.

---

## Break-Glass Process

### Step 1: Declare Incident

**Before requesting break-glass, create incident ticket:**

```markdown
**Incident ID**: INC-YYYY-MM-DD-NNN  
**Severity**: P0 / Security / Data Loss / Broken Main  
**Detected**: YYYY-MM-DD HH:MM UTC  
**Impact**: [Describe user/business impact with metrics]  
**Root Cause Hypothesis**: [Initial assessment]  
**Responders**: @oncall-engineer, @engineering-lead  
```

**Channels to notify**:
- Create GitHub Issue with `incident` label
- Post in Slack `#incidents` channel
- Page on-call Engineering Lead via PagerDuty (P0 only)

### Step 2: Prepare Emergency PR

Create PR with **emergency template**:

```markdown
## 🚨 EMERGENCY BREAK-GLASS REQUEST 🚨

**Incident**: [Link to incident ticket INC-YYYY-MM-DD-NNN]  
**Severity**: P0 / Security / Data Loss / Broken Main  
**Detected**: YYYY-MM-DD HH:MM UTC  
**Impact**: [Concise summary with metrics]  

### Time Sensitivity
**Why this cannot wait for standard review**: [Explain urgency]  
**Estimated downtime if delayed**: [Hours/minutes]  
**Users affected**: [Number or percentage]  

### Change Summary
**What this PR does**: [1-2 sentences]  
**Files changed**: [List key files]  
**Risk level**: Low / Medium / High  
**Rollback plan**: [How to revert if this makes it worse]  

### Pre-Merge Checklist
- [ ] Incident ticket created and linked above
- [ ] Engineering Lead approval obtained (see comments below)
- [ ] Rollback plan documented and tested
- [ ] Post-merge follow-up issue created: #XXX
- [ ] All mandatory checks passed (security scan, build, critical tests)

### Bypasses Requested
- [ ] Skip conversation resolution ← **Why**: No time for multi-round review
- [ ] Skip minimum approval count ← **Why**: Reviewers unavailable
- [ ] Skip non-critical status checks ← **Why**: [List specific checks and justification]

**Approval Required By**: @engineering-lead OR @vp-engineering  
**Expires**: YYYY-MM-DD HH:MM UTC (4 hours from now)
```

### Step 3: Obtain Explicit Approval

**Engineering Lead posts approval comment**:

```markdown
## ✅ BREAK-GLASS APPROVAL GRANTED

**Approved by**: @engineering-lead-name  
**Approval timestamp**: YYYY-MM-DD HH:MM UTC  
**Incident verified**: INC-YYYY-MM-DD-NNN  

**Authorized bypasses**:
- ✅ Merge with 0 approvals (emergency override)
- ✅ Skip conversation resolution
- ✅ Skip advisory checks: [list specific checks]
- ❌ Must still pass: Security scan, build verification, critical unit tests

**Conditions**:
1. Merge window: Next 4 hours only
2. Post-merge: Create follow-up issue for proper review within 24 hours
3. Post-merge: Run skipped checks and document results
4. Post-incident: Retrospective within 48 hours (issue #XXX created)

**Risk acknowledgment**: I accept responsibility for bypassing standard gates. If this breaks production further, immediate revert is authorized.

**Expiration**: YYYY-MM-DD HH:MM UTC
```

### Step 4: Merge and Monitor

1. **Author**: Click "Merge pull request" (GitHub admin override enabled for incident)
2. **CI/CD**: Automatic deployment to production (if configured) OR manual deploy following incident runbook
3. **Monitoring**: Watch dashboards for next 30 minutes:
   - Error rates
   - Request latency
   - User-facing metrics
   - Database health
4. **Slack Update**: Post merge confirmation in `#incidents` channel:
   ```
   🚨 Emergency fix merged: PR #XXX
   Monitoring production for next 30 min. Will update with impact assessment.
   ```

### Step 5: Verify Fix or Revert

**Within 15 minutes of merge**, assess:

| Outcome | Action | Owner |
|---------|--------|-------|
| ✅ **Incident resolved** | Post "All clear" in incident channel | Incident Commander |
| ⚠️ **Partial improvement** | Document remaining issues, create follow-up tasks | Incident Commander |
| ❌ **No improvement or worse** | **REVERT IMMEDIATELY** (see revert procedure below) | Incident Commander |

**Revert Procedure**:
```bash
# On main branch
git revert <merge-commit-sha> --mainline 1
git push origin main

# Post in Slack
🚨 Emergency fix reverted due to [reason]. Incident still active.
```

---

## Mandatory Evidence Collection

All break-glass incidents require:

### During Incident

- [x] **Incident ticket** with impact metrics
- [x] **Explicit approval** from authorized approver (written, timestamped)
- [x] **Rollback plan** documented in PR description
- [x] **Monitoring screenshots** before/after merge
- [x] **Slack thread** in `#incidents` with timeline

### Post-Merge (Within 24 Hours)

- [x] **Follow-up issue** created for proper code review
- [x] **Skipped checks** run manually and results documented
- [x] **Audit log entry** added to `governance/gate_exceptions.log`
- [x] **Incident status** updated to "Resolved" or "Investigating"

### Post-Incident (Within 48 Hours)

- [x] **Retrospective issue** created with `postmortem` label
- [x] **Root cause analysis** completed (5-Whys or equivalent)
- [x] **Preventive actions** documented and assigned

---

## Audit and Accountability

### Exception Log Format

Every break-glass use must be logged in `governance/gate_exceptions.log`:

```markdown
---
incident_id: INC-2026-06-24-001
date: 2026-06-24T03:14:00Z
pr_number: 999
severity: P0
approver: @engineering-lead
bypasses:
  - conversation_resolution
  - minimum_approvals
  - advisory_checks: [code_coverage, dependency_review]
duration_minutes: 47
outcome: resolved
rollback_required: false
follow_up_issue: 1000
retrospective_issue: 1001
---

## Summary
Database connection pool exhaustion caused complete service outage. Emergency fix increased pool size from 50 to 200 connections. Incident resolved 47 minutes after detection.

## Root Cause
Gradual traffic increase over 2 weeks exceeded connection pool capacity. No alerting threshold configured for pool utilization.

## Preventive Actions
1. Issue #1002: Add connection pool utilization alerts (P1)
2. Issue #1003: Auto-scaling connection pool based on load (P2)
3. Issue #1004: Load testing for next capacity increase (P2)
```

### KPIs and Review

**Monthly Review** (Engineering Manager):
- Break-glass usage count (target: <2 per month)
- Average time from approval to merge (target: <30 min)
- Revert rate (target: <10% of emergency merges)
- Follow-up issue closure rate (target: 100% within 7 days)

**Quarterly Review** (Engineering Leadership):
- Root cause pattern analysis (are same issues recurring?)
- Process effectiveness (did break-glass actually help?)
- Gate improvements (can we prevent future emergencies?)

**Red Flags**:
- >5 break-glass events per month → Process misuse investigation
- Same type of incident recurring → Systemic fix needed
- Follow-up issues not closed within 7 days → Accountability issue

---

## Post-Incident Review (Mandatory)

### Retrospective Template

Create issue with `postmortem` label within 48 hours:

```markdown
**Title**: Postmortem: [Incident brief description]  
**Incident ID**: INC-YYYY-MM-DD-NNN  
**Emergency PR**: #XXX  
**Date**: YYYY-MM-DD  
**Severity**: P0 / Security / Data Loss / Broken Main  

## Timeline
- **HH:MM UTC** - Incident detected
- **HH:MM UTC** - Break-glass approval obtained
- **HH:MM UTC** - Emergency fix merged
- **HH:MM UTC** - Incident resolved / reverted

## Impact
- **Users affected**: [Number or percentage]
- **Duration**: [Minutes/hours of degraded service]
- **Revenue impact**: [If applicable]
- **Data loss**: [If any]

## Root Cause
[5-Whys analysis or equivalent]

1. Why did the incident occur? → [Answer]
2. Why did that happen? → [Answer]
3. Why wasn't it caught earlier? → [Answer]
4. Why didn't alerting catch it? → [Answer]
5. Why wasn't there a safeguard? → [Answer]

## What Went Well
- [Things that worked during incident response]

## What Went Wrong
- [Things that didn't work or could be improved]

## Preventive Actions
- [ ] **P0**: [Action] - Owner: @person - Due: YYYY-MM-DD
- [ ] **P1**: [Action] - Owner: @person - Due: YYYY-MM-DD
- [ ] **P2**: [Action] - Owner: @person - Due: YYYY-MM-DD

## Process Improvements
- [ ] Update runbook with lessons learned
- [ ] Add monitoring/alerting gap
- [ ] Update incident response training
```

### Accountability

- **Incident Commander**: Owns retrospective completion
- **Engineering Manager**: Reviews and approves preventive actions
- **Engineering Leadership**: Tracks action item closure

**Failure to complete retrospective within 48 hours** escalates to VP Engineering.

---

## Anti-Patterns (Do NOT Do This)

### ❌ Silent Break-Glass

**Wrong**:
```
# Merge without approval comment, no incident ticket, no audit trail
git merge --no-verify
```

**Right**:
- Create incident ticket
- Obtain explicit written approval
- Document in PR and audit log
- Conduct retrospective

### ❌ Scope Creep

**Wrong**:
```markdown
Emergency PR: Fix database connection pool
[... also refactors auth middleware, updates dependencies, reformats code ...]
```

**Right**: Emergency PR contains **ONLY** the minimal fix for the incident. Other improvements go in separate PRs post-incident.

### ❌ Approval by Proximity

**Wrong**:
```
"I asked Jane in the hallway and she said it's fine."
```

**Right**: Approval must be **written and timestamped** in PR comment, Slack thread, or incident ticket. Verbal approval is not sufficient for audit trail.

### ❌ Perpetual Emergency

**Wrong**:
```
"Everything is P0, we always need break-glass."
```

**Right**: If you're using break-glass more than twice per month, the problem is systemic, not procedural. Escalate to Engineering Leadership for process improvement, not more exceptions.

---

## Escalation Paths

### If Approver Is Unavailable

| Time | Action | Contact |
|------|--------|---------|
| 0-15 min | Page primary Engineering Lead | PagerDuty |
| 15-30 min | Page backup Engineering Lead | PagerDuty |
| 30-45 min | Page VP Engineering | PagerDuty + direct call |
| 45-60 min | Page CTO (absolute last resort) | Direct call |

**Critical**: Do not merge without approval. If all approvers unreachable after 60 minutes, escalate to CTO for guidance.

### If Break-Glass Makes It Worse

1. **Immediate**: Revert the merge (see revert procedure above)
2. **Within 5 min**: Post revert confirmation in `#incidents`
3. **Within 15 min**: Reassess incident and create new emergency PR if needed
4. **Within 1 hour**: Document what went wrong in incident ticket
5. **Within 48 hours**: Retrospective includes "why did emergency fix fail" analysis

---

## Related Documentation

- [Conversation Resolution Policy](./CONVERSATION_RESOLUTION_POLICY.md) - Standard review requirements this bypasses
- [Release Gates](./RELEASE_GATES.md) - Quality gates from PR to production
- [Status Check Matrix](./STATUS_CHECK_MATRIX.md) - Required checks by change type
- [Reviewer Checklist](./REVIEWER_CHECKLIST.md) - What reviewers normally verify

---

## Appendix: Example Break-Glass Scenarios

### Scenario 1: Database Connection Pool Exhaustion (P0)

**Incident**: All API requests timing out, 100% error rate  
**Root Cause**: Connection pool maxed at 50, traffic spike needs 200  
**Emergency Fix**: Increase pool size in config  
**Approval**: Engineering Lead (15 min response)  
**Outcome**: Service restored in 22 minutes total  
**Follow-up**: Load testing, auto-scaling implementation  

### Scenario 2: Active SQL Injection Exploit (Security)

**Incident**: Attacker exfiltrating user data via unparameterized query  
**Root Cause**: String concatenation in search endpoint  
**Emergency Fix**: Replace with parameterized query  
**Approval**: Security Lead + Engineering Lead (12 min response)  
**Outcome**: Exploit closed, incident reported to security team  
**Follow-up**: Full codebase audit for SQL injection vulnerabilities  

### Scenario 3: Broken Main Branch (CI Failure)

**Incident**: All PRs failing due to bad merge, blocking development  
**Root Cause**: Merge conflict resolution broke import paths  
**Emergency Fix**: Revert bad merge  
**Approval**: CI Owner (8 min response)  
**Outcome**: Main branch green again, PRs can merge  
**Follow-up**: Update CI to catch import errors pre-merge  

---

**Runbook Version**: 1.0  
**Approved By**: [Pending Review]  
**Effective Date**: [TBD]  
**Next Review**: 2026-09-24
