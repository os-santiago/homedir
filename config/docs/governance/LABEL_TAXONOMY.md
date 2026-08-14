# Canonical Label Taxonomy

## Purpose

This document defines the canonical taxonomy of GitHub labels for the `os-santiago/homedir` repository. It resolves EN/ES duplicates, establishes clear semantic categories, and provides a migration strategy from legacy labels.

## Design Principles

1. **English Primary**: All canonical labels use English names for international collaboration
2. **Semantic Clarity**: Each label has a single, unambiguous meaning
3. **Hierarchical Structure**: Labels organized by category (type, priority, domain, etc.)
4. **No Duplicates**: One canonical label per concept
5. **Descriptive Colors**: Color coding by category for visual scanning
6. **Clear Definitions**: Each label includes operational criteria for when to apply

## Canonical Label Categories

### 1. Type (Red Spectrum)

Describes the nature of the issue/PR.

| Label | Color | Description | When to Use |
|-------|-------|-------------|-------------|
| `bug` | `#d73a4a` | Something isn't working | Confirmed defect in existing functionality |
| `enhancement` | `#a2eeef` | New feature or request | Adds new capability or improves existing one |
| `documentation` | `#0075ca` | Documentation improvements | Changes to `*.md` files, docs/, comments, or examples |
| `question` | `#d876e3` | Further information is requested | Seeking clarification, not reporting a bug |

**Deprecated**: `error` (ES), `mejora` (ES) - removed from the repository on 2026-08-11, see migration table

### 2. Priority (Traffic Light Colors)

Urgency and business impact of the issue.

| Label | Color | Description | SLA | Criteria |
|-------|-------|-------------|-----|----------|
| `priority:P0` | `#b60205` | Critical - Production outage | 4 hours | Service down, data loss, security breach |
| `priority:P1` | `#d93f0b` | High - Major impact | 48 hours | Severe degradation, blocking release |
| `priority:P2` | `#fbca04` | Medium - Moderate impact | 1 week | Feature gap, minor degradation |
| `priority:P3` | `#0e8a16` | Low - Nice to have | 1 month | Polish, tech debt, future consideration |

**Note**: Priority labels are mutually exclusive. Apply exactly one per issue.

### 3. Status / Lifecycle (White/Gray Spectrum)

Indicates issue disposition or workflow state.

| Label | Color | Description | When to Use |
|-------|-------|-------------|-------------|
| `duplicate` | `#cfd3d7` | Already exists | Issue duplicates another (link to original) |
| `wontfix` | `#ffffff` | Will not be worked on | Out of scope, by design, or superseded |
| `invalid` | `#e4e669` | Doesn't seem right | Cannot reproduce, incomplete info, not a real issue |

**Deprecated**: `no valido` (ES), `no solucionar` (ES) - removed from the repository on 2026-08-11, see migration table

### 4. Collaboration (Purple Spectrum)

Signals for external contributors and automation.

| Label | Color | Description | When to Use |
|-------|-------|-------------|-------------|
| `good first issue` | `#7057ff` | Good for newcomers | Well-defined, low risk, mentorship available |
| `help wanted` | `#008672` | Extra attention needed | Expertise gap, blocker, seeking collaborator |
| `wos-review` | `#7057ff` | Trigger WOS delegation | Issue should be routed to Workspace OS for automated triage |

**Deprecated**: `buen primer issue` (ES), `Se necesita ayuda` (ES) - removed from the repository on 2026-08-11, see migration table

### 5. Domain (Specialized)

Subject area or subsystem affected.

| Label | Color | Description | When to Use |
|-------|-------|-------------|-------------|
| `codex` | `#ededed` | Codex system | Related to IA governance, issue metadata, triage automation |
| `evento` | `#fbca04` | Events | Event-related features or content |
| `hackathon` | `#5319e7` | Hackathon | Created during or for a hackathon |

**Note**: Domain labels are not mutually exclusive. An issue can have multiple domain labels.

## Legacy Label Migration

### Deprecated Labels

The following legacy labels were deprecated and fully migrated to their canonical
equivalents (deleted from the repository on 2026-08-11). The migration workflow keeps
guarding against re-creation:

| Legacy Label (ES) | Canonical Label (EN) | Auto-Migrate? | Deprecation Date |
|-------------------|----------------------|---------------|------------------|
| `error` | `bug` | Yes | 2026-07-15 |
| `mejora` | `enhancement` | Yes | 2026-07-15 |
| `buen primer issue` | `good first issue` | Yes | 2026-07-15 |
| `no valido` | `invalid` | Yes | 2026-07-15 |
| `no solucionar` | `wontfix` | Yes | 2026-07-15 |
| `pregunta` | `question` | Yes | 2026-07-15 |
| `Se necesita ayuda` | `help wanted` | Yes | 2026-07-15 |

### Migration Strategy

**Phase 1: Dual Labeling (2026-06-24 → 2026-07-15)** ✅ **Completed**

- Both legacy (ES) and canonical (EN) labels were kept active
- Both labels applied to new issues during transition
- Historical issues updated opportunistically

**Phase 2: Automated Migration (2026-07-15)** ✅ **Completed**

