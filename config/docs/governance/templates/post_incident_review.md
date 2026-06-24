# Post-Incident Review (PIR)

## Incident Reference
- **Incident ID**: #
- **PIR Date**: 
- **Facilitator**: 
- **Attendees**: 
  - 
  - 

## Executive Summary
**One-paragraph summary for leadership**:


**Key Metrics**:
- **Total Downtime**: 
- **Users Affected**: 
- **Revenue Impact**: 
- **Detection Time**: [time from incident start to detection]
- **Mitigation Time**: [time from detection to resolution]

## Incident Timeline

### Detailed Timeline
| Timestamp | Event | Actor | Evidence/Notes |
|-----------|-------|-------|----------------|
| | [Normal operation baseline] | | |
| | [First anomaly/error] | | |
| | [Detection] | | |
| | [Incident declared] | | |
| | [Investigation milestone] | | |
| | [Root cause identified] | | |
| | [Fix deployed] | | |
| | [Incident resolved] | | |
| | [Monitoring stable] | | |

### Timeline Visualization
```
[Consider adding a visual timeline or link to incident dashboard]
```

## Root Cause Analysis

### What Happened
**Technical Description**:


**Failure Mode**:


**Affected Components**:
- 
- 

### Why It Happened (5 Whys)
1. **Why did [symptom] occur?**
   - Because: 

