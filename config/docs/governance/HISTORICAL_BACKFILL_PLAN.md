# Historical Issue Backfill Plan

## Purpose

Define a structured migration plan to backfill all open historical issues into the canonical metadata and label schema defined by the governance framework.

## Scope

| Dimension | Target | Source |
|-----------|--------|--------|
| **Severity labels** | `severity/critical`, `severity/high`, `severity/medium`, `severity/low`, `severity/wishlist` | None (new) |
| **Priority labels** | `priority/critical`, `priority/high`, `priority/medium`, `priority/low`, `priority/wishlist` | `priority:P0`–`priority:P3` (legacy) |
| **Type labels** | `type/bug`, `type/enhancement`, `type/documentation`, `type/task`, `type/epic` | `bug`, `enhancement`, `documentation` (legacy) |
| **Status labels** | `status/triaged`, `status/in-progress`, `status/blocked`, `status/review`, `status/done` | None (new) |
| **Language labels** | `lang/en`, `lang/es` | None (inferred from template) |
| **Language-typed labels** | Remove EN/ES duplicate labels | `buen primer issue`/`good first issue`, `no valido`/`invalid`, etc. |

### Historical Window

Migrate all **open issues** plus any closed issue from the last **6 months** (since 2025-12-24) that has operational value. Closed issues older than 6 months without references from open issues are excluded.

### Exclusion Criteria

Issues that will NOT be migrated:
- Closed issues older than 6 months with no cross-references from open issues
- Spam or test issues
- Issues with label `wontfix` or `no solucionar` closed more than 30 days ago
- Pull request auto-generated issues (dependabot, GH actions)

## Label Mapping

### Priority Labels

| Legacy Label | Canonical Label |
|--------------|-----------------|
| `priority:P0` | `priority/critical` |
| `priority:P1` | `priority/high` |
| `priority:P2` | `priority/medium` |
| `priority:P3` | `priority/low` |

### Type Labels

| Legacy Label | Canonical Label |
|--------------|-----------------|
| `bug` | `type/bug` |
| `enhancement` | `type/enhancement` |
| `documentation` | `type/documentation` |
| N/A (infer from template) | `type/task` |
| N/A (infer from template) | `type/epic` |

### Language Deduplication

| Remove (ES) | Keep (EN) |
|-------------|-----------|
| `buen primer issue` | `good first issue` |
| `no valido` | `invalid` |
| `no solucionar` | `wontfix` |
| `pregunta` | `question` |
| `Se necesita ayuda` | `help wanted` |

## Migration Strategy

### Phase 1: Automated Label Transformation

Run a script (GitHub Actions or local) that iterates all in-scope issues and applies:

```javascript
// Pseudo-code
for each issue in scope:
  // 1. Replace legacy priority labels
  for each legacy → canonical in priorityMapping:
    if issue has label legacy:
      removeLabel(legacy)
      addLabel(canonical)

  // 2. Replace legacy type labels
  for each legacy → canonical in typeMapping:
    if issue has label legacy:
      removeLabel(legacy)
      addLabel(canonical)

  // 3. Remove deprecated ES labels
  for each esLabel in esDeprecated:
    if issue has label esLabel:
      removeLabel(esLabel)
      addLabel("lang/es")
  
  // 4. Add severity label based on priority heuristic
  if issue has no severity label:
    severity = inferSeverityFromPriority(issue)
    addLabel(severity)

  // 5. Add lang/en if no lang label exists for EN issues
  if issue has no lang/* label and template is EN:
    addLabel("lang/en")
```

### Phase 2: Severity Inference Heuristic

For issues without explicit severity, infer from priority + content:

| Inferred Severity | Rule |
|-------------------|------|
| `severity/critical` | Priority was `P0` or contains keywords: "outage", "security", "vulnerability", "data loss", "breach" |
| `severity/high` | Priority was `P1` or contains keywords: "broken", "unable to", "error 500", "crash" |
| `severity/medium` | Priority was `P2` |
| `severity/low` | Priority was `P3` or `P4` |
| `severity/wishlist` | Label is `enhancement` or `good first issue` |

### Phase 3: Manual Review (Sampling)

After automated migration, manually review a 10% sample:
- Verify severity/priority correctness
- Check for missed deprecated labels
- Validate language label assignment

### Phase 4: Cleanup

- Archive deprecated legacy labels (`priority:P0`,`priority:P1`, etc., `buen primer issue`, `no valido`)
- Remove the legacy label set from the repository
- Lock old-style label templates to prevent re-creation

## Quality Validation

| Check | Method | Threshold |
|-------|--------|-----------|
| Every open issue has `severity/*` | `gh label list` + diff | 100% |
| Every open issue has `priority/*` | `gh label list` + diff | 100% |
| No legacy `priority:P*` labels remain | Label list scan | 0% |
| No ES duplicate labels remain | Label list scan | 0% |
| Every open issue has `type/*` | `gh label list` + diff | 100% |
| Sampling accuracy ≥ 90% | Human review of 10% | ≥ 90% |

## Rollback Plan

If migration causes issues:
1. Re-create any incorrectly removed labels via `gh label create`
2. Re-run Phase 1 with corrected mapping
3. For individual issues, use `gh issue edit --add-label` / `--remove-label`

## Rollout Checklist

- [ ] Phase 1 script written and tested on a dry-run repository
- [ ] Phase 1 executed against all in-scope issues
- [ ] Phase 2 severity inference applied
- [ ] Phase 3 manual sampling review completed
- [ ] Phase 4 legacy label cleanup executed
- [ ] Post-migration validation passes all thresholds
- [ ] Governance README updated with label migration note

---

**Last Updated**: 2026-06-24
**Maintained By**: Engineering Leadership
**Parent Issue**: #838
**Closes**: #846