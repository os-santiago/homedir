# Conversation Resolution and Required Review Policy

**Status**: Active  
**Effective Date**: 2026-06-24  
**Owner**: Platform Engineering  
**Related Issues**: #849

## Purpose

This policy defines mandatory requirements for PR conversation resolution and review approvals before merge. It ensures technical decisions are properly documented, reviewed, and resolved before changes enter the main branch.

## Scope

**Applies to**:
- All pull requests targeting protected branches (`main`, `release/*`, `hotfix/*`)
- All contributors (internal team and external contributors)
- All change types (code, docs, infrastructure, configuration)

**Does NOT apply to**:
- Draft pull requests (work-in-progress, not ready for review)
- Emergency break-glass procedures (see exception process below)

## Required Conversation Resolution

### Policy

All PRs MUST have **required conversation resolution** enabled:

```json
{
  "require_conversation_resolution": true
}
```

This is enforced via GitHub branch protection rules.

### What This Means

- **All review conversations must be marked "resolved"** before merge
- Author OR reviewer can resolve conversations
- Unresolved conversations BLOCK merge (enforced by GitHub)

### When to Resolve

| Scenario | Who Resolves | When |
|----------|--------------|------|
| Reviewer suggestion implemented | Author | After pushing fix commit |
| Reviewer question answered | Reviewer | After satisfactory answer |
| Reviewer concern addressed | Reviewer | After confirming resolution |
| Off-topic discussion | Author or Reviewer | Move to issue, then resolve |
| Disagreement requiring maintainer input | Maintainer | After making decision |

### Resolution Guidelines

**DO**:
- ✅ Resolve after implementing requested changes
- ✅ Resolve after answering questions with supporting evidence
- ✅ Add comment explaining resolution before resolving
- ✅ Request maintainer decision if blocked on disagreement

**DON'T**:
- ❌ Resolve without addressing the concern
- ❌ Resolve reviewer comments without reviewer acknowledgment (for blocking issues)
- ❌ Mark as resolved just to pass checks
- ❌ Leave conversations unresolved when issue moved elsewhere

## Required Review Approvals

### Minimum Approvals by Change Type

| Change Type | Required Approvals | Code Owners Required | Rationale |
|-------------|-------------------|---------------------|-----------|
| **High-Risk** | 2 approvals | Yes | Security, infra, data changes need multiple eyes |
| **Medium-Risk** | 1 approval | If applicable | Standard code changes |
| **Low-Risk** | 1 approval | No | Docs, minor fixes, config |
| **Docs-Only** | 1 approval | No | Typos, README, markdown |

### Change Type Definitions

#### High-Risk Changes
Require **2 approvals** + **Code Owner approval** (if applicable):

- **Security**: Authentication, authorization, crypto, secrets management
- **Infrastructure**: CI/CD workflows, deployment scripts, infra-as-code
- **Data**: Database migrations, schema changes, data transformations
- **API Contracts**: Breaking changes to public APIs or interfaces
- **Dependencies**: Major version bumps, new runtime dependencies

#### Medium-Risk Changes
Require **1 approval** + **Code Owner approval** (if applicable):

- **Backend/API**: Business logic, service layer, controllers
- **Frontend/UI**: User-facing features, component changes
- **Configuration**: Application config, feature flags, environment variables
- **Tests**: Test coverage additions, integration tests

#### Low-Risk Changes
Require **1 approval**:

- **Docs**: Documentation improvements, README updates, code comments
- **Minor Fixes**: Typos, logging improvements, code formatting
- **Non-Functional**: Refactoring without behavior change (with tests proving equivalence)

### Code Owners

Code Owners are defined in `CODEOWNERS` file:

```
# Example CODEOWNERS structure
/.github/workflows/       @platform-team
/docs/security/           @security-team
/quarkus-app/src/main/   @backend-team
```

If a PR touches files with Code Owners:
- **At least one Code Owner MUST approve**
- Code Owner approval counts toward minimum approval count
- Bypassing Code Owner approval requires maintainer exception