- Executed via PRs #1423 and #1426 on 2026-08-11:
  - Added `scripts/ci/migrate_labels.py` + `.github/workflows/label-migration.yml` (daily auto-migration)
  - Deleted all legacy ES labels (`error`, `mejora`, `buen primer issue`, `no valido`,
    `no solucionar`, `pregunta`, `Se necesita ayuda`, `evento`, `hackathon`, `codex`)

**Phase 3: Enforcement (2026-07-16+)** ✅ **Active**

- Legacy labels removed from the label picker (deleted, not archived)
- `label-migration.yml` workflow automatically replaces any re-created legacy labels daily
- Governance docs reference only the canonical taxonomy

### Migration Script

The automated migration runs from the GitHub Actions workflow
[`.github/workflows/label-migration.yml`](../../../.github/workflows/label-migration.yml),
which invokes [`scripts/ci/migrate_labels.py`](../../../scripts/ci/migrate_labels.py) daily
(at 09:00 UTC) and on manual dispatch (`workflow_dispatch`).

```python
# scripts/ci/migrate_labels.py
# Migrates re-created legacy ES labels to canonical EN labels.

LABEL_MIGRATION_MAP = {
    "error": "bug",
    "mejora": "enhancement",
    "buen primer issue": "good first issue",
    "no valido": "invalid",
    "no solucionar": "wontfix",
    "pregunta": "question",
    "Se necesita ayuda": "help wanted",
}

# Labels that are NOT migrated (valid domain labels with no EN equivalent)
SKIP_LABELS = {"evento", "hackathon"}
```

Run manually:

```bash
python scripts/ci/migrate_labels.py  # requires GITHUB_TOKEN and REPOSITORY env vars
```

The script idempotently replaces any legacy label present on open issues/PRs with its
canonical equivalent and posts a comment summarizing the migration.

## Label Usage Guidelines

### For Issue Authors

1. **Type**: Apply exactly one type label (`bug`, `enhancement`, `documentation`, `question`)
2. **Priority**: Apply exactly one priority label if urgent; otherwise defaults to `priority:P3`
3. **Domain**: Apply relevant domain labels (0-N, not mutually exclusive)
4. **Status**: Do not apply status labels yourself - maintainers will apply

### For Maintainers

1. **Triage**: Within 48 hours, verify type and priority labels are correct
2. **Collaboration**: Add `good first issue` or `help wanted` if appropriate
3. **Status**: Apply `duplicate`, `wontfix`, or `invalid` with justification comment
4. **Cleanup**: When touching old issues, opportunistically migrate legacy labels

### For Automation

1. **wos-review**: Issues with this label are automatically routed to Workspace OS for AI-driven triage
2. **good first issue**: Promoted in contributor documentation and issue lists
3. **priority:P0/P1**: Trigger alerts to on-call rotation (when implemented)

## Label Naming Conventions

All canonical labels must follow these rules:

1. **Case**: Lowercase only (e.g., `good first issue`, not `Good First Issue`)
2. **Separators**: Use spaces for multi-word labels (e.g., `good first issue`, not `good-first-issue`)
3. **Hierarchy**: Use `:` for hierarchical labels (e.g., `priority:P1`, not `priority-P1` or `P1`)
4. **Language**: English only (Spanish translations in descriptions if needed)
5. **Length**: Max 50 characters for readability
6. **No Emojis**: Text only, no emoji in label names

## Color Coding

Color palette by category:

| Category | Color Range | Rationale |
|----------|-------------|-----------|
| Type (bug) | Red (`#d73a4a`) | Alerts, problems |
| Type (enhancement) | Blue (`#a2eeef`) | Positive, additive |
| Type (documentation) | Dark blue (`#0075ca`) | Information |
| Type (question) | Pink (`#d876e3`) | Inquiry |
| Priority P0/P1 | Red (`#b60205`, `#d93f0b`) | Urgency |
| Priority P2 | Yellow (`#fbca04`) | Caution |
| Priority P3 | Green (`#0e8a16`) | Low urgency |
| Status | Gray/White (`#cfd3d7`, `#ffffff`, `#e4e669`) | Closed/neutral |
| Collaboration | Purple/Teal (`#7057ff`, `#008672`) | Community |
| Domain | Various | Context-specific |

## Review and Evolution

- **Ownership**: Platform Engineering team
- **Review Frequency**: Quarterly or when adding 5+ new labels
- **Change Process**:
  1. Propose new label or category in GitHub Discussion
  2. Gather feedback from maintainers (min 3 approvals)
  3. Update this document via PR
  4. Add label via GitHub UI with matching color/description
  5. Announce in team channel

## Related Documents

- [Issue Triage Workflow](./TRIAGE_WORKFLOW.md) (future)
- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)
- [Status Check Matrix](./STATUS_CHECK_MATRIX.md)

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-06-24 | Claude (via WOS) | Initial taxonomy for issue #840 |
| 2026-08-13 | Seb (via WOS) | Mark migration phases 1-2 complete, document label deletion and `label-migration.yml` workflow, fix migration script reference |

---

**Maintained by**: Platform Engineering  
**Review frequency**: Quarterly  
**Last reviewed**: 2026-06-24
