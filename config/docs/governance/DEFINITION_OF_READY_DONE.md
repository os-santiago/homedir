# Definition of Ready and Definition of Done

**Parent Issue:** #838  
**Implementing Issue:** #843  
**Version:** 1.0  
**Last Updated:** 2026-06-23  
**Owner:** IA Governance Working Group

---

## Executive Summary

This document establishes the Definition of Ready (DoR) and Definition of Done (DoD) for all issues in the homedir repository.  These standards ensure that issues are well-specified before work begins and properly validated before closure, enabling both human reviewers and AI agents to verify issue quality consistently.

---

## Definition of Ready (DoR)

An issue is **Ready** when it contains sufficient information for a contributor to start work without clarification.

### Universal DoR Criteria (All Types)

- [ ] Clear, specific title (imperative mood)
- [ ] Type label (`type/bug`, `type/feature`, `type/enhancement`, `type/chore`)
- [ ] Domain label (`domain/*`)
- [ ] Priority label (`priority/*`)
- [ ] Problem statement or objective
- [ ] Scope (in/out)
- [ ] Acceptance criteria (≥1, verifiable)

### Bug Reports (`type/bug`)

Additional:
- [ ] Severity label
- [ ] Steps to reproduce
- [ ] Expected vs actual behavior
- [ ] Environment details
- [ ] Failure evidence
- [ ] Impact assessment

### Features (`type/feature`)

Additional:
- [ ] User story
- [ ] Success metrics
- [ ] Dependencies
- [ ] Effort estimate
- [ ] Out of scope statement

---

## Definition of Done (DoD)

An issue is **Done** when all work is complete, validated, and evidenced for third-party verification.

### Universal DoD Criteria

- [ ] All acceptance criteria met
- [ ] Evidence provided (type-specific)
- [ ] Code reviewed and merged
- [ ] Tests passing
- [ ] Documentation updated
- [ ] No regressions
- [ ] Closing comment with evidence

### Bug Fixes

Required evidence:
- [ ] Merged PR link
- [ ] Before/after comparison
- [ ] Regression test added
- [ ] Manual verification
- [ ] Root cause analysis

### Features

Required evidence:
- [ ] Merged PR link(s)
- [ ] Demo (screenshot/video)
- [ ] Test coverage ≥80%
- [ ] Documentation link
- [ ] Success metrics met

---

## Evidence Requirements Summary

| Type | Required Evidence |
|------|------------------|
| **Bug** | PR, before/after, test, verification, root cause |
| **Feature** | PR, demo, coverage, docs, metrics |
| **Enhancement** | PR, before/after metrics, tests |
| **Chore** | Deliverable, checklist, verification |

---

## Rejection Criteria

### Not Ready (label: `status/needs-info`)

- Incomplete DoR
- Ambiguous problem
- No acceptance criteria
- Duplicate (close as duplicate)

### Not Done (reopen with `status/incomplete-closure`)

- Missing evidence
- Criteria not met
- Regression occurred
- Incomplete work

---

## Integration with Issue Templates

Templates must:
1. Include required fields
2. Reference this document
3. Provide DoR checklist
4. Provide DoD checklist

---

## Related Documentation

- [Issue #843](https://github.com/os-santiago/homedir/issues/843)
- [Issue #838](https://github.com/os-santiago/homedir/issues/838)
- [HISTORICAL_ISSUE_MIGRATION_PLAN.md](./HISTORICAL_ISSUE_MIGRATION_PLAN.md)