## Review Re-Approval Requirements

### Stale Review Policy

Reviews become **stale** and require re-approval if:

1. **Significant code changes** pushed after approval:
   - Any change to files that were reviewed (not just addressed in comments)
   - Addition of new files or features beyond original scope
   - Changes to core logic or security-sensitive code

2. **Merge conflict resolution** that affects reviewed code

3. **Force push** or rebase that modifies reviewed commits

### What Triggers Re-Review

| Action | Requires Re-Review? | Rationale |
|--------|---------------------|-----------|
| Address review comment (minor fix) | No | Expected as part of review cycle |
| Fix typo in code comment | No | Non-functional change |
| Fix failing test | Maybe | If test logic changes, yes; if fixing flakiness, no |
| Add new feature/file | **YES** | Out of scope from original review |
| Refactor reviewed code | **YES** | Logic or structure changed |
| Resolve merge conflict | **YES** | Conflict resolution can introduce bugs |
| Rebase with `--force` | **YES** | Commit history altered |

### GitHub Configuration

Enable **Dismiss stale reviews** for protected branches:

```json
{
  "dismiss_stale_reviews": true,
  "require_code_owner_reviews": true
}
```

When enabled, GitHub automatically dismisses approvals if:
- New commits pushed after approval
- Force push occurs

**Important**: This is **advisory** — maintainers can still merge with dismissed reviews via exception process.

## Reviewer Responsibilities

### Required Before Approval

Reviewers MUST verify:

1. **Status Checks**: All required checks passing (see [Status Check Matrix](./STATUS_CHECK_MATRIX.md))
2. **Scope**: Changes align with linked issue, no scope creep
3. **Tests**: Adequate test coverage for new/changed functionality
4. **Security**: No obvious vulnerabilities (see [Reviewer Checklist](./REVIEWER_CHECKLIST.md))
5. **Code Quality**: Follows project conventions and standards
6. **Conversations**: All conversations resolved or marked non-blocking

### Review Types

| Review Type | When to Use | Expectations |
|-------------|-------------|--------------|
| **Approve** | All criteria met, ready to merge | Reviewer takes shared accountability |
| **Request Changes** | Blocking issues found | Provide specific, actionable feedback |
| **Comment** | Non-blocking suggestions or questions | Author can merge after addressing or acknowledging |

## Exception Process

### When Exceptions Are Allowed

Exceptions to this policy are **ONLY** permitted for:

1. **Production Emergencies**:
   - Active outage blocking users
   - Security vulnerability being actively exploited
   - Data loss incident requiring immediate mitigation

2. **Broken Main Branch**:
   - Main branch broken by previous merge
   - Blocking all development work

### Exception Request Process

1. **Author** documents emergency in PR description:
   ```markdown
   ## EMERGENCY EXCEPTION REQUEST
   
   **Type**: Production Outage / Security Incident / Broken Main
   **Incident**: [Link to incident ticket/Slack thread]
   **Impact**: [Describe user/business impact]
   **Time Sensitivity**: [Why this cannot wait for standard review]
   **Rollback Plan**: [How to revert if this makes it worse]
   ```

2. **Author** requests maintainer approval:
   - Comment: `@maintainers Emergency exception approval needed`
   - Tag in Slack: `#platform-engineering` channel

3. **Maintainer** reviews and provides explicit written approval:
   ```markdown
   ## EXCEPTION APPROVED
   
   **Approved by**: @maintainer-name
   **Reason**: Production outage affecting 100% of users
   **Conditions**: 
   - Merge with 0 approvals (bypass review)
   - Skip conversation resolution (bypass)
   - Post-merge: Create follow-up issue #XXX for proper review
   - Post-merge: Run skipped checks and verify no regressions
   
   Approval timestamp: 2026-06-24 03:14:00 UTC
   ```

4. **Author** merges with bypass approval

