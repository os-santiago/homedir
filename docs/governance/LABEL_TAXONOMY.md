# Canonical Label Taxonomy

**Version**: 1.0  
**Status**: Approved  
**Last Updated**: 2026-06-23  
**Owner**: Governance Team

## Overview

This document defines the canonical taxonomy of GitHub labels for the homedir project, resolving duplicates and establishing a unified, AI-friendly labeling system for issue triage, automation, and reporting.

## Objectives

1. **Single source of truth** for label definitions
2. **Eliminate EN/ES duplicates** through language-neutral canonical names
3. **Enable automated triage** and AI-based classification
4. **Preserve historical traceability** during migration
5. **Support external collaboration** with clear, consistent semantics

## Label Hierarchy

### 1. Type Labels

Indicate the nature of the issue or PR.

| Canonical Name | Description | Color | Notes |
|----------------|-------------|-------|-------|
| `type:bug` | Something isn't working correctly | `#d73a4a` | Replaces: `bug`, `error` |
| `type:feature` | New feature or enhancement request | `#a2eeef` | Replaces: `enhancement`, `mejora` |
| `type:docs` | Documentation improvements or additions | `#0075ca` | Replaces: `documentation` |
| `type:question` | Further information requested | `#d876e3` | Replaces: `question`, `pregunta` |

### 2. Priority Labels

Indicate urgency and impact (enforced, non-negotiable).

| Canonical Name | Description | Color | SLA |
|----------------|-------------|-------|-----|
| `priority:P0` | Critical: Production down, security breach | `#b60205` | 4 hours |
| `priority:P1` | High: Major feature broken, significant impact | `#d93f0b` | 1 day |
| `priority:P2` | Medium: Important but not blocking | `#fbca04` | 1 week |
| `priority:P3` | Low: Nice to have, backlog | `#0e8a16` | Best effort |

**Status**: Already canonical ✓

### 3. State Labels

Indicate disposition or workflow state.

| Canonical Name | Description | Color | Notes |
|----------------|-------------|-------|-------|
| `state:duplicate` | Duplicate of another issue | `#cfd3d7` | Replaces: `duplicate` |
| `state:wontfix` | Will not be addressed | `#ffffff` | Replaces: `wontfix`, `no solucionar` |
| `state:invalid` | Not a valid issue | `#e4e669` | Replaces: `invalid`, `no valido` |

### 4. Collaboration Labels

Indicate contributor-friendliness and help needs.

| Canonical Name | Description | Color | Notes |
|----------------|-------------|-------|-------|
| `collab:good-first-issue` | Good for newcomers | `#7057ff` | Replaces: `good first issue`, `buen primer issue` |
| `collab:help-wanted` | Extra attention or expertise needed | `#008672` | Replaces: `help wanted`, `Se necesita ayuda` |

### 5. Domain Labels

Indicate functional area or context.

| Canonical Name | Description | Color | Notes |
|----------------|-------------|-------|-------|
| `domain:hackathon` | Related to hackathon events | `#5319e7` | Replaces: `hackathon` |
| `domain:evento` | Related to community events | `#fbca04` | Replaces: `evento` |
| `domain:codex` | AI/knowledge base integration | `#ededed` | Replaces: `codex` |

### 6. Automation Labels

Special-purpose labels for CI/CD and hooks.

| Canonical Name | Description | Color | Notes |
|----------------|-------------|-------|-------|
| `automation:wos-review` | Triggers WOS delegation hook | `#7057ff` | Replaces: `wos-review` |

## Naming Conventions

### Rules

1. **Format**: `<category>:<value>` (colon-separated namespace)
2. **Case**: Lowercase with hyphens (kebab-case)
3. **Language**: English only (use descriptions for i18n)
4. **Prefixes**: Required for all labels except legacy placeholders during migration

### Examples

✅ **Correct**: `type:bug`, `priority:P0`, `collab:good-first-issue`  
❌ **Incorrect**: `Bug`, `P0`, `good_first_issue`, `error` (Spanish alias)

