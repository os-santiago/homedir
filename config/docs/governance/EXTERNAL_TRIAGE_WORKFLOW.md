# External Triage Workflow and Domain Ownership

## Purpose

This document defines the end-to-end triage process for external contributions, establishes domain ownership, and commits to SLA targets for issue lifecycle stages. It ensures predictable, high-quality responses to community issues and pull requests.

## Triage Lifecycle

### Stage Flow

```
Intake → Validation → Classification → Prioritization → Assignment → Work → Resolution → Closure
```

### Stage 1: Intake

**Owner**: Platform Engineering (rotating triage lead)  
**SLA**: First response within 24 hours (business days)

**Actions**:
1. Auto-apply `wos-review` label to trigger AI-assisted initial assessment
2. Triage lead reviews new issues daily at 09:00 local time
3. Acknowledge receipt with comment: "Thank you for opening this issue. We'll review and classify it shortly."
4. Check for duplicates using GitHub search and Codex system

**Outputs**:
- Issue acknowledged
- Duplicate marked with `duplicate` label and link to original
- Issue queued for validation

### Stage 2: Validation

**Owner**: Domain owner (see Domain Ownership Matrix)  
**SLA**: Validated within 48 hours of intake

**Actions**:
1. **Bug reports**: Verify reproducibility
   - Can reproduce → add `bug` + priority label
   - Cannot reproduce → request more info or mark `invalid`
2. **Feature requests**: Assess scope and alignment
   - Aligned with roadmap → add `enhancement` + priority
   - Out of scope → mark `wontfix` with explanation
3. **Questions**: Answer directly or convert to discussion
4. **Documentation**: Verify accuracy and add `documentation`

**Validation Checklist**:
- [ ] Issue type confirmed (bug/enhancement/documentation/question)
- [ ] Severity assessed (for bugs: S0-S4 per [Severity Contract](./SEVERITY_PRIORITY_CONTRACT.md))
- [ ] Priority assigned (P0-P3 per impact × urgency matrix)
- [ ] Domain owner tagged (if not self)
- [ ] Acceptance criteria clear and measurable

**Outputs**:
- Type label applied
- Priority label applied
- Domain owner assigned (if applicable)
- Issue moves to classification or rejection

**Rejection Criteria**:
- Duplicate → close with `duplicate` label + link
- Invalid → close with `invalid` label + explanation
- Won't fix → close with `wontfix` label + rationale
- Question → answer and optionally convert to discussion

### Stage 3: Classification

**Owner**: Domain owner  
**SLA**: Classified within 72 hours of validation

**Actions**:
1. Apply domain labels (e.g., `codex`, `evento`, `hackathon`)
2. Link to parent issue if part of epic (per [Parent/Child Standard](./PARENT_CHILD_EPIC.md))
3. Add collaboration labels:
   - `good first issue` if suitable for newcomers
   - `help wanted` if expertise gap or bandwidth constraint
4. Cross-reference related issues or PRs
5. Update issue description with acceptance criteria if missing

**Outputs**:
- Domain labels applied
- Parent issue linked (if applicable)
- Collaboration labels added (if applicable)
- Issue ready for assignment

### Stage 4: Prioritization

**Owner**: Domain owner + Product/Engineering lead (for P0/P1 only)  
**SLA**: P0 immediate, P1 within 4 hours, P2/P3 within 1 week

**Actions**:
1. Verify priority label against impact × urgency matrix
2. For P0/P1: escalate to on-call rotation immediately
3. For P2/P3: add to domain backlog
4. Set milestone if tied to release
5. Estimate effort (S/M/L/XL) in comment for planning

**Escalation Protocol** (P0/P1):
1. Tag `@scanalesespinoza` in issue comment
2. Post alert in #platform-eng Slack channel (when available)
3. If production outage (P0), initiate emergency break-glass per [Runbook](./EMERGENCY_BREAK_GLASS_RUNBOOK.md)

**Outputs**:
- Priority validated
- P0/P1 escalated
- Milestone set (if applicable)
- Issue queued for assignment

### Stage 5: Assignment

**Owner**: Domain owner  
**SLA**: P0 immediate, P1 within 8 hours, P2 within 1 week, P3 within 1 month

**Actions**:
1. Assign to available team member or self
2. If community contributor volunteers, assign to them with mentorship
3. If unassigned after SLA, domain owner self-assigns or escalates
4. Add issue to domain project board (when available)
5. Comment with start date and ETA

**Outputs**:
- Assignee set
- ETA communicated
- Issue actively tracked

### Stage 6: Work