5. **Author** creates follow-up issue for post-merge review:
   ```markdown
   **Title**: Post-merge review for emergency PR #XXX
   
   **Description**:
   - Original PR: #XXX (merged via emergency exception)
   - Exception approval: [Link to approval comment]
   - Tasks:
     - [ ] Run skipped status checks
     - [ ] Conduct full code review
     - [ ] Address any issues found
     - [ ] Verify no regressions introduced
   ```

### Non-Emergency Exceptions

If PR cannot meet criteria but is **not an emergency**:

1. **Author** documents why criteria cannot be met
2. **Author** creates follow-up issues for deferred work
3. **Reviewer** evaluates risk vs benefit
4. **Maintainer** provides explicit written approval

Example:
```markdown
## NON-EMERGENCY EXCEPTION REQUEST

**Criteria Not Met**: Missing integration tests for new feature
**Reason**: Test infrastructure for this component doesn't exist yet
**Mitigation**: 
- Unit tests provide 90% coverage
- Manual testing completed (see test plan below)
- Follow-up issue #XXX to build test infrastructure

**Risk Assessment**: Low - unit tests cover core logic, manual tests verify integration
**Follow-up Issue**: #XXX - Build integration test framework for X component
```

## Audit and Compliance

### Audit Trail Requirements

All exception approvals MUST be:
- **Documented** in PR comments with explicit approval text
- **Traceable** to an incident or follow-up issue
- **Logged** with timestamp and approver identity

### Quarterly Review

Platform Engineering team reviews:
- **Exception frequency**: How many exceptions per quarter?
- **Exception patterns**: Are same issues recurring?
- **Follow-up closure**: Were post-merge issues actually resolved?
- **Policy effectiveness**: Is policy reducing issues or adding friction?

### Metrics

Track and report quarterly:

| Metric | Target | Purpose |
|--------|--------|---------|
| % PRs with unresolved conversations at merge | 0% | Verify enforcement |
| % PRs merged with <required approvals | <1% | Verify exception process working |
| Average time to resolve conversations | <24 hours | Measure review responsiveness |
| Exception approval count | <10/quarter | Monitor emergency frequency |
| Follow-up issue closure rate | 100% | Verify exceptions don't create debt |

## Enforcement Mechanism

### Automated Enforcement (GitHub Branch Protection)

```json
{
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true
  },
  "require_conversation_resolution": true
}
```

### Manual Enforcement

- **Reviewers**: Refuse to approve PRs that don't meet criteria
- **Maintainers**: Reject exception requests that don't follow process
- **Platform Engineering**: Audit compliance quarterly

## Related Documents

- [Reviewer Checklist](./REVIEWER_CHECKLIST.md) - Detailed review steps
- [Status Check Matrix](./STATUS_CHECK_MATRIX.md) - Required CI/CD checks
- [Release Gates](./RELEASE_GATES.md) - Quality gates across delivery stages
- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) - Issue/PR completion criteria

## FAQ

### Q: Can I resolve my own review comments?
**A**: Yes, for **informational comments** or **implemented suggestions**. For **blocking concerns**, let the reviewer confirm resolution first.

### Q: What if reviewer is unresponsive?
**A**: 
1. Wait 24 hours
2. Ping in PR comment: `@reviewer gentle ping for review`
3. If still no response after 48 hours, escalate to `@maintainers`

### Q: Can I merge with 1 approval if Code Owner hasn't reviewed?
**A**: No. If files have Code Owners, at least one Code Owner MUST approve before merge.

### Q: What counts as "significant changes" requiring re-approval?
**A**: Any change to files that were part of the original review. Typo fixes in docs are minor; logic changes are significant. **When in doubt, request re-review.**

### Q: Can I bypass reviews for small typo fixes?
**A**: No. All PRs require at least 1 approval. However, typo-only docs PRs should get fast reviews (maintainer can approve in <5 minutes).

---

**Policy Version**: 1.0  
**Last Updated**: 2026-06-24  
**Next Review**: 2026-09-24  
**Change Log**:
- 2026-06-24: Initial policy creation (issue #849)
