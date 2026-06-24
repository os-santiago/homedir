# Label Migration Plan

## Overview

This document provides the migration strategy from legacy labels (including EN/ES duplicates) to the canonical taxonomy defined in [LABEL_TAXONOMY.md](./LABEL_TAXONOMY.md).

## Migration Principles

1. **Preserve historical traceability** - Legacy labels remain on closed issues/PRs
2. **Gradual migration** - Active issues migrated first, then bulk historical migration
3. **No data loss** - All label information preserved through mapping
4. **Automation-friendly** - Mapping table supports automated bulk migration
5. **Deprecation visibility** - Deprecated labels marked with `[DEPRECATED]` prefix

## Legacy to Canonical Mapping

### Type Labels

| Legacy Label (EN) | Legacy Label (ES) | Canonical Label | Notes |
|-------------------|-------------------|-----------------|-------|
| `bug` | `error` | `type:bug` | Most common issue type |
| `enhancement` | `mejora` | `type:enhancement` | Improvements to existing features |
| N/A | N/A | `type:feature` | **New** - for net-new functionality |
| `documentation` | N/A | `type:docs` | Documentation-only changes |
| N/A | N/A | `type:refactor` | **New** - internal restructuring |
| N/A | N/A | `type:chore` | **New** - maintenance tasks |

### Priority Labels

| Legacy Label (EN) | Legacy Label (ES) | Canonical Label | Notes |
|-------------------|-------------------|-----------------|-------|
| `priority:P0` | N/A | `priority:P0` | **No change** - already canonical |
| `priority:P1` | N/A | `priority:P1` | **No change** - already canonical |
| `priority:P2` | N/A | `priority:P2` | **No change** - already canonical |
| `priority:P3` | N/A | `priority:P3` | **No change** - already canonical |

### Collaboration Labels

| Legacy Label (EN) | Legacy Label (ES) | Canonical Label | Notes |
|-------------------|-------------------|-----------------|-------|
| `good first issue` | `buen primer issue` | `good-first-issue` | Standardize to kebab-case |
| `help wanted` | `Se necesita ayuda` | `help-wanted` | Standardize to kebab-case |
| `question` | `pregunta` | `needs-info` | More specific semantic meaning |
| `invalid` | `no valido` | `status:wontfix` | Better reflects intent |

### Status Labels

| Legacy Label (EN) | Legacy Label (ES) | Canonical Label | Notes |
|-------------------|-------------------|-----------------|-------|
| `wontfix` | `no solucionar` | `status:wontfix` | Add namespace prefix |
| `duplicate` | N/A | `status:duplicate` | Add namespace prefix |
| N/A | N/A | `status:blocked` | **New** - explicit blocking state |
| N/A | N/A | `status:wip` | **New** - work in progress marker |
| N/A | N/A | `status:stale` | **New** - for automated stale detection |
| N/A | N/A | `needs-review` | **New** - explicit review request |

### Special Purpose Labels (No Migration)

These labels remain unchanged as they serve specific system purposes:

| Label | Status | Notes |
|-------|--------|-------|
| `codex` | **Keep as-is** | Internal Codex tracking |
| `wos-review` | **Keep as-is** | WOS delegation trigger |
| `evento` | **Keep as-is** | Event tracking |
| `hackathon` | **Keep as-is** | Hackathon tracking |

## Migration Phases

### Phase 1: Create Canonical Labels (Week 1)

**Objective:** Establish all canonical labels in the repository

**Actions:**
1. Create all `type:*` labels with correct colors and descriptions
2. Create all `severity:*` labels
3. Create all `domain:*` labels
4. Create all `status:*` labels
5. Create standardized collaboration labels (`good-first-issue`, `help-wanted`, `needs-info`)

**Validation:**
```bash
# Verify all canonical labels exist
gh label list --json name,description,color | jq -r '.[] | select(.name | startswith("type:") or startswith("priority:") or startswith("severity:") or startswith("domain:") or startswith("status:")) | .name'
```

### Phase 2: Mark Legacy Labels as Deprecated (Week 1)