## Color Scheme

| Category | Color Range | Purpose |
|----------|-------------|---------|
| Type | Blue-cyan (`#0075ca` - `#a2eeef`) | Informational |
| Priority | Red-yellow gradient (`#b60205` → `#0e8a16`) | Urgency scale |
| State | Gray/neutral (`#cfd3d7`, `#e4e669`, `#ffffff`) | Disposition |
| Collaboration | Purple (`#7057ff`, `#008672`) | Community engagement |
| Domain | Varied (`#5319e7`, `#fbca04`, `#ededed`) | Contextual |
| Automation | Purple (`#7057ff`) | System-triggered |

## Legacy-to-Canonical Mapping

### Complete Mapping Matrix

| Legacy Label | Canonical Label | Action | Rationale |
|--------------|----------------|--------|-----------|
| `bug` | `type:bug` | Rename | Namespace consistency |
| `error` (ES) | `type:bug` | Alias → Deprecate | Eliminate duplicate |
| `enhancement` | `type:feature` | Rename | Clearer semantics |
| `mejora` (ES) | `type:feature` | Alias → Deprecate | Eliminate duplicate |
| `documentation` | `type:docs` | Rename | Namespace consistency |
| `question` | `type:question` | Rename | Namespace consistency |
| `pregunta` (ES) | `type:question` | Alias → Deprecate | Eliminate duplicate |
| `priority:P0` | `priority:P0` | Keep | Already canonical ✓ |
| `priority:P1` | `priority:P1` | Keep | Already canonical ✓ |
| `priority:P2` | `priority:P2` | Keep | Already canonical ✓ |
| `priority:P3` | `priority:P3` | Keep | Already canonical ✓ |
| `duplicate` | `state:duplicate` | Rename | Namespace consistency |
| `wontfix` | `state:wontfix` | Rename | Namespace consistency |
| `no solucionar` (ES) | `state:wontfix` | Alias → Deprecate | Eliminate duplicate |
| `invalid` | `state:invalid` | Rename | Namespace consistency |
| `no valido` (ES) | `state:invalid` | Alias → Deprecate | Eliminate duplicate |
| `good first issue` | `collab:good-first-issue` | Rename | Namespace consistency |
| `buen primer issue` (ES) | `collab:good-first-issue` | Alias → Deprecate | Eliminate duplicate |
| `help wanted` | `collab:help-wanted` | Rename | Namespace consistency |
| `Se necesita ayuda` (ES) | `collab:help-wanted` | Alias → Deprecate | Eliminate duplicate |
| `hackathon` | `domain:hackathon` | Rename | Namespace consistency |
| `evento` | `domain:evento` | Rename | Namespace consistency |
| `codex` | `domain:codex` | Rename | Namespace consistency |
| `wos-review` | `automation:wos-review` | Rename | Namespace consistency |

### Migration Actions

- **Rename**: Update label name, preserve color, update all issues automatically
- **Alias → Deprecate**: Create canonical label, alias legacy label (description: "Deprecated: use `<canonical>`"), bulk-relabel issues, archive after 30 days
- **Keep**: No action required (already canonical)

## Migration Strategy

See [LABEL_MIGRATION_RUNBOOK.md](./LABEL_MIGRATION_RUNBOOK.md) for detailed migration procedures.

## Triage Quick Reference

See [LABEL_TRIAGE_GUIDE.md](./LABEL_TRIAGE_GUIDE.md) for maintainer and contributor guidelines.

## Governance and Ownership

- **Label changes**: Require maintainer approval (propose via PR to this document)
- **New label requests**: Open issue with justification and proposed naming
- **Taxonomy updates**: Versioned (update header, document changes, announce to community)

## References

- **Parent Issue**: #838 (Main Governance)
- **Implementation Issue**: #840 (Canonical Label Taxonomy)
- **GitHub Label Documentation**: https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work/managing-labels

---

**Change Log**:

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-23 | Claude (via WOS) | Initial canonical taxonomy definition |
