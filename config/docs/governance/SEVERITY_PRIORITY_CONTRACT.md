# Severity and Priority Classification Contract

## Purpose

Define a formal severity-priority matrix that connects technical impact, operational urgency, and business/user impact into a single classification system usable by humans and AI agents.

## Severity Scale (S0–S4)

Severity measures the **technical impact** of an issue on system integrity, security, or functionality.

> **Note**: Severity levels S0–S4 are an internal classification concept. The repository does **not** have `severity/*` labels; the only priority labels actually defined are `priority:P0`–`priority:P3` (see [LABEL_TAXONOMY](LABEL_TAXONOMY.md)). Severity feeds into the priority mapping below.

| Level | Definition | Examples |
|-------|------------|----------|
| **S0** | Complete system outage, active security breach, or data loss in progress | Service unavailable, PII exfiltration, database corruption |
| **S1** | Major feature broken with no workaround; security vulnerability requiring immediate patch | Auth broken for all users, XSS in public page, API returns 500 for core endpoint |
| **S2** | Feature degraded or broken with a partial or cumbersome workaround | Slow page loads, UI bug in non-critical flow, missing validation on optional field |
| **S3** | Minor issue with clear workaround or cosmetic-only impact | Typo in documentation, non-functional style glitch, missing alt text |
| **S4** | Enhancement or cosmetic improvement with no production impact | Refactor suggestion, tech debt cleanup, new feature request |

## Priority Scale (P0–P4)

Priority measures the **operational urgency** based on impact × urgency.

> **Note**: The canonical labels are `priority:P0`–`priority:P3` (see [LABEL_TAXONOMY](LABEL_TAXONOMY.md)). `priority:P4` (wishlist) is only a conceptual level here and has no dedicated label.

| Level | Label | Definition | Typical Trigger |
|-------|-------|------------|-----------------|
| **P0** | `priority:P0` | Must be resolved immediately; stop-the-line event | S0 incident, legal/compliance deadline |
| **P1** | `priority:P1` | Must be resolved within hours; top of backlog | S1 incident, blocked external dependency |
| **P2** | `priority:P2` | Should be resolved within current sprint/iteration | S2 issue, feature with committed date |
| **P3** | `priority:P3` | Resolve when capacity permits; nice-to-have | S3 issue, internal improvement |
| **P4** | *(no label)* | Backlog item; no current commitment | S4 enhancement, long-term roadmap |

## Impact × Urgency Matrix

Final priority is determined by mapping severity (technical impact) against time sensitivity (urgency).

| Severity ↓ \ Urgency → | **Urgent** (blocking) | **High** (this week) | **Medium** (this sprint) | **Low** (anytime) |
|------------------------|-----------------------|----------------------|--------------------------|-------------------|
| **S0** (critical) | P0 | P0 | P1 | P1 |
| **S1** (high) | P0 | P1 | P1 | P2 |
| **S2** (medium) | P1 | P2 | P2 | P3 |
| **S3** (low) | P2 | P3 | P3 | P3 |
| **S4** (wishlist) | P3 | P3 | P3 | P3 |

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

The actual issue templates (`.github/ISSUE_TEMPLATE/*.yml`) follow a **simplified dropdown** convention: `bug_report.yml` and `feature_request.yml` expose a `Severity` dropdown with `Critical / High / Medium / Low`, and `epic.yml` exposes a `Priority` dropdown (also `Critical / High / Medium / Low`) plus the default label `priority:P1`. There is **no** S0–S4 numeric dropdown and no separate `urgency` dropdown.

- Triage maps the textual dropdown to the canonical `priority:P0`–`priority:P3` label.
- Severity values map to the S-scale defined above for internal tracking (e.g., template "Critical" → S0).

When adding new template fields, prefer the existing Critical/High/Medium/Low dropdowns for consistency; do **not** introduce literal `S0`–`S4` options unless the templates are updated in the same PR.

## Label Sync

Every issue MUST have exactly one `priority:P*` label applied at triage time. Priority is derived from severity × urgency using the matrix above.

| Severity Level | Canonical Label |
|----------------|-----------------|
| **S0** (critical) | `priority:P0` |
| **S1** (high) | `priority:P1` (or `priority:P0` if urgent) |
| **S2** (medium) | `priority:P2` (or `priority:P1` if urgent) |
| **S3** (low) | `priority:P3` |
| **S4** (wishlist) | `priority:P3` |

> There are no `severity/*` or `priority/*` (slash) labels in the repository. Only `priority:P0`–`priority:P3` exist (see [LABEL_TAXONOMY](LABEL_TAXONOMY.md)).

---

**Last Updated**: 2026-08-11
**Maintained By**: Engineering Leadership
**Parent Issue**: #838
**Closes**: #841