# Label Triage Quick Reference

## Overview

Fast reference guide for labeling issues and PRs using the canonical taxonomy. For full details, see [LABEL_TAXONOMY.md](./LABEL_TAXONOMY.md).

## 30-Second Triage

Every issue needs **at minimum**:

1. ✅ **One type label** → `type:bug`, `type:feature`, `type:enhancement`, `type:docs`, `type:refactor`, or `type:chore`
2. ✅ **One priority label** → `priority:P0` (critical), `P1` (high), `P2` (medium), `P3` (low)

If it's a bug, add:

3. ✅ **One severity label** → `severity:critical`, `severity:high`, `severity:medium`, or `severity:low`

Optional but helpful:

4. 🔹 **Domain labels** → `domain:ia-governance`, `domain:wos`, `domain:ci-cd`, etc.
5. 🔹 **Collaboration labels** → `good-first-issue`, `help-wanted`, `needs-info`

## Type Label Decision Tree

```
What is this issue about?

┌─ Something broken? → type:bug
│
├─ New functionality that doesn't exist yet? → type:feature
│
├─ Making existing feature better? → type:enhancement
│
├─ Only documentation changes? → type:docs
│
├─ Code cleanup, no behavior change? → type:refactor
│
└─ CI/CD, dependencies, tooling? → type:chore
```

## Priority Label Guide

| Priority | When to Use | Examples |
|----------|-------------|----------|
| **P0** | Production down, security breach, data loss | Outage, critical vulnerability, blocker |
| **P1** | High impact, high value, next sprint | Major bug affecting many users, important feature |
| **P2** | Standard priority, plan for upcoming cycle | Regular bugs, standard features, improvements |
| **P3** | Low impact, nice to have | Minor cosmetic issues, backlog ideas |

**Default:** When unsure, use `priority:P2` and let maintainers adjust.

## Severity Label Guide (Bugs Only)

| Severity | When to Use | Examples |
|----------|-------------|----------|
| **critical** | System unusable, data loss | App crashes on launch, database corruption |
| **high** | Major feature broken | Login fails, core workflow broken |
| **medium** | Feature degraded, workaround exists | Slow performance, UI glitch with workaround |
| **low** | Minor issue, cosmetic | Typo, misaligned button, minor visual bug |

**Rule:** If `priority:P0` → always `severity:critical` or `severity:high`

## Common Scenarios

### Scenario: Bug Report

**Example:** "App crashes when clicking 'Save' button"

**Labels:**
- `type:bug` (something broken)
- `priority:P1` (high impact - core functionality)
- `severity:high` (major feature broken)
- `domain:ui` (if applicable)

### Scenario: Feature Request

**Example:** "Add dark mode support"

**Labels:**
- `type:feature` (new functionality)
- `priority:P2` or `P3` (enhancement, not urgent)
- `domain:ui` (if applicable)
- `help-wanted` (if open to contributions)

### Scenario: Documentation Improvement

**Example:** "README missing installation instructions"

**Labels:**
- `type:docs` (docs only)
- `priority:P2` (helpful but not urgent)
- `good-first-issue` (if simple and well-scoped)

### Scenario: Performance Issue

**Example:** "Page load takes 10 seconds"

**Labels:**
- `type:bug` (degraded performance = broken behavior)
- `priority:P1` or `P2` (depending on user impact)
- `severity:medium` or `severity:high`
- `domain:performance` (if applicable)

### Scenario: Security Vulnerability

**Example:** "XSS vulnerability in user input field"

**Labels:**
- `type:bug` (security issue = bug)
- `priority:P0` (security = immediate)
- `severity:critical` (security vulnerabilities always critical)
- `domain:security`

**Important:** Do NOT disclose details publicly. Use GitHub Security Advisories.

### Scenario: Dependency Update

**Example:** "Update React to v19"

**Labels:**
- `type:chore` (dependency management)
- `priority:P2` or `P3` (unless security update)
- If security: `priority:P0` + `domain:security`