**Objective:** Signal deprecation without removing labels

**Actions:**
1. Rename legacy labels with `[DEPRECATED]` prefix
   - `bug` → `[DEPRECATED] bug → use type:bug`
   - `error` → `[DEPRECATED] error → use type:bug`
   - `enhancement` → `[DEPRECATED] enhancement → use type:enhancement`
   - `mejora` → `[DEPRECATED] mejora → use type:enhancement`
   - etc.

2. Update label descriptions to reference canonical replacement
3. Change colors to gray (`#e4e669`) to visually indicate deprecation

**Script:**
```bash
# Example deprecation script
gh label edit "bug" --name "[DEPRECATED] bug → use type:bug" --description "DEPRECATED: Use type:bug instead" --color "e4e669"
gh label edit "error" --name "[DEPRECATED] error → use type:bug" --description "DEPRECATED: Use type:bug instead" --color "e4e669"
```

### Phase 3: Migrate Active Issues (Week 2-3)

**Objective:** Apply canonical labels to all open issues and active PRs

**Actions:**
1. Create migration script using mapping table
2. For each open issue:
   - Read current labels
   - Apply canonical equivalents using mapping table
   - Validate: every issue has exactly one `type:*` label
   - Validate: every issue has exactly one `priority:*` label
   - Remove deprecated labels
3. Post migration comment on each issue documenting label changes

**Script:**
```bash
# Bulk migration script (example)
gh issue list --state open --limit 1000 --json number,labels | jq -r '
  .[] | 
  select(.labels[] | .name == "bug") | 
  .number
' | while read issue_number; do
  gh issue edit "$issue_number" --add-label "type:bug" --remove-label "bug"
  echo "Migrated issue #$issue_number: bug → type:bug"
done
```

**Migration Comment Template:**
```markdown
🏷️ **Label Migration Notice**

This issue's labels have been updated to match the new [canonical taxonomy](../governance/LABEL_TAXONOMY.md):

- ~~`bug`~~ → `type:bug`
- ~~`priority:P1`~~ → `priority:P1` ✅ (already canonical)

No action required. See [LABEL_MIGRATION.md](../governance/LABEL_MIGRATION.md) for details.
```

### Phase 4: Historical Migration (Week 4)

**Objective:** Migrate closed issues and PRs for reporting consistency

**Actions:**
1. Same process as Phase 3, but targeting closed issues
2. Lower priority - can be done in batches
3. Focus on issues closed within last 6 months first

**Batch Processing:**
```bash
# Process in batches of 100 to avoid rate limits
gh issue list --state closed --limit 100 --search "label:bug" --json number,labels | 
  jq -r '.[] | .number' | 
  xargs -I {} gh issue edit {} --add-label "type:bug"
```

### Phase 5: Deprecation Cleanup (Week 5+)

**Objective:** Remove deprecated labels after migration complete

**Actions:**
1. Verify no open issues use deprecated labels:
   ```bash
   gh issue list --state open --label "[DEPRECATED] bug → use type:bug"
   ```
2. If empty, delete deprecated label:
   ```bash
   gh label delete "[DEPRECATED] bug → use type:bug"
   ```
3. Repeat for all deprecated labels

**Validation:**
```bash
# Ensure no deprecated labels remain
gh label list | grep -i deprecated
# Should return empty
```

## Automated Migration Tools

### Label Sync Configuration

Create `.github/labels.yml` for automated label synchronization:

```yaml
# Type labels
- name: "type:bug"
  color: "d73a4a"
  description: "Something isn't working correctly"

- name: "type:feature"
  color: "0075ca"
  description: "New feature or capability request"

- name: "type:enhancement"
  color: "a2eeef"
  description: "Improvement to existing feature"

# ... (all canonical labels)

# Deprecated labels (to be removed after migration)
- name: "[DEPRECATED] bug → use type:bug"
  color: "e4e669"
  description: "DEPRECATED: Use type:bug instead"
  delete: true  # Flag for removal after migration
```

### GitHub Actions Workflow

