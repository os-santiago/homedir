# Label Taxonomy and Governance

## Overview

This document defines the canonical label taxonomy for the repository, resolving EN/ES duplicates and establishing clear semantic categories for automated triage and reporting.

## Canonical Label Catalog

### Type Labels (Mutually Exclusive)

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `type:bug` | `#d73a4a` | Something isn't working correctly | Use for defects, errors, or broken functionality |
| `type:feature` | `#0075ca` | New feature or capability request | Use for new functionality, not improvements to existing features |
| `type:enhancement` | `#a2eeef` | Improvement to existing feature | Use for improvements to existing functionality |
| `type:docs` | `#0075ca` | Documentation changes only | Use when PR/issue only affects documentation |
| `type:refactor` | `#fbca04` | Code restructuring without behavior change | Use for internal improvements that don't change external behavior |
| `type:chore` | `#fef2c0` | Maintenance tasks, dependencies | Use for tooling, dependencies, CI/CD, and build changes |

### Priority Labels (Mutually Exclusive)

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `priority:P0` | `#b60205` | Critical - immediate attention required | Blockers, production outages, security vulnerabilities |
| `priority:P1` | `#d93f0b` | High - next sprint | Significant impact, high-value features |
| `priority:P2` | `#fbca04` | Medium - backlog planning | Standard priority, plan for upcoming cycles |
| `priority:P3` | `#0e8a16` | Low - nice to have | Low impact, can be deferred |

### Severity Labels (For Bugs)

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `severity:critical` | `#b60205` | System down, data loss | Production blocker, immediate fix required |
| `severity:high` | `#d93f0b` | Major functionality broken | Significant functionality impacted |
| `severity:medium` | `#fbca04` | Feature degraded | Partial functionality loss, workaround exists |
| `severity:low` | `#0e8a16` | Minor issue | Cosmetic issues, minor inconveniences |

### Domain Labels (Contextual)

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `domain:ia-governance` | `#5319e7` | IA governance and policy | Issues related to AI agent governance, policies, workflows |
| `domain:wos` | `#1d76db` | Workspace OS | Workspace OS routing, delegation, agent coordination |
| `domain:ci-cd` | `#0052cc` | CI/CD pipeline | GitHub Actions, automation, release workflows |
| `domain:security` | `#b60205` | Security-related | Security vulnerabilities, authentication, authorization |
| `domain:testing` | `#c5def5` | Testing infrastructure | Test frameworks, test coverage, test utilities |

### Collaboration Labels

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `good-first-issue` | `#7057ff` | Good for newcomers | Well-scoped, documented, suitable for first-time contributors |
| `help-wanted` | `#008672` | Community help requested | Maintainers explicitly requesting community contributions |
| `needs-info` | `#d876e3` | Awaiting additional information | Blocked pending clarification from issue author |
| `needs-review` | `#0e8a16` | Ready for review | PR ready for maintainer review |

### Status Labels

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `status:blocked` | `#b60205` | Blocked by dependencies | Waiting on external dependencies or other issues |
| `status:wip` | `#fbca04` | Work in progress | Active development, not ready for review |
| `status:stale` | `#e4e669` | No recent activity | Automatically applied after inactivity threshold |
| `status:wontfix` | `#ffffff` | Will not be addressed | Issue rejected, out of scope, or duplicate |
| `status:duplicate` | `#cfd3d7` | Duplicate issue | Closed as duplicate (reference canonical issue) |

### Special Purpose Labels

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `codex` | `#ededed` | Codex tracking | Internal tracking for Codex system |
| `wos-review` | `#7057ff` | WOS delegation trigger | Triggers Claude → WOS delegation hook |
| `evento` | `#fbca04` | Event-related | Issues related to events, workshops |
| `hackathon` | `#5319e7` | Hackathon project | Hackathon-related issues |

## Naming Conventions

1. **Use English for all canonical labels** - Ensures consistency with GitHub ecosystem and AI tooling
2. **Use kebab-case** - `good-first-issue` not `good_first_issue` or `GoodFirstIssue`
3. **Use namespace prefixes for categories** - `type:`, `priority:`, `domain:`, `status:`, `severity:`
4. **Use descriptive, not generic names** - `needs-info` not `question`
5. **Avoid emoji in label names** - Use clear text descriptions instead

## Label Usage Rules

### Assignment Rules

1. **Every issue MUST have one type label** - `type:bug`, `type:feature`, `type:enhancement`, etc.
2. **Every issue SHOULD have one priority label** - `priority:P0` through `priority:P3`
3. **Bugs SHOULD have one severity label** - `severity:critical` through `severity:low`
4. **Domain labels are contextual** - Issues may have 0-3 domain labels
5. **Status labels are workflow-driven** - Applied/removed during issue lifecycle

### Combination Guidelines

**Valid combinations:**
- `type:bug` + `priority:P0` + `severity:critical` + `domain:security`
- `type:feature` + `priority:P2` + `domain:wos` + `good-first-issue`
- `type:docs` + `priority:P3` + `domain:ia-governance`

**Invalid combinations:**
- ❌ Multiple type labels (e.g., `type:bug` + `type:feature`)
- ❌ Multiple priority labels (e.g., `priority:P0` + `priority:P1`)
- ❌ Multiple severity labels (e.g., `severity:critical` + `severity:high`)
- ❌ Legacy + canonical (e.g., `bug` + `type:bug`)

## Label Colors

Colors follow semantic conventions:
- **Red (`#b60205`, `#d73a4a`)** - Critical issues, bugs, security
- **Orange (`#d93f0b`, `#fbca04`)** - High priority, warnings
- **Yellow (`#fbca04`, `#fef2c0`)** - Medium priority, maintenance
- **Green (`#0e8a16`, `#008672`)** - Low priority, help wanted, ready states
- **Blue (`#0075ca`, `#1d76db`)** - Features, documentation, domains
- **Purple (`#5319e7`, `#7057ff`)** - Special workflows, good first issue
- **Pink (`#d876e3`)** - Information needed
- **Gray/White (`#cfd3d7`, `#ffffff`, `#ededed`)** - Won't fix, duplicates, internal

## Migration Strategy

See [LABEL_MIGRATION.md](./LABEL_MIGRATION.md) for the complete migration plan.

## Quick Reference for Triage

### New Issue Triage Checklist

1. ✅ Add one type label (`type:*`)
2. ✅ Add one priority label (`priority:P0-P3`)
3. ✅ If bug: add one severity label (`severity:*`)
4. ✅ Add relevant domain labels (`domain:*`)
5. ✅ Add collaboration labels if appropriate (`good-first-issue`, `help-wanted`)
6. ✅ Remove any legacy labels (see migration mapping)

### PR Triage Checklist

1. ✅ Ensure linked issue has correct labels
2. ✅ Add `status:wip` if not ready for review
3. ✅ Add `status:needs-review` when ready
4. ✅ Add `status:blocked` if waiting on dependencies

## References

- [Label Migration Plan](./LABEL_MIGRATION.md) - Legacy to canonical mapping
- [Issue Templates](./templates/) - Pre-configured label sets
- [GitHub Labels API](https://docs.github.com/en/rest/issues/labels) - Automation reference

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-06-24 | 1.0 | Initial canonical taxonomy | AI Governance Team |
