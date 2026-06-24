# Merge Strategy and Linear History Policy

**Status**: Approved  
**Version**: 1.0  
**Last Updated**: 2026-06-23  
**Owner**: Platform Team  
**Related Issue**: #850  
**Parent Issue**: #838

## Purpose

This document defines the canonical merge strategy for the `main` branch and establishes the linear history policy to ensure audit trail clarity, incident traceability, and operational rollback capability.

## Executive Summary

**Canonical Merge Strategy**: **Squash and Merge**  
**Linear History Requirement**: **MANDATORY** (enforced via GitHub branch protection)  
**Merge Message Standard**: Conventional Commits format with PR reference

## Policy Statement

### 1. Canonical Merge Strategy

All pull requests targeting `main` must use **Squash and Merge** as the default and preferred merge strategy.

**Rationale**:
- **Clean history**: One commit per feature/fix, making `git log` readable
- **Auditability**: Each merge commit represents a complete, tested unit of work
- **Rollback safety**: Reverting a feature requires reverting a single commit
- **Traceability**: Direct mapping between PR, issue, and commit SHA
- **Bisect efficiency**: `git bisect` operates on meaningful units of work

### 2. Linear History Requirement

The `main` branch **MUST** maintain a linear history (no merge commits).

**GitHub Setting**:
```json
{
  "required_linear_history": true
}
```

**Enforcement**: 
- Enabled via GitHub branch protection settings
- Automatically prevents merge commits and non-fast-forward merges
- Only squash and rebase merge strategies are permitted

**Rationale**:
- **Incident tracing**: Linear timeline simplifies root cause analysis during outages
- **Cherry-pick reliability**: Clean history enables safe hotfix backports
- **Audit compliance**: Sequential commit history meets regulatory audit requirements
- **Cognitive load**: Developers can trace changes without navigating merge diamonds

### 3. Merge Message Standard

All merge commits to `main` must follow this format:

```
<type>(<scope>): <subject> (#<pr-number>)

<body>

Closes #<issue-number>
```