Create `.github/workflows/label-enforcement.yml`:

```yaml
name: Label Enforcement

on:
  issues:
    types: [opened, labeled, unlabeled]
  pull_request:
    types: [opened, labeled, unlabeled]

jobs:
  enforce-labels:
    runs-on: ubuntu-latest
    steps:
      - name: Check required labels
        uses: actions/github-script@v7
        with:
          script: |
            const { data: labels } = await github.rest.issues.listLabelsOnIssue({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number
            });
            
            const hasType = labels.some(l => l.name.startsWith('type:'));
            const hasPriority = labels.some(l => l.name.startsWith('priority:'));
            const hasDeprecated = labels.some(l => l.name.includes('[DEPRECATED]'));
            
            if (!hasType) {
              await github.rest.issues.createComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: context.issue.number,
                body: '⚠️ Missing required `type:*` label. Please add one: `type:bug`, `type:feature`, `type:enhancement`, `type:docs`, `type:refactor`, or `type:chore`.'
              });
            }
            
            if (hasDeprecated) {
              const deprecatedLabels = labels.filter(l => l.name.includes('[DEPRECATED]')).map(l => l.name);
              await github.rest.issues.createComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: context.issue.number,
                body: `🏷️ This issue uses deprecated labels: ${deprecatedLabels.join(', ')}. See [Label Migration Guide](../governance/LABEL_MIGRATION.md) for canonical replacements.`
              });
            }
```

## Rollback Plan

If critical issues arise during migration:

1. **Stop migration scripts** - Halt all automated label changes
2. **Assess impact** - Identify affected issues/PRs
3. **Revert batch** - Use GitHub API to restore previous labels:
   ```bash
   # Example rollback for last 24 hours
   gh issue list --search "updated:>=$(date -d '24 hours ago' --iso-8601)" --json number | 
     jq -r '.[].number' | 
     xargs -I {} gh issue edit {} --add-label "bug" --remove-label "type:bug"
   ```
4. **Document lessons** - Update migration plan with safeguards
5. **Resume with smaller batches** - Reduce batch size, add validation

## Monitoring and Reporting

### Migration Dashboard Queries

**Progress tracking:**
```bash
# Count issues by label type
echo "Canonical labels:"
gh issue list --state all --label "type:bug" --json number | jq '. | length'

echo "Legacy labels:"
gh issue list --state all --label "bug" --json number | jq '. | length'
```

**Quality checks:**
```bash
# Find issues without type label
gh issue list --state open --json number,labels | 
  jq -r '.[] | select([.labels[].name | select(startswith("type:"))] | length == 0) | .number'

# Find issues with multiple type labels (violation)
gh issue list --state open --json number,labels | 
  jq -r '.[] | select([.labels[].name | select(startswith("type:"))] | length > 1) | .number'
```

## Success Metrics

- ✅ 100% of open issues have exactly one `type:*` label
- ✅ 95%+ of open issues have one `priority:*` label
- ✅ 0 open issues use deprecated labels
- ✅ 0 label assignment errors (multiple mutually-exclusive labels)
- ✅ Maintainer training completed (triage guide distributed)

## Timeline Summary

| Phase | Duration | Effort | Risk |
|-------|----------|--------|------|
| Phase 1: Create canonical labels | 1 day | Low | Low |
| Phase 2: Mark deprecated | 1 day | Low | Low |
| Phase 3: Migrate active issues | 1-2 weeks | Medium | Medium |
| Phase 4: Historical migration | 2-4 weeks | Low | Low |
| Phase 5: Cleanup deprecated | 1 day | Low | Low |
| **Total** | **4-6 weeks** | **Medium** | **Low-Medium** |

## References

- [Label Taxonomy](./LABEL_TAXONOMY.md) - Canonical label definitions
- [GitHub Labels API](https://docs.github.com/en/rest/issues/labels) - API documentation
- [Label Sync Action](https://github.com/marketplace/actions/label-sync) - Automated label management

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-06-24 | 1.0 | Initial migration plan | AI Governance Team |
