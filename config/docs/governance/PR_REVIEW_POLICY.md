# Pull Request Review and Conversation Resolution Policy

## Purpose

This policy establishes mandatory review requirements and conversation resolution rules to ensure code quality, maintainability, and traceable technical decisions before merging changes.

## Core Principles

1. **All conversations must be resolved** before merge
2. **Minimum review approvals** required based on change risk
3. **Stale reviews** require re-approval after significant changes
4. **Exceptions** follow documented escalation protocol

## Required Conversation Resolution

### Policy

- `required_conversation_resolution: true` is MANDATORY for all repositories
- All PR conversations, comments, and threads must be resolved before merge
- Unresolved conversations indicate incomplete review or unaddressed concerns

### Resolution Criteria

A conversation is considered resolved when:
- The raised concern has been addressed (code changed, documentation added, follow-up issue created)
- The reviewer explicitly marks the conversation as resolved
- Both parties agree the concern is addressed

### Unresolved Conversation Handling

If a conversation cannot be resolved:
1. Document the disagreement in the conversation thread
2. Escalate to maintainer or technical lead
3. Document decision rationale in PR description
4. If decision deferred: create follow-up issue and link it

## Review Approval Requirements

### Minimum Approvals by Change Risk

| Risk Level | Criteria | Min Approvals | Additional Requirements |
|------------|----------|---------------|------------------------|
| **Low** | Docs only, typos, config tweaks | 1 | - |
| **Medium** | Feature additions, refactors, new dependencies | 2 | At least 1 from code owners |
| **High** | Security changes, API breaking changes, data migrations | 2 | All from code owners + security review if applicable |
| **Critical** | Auth/authz, encryption, financial logic, compliance | 3 | All from code owners + security team approval |

### Code Owner Requirements

- Code owners are defined in `CODEOWNERS` file
- At least one code owner approval required for Medium+ changes
- All code owner approvals required for High/Critical changes

### Reviewer Responsibilities

Reviewers must verify:
- [ ] Code meets acceptance criteria from linked issue
- [ ] Tests are comprehensive and pass
- [ ] No security vulnerabilities introduced
- [ ] Documentation updated appropriately
- [ ] Follows project conventions and style
- [ ] Error handling is appropriate
- [ ] Performance implications considered
- [ ] Backward compatibility maintained (or breaking change justified)

## Stale Review Policy

### When Reviews Become Stale

A review is considered stale when:
- **Significant changes** pushed after approval (>50 lines changed in reviewed files)
- **New files** added to PR after approval
- **Dependencies changed** after approval (package.json, requirements.txt, etc.)
- **More than 7 days** elapsed since approval (for active PRs)

### Re-approval Requirements

When reviews become stale:
1. GitHub automatically dismisses stale reviews (if configured)
2. PR author must request re-review from previous approvers
3. New approval required before merge
4. Comment explaining changes since last approval recommended

### Preventing Stale Reviews

- Keep PRs focused and small (<400 lines preferred)
- Request reviews early and often
- Respond to feedback promptly
- Avoid force-pushing after approval (use additive commits)

## Merge Checklist

Before merging any PR, verify:

- [ ] All conversations resolved
- [ ] Minimum required approvals obtained
- [ ] No stale reviews (or re-approved)
- [ ] CI/CD pipeline passes all checks
- [ ] Branch is up to date with target branch
- [ ] Linked issue has acceptance criteria met
- [ ] Commit messages follow conventional commits
- [ ] No merge conflicts

## Exception Protocol

### When Exceptions Are Allowed

Exceptions to this policy may be granted for:
- **Emergency hotfixes** (production outages, critical security patches)
- **Break-glass scenarios** (see `docs/governance/templates/break_glass_runbook.md`)
- **Dependency security updates** (automated bot PRs with passing tests)

### Exception Request Process

1. **Document justification** in PR description
2. **Tag technical lead** or maintainer for approval
3. **Log exception** in `docs/governance/break_glass_exceptions.log`
4. **Create follow-up issue** for post-merge review if required
5. **Include rollback plan** in PR description

### Exception Documentation

Every exception must log:
- Date and time
- PR number and title
- Justification for exception
- Approver name and role
- Post-merge actions taken
- Lessons learned

## Audit and Compliance

### Audit Criteria

Regular audits should verify:
- [ ] All merged PRs had required approvals
- [ ] All conversations were resolved before merge
- [ ] Exception protocol was followed for emergency merges
- [ ] Stale reviews were re-approved appropriately
- [ ] Code owner approvals obtained when required

### Audit Frequency

- **Weekly**: Sample 10% of merged PRs
- **Monthly**: Full audit of High/Critical risk merges
- **Quarterly**: Policy effectiveness review and adjustment

### Non-Compliance Handling

If non-compliant merge detected:
1. Document the violation in audit log
2. Review PR for quality issues
3. Create follow-up issue if technical debt introduced
4. Educate team on policy requirements
5. Consider reverting merge if significant risk introduced

## GitHub Branch Protection Rules

Required branch protection settings for `main` and release branches:

```yaml
required_pull_request_reviews:
  required_approving_review_count: 2
  dismiss_stale_reviews: true
  require_code_owner_reviews: true
  require_last_push_approval: false

required_conversation_resolution: true

required_status_checks:
  strict: true
  contexts:
    - ci/tests
    - ci/lint
    - ci/security-scan

enforce_admins: false  # Allows break-glass exceptions
allow_force_pushes: false
allow_deletions: false
```

## Escalation Path

1. **PR Author ↔ Reviewer**: Direct conversation resolution
2. **Code Owner**: Technical decision authority
3. **Technical Lead**: Cross-team coordination
4. **Maintainer**: Policy exceptions and final decisions

## Training and Onboarding

All contributors must:
- [ ] Read this policy before first PR
- [ ] Acknowledge understanding in onboarding checklist
- [ ] Receive policy link in PR template

## Policy Maintenance

- **Owner**: Technical Lead / Maintainer
- **Review Frequency**: Quarterly or after major incidents
- **Update Process**: PR to `docs/governance/PR_REVIEW_POLICY.md` with team review

## References

- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)
- [Break-Glass Runbook](./templates/break_glass_runbook.md)
- [Exception Log](./break_glass_exceptions.log)
- GitHub Docs: [About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- GitHub Docs: [Require conversation resolution](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-conversation-resolution-before-merging)
