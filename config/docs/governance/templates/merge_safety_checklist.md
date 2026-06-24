# Merge Safety Checklist

Use this checklist before merging any pull request to ensure compliance with review policy and code quality standards.

## Pre-Merge Verification

### Review Requirements

- [ ] **Minimum approvals obtained** (verify based on change risk level)
  - Low risk (docs/typos): 1 approval
  - Medium risk (features/refactors): 2 approvals
  - High risk (security/breaking): 2 approvals from code owners
  - Critical risk (auth/encryption): 3 approvals including security team

- [ ] **All conversations resolved**
  - No open comment threads
  - All reviewer concerns addressed
  - Disagreements escalated and documented

- [ ] **Code owner approval** (for Medium+ risk changes)
  - At least one code owner approved
  - All code owners approved for High/Critical changes

- [ ] **No stale reviews**
  - No significant changes since last approval (>50 lines)
  - No new files added after approval
  - No dependency changes after approval
  - Less than 7 days since last approval

### Technical Quality

- [ ] **All CI/CD checks pass**
  - Tests pass (unit, integration, e2e)
  - Linting passes
  - Type checking passes (if applicable)
  - Security scans pass
  - Build succeeds

- [ ] **Branch up to date**
  - Merged latest from target branch
  - No merge conflicts
  - Rebase completed if required

- [ ] **Linked issue complete**
  - Issue linked in PR description
  - All acceptance criteria met
  - Scope matches issue requirements

### Code Quality

- [ ] **Tests added/updated**
  - New functionality has tests
  - Edge cases covered
  - Test coverage maintained or improved

- [ ] **Documentation updated**
  - README updated if public API changed
  - Inline comments for complex logic
  - Changelog entry added (if applicable)
  - API docs updated (if applicable)

- [ ] **Commit quality**
  - Conventional commit format used
  - Commits are atomic and logical
  - Signed-off-by included
  - No WIP or fixup commits

### Security and Risk

- [ ] **Security review** (if High/Critical risk)
  - No hardcoded secrets
  - Input validation appropriate
  - Authentication/authorization correct
  - Dependency vulnerabilities resolved

- [ ] **Breaking changes handled**
  - Migration path documented
  - Deprecation warnings added
  - Version bump appropriate
  - Backward compatibility considered

- [ ] **Performance impact**
  - Database queries optimized
  - N+1 queries avoided
  - Caching appropriate
  - Resource usage reasonable

## Exception Handling

If checklist cannot be fully completed:

- [ ] **Exception justified** in PR description
- [ ] **Required approval** obtained per exception type:
  - Type 1 (Reduced Review): Code Owner approval
  - Type 2 (Conversation Bypass): Technical Lead approval
  - Type 3 (Stale Review): Code Owner approval
  - Type 4 (Break-Glass): On-Call Technical Lead approval
- [ ] **Exception logged** in ../break_glass_exceptions.log
- [ ] **Rollback plan** documented
- [ ] **Follow-up issue** created for deferred items

## Post-Merge Actions

After merge:

- [ ] **Issue closed** with link to merged PR
- [ ] **Branch deleted** (local and remote)
- [ ] **Deployment tracked** (if applicable)
- [ ] **Monitoring verified** (if applicable)
- [ ] **Documentation published** (if applicable)

## Risk Level Assessment

Determine change risk level:

| Risk Level | Examples |
|------------|----------|
| **Low** | Documentation updates, typo fixes, config tweaks, log message changes |
| **Medium** | New features, refactoring, dependency updates, UI changes |
| **High** | Breaking API changes, security fixes, database migrations, auth changes |
| **Critical** | Authentication/authorization, encryption, financial logic, compliance, PII handling |

## Common Rejection Reasons

Do NOT merge if:

- ❌ Conversations still open or unresolved
- ❌ Required approvals not obtained
- ❌ CI/CD pipeline failing
- ❌ Merge conflicts present
- ❌ Stale reviews not re-approved
- ❌ Code owner approval missing (Medium+ risk)
- ❌ Security vulnerabilities detected
- ❌ Tests not added for new functionality
- ❌ Breaking changes without migration plan

## Emergency Merge Protocol

For emergency hotfixes only:

1. Document emergency in PR description
2. Tag on-call technical lead
3. Follow break-glass runbook
4. Log exception immediately
5. Schedule post-merge review
6. Create follow-up issue for proper review

See: [Break-Glass Runbook](./break_glass_runbook.md)

## References

- [PR Review Policy](../PR_REVIEW_POLICY.md)
- [Definition of Done](../DEFINITION_OF_READY_DONE.md)
- [Break-Glass Runbook](./break_glass_runbook.md)
