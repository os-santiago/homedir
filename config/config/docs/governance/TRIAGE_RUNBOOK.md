# External Contribution Triage Runbook

## Purpose

This document defines the end-to-end workflow for triaging external contributions (issues and PRs) from intake through resolution, with explicit ownership by domain and SLA enforcement.

## Triage Workflow

```
┌─────────────┐
│   Intake    │  Issue/PR opened by external contributor
└──────┬──────┘
       │
       v
┌─────────────┐
│ Validation  │  Check completeness, reproducibility, scope
└──────┬──────┘
       │
       v
┌─────────────┐
│Classification│ Assign severity, priority, domain labels
└──────┬──────┘
       │
       v
┌─────────────┐
│Prioritization│ Apply SLA, route to owner
└──────┬──────┘
       │
       v
┌─────────────┐
│ Assignment  │  Owner claims issue, sets status
└──────┬──────┘
       │
       v
┌─────────────┐
│ Resolution  │  Work completed, PR merged, issue closed
└──────┬──────┘
       │
       v
┌─────────────┐
│   Closure   │  Verify acceptance criteria, notify contributor
└─────────────┘
```

## Stage Definitions

### 1. Intake

**Trigger**: Issue or PR opened by external contributor

**Actions**:
1. Automated: `status/triage` label applied
2. Automated: Issue added to triage board
3. Human/AI: Review within Response SLA (varies by priority)

**Duration**: Immediate (automated)

### 2. Validation

