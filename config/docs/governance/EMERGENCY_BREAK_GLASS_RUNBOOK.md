# Emergency Break-Glass Runbook

## Purpose
This runbook defines the controlled emergency exception process for bypassing standard governance controls when production incidents require immediate intervention.

## When to Use Break-Glass

### Triggering Conditions
Break-glass procedures MAY be invoked ONLY when ALL of the following are true:

1. **Production Incident Active**
   - Service degradation or outage affecting users
   - Security breach requiring immediate mitigation
   - Data loss or corruption in progress
   - Regulatory compliance violation requiring urgent remediation

2. **Standard Process Blocked**
   - Required approvers unavailable within incident SLA window
   - CI/CD pipeline failure blocking critical fix
   - Branch protection preventing emergency merge
   - Normal change control timeline incompatible with incident severity

3. **Business Impact Severe**
   - Revenue loss > $X/hour (define threshold)
   - User-facing service unavailable
   - Security exposure actively exploited
   - Legal/compliance penalty imminent

### Non-Qualifying Scenarios
Break-glass SHALL NOT be used for:
- Feature deadlines or release pressure
- Convenience or process shortcuts
- Avoiding code review feedback
- Personal urgency without production impact
- "Urgent" requirements without verified incident

## Authorization Requirements

### Minimum Authorizers
Required approvals based on incident severity:

| Severity | Required Approvers | Response Time SLA |
|----------|-------------------|-------------------|
| **Critical** (P0) | 1 Tech Lead OR 1 Engineering Manager | 15 minutes |
| **High** (P1) | 1 Tech Lead AND 1 Engineering Manager | 30 minutes |
| **Medium** (P2) | 2 Tech Leads OR 1 EM + 1 Product Owner | 1 hour |