2. **Why did [cause from #1] happen?**
   - Because: 

3. **Why did [cause from #2] happen?**
   - Because: 

4. **Why did [cause from #3] happen?**
   - Because: 

5. **Why did [cause from #4] happen?**
   - Because: 

**Root Cause(s)**:
- 
- 

### Contributing Factors
**Technical Factors**:
- 
- 

**Process Factors**:
- 
- 

**Human Factors**:
- 
- 

## Break-Glass Justification Review

### Was Break-Glass Necessary?
**Decision**: [Yes/No]

**Retrospective Analysis**:
- **Time saved by break-glass**: 
- **Standard process estimated time**: 
- **Actual urgency vs. perceived urgency**: 

**Verdict**: [Justified / Questionable / Unnecessary]

### Could Standard Process Have Worked?
**Analysis**:


**Alternative Approaches Not Considered**:
- 
- 

**Process Improvements Identified**:
- 
- 

### Authorization Appropriateness
**Authorizers**: 
- Were they the correct roles? [Yes/No]
- Was context sufficient? [Yes/No]
- Was authorization timely? [Yes/No]

**Authorization Timeline**: [met SLA / exceeded SLA]

### Execution Correctness
**Procedure Adherence**: [Full / Partial / Deviated]

**Deviations from runbook**:
- 
- 

**Audit Trail Completeness**: [Complete / Gaps identified]

**Missing elements**:
- 
- 

## Impact Assessment

### User Impact
**Affected Users**:
- **Total**: 
- **Percentage of user base**: 
- **Geographic distribution**: 

**User Experience**:
- **Symptom severity**: [unable to access / degraded performance / intermittent errors]
- **User reports received**: 
- **Support tickets created**: 

**Duration**:
- **Mean time to detect (MTTD)**: 
- **Mean time to resolve (MTTR)**: 
- **Total user-minutes lost**: [users × minutes]

### Revenue Impact
**Direct Revenue Loss**: $
- **Calculation method**: 
- **Assumptions**: 

**SLA Credits Owed**: $
- **Affected customers**: 
- **SLA violation severity**: 

**Opportunity Cost**: $
- **Delayed launches**: 
- **Lost deals**: 

**Total Financial Impact**: $

### Security Impact
**Exposure Created During Incident**: [Yes/No]
- **Description**: 
- **Severity**: 
- **Mitigated**: [Yes/No]

**Security Posture Changes from Emergency Fix**: [Improved/Degraded/Neutral]
- **Details**: 

### Technical Debt Created
**Shortcuts Taken**:
1. 
2. 

**Code Quality Impact**:
- **Tests skipped**: 
- **Coverage reduced**: [by X%]
- **Monitoring gaps**: 

**Estimated cleanup effort**: [person-hours]

## Detection and Monitoring Analysis

### How Was It Detected?
**Detection Method**: [Automated alert / User report / Manual check / Proactive monitoring]

**Detection Latency**: 
- **Incident began**: 
- **First detection**: 
- **Gap**: 

### Should We Have Detected Earlier?
**Analysis**: [Yes/No]

**Existing Monitoring Gaps**:
- 
- 

**Why Alerts Didn't Fire**:
- 
- 

### Alert Effectiveness
**Alerts that fired**:
| Alert | Time | Actionable? | Signal/Noise |
|-------|------|-------------|--------------|
| | | | |

**Alerts that should have fired but didn't**:
- 
- 

## Response Analysis

### What Went Well
1. 
2. 
3. 

**Strengths to Reinforce**:
- 
- 

### What Went Wrong
1. 
2. 
3. 

**Weaknesses to Address**:
- 
- 

### What We Got Lucky With
1. 
2. 

**Luck Dependency to Eliminate**:
- 
- 

### Communication Effectiveness
**Internal Communication**: [Effective / Needs Improvement]
- **Gaps identified**: 

**External Communication**: [Effective / Needs Improvement]
- **Customer feedback**: 

**Status Page Accuracy**: [Accurate / Misleading / Not updated]

### Decision Quality
**Good Decisions**:
- 
- 

**Poor Decisions**:
- 
- 

**Decision Delays**:
- 
- 

## Preventive Action Items

### Immediate Actions (Complete within 1 week)
- [ ] **#[issue-number]**: [Description]
  - **Owner**: 
  - **Due**: 
  - **Type**: [Technical/Process/Training]
  - **Priority**: Critical

- [ ] **#[issue-number]**: [Description]
  - **Owner**: 
  - **Due**: 
  - **Type**: [Technical/Process/Training]
  - **Priority**: High

### Short-Term Actions (Complete within 1 month)
- [ ] **#[issue-number]**: [Description]
  - **Owner**: 
  - **Due**: 
  - **Type**: [Technical/Process/Training]
  - **Priority**: Medium

### Long-Term Actions (Complete within 1 quarter)
- [ ] **#[issue-number]**: [Description]
  - **Owner**: 
  - **Due**: 
  - **Type**: [Technical/Process/Training]
  - **Priority**: Low

### Action Item Categories

**Preventive** (Stop it from happening):
- 
- 

**Detective** (Find it faster next time):
- 
- 

**Process** (Improve response):
- 
- 

**Technical Debt Cleanup** (Remove emergency shortcuts):
- 
- 

**Training** (Knowledge gaps identified):
- 
- 

## Systemic Issues Identified

### Pattern Recognition
**Is this a repeat incident?**: [Yes/No]
- **Similar incidents**: 
- **Common root cause**: 

**Systemic weaknesses exposed**:
1. 
2. 

### Investment Recommendations
**Technical Investment Needed**:
- 
- 

**Process Investment Needed**:
- 
- 

**Staffing/Training Investment Needed**:
- 
- 

## Runbook and Process Updates

### Break-Glass Runbook Changes
**Proposed updates**:
- 
- 

### Other Runbook Updates
- 
- 

### Policy Changes
- 
- 

## Metrics and KPIs

### Incident Metrics
- **MTTD** (Mean Time to Detect): 
- **MTTI** (Mean Time to Investigate): 
- **MTTF** (Mean Time to Fix): 
- **MTTR** (Mean Time to Resolve): 

### Historical Comparison
| Metric | This Incident | Team Average | Trend |
|--------|---------------|--------------|-------|
| MTTD | | | |
| MTTR | | | |
| Severity | | | |

### Break-Glass KPI Impact
- **Break-glass events this month**: [X] (target: <2)
- **Authorization compliance**: [100% / gaps noted]
- **PIR completion**: [On time / Delayed]

## Lessons Learned Summary

### For Engineering Team
1. 
2. 
3. 

### For Leadership
1. 
2. 
3. 

### For Organization
1. 
2. 
3. 

## Acknowledgments

**Exceptional Contributions**:
- 
- 

**Team Shoutouts**:
- 
- 

## Appendices

### Appendix A: Raw Logs
[Link to log snippets or attach if brief]

### Appendix B: Metrics Dashboards
[Links to relevant dashboards]

### Appendix C: Communication Artifacts
[Links to status page updates, customer emails, etc.]

### Appendix D: Code Changes
- **Emergency PR**: #
- **Rollback PR** (if applicable): #
- **Follow-up cleanup PR**: #

## Sign-Off

**PIR Facilitator**: _________________ Date: _______

**Incident Commander**: _________________ Date: _______

**Engineering Manager**: _________________ Date: _______

**VP Engineering** (for P0/P1): _________________ Date: _______

## Publication

- [ ] PIR reviewed by all participants
- [ ] Action items created and tracked
- [ ] PIR published to team documentation (link: )
- [ ] Exception logged in registry
- [ ] Leadership briefed
- [ ] Runbook updates implemented

---

**Document Created**: 
**Last Updated**: 
**Version**: 1.0
