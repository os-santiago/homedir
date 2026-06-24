# Governance Documentation

This directory contains governance policies, standards, and operational procedures for the homedir project.

## Quick Links

### Quality & Process
- **[Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)** - Issue and PR completion criteria
- **[Conversation Resolution Policy](./CONVERSATION_RESOLUTION_POLICY.md)** - Required reviews and conversation resolution
- **[Release Gates](./RELEASE_GATES.md)** - PR → Main → Release quality gate contract
- **[Status Check Matrix](./STATUS_CHECK_MATRIX.md)** - Required CI/CD checks by change type
- **[Reviewer Checklist](./REVIEWER_CHECKLIST.md)** - Quick reference for PR reviewers

### Security & Compliance
- [Input Validation Baseline](../security/input-validation-baseline.md)
- [Threat Models](../security/threat-models/README.md)

## Document Index

### For Contributors

| Document | Purpose | When to Use |
|----------|---------|-------------|
| [Reviewer Checklist](./REVIEWER_CHECKLIST.md) | PR review steps and decision tree | Before reviewing any PR |
| [Conversation Resolution Policy](./CONVERSATION_RESOLUTION_POLICY.md) | Review approval and conversation resolution rules | When reviewing PRs or requesting exception approval |
| [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) | Issue and PR acceptance criteria | Before closing an issue or requesting review |

### For Maintainers

| Document | Purpose | When to Use |
|----------|---------|-------------|
| [Release Gates](./RELEASE_GATES.md) | PR/main/release quality gates | When determining blocking criteria or handling exceptions |
| [Conversation Resolution Policy](./CONVERSATION_RESOLUTION_POLICY.md) | Review and resolution requirements | When approving exception requests or enforcing review policy |
| [Status Check Matrix](./STATUS_CHECK_MATRIX.md) | Required checks by change type | When configuring workflows or updating rulesets |
| [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) | DoR/DoD enforcement | When triaging issues or approving exceptions |

### For Platform Engineering

| Document | Purpose | When to Use |
|----------|---------|-------------|
| [Status Check Matrix](./STATUS_CHECK_MATRIX.md) | Check configuration reference | When adding/removing CI/CD checks |

## Governance Principles

1. **Transparency**: All policies are documented and versioned
2. **Automation**: Enforce policies via CI/CD, not manual review
3. **Consistency**: Same rules apply to all contributors
4. **Flexibility**: Exception process for legitimate edge cases
5. **Security**: Defense in depth with multiple check types

## Policy Updates

All governance documents follow this update process:

1. **Propose**: Open issue describing change and rationale
2. **Review**: Maintainer team reviews and approves
3. **Update**: Create PR with document changes
4. **Announce**: Notify team via GitHub Discussions
5. **Archive**: Keep revision history in document

## Enforcement

### Automated Enforcement
- Repository rulesets (`ruleset-main.json`)
- Required status checks (GitHub branch protection)
- Commit message validation (Conventional Commits)

### Manual Enforcement
- PR review approval requirement
- Code owner review (for sensitive paths)
- Maintainer exception approval

## Compliance Monitoring

**Quarterly Review**: Platform Engineering team reviews:
- Ruleset compliance across branches
- Check coverage vs documented matrix
- Exception frequency and patterns
- Document accuracy vs actual practice

**Continuous Monitoring**:
- All PR checks logged and aggregated
- Failed check metrics tracked
- Exception approvals audited

## Related Resources

- [GitHub Repository Rulesets Documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)
- [Conventional Commits Specification](https://www.conventionalcommits.org/)
- [Developer Certificate of Origin](https://developercertificate.org/)

## Contact

- **General questions**: Open a discussion in GitHub Discussions
- **Policy change proposals**: Open an issue with `governance` label
- **Urgent exceptions**: Tag `@maintainers` in PR comment

---

**Maintained by**: Platform Engineering Team
**Review frequency**: Quarterly
**Last reviewed**: 2026-06-24