### Approval Channels
1. **Primary**: Incident Slack channel (#incidents)
2. **Fallback**: Direct message to oncall rotation
3. **Emergency**: Phone call to escalation chain

### Authorization Evidence
Each approval must include:
- Timestamp of approval
- Approver name and role
- Incident ticket reference
- Risk acknowledgment statement
- Estimated blast radius

## Break-Glass Execution Process

### 1. Incident Declaration
```bash
# Create incident ticket
gh issue create --title "INCIDENT: [Brief Description]" \
  --label "incident,p0" \
  --body "$(cat <<EOF
## Incident Summary
- **Started**: $(date -Iseconds)
- **Severity**: P0/P1/P2
- **Affected Service**: [service name]
- **User Impact**: [describe]
- **Estimated Affected Users**: [number/percentage]

## Initial Assessment
[What is broken and why]

## Proposed Break-Glass Action
[What governance controls need bypass]

## Risk Analysis
- **Change Blast Radius**: [scope]
- **Rollback Plan**: [describe]
- **Monitoring Plan**: [metrics to watch]

## Authorization
- [ ] Approver 1: [name] at [timestamp]
- [ ] Approver 2: [name] at [timestamp]
EOF
)"
```

### 2. Request Authorization
Post in incident channel with template:
```
🚨 BREAK-GLASS REQUEST 🚨
Incident: #[ticket-number]
Severity: P[0/1/2]
Action: [Merge without approval / Force push / Bypass CI / etc]
Risk: [Brief risk statement]
Rollback: [Brief rollback plan]
Authorizers needed: [X people, roles]
```

### 3. Execute Bypass with Audit Trail
```bash
# Example: Emergency merge to main
git checkout main
git pull origin main

# Create emergency fix branch
git checkout -b emergency/incident-[number]-[brief-desc]

# Make minimal fix (ONLY what is needed)
# ... edit files ...

# Commit with incident reference
git add [changed-files]
git commit -m "fix(emergency): [description] (Incident #[number])

Emergency bypass authorized by:
- [Authorizer 1 Name] at [timestamp]
- [Authorizer 2 Name] at [timestamp]

Incident ticket: #[number]
Risk accepted: [brief statement]
Rollback plan: [brief plan]

Signed-off-by: [Your Name] <[email]>"

# Push and merge with bypass
git push origin emergency/incident-[number]-[brief-desc]

# Use admin override to merge
gh pr create --title "EMERGENCY: [description]" \
  --body "Incident #[number] - Break-glass merge authorized" \
  --label "incident,break-glass"

# Admin merges with bypass (document in PR comment)
gh pr merge [PR-number] --admin --squash
```

### 4. Document Exception in Incident Log
Create exception record:
```bash
# Log to exceptions registry
cat >> docs/governance/break_glass_exceptions.log <<EOF
---
Date: $(date -Iseconds)
Incident: #[number]
PR: #[pr-number]
Authorizers: [names]
Bypass Type: [merge-without-approval / force-push / ci-skip / etc]
Duration: [start] to [end]
Impact: [brief]
Rollback Executed: [yes/no]
Post-Incident Review: [link-when-available]
---
EOF
```

## Temporal Window

### Maximum Exception Duration
- **Critical (P0)**: 4 hours before mandatory post-incident review
- **High (P1)**: 8 hours before mandatory post-incident review
- **Medium (P2)**: 24 hours before mandatory post-incident review

### Expiration Actions
When time window expires:
1. Automatic escalation to VP Engineering
2. Mandatory post-incident review scheduling
3. Audit trail verification required
4. Follow-up issue creation mandatory

## Mandatory Evidence Checklist

Before break-glass execution, document:

- [ ] **Incident Ticket**: Created with severity, impact, timeline
- [ ] **Business Justification**: Revenue/user/security/compliance impact quantified
- [ ] **Standard Process Blocker**: Why normal process cannot meet SLA
- [ ] **Authorizer Approvals**: Names, timestamps, roles verified
- [ ] **Change Scope**: Exact files/services/configs affected
- [ ] **Risk Assessment**: Blast radius, failure modes, dependencies
- [ ] **Rollback Plan**: Specific steps, data backup confirmation, tested procedure
- [ ] **Monitoring Plan**: Metrics to track, alert thresholds, oncall assignment
- [ ] **Communication Plan**: Stakeholder notifications, status page updates

## Post-Incident Review (PIR) Requirements

### PIR Timeline
- **P0**: Within 24 hours of incident resolution
- **P1**: Within 48 hours of incident resolution
- **P2**: Within 72 hours of incident resolution

### PIR Mandatory Components

#### 1. Incident Timeline
```markdown
| Timestamp | Event | Actor |
|-----------|-------|-------|
| [time] | Incident detected | [detector] |
| [time] | Break-glass requested | [requester] |
| [time] | Authorization received | [authorizers] |
| [time] | Emergency change deployed | [deployer] |
| [time] | Incident resolved | [resolver] |
```

#### 2. Root Cause Analysis
- **What Happened**: Technical description
- **Why It Happened**: Root cause(s)
- **Why Standard Process Failed**: Control gaps identified
- **Detection Delay**: Why not caught earlier

#### 3. Break-Glass Justification Review
- **Was break-glass necessary?**: Retrospective validation
- **Could standard process have worked?**: Alternative analysis
- **Authorization appropriate?**: Correct approvers, sufficient context
- **Execution correct?**: Procedure followed, audit trail complete

#### 4. Impact Assessment
- **User Impact**: Affected users, duration, severity
- **Revenue Impact**: Lost revenue, SLA credits, penalties
- **Security Impact**: Exposure created/mitigated
- **Technical Debt Created**: Shortcuts taken, cleanup needed

#### 5. Action Items
Each action item must include:
- **Description**: What needs to be done
- **Owner**: Assigned DRI (Directly Responsible Individual)
- **Due Date**: Target completion
- **Type**: [Process / Technical / Training / Tooling]
- **Priority**: [Critical / High / Medium / Low]

Required action categories:
- [ ] **Preventive**: How to prevent recurrence
- [ ] **Detective**: How to detect faster next time
- [ ] **Process**: Governance improvements needed
- [ ] **Technical Debt**: Cleanup from emergency shortcuts
- [ ] **Training**: Knowledge gaps identified

### PIR Approval
Post-incident review must be:
- Reviewed by all incident participants
- Approved by Engineering Manager
- Published to team documentation
- Action items tracked in issue tracker

## Audit and Compliance

### Exception Registry
All break-glass events logged in: `docs/governance/break_glass_exceptions.log`

Fields tracked:
- Date/time of exception
- Incident reference
- Authorizers and timestamps
- Controls bypassed
- Duration of exception
- Outcome (success/rollback/escalation)
- PIR completion status

### Monthly Audit Report
Engineering Manager reviews:
1. **Total exceptions**: Count by severity
2. **Authorization compliance**: All required approvals present
3. **Temporal compliance**: Time windows respected
4. **PIR completion rate**: All reviews conducted on time
5. **Repeat incidents**: Patterns requiring systemic fixes
6. **False positives**: Break-glass used unnecessarily

### KPIs and Thresholds

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Break-glass events per month | < 2 | >= 3 |
| Unauthorized bypass incidents | 0 | >= 1 |
| PIR completion rate | 100% | < 100% |
| Repeat incident rate | < 10% | >= 20% |
| Average authorization time (P0) | < 10 min | >= 15 min |

### Quarterly Review
VP Engineering reviews:
- Exception trends and patterns
- Process effectiveness
- Authorization chain performance
- Systemic issues requiring investment
- Runbook updates needed

## Rollback Procedures

### Immediate Rollback Triggers
Execute rollback if ANY occur within 1 hour of deployment:
- Error rate increase > 5%
- Latency increase > 50%
- New critical errors in logs
- User-reported issues spike
- Security scan alerts on new code

### Rollback Execution
```bash
# 1. Declare rollback decision
gh issue comment [incident-number] --body "ROLLBACK INITIATED at $(date -Iseconds)"

# 2. Revert merge commit
git checkout main
git pull origin main
git revert [merge-commit-sha] -m 1
git push origin main

# 3. Verify rollback success
# ... check metrics, logs, health endpoints ...

# 4. Document rollback
gh issue comment [incident-number] --body "ROLLBACK COMPLETED at $(date -Iseconds)
Metrics verified:
- Error rate: [%]
- Latency p95: [ms]
- Health check: [status]"
```

### Post-Rollback Actions
- [ ] Update incident ticket with rollback details
- [ ] Notify stakeholders of rollback
- [ ] Schedule extended PIR (includes rollback analysis)
- [ ] Create follow-up issue for proper fix
- [ ] Update exception log with rollback outcome

## Governance Bypass Types

### Approved Bypass Methods
1. **Merge without PR approval** (branch protection admin override)
2. **Merge with failing CI** (require status checks bypass)
3. **Direct push to main** (push protection bypass)
4. **Skip automated tests** (workflow manual dispatch)

### Prohibited Actions (Even in Emergency)
- ❌ Committing secrets or credentials
- ❌ Disabling security scanning permanently
- ❌ Removing audit logs
- ❌ Bypassing authentication/authorization in code
- ❌ Deploying without any testing (smoke test minimum)
- ❌ Changing production data without backup

## Templates and Tools

### Exception Request Template
See: `docs/governance/templates/break_glass_request.md`

### Incident Declaration Template
See: `docs/governance/templates/incident_declaration.md`

### Post-Incident Review Template
See: `docs/governance/templates/post_incident_review.md`

### Automation Tools
```bash
# Helper script for break-glass request
./scripts/break-glass-request.sh [incident-number] [severity]

# Helper script for PIR generation
./scripts/generate-pir.sh [incident-number]

# Helper script for exception audit
./scripts/audit-exceptions.sh [month]
```

## Communication Requirements

### Required Notifications

**At Break-Glass Request:**
- Incident channel (#incidents)
- Engineering leadership
- Oncall rotation

**At Authorization:**
- Incident channel (approvals logged)
- PR comments (authorization evidence)

**At Execution:**
- Incident channel (deployment notice)
- Status page (if user-facing)
- Stakeholder email (for P0/P1)

**At Resolution:**
- Incident channel (resolution confirmed)
- Status page (all-clear)
- Stakeholder email (with summary)

**At PIR Completion:**
- Engineering team (PIR published)
- Leadership (action items assigned)

## Training and Drills

### Oncall Training
All oncall engineers must complete:
- Break-glass runbook review
- Practice drill (tabletop exercise)
- Authorization chain contact verification
- Rollback procedure practice

### Quarterly Fire Drills
Schedule simulated incidents to practice:
- Authorization request process
- Emergency decision-making under pressure
- Rollback execution speed
- PIR facilitation

### Runbook Maintenance
- Review quarterly for accuracy
- Update after each real incident
- Incorporate drill learnings
- Adjust thresholds based on KPI data

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-23 | System | Initial runbook creation |

## References
- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)
- [Branch Protection Policy](#)
- [Incident Response Policy](#)
- [Change Management Policy](#)

## Appendix: Decision Tree

```
START: Production Issue Detected
  |
  v
[Is there active user impact?]
  |--NO--> Use standard change process
  |
  v--YES
  |
[Can standard process meet SLA?]
  |--YES--> Use standard process with expedited review
  |
  v--NO
  |
[Severity P0, P1, or P2?]
  |--P3/P4--> Wait for standard process
  |
  v--P0/P1/P2
  |
[Request break-glass authorization]
  |
  v
[Authorization received within SLA?]
  |--NO--> Escalate to VP Engineering
  |
  v--YES
  |
[Execute break-glass procedure]
  |
  v
[Monitor deployment (1 hour)]
  |
  v
[Rollback needed?]
  |--YES--> Execute rollback procedure
  |
  v--NO
  |
[Incident resolved]
  |
  v
[Schedule PIR within timeline]
  |
  v
[Complete PIR with action items]
  |
  v
[Track exception in registry]
  |
  v
END
```

---

**Document Owner**: Engineering Leadership  
**Review Frequency**: Quarterly  
**Next Review**: 2026-09-23  
**Contact**: engineering@example.com
