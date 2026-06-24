# Severity and Priority Classification Contract

## Purpose

Define a formal severity-priority matrix that connects technical impact, operational urgency, and business/user impact into a single classification system usable by humans and AI agents.

## Severity Scale (S0–S4)

Severity measures the **technical impact** of an issue on system integrity, security, or functionality.

| Level | Label | Definition | Examples |
|-------|-------|------------|----------|
| **S0** | `severity/critical` | Complete system outage, active security breach, or data loss in progress | Service unavailable, PII exfiltration, database corruption |
| **S1** | `severity/high` | Major feature broken with no workaround; security vulnerability requiring immediate patch | Auth broken for all users, XSS in public page, API returns 500 for core endpoint |
| **S2** | `severity/medium` | Feature degraded or broken with a partial or cumbersome workaround | Slow page loads, UI bug in non-critical flow, missing validation on optional field |
| **S3** | `severity/low` | Minor issue with clear workaround or cosmetic-only impact | Typo in documentation, non-functional style glitch, missing alt text |
| **S4** | `severity/wishlist` | Enhancement or cosmetic improvement with no production impact | Refactor suggestion, tech debt cleanup, new feature request |

## Priority Scale (P0–P4)

Priority measures the **operational urgency** based on impact × urgency.

| Level | Label | Definition | Typical Trigger |
|-------|-------|------------|-----------------|
| **P0** | `priority/critical` | Must be resolved immediately; stop-the-line event | S0 incident, legal/compliance deadline |
| **P1** | `priority/high` | Must be resolved within hours; top of backlog | S1 incident, blocked external dependency |
| **P2** | `priority/medium` | Should be resolved within current sprint/iteration | S2 issue, feature with committed date |
| **P3** | `priority/low` | Resolve when capacity permits; nice-to-have | S3 issue, internal improvement |
| **P4** | `priority/wishlist` | Backlog item; no current commitment | S4 enhancement, long-term roadmap |

## Impact × Urgency Matrix

Final priority is determined by mapping severity (technical impact) against time sensitivity (urgency).

| Severity ↓ \ Urgency → | **Urgent** (blocking) | **High** (this week) | **Medium** (this sprint) | **Low** (anytime) |
|------------------------|-----------------------|----------------------|--------------------------|-------------------|
| **S0** (critical) | P0 | P0 | P1 | P1 |
| **S1** (high) | P0 | P1 | P1 | P2 |
| **S2** (medium) | P1 | P2 | P2 | P3 |
| **S3** (low) | P2 | P3 | P3 | P4 |
| **S4** (wishlist) | P3 | P4 | P4 | P4 |

### Tiebreaker Rules

1. **Customer-first**: If the issue affects paying/external users, escalate one priority level.
2. **Security-first**: If the issue involves auth, data privacy, or compliance, escalate one severity level.
3. **Blocking-chain**: If the issue blocks another P0/P1 issue, match the blocked issue's priority.

## SLA Targets by Level

| Level | Triage Time | Assignment Time | First Response | Resolution Target |
|-------|-------------|-----------------|----------------|-------------------|
| **P0** | 15 min | 30 min | 1 hour | 4 hours (hotfix) |
| **P1** | 1 hour | 2 hours | 4 hours | 24 hours |
| **P2** | 4 hours | 1 business day | 2 business days | 1 sprint |
| **P3** | 1 business day | 2 business days | 1 week | 2 sprints |
| **P4** | 1 week | 2 weeks | 1 month | Next quarter |

> Triage Time = time until first label/priority assignment.
> Assignment Time = time until an owner is identified.
> First Response = time until a human acknowledges the issue.
> Resolution Target = expected time to deploy a fix (measured from assignment).

## Classification Examples

### Example 1: Production outage on login
```
Severity: S0 (complete service unavailable)
Urgency:   Urgent (all users blocked)
Priority:  P0
Rationale: S0 + Urgent → P0. Customer-first: escalates to P0.
Action:    Immediate hotfix, break-glass if needed.
```

### Example 2: Missing alt text on profile image
```
Severity: S3 (cosmetic, accessibility gap)
Urgency:   Medium (no blocking dependency)
Priority:  P3
Rationale: S3 + Medium → P3.
Action:    Add to sprint backlog, resolve when capacity permits.
```

### Example 3: API rate limiting too strict for legitimate users
```
Severity: S2 (degraded experience, workaround exists)
Urgency:   High (multiple users affected this week)
Priority:  P2
Rationale: S2 + High → P2. Not security/auth related, no escalation.
Action:    Schedule within current sprint.
```

### Example 4: Dependency with known CVE
```
Severity: S1 (security vulnerability in production)
Urgency:   Urgent (exploit in the wild)
Priority:  P0
Rationale: S1 + Urgent → P0. Security-first escalates one severity → S0 → P0.
Action:    Emergency patch, break-glass if standard CI blocks.
```

### Example 5: Refactor legacy notification module
```
Severity: S4 (enhancement, no production impact)
Urgency:   Low (no deadline)
Priority:  P4
Rationale: S4 + Low → P4.
Action:    Add to roadmap, revisit quarterly.
```

## Issue Template Integration

Issue templates should include a severity-priority classification block:

```yaml
# In .github/ISSUE_TEMPLATE/*.yml
- type: dropdown
  id: severity
  attributes:
    label: Severity
    options:
      - S0 - Critical (outage/breach)
      - S1 - High (major broken/security)
      - S2 - Medium (degraded/workaround)
      - S3 - Low (cosmetic/docs)
      - S4 - Wishlist (enhancement)
  validations:
    required: true

- type: dropdown
  id: urgency
  attributes:
    label: Urgency
    options:
      - Urgent - Blocking
      - High - This week
      - Medium - This sprint
      - Low - Anytime
  validations:
    required: true
```

## Label Sync

Every issue MUST have exactly one `severity/*` and one `priority/*` label applied at triage time.

| Severity Label | Priority Label |
|----------------|----------------|
| `severity/critical` | `priority/critical` |
| `severity/high` | `priority/high` |
| `severity/medium` | `priority/medium` |
| `severity/low` | `priority/low` |
| `severity/wishlist` | `priority/wishlist` |

---

**Last Updated**: 2026-06-24
**Maintained By**: Engineering Leadership
**Parent Issue**: #838
**Closes**: #841