**Owner**: Assignee  
**SLA**: Updates every 5 business days for active issues

**Actions**:
1. Create feature branch per [ADEV workflow](../../ADEV.md)
2. Implement fix/feature with tests
3. Post progress updates every 5 days (or on blockers)
4. Request help via `help wanted` label if blocked
5. Submit PR linking issue with "Closes #NNN"

**Stale Issue Policy**:
- If no update in 10 days, bot pings assignee
- If no response in 5 more days, unassign and return to queue
- Original assignee can reclaim by commenting

**Outputs**:
- PR submitted linking issue
- Progress communicated regularly
- Blockers escalated

### Stage 7: Resolution

**Owner**: Domain owner (as reviewer)  
**SLA**: PR reviewed within 48 hours

**Actions**:
1. Code review per [Reviewer Checklist](./REVIEWER_CHECKLIST.md)
2. Verify all [Status Check Matrix](./STATUS_CHECK_MATRIX.md) gates pass
3. Verify acceptance criteria met
4. Approve and merge, or request changes
5. Issue auto-closes on merge (if "Closes #NNN" in PR)

**Manual Closure** (for non-PR resolutions):
- Link to commit/PR that resolved it
- Summarize solution in closing comment
- Verify Definition of Done per [DoR/DoD](./DEFINITION_OF_READY_DONE.md)

**Outputs**:
- PR merged
- Issue closed with resolution comment
- Release notes updated (if user-facing)

### Stage 8: Closure

**Owner**: Assignee + Domain owner  
**SLA**: Verified within 1 week of merge

**Actions**:
1. Verify fix deployed to production (if applicable)
2. Confirm no regressions via monitoring
3. Thank contributor (especially external)
4. Add "🎉 Fixed in vX.Y.Z" comment with release link
5. Update related documentation if needed

**Follow-Up**:
- If issue reoccurs, reopen with "Regression" label
- Create new issue for discovered related work
- Update backlog if scope was reduced

**Outputs**:
- Issue closed and verified
- Contributor thanked
- Documentation updated

## Domain Ownership Matrix

| Domain | Owner | Scope | Priority Bias |
|--------|-------|-------|---------------|
| **Backend/API** | Platform Engineering | Java/Kotlin services, database, business logic | High - impacts core functionality |
| **Frontend/UI** | Platform Engineering | Qute templates, CSS, JS, user-facing components | Medium - impacts user experience |
| **Security** | Platform Engineering | AppSec, secrets, CSP, rate limiting, auth | Critical - P0/P1 default |
| **CI/CD** | Platform Engineering | GitHub Actions, workflows, release automation | High - impacts delivery velocity |
| **Documentation** | Platform Engineering | `*.md` files, guides, runbooks | Low - defer to bandwidth |
| **Codex (IA)** | Platform Engineering | Issue governance, triage automation, metadata | Medium - strategic enabler |
| **Events** | Community Team (future) | Event-related features and content | Low - seasonal priority |
| **Hackathon** | Community Team (future) | Hackathon-specific issues | Low - time-bound |
| **Infrastructure** | Platform Engineering | VPS, deployments, observability, DR | High - impacts availability |

**Cross-Domain Issues**: If issue spans multiple domains, primary domain owner coordinates and tags secondary owners.

**Owner Responsibilities**:
1. Monitor domain-labeled issues daily
2. Respond to mentions within SLA
3. Review PRs in domain within 48 hours
4. Escalate blockers to team lead
5. Update domain documentation quarterly

## SLA Commitments

### Response SLAs

| Priority | First Response | Validation | Classification | Assignment | PR Review |
|----------|---------------|------------|----------------|-----------|-----------|
| **P0** (Critical) | Immediate (<1h) | Immediate | Immediate | Immediate | Immediate |
| **P1** (High) | 4 hours | 8 hours | 12 hours | 8 hours | 24 hours |
| **P2** (Medium) | 24 hours | 48 hours | 72 hours | 1 week | 48 hours |
| **P3** (Low) | 48 hours | 1 week | 1 week | 1 month | 1 week |

**Notes**:
- SLAs measured in **business hours** (Mon-Fri 09:00-17:00 local time)
- P0/P1 SLAs apply 24/7 for production incidents
- Community issues default to P3 unless severity warrants higher

### Update SLAs

| Issue Age | Update Frequency |
|-----------|------------------|
| Active (assigned, < 30 days) | Every 5 business days |
| Aging (assigned, 30-60 days) | Weekly |
| Stale (assigned, > 60 days) | Re-evaluate: complete, close, or re-assign |
| Backlog (unassigned) | No update required |