**Owner**: Triage on-call rotation (see [On-Call Schedule](#on-call-schedule))

**Actions**:
1. **Check completeness**:
   - [ ] Title is descriptive (not "bug" or "issue")
   - [ ] Description provides context (what/why/how)
   - [ ] For bugs: Steps to reproduce, expected vs actual behavior
   - [ ] For features: Use case, acceptance criteria
2. **Check reproducibility** (bugs only):
   - [ ] Can reproduce locally OR contributor provides environment details
   - [ ] Logs/screenshots attached if UI issue
3. **Check scope**:
   - [ ] Single issue (not multiple unrelated requests)
   - [ ] Within project scope (not duplicate, not support question)

**Outcomes**:
- ✅ **Valid**: Proceed to Classification
- ❌ **Invalid**: Close with label `status/invalid`, comment explaining why
- ⏸️ **Needs info**: Add label `status/needs-info`, request clarification, wait for response (7-day timeout)

**SLA**: Within triage rotation shift (see Response SLA by priority)

### 3. Classification

**Owner**: Triage on-call OR domain owner (if obvious)

**Actions**:
1. **Assign severity** (S0-S4) per [Severity/Priority Contract](./SEVERITY_PRIORITY_CONTRACT.md)
2. **Assign priority** (P0-P4) based on Impact × Urgency matrix
3. **Assign domain label** (see [Domain Ownership Matrix](#domain-ownership-matrix))
4. **Assign type label**: `type/bug`, `type/feature`, `type/docs`, `type/question`
5. Remove `status/triage` label

**Labels after classification**:
- `severity/<level>`
- `priority/<level>`
- `domain/<domain>`
- `type/<type>`

**SLA**: Same as Validation (within Response SLA)

### 4. Prioritization

**Owner**: Domain owner (auto-assigned by domain label)

**Actions**:
1. **Validate classification**: Confirm severity/priority correct
2. **Adjust priority** if needed (e.g., escalate if revenue-impacting)
3. **Set milestone** (for P0/P1) or add to backlog (P2/P3/P4)
4. **Check dependencies**: Link to blocking/blocked-by issues

**SLA**: Within 1 business day of classification

### 5. Assignment

**Owner**: Domain owner OR delegate

**Actions**:
1. **Assign to developer** (human or AI agent)
2. **Set status**: `status/in-progress`
3. **Comment with ETA**: "Assigned to @dev1. Expected resolution: YYYY-MM-DD"
4. **Create child issues** if work requires breakdown (per [Parent/Child/Epic Standard](./PARENT_CHILD_EPIC_STANDARD.md))

**SLA**: Within assignment SLA (varies by priority, see table below)

### 6. Resolution

**Owner**: Assigned developer

**Actions**:
1. **Implement fix/feature**
2. **Create PR** linking issue (`Closes #NNN`)
3. **Pass all required checks** (per [Status Check Matrix](./STATUS_CHECK_MATRIX.md))
4. **Request review** from domain owner or peer
5. **Merge PR** after approval

**SLA**: Within resolution target (varies by priority)

### 7. Closure

**Owner**: Domain owner (verifies) OR developer (closes)

**Actions**:
1. **Verify acceptance criteria** met (for features) or bug resolved (for bugs)
2. **Close issue** with comment:
   ```markdown
   Resolved in PR #<pr-number>. 
   
   [Brief summary of resolution]
   
   Thank you for contributing! 🎉
   ```
3. **Remove `status/in-progress`**, **add `status/resolved`**
4. **Update parent issue** (if applicable) per Parent/Child standard

**SLA**: Within 1 business day of PR merge

## Domain Ownership Matrix

Each domain has a primary owner responsible for triage, prioritization, and assignment.

| Domain Label | Scope | Primary Owner | Backup Owner | Typical SLA |
|--------------|-------|---------------|--------------|-------------|
| `domain/backend` | Java/Kotlin backend, APIs, business logic | @backend-lead | @backend-dev1 | P1: 2 days |
| `domain/frontend` | HTML/CSS/JS, UI components, templates | @frontend-lead | @frontend-dev1 | P1: 2 days |
| `domain/security` | Security policies, auth, CSP, secrets | @security-lead | @platform-lead | P0: 4 hours |
| `domain/infra` | CI/CD, deployment, workflows, monitoring | @platform-lead | @devops-dev1 | P0: 4 hours |
| `domain/docs` | Documentation, README, guides | @docs-lead | @any-contributor | P2: 1 week |
| `domain/tests` | Test infrastructure, coverage, quality | @qa-lead | @backend-lead | P2: 1 week |
| `domain/database` | Schema, migrations, queries | @backend-lead | @dba | P1: 2 days |

**Ownership responsibilities**:
1. Monitor issues with your domain label
2. Respond within Response SLA
3. Assign to yourself or delegate to team member
4. Escalate P0/P1 if cannot meet Resolution Target

**Coverage**: If primary owner unavailable (PTO, OOO), backup owner takes triage rotation.

## SLA Table

SLAs from [Severity/Priority Contract](./SEVERITY_PRIORITY_CONTRACT.md), restated here for triage context:

| Priority | Response SLA | Assignment SLA | Resolution Target | Escalation |
|----------|-------------|----------------|-------------------|------------|
| **P0** (Critical) | 15 minutes | 30 minutes | <4 hours | Page on-call immediately |
| **P1** (High) | 2 hours | 4 hours | <2 business days | Notify domain lead if >1 day |
| **P2** (Medium) | 1 business day | 2 business days | <1 week | Notify maintainer if >1 week |
| **P3** (Low) | 3 business days | 1 week | <1 month | No escalation |
| **P4** (Backlog) | No SLA | No SLA | Opportunistic | No escalation |

**Business hours**: Monday-Friday 09:00-17:00 local time  
**P0 exception**: 24/7 on-call coverage (weekends/holidays)

## Escalation Protocol

### When to Escalate

Escalate when:
1. **SLA at risk**: Issue approaching SLA deadline and cannot be resolved in time
2. **Blocked**: Issue blocked by dependency and cannot proceed
3. **Scope expansion**: Issue discovered to be larger than initially classified
4. **Priority dispute**: Contributor or reviewer disagrees with priority assignment

### Escalation Paths

| Scenario | Escalate To | Method | Expected Response Time |
|----------|-------------|--------|------------------------|
| **P0 SLA miss** | On-call engineer | PagerDuty page | 15 minutes |
| **P1 SLA miss** | Domain lead | Slack `@domain-lead` in `#triage` | 2 hours |
| **P2 SLA miss** | Maintainer | GitHub issue comment + Slack | 1 business day |
| **Technical blocker** | Domain expert | Slack `#<domain>` channel | 4 hours |
| **Priority dispute** | Maintainer | GitHub comment with justification | 1 business day |

### Escalation Template

When escalating in GitHub issue:

```markdown
## Escalation Notice

**Escalated to**: @maintainer  
**Reason**: SLA at risk — P1 issue open for 3 days, resolution target is 2 days  
**Blocker**: Waiting on API key from external service (requested 2024-06-20)  
**Request**: Help expedite API key request OR approve de-prioritization to P2

cc @domain-lead
```

## On-Call Schedule

### Triage Rotation

**Rotation period**: 1 week (Monday 00:00 - Sunday 23:59 UTC)  
**Rotation members**: Domain owners + maintainers  
**Schedule**: Published in `docs/governance/triage-schedule.md` (updated monthly)

**On-call responsibilities**:
1. Monitor `status/triage` labeled issues
2. Perform Validation and Classification within Response SLA
3. Assign to domain owner for Prioritization
4. Respond to P0 escalations 24/7

**Off-hours (P0 only)**:
- Weekdays 17:00-09:00: On-call via PagerDuty
- Weekends/holidays: On-call via PagerDuty

### Coverage During PTO

If on-call engineer is OOO:
1. **Swap shift** with another rotation member (document in schedule)
2. **Notify team** in `#triage` Slack channel 3 days in advance
3. **Update PagerDuty schedule** with coverage

## Stale Issue Handling

### Definition of Stale

An issue is **stale** if:
- `status/needs-info` label AND no contributor response in >7 days
- `status/in-progress` label AND no update in >14 days
- `priority/low` or `priority/backlog` AND no activity in >30 days

### Stale Bot Actions

Automated stale bot runs daily:

1. **7-day stale** (`status/needs-info`):
   - Add `status/stale` label
   - Comment: "This issue is stale due to no response. It will be closed in 7 days if no activity."
2. **14-day stale** (`status/needs-info` + `status/stale`):
   - Close issue with label `status/closed-stale`
   - Comment: "Closing due to no response. Please reopen if you can provide the requested info."

**Manual override**: Domain owner can remove `status/stale` label to prevent auto-close.

## Contributor Communication Best Practices

### Initial Response Template

When responding to new issue:

```markdown
Thank you for reporting this! 🙏

**Triage summary**:
- **Severity**: S2 (medium - minor functionality broken)
- **Priority**: P2 (planned work, target resolution <1 week)
- **Domain**: Backend
- **Owner**: @backend-lead

**Next steps**:
1. Assigned to @dev1 for investigation
2. Expected update by YYYY-MM-DD
3. We'll notify you when a fix is ready for testing

If you have additional details (logs, screenshots, reproduction steps), please add them as a comment.
```

### Status Update Template

When updating contributor on progress:

```markdown
## Status Update

**Current status**: In progress (PR #<pr-number> opened)  
**Completed**:
- [x] Root cause identified (middleware timeout)
- [x] Fix implemented and tested locally

**Next steps**:
- [ ] Awaiting PR review
- [ ] Merge to main (expected YYYY-MM-DD)
- [ ] Deploy to production (release YYYY.MM.DD)

**Expected resolution**: YYYY-MM-DD

Thank you for your patience! 🙌
```

### Closure Template (Won't Fix)

When closing as won't-fix:

```markdown
Thank you for the suggestion! After review, we're not planning to implement this because:

**Reason**: [e.g., Out of scope for this project, duplicate of #<issue>, architectural constraint]

**Alternative**: [If applicable, suggest workaround or different approach]

We appreciate your contribution and encourage you to open new issues for other ideas! 💡

Closing with label `status/wontfix`.
```

## AI Agent Triage Guidance

When triaging as an AI agent:

1. **Validation**:
   - Check issue body for required sections (use issue template as checklist)
   - If incomplete, add `status/needs-info` and request missing details
   - If spam/abuse, flag for human review (do NOT close automatically)

2. **Classification**:
   - Apply severity per [Severity/Priority Contract](./SEVERITY_PRIORITY_CONTRACT.md)
   - Apply priority using Impact × Urgency matrix
   - Apply domain label based on keywords:
     - Backend: "API", "service", "endpoint", "auth", "database"
     - Frontend: "UI", "button", "page", "CSS", "template"
     - Security: "vulnerability", "exploit", "CSP", "XSS", "auth"
     - Infra: "workflow", "deploy", "CI", "build", "release"
     - Docs: "README", "documentation", "guide", "typo"

3. **Prioritization**:
   - Default to conservative (one level higher than matrix suggests)
   - Always escalate P0 to human for verification
   - Comment with classification justification:
     ```markdown
     **AI Triage**: Classified as S2/P2 because:
     - Impact: Search broken for specific query (medium)
     - Urgency: Noticeable but not blocking (medium)
     - Workaround: Use alternative search terms
     
     cc @domain/backend for prioritization review
     ```

4. **Assignment**:
   - Tag domain owner: `@backend-lead` (based on domain label)
   - Do NOT assign directly to developer (let domain owner delegate)

## Metrics and Reporting

### Monthly Triage Metrics

Track and publish monthly:

| Metric | Target | Calculation |
|--------|--------|-------------|
| **Response SLA compliance** | >90% for P0/P1 | % of issues with first response within SLA |
| **Resolution SLA compliance** | >85% for P0/P1 | % of issues closed within Resolution Target |
| **Stale issue rate** | <10% | % of issues closed as stale / total closed |
| **Median time-to-triage** | <4 hours | Median time from open → classified |
| **Median time-to-resolution** | <3 days (P1) | Median time from open → closed (by priority) |

**Report location**: `docs/governance/triage-reports/YYYY-MM.md`

### Red Flags (Trigger Process Review)

If any of these occur:
- **P0 SLA miss** >1x per month → Review on-call coverage
- **P1 SLA compliance** <80% for 2 consecutive months → Add triage capacity
- **Stale rate** >20% → Improve issue templates or validation
- **Escalation rate** >30% → Review classification accuracy

## Related Documents

- [Severity/Priority Contract](./SEVERITY_PRIORITY_CONTRACT.md) - Classification rules
- [Parent/Child/Epic Standard](./PARENT_CHILD_EPIC_STANDARD.md) - Issue hierarchy
- [Status Check Matrix](./STATUS_CHECK_MATRIX.md) - PR quality gates
- [Issue Templates](../../.github/ISSUE_TEMPLATE/) - Structured issue creation

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-06-24 | Claude (via WOS) | Initial version for issue #844 |

---

**Maintained by**: Platform Engineering  
**Review frequency**: Quarterly or when SLA violations trend upward  
**Last reviewed**: 2026-06-24
