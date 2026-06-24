# Incident Declaration Template

## Incident Metadata
- **Incident ID**: #
- **Severity**: [P0/P1/P2/P3]
- **Status**: [Active/Resolved/Investigating]
- **Declared By**: 
- **Declared At**: 
- **Incident Commander**: 
- **Oncall Engineer**: 

## Severity Definitions
- **P0 (Critical)**: Complete outage or critical security breach affecting all users
- **P1 (High)**: Major functionality broken or significant user subset affected
- **P2 (Medium)**: Degraded performance or minor functionality issue
- **P3 (Low)**: Minor issue with workaround available

## Incident Summary
**One-Line Description**: 

**Affected Service(s)**: 
- 
- 

**User Impact**: 
- **Users Affected**: [number or percentage]
- **User-Facing Symptoms**: 
- **Geographic Scope**: [global/region-specific]

**Business Impact**:
- **Revenue Impact**: [estimated $/hour if applicable]
- **SLA Impact**: [which SLAs violated, credit exposure]
- **Compliance Impact**: [regulatory implications]
- **Reputation Impact**: [customer communication, public visibility]

## Timeline

| Time | Event | Actor | Evidence |
|------|-------|-------|----------|
| | Incident began (estimated) | | |
| | First detection | | |
| | Incident declared | | |
| | Investigation started | | |
| | Root cause identified | | |
| | Mitigation deployed | | |
| | Incident resolved | | |

## Technical Details

### Initial Symptoms
```
Error messages:


Metrics observed:


User reports:
```

### Investigation Findings
**What we know**:
- 
- 

**What we don't know**:
- 
- 

**Current hypothesis**:


### Root Cause (if known)
**Technical Description**:


**Why It Happened**:


**Why It Wasn't Caught Earlier**:


## Mitigation Actions

### Immediate Actions Taken
1. 
2. 
3. 

### Proposed Break-Glass Action (if needed)
**Required**: [Yes/No]

**Reason**: 

**See Break-Glass Request**: [link to break-glass request if applicable]

## Communication

### Internal
- [ ] Incident channel created/updated (#incidents)
- [ ] Engineering team notified
- [ ] Leadership escalation (for P0/P1)
- [ ] Customer support briefed

### External
- [ ] Status page updated (link: )
- [ ] Customer email sent (for P0/P1)
- [ ] Social media update (if public)
- [ ] Regulatory notification (if compliance incident)

### Communication Log
| Time | Audience | Message | Channel |
|------|----------|---------|---------|
| | | | |

## Monitoring

### Key Metrics
| Metric | Before Incident | During Incident | After Mitigation | Target |
|--------|----------------|-----------------|------------------|--------|
| Error rate | | | | |
| Latency p95 | | | | |
| Throughput | | | | |
| Active users | | | | |

### Dashboards
- Primary: 
- Secondary: 

### Alerts Fired
- 
- 

## Rollback/Recovery Plan

**If mitigation fails, execute**:
1. 
2. 
3. 

**Estimated rollback time**: 
**Data loss risk**: [Yes/No - describe if yes]

## Dependencies and Blockers

**Blocked on**:
- 
- 

**Dependent services**:
- 
- 

## Team Assignments

| Role | Name | Responsibilities |
|------|------|------------------|
| Incident Commander | | Overall coordination |
| Technical Lead | | Root cause and fix |
| Communications Lead | | Stakeholder updates |
| Oncall Engineer | | Monitoring and alerts |
| Subject Matter Expert | | Domain knowledge |

## Resolution

**Resolved At**: 
**Resolution Method**: [Deployed fix/Rolled back/Config change/Manual intervention]

**Verification**:
- [ ] Error rate returned to baseline
- [ ] Latency returned to baseline
- [ ] User reports stopped
- [ ] All services healthy
- [ ] Monitoring stable for [X minutes]

**Residual Issues**:
- 
- 

## Follow-Up

### Post-Incident Review
- [ ] PIR scheduled for: 
- [ ] PIR facilitator: 
- [ ] Attendees invited

### Immediate Follow-Up Tasks
- [ ] Clean up emergency changes (if temporary)
- [ ] Update documentation
- [ ] Implement additional monitoring
- [ ] Create preventive action issues

### Action Items Created
- [ ] Issue #: [Description]
- [ ] Issue #: [Description]
- [ ] Issue #: [Description]

## Lessons Learned (Preliminary)

**What Went Well**:
- 
- 

**What Went Wrong**:
- 
- 

**What We Got Lucky With**:
- 
- 

**Questions for PIR**:
- 
- 

---

**Last Updated**: 
**Updated By**: 