**Required Elements**:
- **Type**: Must be a valid Conventional Commits type (`feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `ci`, `perf`, `build`)
- **Subject**: Concise description (max 72 characters)
- **PR reference**: Must include `(#<pr-number>)` in the subject line
- **Issue closure**: Must include `Closes #<issue-number>` in the body (if applicable)

**Example**:
```
feat(auth): implement OAuth 2.0 login flow (#456)

Adds OAuth 2.0 authentication provider integration with support
for Google and GitHub identity providers.

- New AuthProvider interface
- OAuth callback handler
- Session token management
- User profile sync

Closes #423
```

**Automated Enforcement**:
- PR title linting via CI (ensures Conventional Commits format)
- Merge message is derived from PR title + body
- Squash commit inherits PR metadata automatically

## Merge Strategy Comparison Matrix

### Strategy Evaluation

| Criterion | Squash Merge | Rebase Merge | Merge Commit |
|-----------|--------------|--------------|--------------|
| **History Linearity** | ✅ Linear | ✅ Linear | ❌ Non-linear |
| **Commit Atomicity** | ✅ One commit per PR | ⚠️ Multiple commits per PR | ⚠️ Multiple commits + merge |
| **Rollback Simplicity** | ✅ Single `git revert` | ⚠️ Revert multiple commits | ❌ Complex merge revert |
| **Audit Trail** | ✅ PR-to-commit 1:1 | ⚠️ Loses PR boundary | ✅ Preserves PR context |
| **Author Attribution** | ⚠️ Squashed to merger | ✅ Preserves all authors | ✅ Preserves all authors |
| **Bisect Granularity** | ✅ Feature-level | ⚠️ Commit-level | ❌ Mixed granularity |
| **CI Signal Preservation** | ⚠️ Squash loses intermediate | ✅ All commits tested | ⚠️ Merge commit untested |
| **Git Log Readability** | ✅ Very clean | ⚠️ Can be noisy | ❌ Merge diamonds |
| **Conflicts Handling** | ✅ Squash at merge time | ⚠️ Rebase required | ✅ Merge preserves history |

### Pros and Cons by Strategy

#### Squash and Merge ✅ CANONICAL

**Pros**:
- Clean, linear `git log` with one commit per feature
- Easy to revert entire features with single `git revert <sha>`
- PR title/description becomes the commit message (enforces quality)
- Ideal for feature-branch workflow (our current model)
- No merge commits cluttering history
- Supports `required_linear_history` enforcement

**Cons**:
- Loses intermediate commit history from PR branch
- Co-author attribution requires manual `Co-authored-by` tags
- Cannot see individual WIP commits that led to final state
- Debugging within a feature requires PR branch inspection

**Best For**:
- Feature development (our primary use case)
- Bug fixes spanning multiple commits
- Documentation updates
- Any work that represents a single logical unit

#### Rebase and Merge

**Pros**:
- Preserves all commits from PR branch
- Maintains individual commit authors
- Useful for reviewing detailed implementation steps
- Supports `required_linear_history` enforcement

**Cons**:
- Can clutter `git log` with WIP commits ("fix typo", "address review comments")
- Harder to identify PR boundaries in commit history
- Reverting a feature requires reverting multiple commits
- Developers must maintain clean commit history (rebase/squash locally)
- No clear 1:1 mapping from PR to `main` commits

**Best For**:
- PRs with well-crafted, atomic commits (rare in practice)
- Contributions from external maintainers with clean history
- Educational repositories where commit-by-commit learning is valued

#### Merge Commit ❌ NOT PERMITTED

**Pros**:
- Preserves exact PR branch history
- Clear PR boundary via merge commit
- No history rewriting required

**Cons**:
- **Violates `required_linear_history`** (disallowed by policy)
- Creates merge diamonds that complicate `git log` navigation
- `git bisect` can land on untested merge commits
- Reverting requires `git revert -m 1`, which is error-prone
- Audit trail requires navigating merge graph

**Best For**:
- Long-lived integration branches (not applicable to our workflow)
- Repositories without linear history requirements

## Operational Implementation Guide

### For Pull Request Authors

1. **Title Format**: Ensure PR title follows Conventional Commits:
   ```
   feat(component): add new feature
   fix(module): resolve crash on startup
   docs(readme): update installation instructions
   ```

2. **Description**: Write a clear PR description (becomes squash commit body):
   - What changed and why
   - Link to related issues (`Closes #123`)
   - Testing performed
   - Breaking changes (if any)

3. **Commit Hygiene**: 
   - Commit messages in PR branches can be informal (will be squashed)
   - Focus on clear PR title and description instead

### For Reviewers and Maintainers

1. **Pre-Merge Checklist**:
   - [ ] PR title follows Conventional Commits format
   - [ ] PR description is clear and complete
   - [ ] Issue closure keywords present (`Closes #123`)
   - [ ] All CI checks passed
   - [ ] Conversation threads resolved

2. **Merge Process**:
   - **Always use "Squash and merge" button in GitHub UI**
   - Review auto-generated commit message (derived from PR title + body)
   - Edit if necessary to improve clarity
   - Confirm merge

3. **Post-Merge**:
   - Verify commit appears in `main` with expected message
   - Confirm linked issue auto-closed
   - Delete source branch (standard practice)

### For Repository Administrators

#### Apply Linear History Enforcement

**Via GitHub UI**:
1. Repository Settings → Branches → `main` → Edit
2. Enable "Require linear history"
3. Save changes

**Via GitHub API**:
```bash
gh api -X PATCH repos/:owner/:repo/branches/main/protection \
  -f required_linear_history=true
```

#### Configure Allowed Merge Methods

**Recommended Settings**:
- ✅ Allow squash merging (ENABLED)
- ❌ Allow merge commits (DISABLED)
- ⚠️ Allow rebase merging (OPTIONAL - can be enabled for flexibility)

**Via GitHub UI**:
1. Repository Settings → General → Pull Requests
2. Configure merge button options as above

**Via GitHub API**:
```bash
gh api -X PATCH repos/:owner/:repo \
  -f allow_squash_merge=true \
  -f allow_merge_commit=false \
  -f allow_rebase_merge=false
```

#### Verify Current Settings

```bash
gh api repos/:owner/:repo | jq '{
  allow_squash_merge,
  allow_merge_commit,
  allow_rebase_merge
}'

gh api repos/:owner/:repo/branches/main/protection | jq '{
  required_linear_history
}'
```

## Exception Handling

### When Exceptions Are Permitted

**NEVER**: This policy has no exceptions. All merges to `main` must follow the canonical strategy.

**Rationale**: Consistent merge strategy is fundamental to audit trail integrity. Mixed strategies create confusion and break tooling assumptions.

### Historical Merges

**Pre-Policy Commits**: 
- Commits merged before this policy was adopted are grandfathered
- No retroactive history rewriting
- New merges must follow this policy from effective date forward

## Enforcement Mechanisms

### Automated Enforcement

1. **Branch Protection**: `required_linear_history` prevents merge commits
2. **Merge Button Restriction**: Disable merge commit option in GitHub settings
3. **PR Title Linting**: CI check validates Conventional Commits format
4. **Commit Message Validation**: GitHub Actions job validates format on push

### Manual Enforcement

1. **Code Review**: Reviewers verify PR title quality before approval
2. **Merge Discipline**: Only maintainers with write access perform merges
3. **Quarterly Audit**: Review `git log --oneline main` for compliance

## Rollback Procedures

### Reverting a Single Commit

```bash
# Identify the commit to revert
git log --oneline main | grep "feat(auth)"

# Revert the commit (creates a new revert commit)
git revert <commit-sha>

# Push to main (requires PR if branch protection enforced)
git push origin main
```

### Reverting Multiple Features

```bash
# Revert in reverse chronological order (newest first)
git revert <sha-1>
git revert <sha-2>
git revert <sha-3>

# Or revert a range (requires linear history)
git revert <oldest-sha>^..<newest-sha>
```

## Audit Trail Usage

### Tracing an Incident to Root Commit

```bash
# 1. Identify when the issue first appeared (via bisect)
git bisect start
git bisect bad HEAD
git bisect good v1.2.0
# ... bisect will find the problematic commit

# 2. Extract PR and issue numbers from commit message
git show <commit-sha>

# 3. Review PR discussion and issue context
gh pr view <pr-number>
gh issue view <issue-number>
```

### Generating Change Log

```bash
# Extract all features between releases
git log v1.0.0..v2.0.0 --oneline --grep="^feat"

# Extract all fixes
git log v1.0.0..v2.0.0 --oneline --grep="^fix"

# Group by type
git log v1.0.0..v2.0.0 --format="%s" | sort
```

## Training and Adoption

### Developer Onboarding

New contributors should review:
1. This merge strategy policy
2. [Conventional Commits specification](https://www.conventionalcommits.org/)
3. PR template (pre-fills structure)

### Existing Contributors

- **Announcement**: Share this policy via team channels
- **Grace Period**: None (effective immediately)
- **Support**: Platform team available for questions

## Review and Maintenance

### Review Schedule

- **Quarterly**: Verify compliance via `git log` audit
- **Post-Incident**: Assess if linear history aided debugging
- **On Feedback**: If merge strategy causes friction, reassess

### Success Metrics

- **Compliance Rate**: % of `main` commits following format (target: 100%)
- **Revert Time**: Average time to identify and revert broken changes
- **Audit Efficiency**: Time to trace incident to root commit

## References

- [Conventional Commits Specification](https://www.conventionalcommits.org/)
- [GitHub Branch Protection Documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
- [Git Revert Best Practices](https://git-scm.com/docs/git-revert)
- Issue #850: Merge strategy and linear history policy
- Issue #847: Main branch protection baseline
- Issue #838: Main governance audit (parent)

## Appendix A: GitHub Settings Summary

**Required GitHub Repository Settings**:

| Setting | Value | Location |
|---------|-------|----------|
| `required_linear_history` | `true` | Branch Protection: `main` |
| `allow_squash_merge` | `true` | General → Pull Requests |
| `allow_merge_commit` | `false` | General → Pull Requests |
| `allow_rebase_merge` | `false` (or `true` if flexibility needed) | General → Pull Requests |

**Verification Command**:
```bash
gh api repos/:owner/:repo | jq '{squash: .allow_squash_merge, merge: .allow_merge_commit, rebase: .allow_rebase_merge}'
gh api repos/:owner/:repo/branches/main/protection | jq '.required_linear_history'
```

## Changelog

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-06-23 | 1.0 | Initial policy definition | Platform Team |
