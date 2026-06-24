# Definition of Ready / Definition of Done

## Definition of Ready (DoR)

An issue is ready for work when all criteria are met:

### Bug Reports
- [ ] Severity assessed (Critical/High/Medium/Low)
- [ ] Steps to reproduce are clear and verifiable
- [ ] Expected vs actual behavior documented
- [ ] Technical context provided (environment, logs)
- [ ] No duplicate issue exists

### Feature Requests
- [ ] Problem statement clearly defined
- [ ] Acceptance criteria defined and measurable
- [ ] Area identified (API, UI, DevOps, etc.)
- [ ] No duplicate issue exists
- [ ] Priority assessed

### Tasks
- [ ] Objective clearly defined
- [ ] Scope documented (in scope / out of scope)
- [ ] Acceptance criteria defined and measurable
- [ ] Dependencies identified
- [ ] No duplicate issue exists

## Definition of Done (DoD)

An issue is complete when all criteria are met:

### All Issues
- [ ] All acceptance criteria are satisfied
- [ ] Code changes reviewed and approved
- [ ] Tests added or updated (unit, integration)
- [ ] CI/CD pipeline passes all gates
- [ ] Documentation updated (if applicable)
- [ ] No regressions introduced

### Code Changes
- [ ] Commit messages follow conventional commits
- [ ] Signed-off-by included
- [ ] Code follows project style conventions
- [ ] Error handling implemented
- [ ] Logging added for operations visibility

### Documentation
- [ ] English primary document created/updated
- [ ] Spanish stub exists (if applicable)
- [ ] Cross-references updated
- [ ] No AI watermarks in content

## Minimum Closure Evidence

Every closed issue must include:
- Link to the merged PR(s) that resolved it
- Summary of the solution implemented
- Verification that acceptance criteria were met
- Any follow-up issues created (if scope was reduced)

## Exception Process

If an issue cannot meet all DoR/DoD criteria:
1. Document which criteria are not met and why
2. Obtain maintainer approval for the exception
3. Set a target date for meeting remaining criteria
4. Create follow-up issues for deferred items
