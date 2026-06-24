# Governance Documentation

This directory contains governance policies, runbooks, and templates for repository and operational procedures.

## Core Policies

### [Historical Issue Backfill Plan](./HISTORICAL_BACKFILL_PLAN.md)
Structured migration plan to transform all open historical issues into the canonical metadata and label schema. Covers label mapping, severity inference heuristics, 4-phase rollout, quality validation thresholds, and rollback plan.

**Key sections:**
- Label mapping from legacy (`priority:P*`, `bug`, `buen primer issue`) to canonical
- Severity inference heuristic for unlabeled issues
- Language deduplication (ES → EN consolidation)
- Automated Phase 1 script specification
- Post-migration validation with sampling

### [Severity & Priority Classification Contract](./SEVERITY_PRIORITY_CONTRACT.md)
Formal severity-priority matrix connecting technical impact with operational urgency. Defines S0–S4 and P0–P4 scales, impact × urgency matrix with tiebreakers, SLA targets, and classification examples.

**Key sections:**
- Severity scale (S0–S4) with label mappings
- Priority scale (P0–P4) with SLA targets
- Impact × urgency decision matrix
- Tiebreaker rules (customer-first, security-first, blocking-chain)
- Classification examples for onboarding

### [Definition of Ready / Definition of Done](./DEFINITION_OF_READY_DONE.md)
Criteria for issue readiness and completion. Defines what makes an issue ready for work and what constitutes "done."

**Key sections:**
- Definition of Ready (DoR) for bugs, features, and tasks
- Definition of Done (DoD) for all issue types
- Minimum closure evidence requirements
- Exception process

### [Emergency Break-Glass Runbook](./EMERGENCY_BREAK_GLASS_RUNBOOK.md)
Controlled emergency exception process for bypassing standard governance controls during production incidents.

**Key sections:**
- When to use break-glass (triggering conditions)
- Authorization requirements by severity (P0/P1/P2)
- Execution process with audit trail
- Post-incident review requirements
- KPIs and audit compliance

**Use this when:**
- Production incident requires immediate intervention
- Standard process blocked or too slow for incident SLA
- Severe business/security/compliance impact

## Templates

Templates are located in `./templates/` directory:

### [Break-Glass Request Template](./templates/break_glass_request.md)
Structured template for requesting emergency governance bypass authorization.

**Includes:**
- Incident impact assessment
- Standard process blocker justification
- Proposed emergency action details
- Risk analysis and rollback plan
- Authorization section with required approvals
- Pre-flight checklist

### [Incident Declaration Template](./templates/incident_declaration.md)
Template for formally declaring and tracking production incidents.

**Includes:**
- Severity definitions (P0-P3)
- Incident timeline
- Technical details and investigation findings
- Communication plan
- Team assignments
- Resolution verification

### [Post-Incident Review Template](./templates/post_incident_review.md)
Comprehensive template for conducting post-incident reviews (PIRs).

**Includes:**
- Executive summary
- Detailed timeline
- Root cause analysis (5 Whys)
- Break-glass justification review
- Impact assessment (user, revenue, security, technical debt)
- Preventive action items
- Lessons learned

## Audit Logs

### [Break-Glass Exception Log](./break_glass_exceptions.log)
Centralized log of all emergency governance bypass events for audit and compliance tracking.

**Logged information:**
- Date/time of exception
- Incident and PR references
- Authorizers and timestamps
- Controls bypassed
- Duration and outcome
- PIR completion status

## Helper Scripts

Located in `../../scripts/`:

### `break-glass-request.sh`
Interactive script to assist with creating break-glass requests.

**Usage:**
```bash
./scripts/break-glass-request.sh <incident-number> <severity>
```

**Example:**
```bash
./scripts/break-glass-request.sh 1001 P0
```

**Features:**
- Validates incident exists
- Creates pre-filled request from template
- Shows required authorizers based on severity
- Guides through next steps

## Workflow Overview

### Normal Change Process
```
Issue Created → DoR Validated → Work Started → PR Created → 
Code Review → CI Passes → Approvals → Merge → DoD Validated → 
Issue Closed
```

### Emergency Break-Glass Process
```
Production Incident → Incident Declaration → 
Break-Glass Request → Authorization → Emergency Bypass → 
Monitoring → Resolution → Exception Logging → 
Post-Incident Review → Preventive Actions
```

## Severity Definitions

See [Severity & Priority Classification Contract](./SEVERITY_PRIORITY_CONTRACT.md) for the complete S0–S4 / P0–P4 framework.

| Severity | Description | Example | Break-Glass SLA |
|----------|-------------|---------|-----------------|
| **P0** | Critical outage or security breach | Complete service down, active exploit | 15 min authorization |
| **P1** | Major functionality broken | Key feature unavailable for subset of users | 30 min authorization |
| **P2** | Degraded performance | Slow response times, minor feature issue | 60 min authorization |
| **P3** | Minor issue with workaround | UI bug, non-critical error | No break-glass |

## Review Frequency

| Document | Owner | Review Frequency | Next Review |
|----------|-------|------------------|-------------|
| Historical Backfill Plan | Engineering Leadership | One-time (2026-Q3) | N/A |
| Definition of Ready/Done | Product/Engineering | Quarterly | TBD |
| Break-Glass Runbook | Engineering Leadership | Quarterly | 2026-09-23 |
| Templates | Engineering Manager | Semi-annually | TBD |

## Related Documentation

- [Historical Issue Backfill Plan](./HISTORICAL_BACKFILL_PLAN.md)
- [Severity & Priority Classification Contract](./SEVERITY_PRIORITY_CONTRACT.md)
- [Branch Protection Policy](#) _(link when available)_
- [Incident Response Policy](#) _(link when available)_
- [Change Management Policy](#) _(link when available)_
- [Security Policy](../../SECURITY.md)

## Contact

**Questions or issues with governance processes:**
- Create an issue with label `governance`
- Tag: @engineering-leadership
- Slack: #engineering-ops

---

**Last Updated**: 2026-06-24  
**Maintained By**: Engineering Leadership