## What NOT to Do

❌ **Don't use multiple type labels**
- Wrong: `type:bug` + `type:feature`
- Right: Choose the primary type

❌ **Don't use legacy labels**
- Wrong: `bug`, `enhancement`, `buen primer issue`
- Right: `type:bug`, `type:enhancement`, `good-first-issue`

❌ **Don't skip type labels**
- Wrong: Only adding `priority:P1`
- Right: Add `type:*` first, then priority

❌ **Don't over-label**
- Wrong: Adding 10 different domain labels
- Right: 1-3 most relevant domain labels

## Label Prefixes Cheat Sheet

| Prefix | Category | Mutually Exclusive? | Required? |
|--------|----------|---------------------|-----------|
| `type:` | Issue type | ✅ Yes (pick ONE) | ✅ Required |
| `priority:` | Priority level | ✅ Yes (pick ONE) | ✅ Recommended |
| `severity:` | Bug severity | ✅ Yes (pick ONE) | Only for bugs |
| `domain:` | Domain/area | ❌ No (0-3 labels) | Optional |
| `status:` | Workflow state | ❌ No | Optional |

## External Contributors

If you're filing an issue and unsure about labels:

1. **Add what you know confidently:**
   - Type: Is it a bug, feature, or docs?
   - Priority: How urgent is it to you?

2. **Skip what you don't know:**
   - Maintainers will add domain/status labels during triage

3. **When in doubt:**
   - `type:bug` or `type:feature` covers 80% of cases
   - `priority:P2` is a safe default
   - Add `needs-info` if you need clarification

4. **Good practices:**
   - ✅ Search for duplicates before filing
   - ✅ Use issue templates (they pre-populate labels)
   - ✅ Describe the issue clearly (helps maintainers label correctly)

## Maintainer Workflows

### New Issue Triage

1. Read issue description
2. Apply type label (`type:*`)
3. Apply priority label (`priority:*`)
4. If bug: Apply severity label (`severity:*`)
5. Apply 1-3 domain labels if applicable
6. Add `good-first-issue` if suitable for newcomers
7. Add `help-wanted` if seeking community contributions
8. Add `needs-info` if description incomplete
9. Remove any legacy labels (see [LABEL_MIGRATION.md](./LABEL_MIGRATION.md))

### PR Labeling

1. **Match the linked issue labels** (if applicable)
2. Add `status:wip` if PR is not ready for review
3. Add `status:needs-review` when ready for review
4. Add `status:blocked` if waiting on dependencies
5. Type/priority labels automatically sync from linked issue

### Stale Issues

1. Bot automatically adds `status:stale` after 90 days inactivity
2. If still relevant: Remove `status:stale`, re-triage priority
3. If no longer relevant: Add `status:wontfix` and close

## Automation Hints

- **Issue templates** pre-populate labels based on issue type
- **PR auto-labeling** inherits labels from linked issues (use `Closes #123`)
- **Stale bot** adds `status:stale` after inactivity threshold
- **CI checks** validate that every issue has required labels
- **Label enforcement** warns when using deprecated labels

## Quick Commands (GitHub CLI)

```bash
# Add labels to issue
gh issue edit 123 --add-label "type:bug,priority:P1,severity:high"

# Remove deprecated label
gh issue edit 123 --remove-label "bug"

# List issues by label
gh issue list --label "type:bug" --label "priority:P0"

# Bulk label update (use carefully!)
gh issue list --search "label:bug" --json number | 
  jq -r '.[].number' | 
  xargs -I {} gh issue edit {} --add-label "type:bug" --remove-label "bug"
```

## Need Help?

- 📚 Full taxonomy: [LABEL_TAXONOMY.md](./LABEL_TAXONOMY.md)
- 🔄 Migration guide: [LABEL_MIGRATION.md](./LABEL_MIGRATION.md)
- 💬 Questions: Open an issue with `type:question` + `needs-info`

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-06-24 | 1.0 | Initial triage guide | AI Governance Team |
