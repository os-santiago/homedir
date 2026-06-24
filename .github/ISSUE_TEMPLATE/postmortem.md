---
name: Post-Incident Retrospective
about: Required postmortem for all break-glass emergency incidents
title: 'Postmortem: [Brief incident description]'
labels: postmortem, incident
assignees: ''
---

**Incident ID**: INC-YYYY-MM-DD-NNN  
**Emergency PR**: #XXX  
**Date**: YYYY-MM-DD  
**Severity**: P0 | Security | Data Loss | Broken Main  

## Timeline

| Time (UTC) | Event |
|------------|-------|
| HH:MM | Incident detected |
| HH:MM | Break-glass approval obtained |
| HH:MM | Emergency fix merged (PR #XXX) |
| HH:MM | Incident resolved / reverted |

**Total Duration**: [Minutes/hours from detection to resolution]

## Impact

- **Users affected**: [Number or percentage]
- **Duration of degraded service**: [Minutes/hours]
- **Revenue impact**: [If applicable, or "None"]
- **Data loss/corruption**: [If any, or "None"]
- **User-facing errors**: [Error rate, request failures, etc.]

## Root Cause Analysis

**Primary Root Cause**: [One-sentence summary]

**5-Whys Analysis**:

1. **Why did the incident occur?**  
   → [Answer]

2. **Why did that happen?**  
   → [Answer]

3. **Why wasn't it caught earlier?**  
   → [Answer]

4. **Why didn't monitoring/alerting catch it?**  
   → [Answer]

5. **Why wasn't there a preventive safeguard?**  
   → [Answer]

**Contributing Factors**:
- [Factor 1]
- [Factor 2]

## What Went Well

- [Things that worked during incident response]
- [Effective tools, processes, or team coordination]

## What Went Wrong

- [Things that didn't work as expected]
- [Gaps in tooling, process, or communication]
- [Areas for improvement]

## Preventive Actions

**High Priority** (Must complete within 7 days):
- [ ] **P0**: [Action description] - Owner: @github-username - Due: YYYY-MM-DD - Tracking: #XXX

**Medium Priority** (Must complete within 30 days):
- [ ] **P1**: [Action description] - Owner: @github-username - Due: YYYY-MM-DD - Tracking: #XXX
- [ ] **P1**: [Action description] - Owner: @github-username - Due: YYYY-MM-DD - Tracking: #XXX

**Low Priority** (Complete within 90 days):
- [ ] **P2**: [Action description] - Owner: @github-username - Due: YYYY-MM-DD - Tracking: #XXX

## Process Improvements

Changes to prevent similar incidents:

- [ ] **Monitoring/Alerting**: [Specific alert to add] - Issue: #XXX
- [ ] **Testing**: [Test coverage gap to fill] - Issue: #XXX
- [ ] **Documentation**: [Runbook/doc to update] - Issue: #XXX
- [ ] **Tooling**: [Automation or tool improvement] - Issue: #XXX

## Lessons Learned

**Technical**:
- [Key technical insight from this incident]

**Process**:
- [Process or workflow improvement identified]

**Team**:
- [Coordination, communication, or training need]

---

## Checklist for Completion

- [ ] Timeline verified with incident channel logs
- [ ] Impact metrics validated with monitoring dashboards
- [ ] Root cause analysis reviewed by Engineering Manager
- [ ] All preventive actions have assigned owners and due dates
- [ ] All preventive actions have tracking issues created
- [ ] Exception logged in `governance/gate_exceptions.log`
- [ ] Follow-up code review completed (if applicable)
- [ ] Runbook updated with lessons learned (if applicable)

---

**Retrospective Owner**: @github-username  
**Reviewed By**: @engineering-manager  
**Completion Deadline**: [48 hours after incident - YYYY-MM-DD HH:MM UTC]

---

**Related Documents**:
- Incident Ticket: INC-YYYY-MM-DD-NNN
- Emergency PR: #XXX
- Follow-up Review PR: #XXX (if applicable)
- Exception Log: `governance/gate_exceptions.log`
