# Break-Glass Request Template

## Incident Information
- **Incident Number**: #
- **Severity**: [P0/P1/P2]
- **Reported By**: 
- **Reported At**: 
- **Affected Service(s)**: 

## Impact Assessment
- **Users Affected**: [number/percentage]
- **Business Impact**: [revenue loss/SLA violation/security exposure/compliance risk]
- **Current Status**: [degraded/outage/security breach/data loss]
- **Estimated Impact if Not Resolved**: 

## Standard Process Blocker
**Why can't we use standard change process?**
- [ ] Required approvers unavailable (list who)
- [ ] CI/CD pipeline blocked (describe issue)
- [ ] Time constraint incompatible with SLA (explain)
- [ ] Other: 

**Time Sensitivity**: 
- **Incident SLA**: Must resolve within [X hours]
- **Standard Process Timeline**: Would take [Y hours]
- **Gap**: [Y - X] hours too slow

## Proposed Emergency Action
**What governance controls need bypass?**
- [ ] Merge without PR approval
- [ ] Merge with failing CI checks
- [ ] Direct push to main
- [ ] Skip automated tests
- [ ] Other: 

**Specific Changes Planned**:
```
Files to modify:
- 
- 

Change description:
[Brief technical description]
```

## Risk Analysis
**Blast Radius**:
- **Services Affected**: 
- **Data Affected**: 
- **User Journeys Affected**: 

**Failure Modes**:
1. 
2. 
3. 

**Dependencies**:
- 
- 

**Risk Rating**: [Low/Medium/High]

## Rollback Plan
**Rollback Method**: [git revert / feature flag disable / config rollback / database restore]

**Rollback Steps**:
1. 
2. 
3. 

**Rollback Time Estimate**: 
**Data Loss During Rollback**: [Yes/No - if yes, describe]

**Rollback Tested**: [Yes/No]

## Monitoring Plan
**Metrics to Watch**:
- Error rate: current [X%], threshold [Y%]
- Latency p95: current [X ms], threshold [Y ms]
- Health check: 
- Business metric: 

**Alert Configuration**:
- [ ] Error rate alert configured
- [ ] Latency alert configured
- [ ] Custom incident metric alert configured

**Oncall Assignment**: 

**Monitoring Duration**: [hours after deployment]

## Authorization

**Authorizers Required**: [Based on severity per runbook]

**Approvals**:
- [ ] **Authorizer 1**: [Name, Role] - Timestamp: 
  - Risk Acknowledgment: "I acknowledge the risks and approve this break-glass action"
  
- [ ] **Authorizer 2**: [Name, Role] - Timestamp: 
  - Risk Acknowledgment: "I acknowledge the risks and approve this break-glass action"

## Communication Plan
- [ ] Incident channel notified (#incidents)
- [ ] Status page updated (if user-facing)
- [ ] Stakeholders emailed (list): 
- [ ] Customer support briefed (if needed)

## Pre-Flight Checklist
Before executing break-glass:
- [ ] Incident ticket created and linked
- [ ] Impact quantified (users, revenue, security)
- [ ] Standard process blocker confirmed
- [ ] Required authorizations obtained
- [ ] Rollback plan documented and tested
- [ ] Monitoring configured
- [ ] Communication sent
- [ ] Backup of current state taken (if applicable)

## Execution Log
**Started**: 
**Emergency Branch**: 
**PR Number**: 
**Deployed At**: 
**Initial Metrics Post-Deployment**:
- Error rate: 
- Latency: 
- Health: 

**Completed**: 
**Outcome**: [Resolved/Rolled Back/Escalated]

## Post-Incident Review
- [ ] PIR scheduled for: [date/time]
- [ ] PIR facilitator assigned: 
- [ ] Exception logged in registry
- [ ] Follow-up issues created

---
**Requester Signature**: 
**Date**: 
