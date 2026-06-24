# Merge Strategy Quick Reference

**Version**: 1.0  
**Last Updated**: 2026-06-23  
**Related**: [Merge Strategy Policy](./merge-strategy-policy.md)

## TL;DR

- ✅ **Always use "Squash and merge"**
- ✅ **PR title must follow Conventional Commits** (`type(scope): description`)
- ✅ **Include `Closes #issue` in PR body**
- ❌ **Never use "Create a merge commit"**
- ❌ **Avoid "Rebase and merge" unless exceptional**

## Quick Decision Tree

```
┌─────────────────────────────────┐
│ Ready to merge PR to main?      │
└────────────┬────────────────────┘
             │
             ├─ Is PR title in Conventional Commits format?
             │  ├─ ❌ No → Fix title first
             │  └─ ✅ Yes → Continue
             │
             ├─ Does PR description have "Closes #123"?
             │  ├─ ❌ No → Add issue reference
             │  └─ ✅ Yes → Continue
             │
             ├─ All CI checks passed?
             │  ├─ ❌ No → Wait for green CI
             │  └─ ✅ Yes → Continue
             │
             ├─ All conversations resolved?
             │  ├─ ❌ No → Resolve threads
             │  └─ ✅ Yes → Continue
             │
             └─ Click "Squash and merge" ✅
```

## Valid PR Title Examples

### Features
```
✅ feat(auth): add OAuth 2.0 support
✅ feat(api): implement rate limiting
✅ feat(ui): add dark mode toggle
```

### Bug Fixes
```
✅ fix(login): resolve session timeout issue
✅ fix(db): prevent connection pool exhaustion
✅ fix(ci): correct test flakiness in parallel runs
```

### Documentation
```
✅ docs(readme): update installation steps
✅ docs(api): add authentication examples
✅ docs(contributing): clarify PR guidelines
```

### Other Types
```
✅ chore(deps): update dependencies
✅ refactor(utils): simplify date formatting
✅ test(auth): add edge case coverage
✅ ci(workflow): optimize build caching
✅ perf(query): add database indexes
```

## Invalid PR Title Examples

```
❌ Add feature (no type prefix)
❌ Fix bug (too vague)
❌ feat: stuff (missing scope, vague description)
❌ FEAT(auth): login (wrong case)
❌ feature(auth): add login (wrong type keyword)
```

## Common Scenarios

### Scenario: Feature with multiple commits

**Before Merge**:
```
commit abc123 fix typo
commit def456 address review feedback
commit ghi789 implement user login
commit jkl012 add tests
```

**After Squash Merge**:
```
commit xyz999 feat(auth): implement user login (#123)
```

**Result**: Clean history, one commit represents entire feature.

### Scenario: Hotfix for production

**Process**:
1. Create PR from `hotfix/fix-critical-bug` → `main`
2. Title: `fix(api): prevent data leak in /users endpoint`
3. Get expedited review
4. **Still use "Squash and merge"** (policy has no exceptions)
5. Revert is then simple: `git revert <sha>`

### Scenario: PR with co-authors

**Solution**: Add co-author tags in PR description (will be included in squash commit):

```markdown
## Description
Implements the new caching layer.

## Co-authors
Co-authored-by: Alice Developer <alice@example.com>
Co-authored-by: Bob Engineer <bob@example.com>
```

**Result**: Squash commit includes co-author attribution.

## Pre-Merge Checklist (Maintainers)

Before clicking "Squash and merge":

- [ ] PR title follows `type(scope): description` format
- [ ] PR description is clear and includes `Closes #issue`
- [ ] All CI checks are green
- [ ] All review comments resolved
- [ ] At least 1 approval obtained
- [ ] No conflicts with `main` branch

## Post-Merge Verification

```bash
# 1. Verify commit appeared in main
git fetch origin main
git log origin/main -1 --oneline

# 2. Confirm issue was auto-closed
gh issue view <issue-number>

# 3. Check commit message format
git log origin/main -1 --format=full
```

## Rollback Quick Reference

### Revert the last merged PR

```bash
# 1. Identify the commit
git log --oneline main -1

# 2. Create revert PR
git checkout -b revert/pr-123-feature
git revert <commit-sha>
git push origin revert/pr-123-feature

# 3. Create PR to main
gh pr create --title "revert: undo PR #123" --body "Reverts changes from #123 due to production issue."
```

### Revert a specific older commit

```bash
# 1. Find the commit SHA
git log --oneline main --grep="feat(auth)"

# 2. Revert it
git checkout -b revert/auth-feature
git revert <commit-sha>
git push origin revert/auth-feature

# 3. Create PR
gh pr create --title "revert: undo OAuth feature" --body "Reverts #456 due to security concern."
```

## Troubleshooting

### Problem: Merge button shows "Create a merge commit"

**Cause**: Wrong merge method selected  
**Solution**: Click dropdown arrow → Select "Squash and merge"

### Problem: PR title doesn't follow format but CI passed

**Cause**: CI check may not be enforced yet  
**Solution**: Manually fix title before merging (you're the last line of defense)

### Problem: Multiple related PRs need coordinated merge

**Approach**:
1. Merge PRs in dependency order using "Squash and merge"
2. Each PR becomes one commit in `main`
3. Clean linear history: `commit-1 → commit-2 → commit-3`

### Problem: Want to preserve individual commit messages from PR

**Answer**: This is intentionally not supported (policy decision).  
**Reason**: Squash merge prioritizes clean history over preserving WIP commits.  
**Workaround**: Include detailed implementation notes in PR description (becomes commit body).

## Reference Links

- [Full Merge Strategy Policy](./merge-strategy-policy.md)
- [Conventional Commits Spec](https://www.conventionalcommits.org/)
- [Main Branch Protection Baseline](./main-branch-protection-baseline.md)

## Support

Questions about merge strategy? Contact:
- Platform Team (primary)
- Check #engineering Slack channel
- Review [Merge Strategy Policy](./merge-strategy-policy.md)