**Stale Issue Bot**:
- After 10 days no update: "👋 Checking in - any progress or blockers?"
- After 15 days no response: Unassign and return to queue
- After 90 days no activity: Close with "Closing due to inactivity. Please reopen if still relevant."

## Escalation Protocol

### P0 (Production Outage)

1. **Immediate**: Tag maintainer `@scanalesespinoza` in issue
2. **< 5 min**: Initiate emergency break-glass per [Runbook](./EMERGENCY_BREAK_GLASS_RUNBOOK.md)
3. **< 15 min**: Assemble incident response team
4. **< 1 hour**: Deploy hotfix or rollback
5. **< 24 hours**: Post-mortem issue created

### P1 (Major Impact)

1. **< 4 hours**: Domain owner triages and assigns
2. **< 8 hours**: Assignee starts work or requests help
3. **< 48 hours**: PR submitted or escalate to team lead
4. **< 1 week**: Merged and deployed

### P2/P3 (Normal Flow)

1. Follow standard triage lifecycle
2. Escalate to team lead if blocked > 5 days
3. Re-prioritize to P1 if impacts expand

## External Contributor Flow

### For First-Time Contributors

1. **Discover**: Browse `good first issue` label in issue list
2. **Claim**: Comment "I'd like to work on this" on the issue
3. **Assign**: Maintainer assigns issue and provides guidance
4. **Develop**: Follow [ADEV workflow](../../ADEV.md) and commit standards
5. **Submit PR**: Link issue with "Closes #NNN" in PR description
6. **Review**: Maintainer reviews per [Reviewer Checklist](./REVIEWER_CHECKLIST.md)
7. **Merge**: Maintainer merges and thanks contributor
8. **Recognition**: Contributor added to release notes

### Mentorship Commitment

For `good first issue` and `help wanted`:
- Maintainer responds to questions within 24 hours
- Pair programming offered for first PR (when feasible)
- Detailed code review with learning focus, not just approval/rejection
- Recognition in release notes and Discord announcements

### Contributor Guidelines

- Read [ADEV.md](../../ADEV.md) before starting work
- Ask questions early - no question is too small
- Commit atomically per [Conventional Commits](https://www.conventionalcommits.org/)
- Sign off commits (`Signed-off-by:` in message)
- Run tests locally before submitting PR
- Be patient - reviews may take 48 hours

## Metrics and Reporting

### Key Metrics (Monthly)

| Metric | Target | Definition |
|--------|--------|------------|
| **Median Time to First Response** | < 24 hours | Intake → first maintainer comment |
| **Median Time to Close** (P0) | < 1 day | Open → closed |
| **Median Time to Close** (P1) | < 1 week | Open → closed |
| **Median Time to Close** (P2/P3) | < 1 month | Open → closed |
| **% Issues Acknowledged (24h)** | > 90% | Issues with first response < 24h |
| **% PRs Reviewed (48h)** | > 80% | PRs with first review < 48h |
| **% Stale Issues Closed** | < 10% | Closed due to inactivity |
| **Community Contributor Rate** | > 20% | % of PRs from external contributors |

### Reporting Cadence

- **Daily**: Triage lead reviews new issues
- **Weekly**: Domain owners review backlog and stale issues
- **Monthly**: Team lead publishes metrics dashboard
- **Quarterly**: Review and update SLA targets, domain ownership, and workflow

### Dashboard (Future)

- Open issues by domain and priority (heatmap)
- SLA compliance rate by domain
- Contributor leaderboard (community engagement)
- Triage velocity trend (issues closed per week)

## Related Documents

- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) - Issue completion criteria
- [Severity/Priority Contract](./SEVERITY_PRIORITY_CONTRACT.md) - Impact × urgency matrix
- [Label Taxonomy](./LABEL_TAXONOMY.md) - Canonical label definitions
- [Parent/Child/Epic Standard](./PARENT_CHILD_EPIC.md) - Multi-issue tracking
- [Reviewer Checklist](./REVIEWER_CHECKLIST.md) - PR review process
- [Status Check Matrix](./STATUS_CHECK_MATRIX.md) - Required CI checks
- [Emergency Break-Glass Runbook](./EMERGENCY_BREAK_GLASS_RUNBOOK.md) - P0 incident response

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-06-24 | Claude (via WOS) | Initial workflow for issue #844 |

---

**Maintained by**: Platform Engineering  
**Review frequency**: Quarterly or when SLA targets are consistently missed  
**Last reviewed**: 2026-06-24
