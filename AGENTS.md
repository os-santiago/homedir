# AI Agents Guide for Homedir

This guide helps AI agents (like Claude Code, Devin, etc.) understand how to collaborate effectively on Homedir.

## Project Context

**Homedir** is a Quarkus-based community platform for DevRel/Open Source initiatives. It's built on **voluntary contributions** as a **learning and practice space** for the community.

- **Tech Stack**: Quarkus, Java 21, Qute templates, Maven
- **Languages**: Bilingual (English + Spanish)
- **License**: Apache 2.0
- **Philosophy**: Educational, community-driven, open source

## Labels Guide for AI Agents

Understanding and using labels correctly is crucial for effective contribution. Here's the complete label system:

### Issue Labels

**Type Labels:**
- `bug` - Something isn't working
- `enhancement` / `feature-request` - New feature or enhancement requests
- `documentation` / `documentation-improvement` - Documentation work
- `question` / `pregunta` - Questions about the project
- `platform-maintenance` - Infrastructure/platform work

**Bounty Hunter Eligible Labels** (see [Bounty Hunters Program](README.md#-bounty-hunters-program)):

> **Note**: The "Bounty Hunters" program is a **meritocracy and gamification system** to recognize contributors, NOT a payment system. Points unlock badges and recognition, not monetary rewards.

- `bug-impact-low` - Low impact bug fix (5 pts)
- `bug-impact-medium` - Medium impact bug fix (15 pts)
- `bug-impact-high` - High impact bug fix (30 pts)
- `feature-request` - New feature request (20 pts)
- `documentation-improvement` - Documentation improvement (10 pts)
- `platform-maintenance` - Platform maintenance task (15 pts)

**Priority Labels:**
- `priority:P0` - Critical, immediate attention required
- `priority:P1` - High priority
- `priority:P2` - Medium priority
- `priority:P3` - Low priority

**Status Labels:**
- `good first issue` / `buen primer issue` - Good for newcomers
- `help wanted` / `Se necesita ayuda` - Extra attention needed
- `needs-human` - Requires human decision or intervention

**Resolution Labels:**
- `duplicate` - Issue/PR already exists
- `invalid` / `no valido` - Doesn't seem right
- `wontfix` / `no solucionar` - Won't be worked on

### Pull Request Labels

**PR State Labels** (managed by `pr-state-labeler.yml` automation — do NOT apply manually):

These labels track the PR lifecycle state. They are mutually exclusive and auto-assigned based on CI checks + human review status:

- `pr:draft` - PR is draft / work in progress
- `pr:checks-pending` - CI checks are running
- `pr:checks-failed` - CI checks are failing
- `pr:needs-review` - CI green, ready for maintainer review
- `pr:changes-requested` - Maintainer requested changes
- `pr:approved` - Required human approvals met per risk level
- `pr:merged` - PR has been merged
- `pr:blocked` - Blocked: merge conflicts, stale, or other blocker

**PR Risk Labels** (applied by contributor/AI, exactly one required):

These labels determine the required number of human approvals:

- `pr:risk-low` - Docs, typos, config tweaks (min 1 approval)
- `pr:risk-medium` - Features, refactors, new dependencies (min 2 approvals, 1 code owner)
- `pr:risk-high` - Security, breaking changes, data migration (min 2 approvals, all code owners + security)
- `pr:risk-critical` - Auth, encryption, financial, compliance (min 3 approvals, all code owners + security)

**PR Self-Attestation Labels** (applied by contributor/AI):

The contributor's AI pre-verifies these and applies the labels. The `pr-readiness-validator.yml` workflow auto-validates them and removes false claims:

- `pr:traceability-ok` - `Closes #N` present, issue exists with priority + type labels
- `pr:acceptance-ok` - All acceptance criteria from linked issue are met
- `pr:tests-ok` - Tests added/updated for new functionality
- `pr:i18n-ok` - i18n complete (EN + ES) for user-facing changes

### Label Usage Protocol for AI Agents

**When creating issues:**
1. Add appropriate **type label** (`bug`, `feature-request`, `documentation`, etc.)
2. Add **priority label** if urgent (`priority:P0`, `priority:P1`)
3. Do NOT add Bounty Hunter labels yourself - human admins will add these if eligible
4. Add `help wanted` if the issue needs human attention
5. Add `needs-human` if a human decision is required

**When creating PRs:**
1. **Always** reference the issue: `Closes #XXX` or `Fixes #XXX`
   - **IMPORTANT**: When closing multiple issues, repeat the keyword per issue:
     `Closes #10` on one line, `Closes #11` on the next. Do NOT use `Closes #10, #11`
     — GitHub only auto-closes the first issue in a comma-separated list without
     repeated keywords. This caused 9 issues to stay open after their PRs merged.
2. **MUST** add exactly one `pr:risk-*` label (`pr:risk-low`, `pr:risk-medium`, `pr:risk-high`, `pr:risk-critical`) based on the change type
3. **MUST** add self-attestation labels that apply: `pr:traceability-ok`, `pr:acceptance-ok`, `pr:tests-ok`, `pr:i18n-ok`
4. Do NOT add `pr:` state labels (`pr:draft`, `pr:needs-review`, etc.) — those are managed by automation
5. Let CI and human reviewers handle state labels automatically

**When working on existing issues:**
1. Check for `pr:draft` label on linked PRs - someone may already be working on it
2. Respect `needs-human` label - don't proceed without human approval
3. Pay attention to priority labels - handle P0/P1 before P2/P3

## Autonomous AI Agent Contract

This section defines **mandatory** rules for any AI agent (Claude, Devin, Copilot, etc.) creating issues, PRs, or reviewing code in this repository. These rules use RFC 2119 keywords (MUST, SHOULD, MAY).

### Creating an Issue

1. **MUST** include exactly one `priority:P*` label (`priority:P0`–`priority:P3`). If unsure, default to `priority:P2`.
2. **MUST** include exactly one type label (`bug`, `enhancement`, `documentation`, `feature-request`, `platform-maintenance`).
3. **MUST** include a `## Parent` section with `#N` or `None (root issue)` per [PARENT_CHILD_EPIC_STANDARD](config/docs/governance/PARENT_CHILD_EPIC_STANDARD.md).
4. **MUST** include sections: Problem Statement, Expected Behavior, Acceptance Criteria, Affected Files (best guess), Validation (command to verify), Complexity (Simple/Medium/Complex).
5. **MUST** use canonical EN labels only. Never apply legacy ES labels (`error`, `mejora`, `pregunta`, etc.).
6. **MUST NOT** self-assign issues. Leave unassigned for triage (board automation will assign).
7. **SHOULD** follow the [Definition of Ready](config/docs/governance/DEFINITION_OF_READY_DONE.md) checklist.

### Creating a PR

1. **MUST** include `Closes #N` or `Fixes #N` in the PR body. No exceptions. If there's no issue, create one first.
2. **MUST** use branch naming: `feat/issue-XXX-description`, `fix/issue-XXX-description`, `docs/issue-XXX-description`.
3. **MUST** use conventional commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, `style:`) with `Signed-off-by`.
4. **MUST** write PR title, body, and commits in **English**. No exceptions.
5. **MUST** add exactly one `pr:risk-*` label based on change type (low/medium/high/critical).
6. **SHOULD** add self-attestation labels (`pr:traceability-ok`, `pr:acceptance-ok`, `pr:tests-ok`, `pr:i18n-ok`) when the corresponding checks pass.
7. **MUST NOT** apply `pr:` state labels (`pr:draft`, `pr:needs-review`, `pr:approved`, etc.) — those are managed by automation.
8. **SHOULD** use the [PR template](.github/PULL_REQUEST_TEMPLATE.md) when available.
9. **SHOULD** include a Test Plan section with checkboxes.

### Reviewing a PR

1. **MUST** verify the PR references an issue (`Closes #N`). If not, request changes.
2. **MUST** verify the issue has priority + type labels. If not, add them or request the author to add.
3. **MUST** verify all relevant CI checks pass per the [Status Check Matrix](config/docs/governance/STATUS_CHECK_MATRIX.md) for the change type.
4. **MUST** verify the PR actually addresses the issue's acceptance criteria. If not, request changes and remove `pr:acceptance-ok` if present.
5. **MUST** only approve if: checks green + traceability verified + acceptance criteria met. The `pr:approved` label is auto-assigned by automation when human approval count meets the risk-level threshold.
6. **SHOULD** follow the [Reviewer Checklist](config/docs/governance/REVIEWER_CHECKLIST.md) for the change type.
7. **SHOULD** follow the [PR Review Policy](config/docs/governance/PR_REVIEW_POLICY.md) for approval requirements by risk level.

### Working on an Issue

1. **MUST** check if someone is already working on the issue (linked PR with `pr:draft` label) before starting.
2. **MUST NOT** proceed on issues with `needs-human` label without human approval.
3. **MUST NOT** claim an issue if the assignee already has 3 open issues (check with `gh issue list --assignee <login> --state open`).
4. **MUST NOT** self-assign P0/P1 issues — those are reserved for core maintainers (`Axel-DaMage`, `scanalesespinoza`, `VECTORG99`).
5. **SHOULD** handle P0/P1 before P2/P3 when multiple issues are available.

### Language Policy

1. **MUST** write PRs (title, body, commits, branch names) in **English**.
2. **MUST** write code comments in **English**.
3. **SHOULD** write issues in English (default). Spanish is optional via `es/` templates.
4. **MAY** write documentation in English or Spanish. Both are acceptable.
5. **MAY** write internal rules and governance docs in English or Spanish. Both are acceptable.
6. **MUST** use English labels only (no legacy ES labels).

### Governance References

AI agents MUST be aware of and apply these governance documents when relevant:

- [Definition of Ready/Done](config/docs/governance/DEFINITION_OF_READY_DONE.md) — DoR/DoD criteria
- [Triage Runbook](config/docs/governance/TRIAGE_RUNBOOK.md) — Issue triage flow
- [PR Review Policy](config/docs/governance/PR_REVIEW_POLICY.md) — Approval requirements by risk
- [Reviewer Checklist](config/docs/governance/REVIEWER_CHECKLIST.md) — Review checklist by change type
- [Parent/Child/Epic Standard](config/docs/governance/PARENT_CHILD_EPIC_STANDARD.md) — Issue hierarchy
- [Severity/Priority Contract](config/docs/governance/SEVERITY_PRIORITY_CONTRACT.md) — S×P matrix
- [Label Taxonomy](config/docs/governance/LABEL_TAXONOMY.md) — Canonical label catalog
- [Status Check Matrix](config/docs/governance/STATUS_CHECK_MATRIX.md) — Required CI checks by type
- [Release Gates](config/docs/governance/RELEASE_GATES.md) — PR → main → production gates
- [Emergency Break-Glass](config/docs/governance/EMERGENCY_BREAK_GLASS_RUNBOOK.md) — Incident process

### Bounty Hunter Points System

Points are awarded when:
- **Issue creators**: Issue is validated and labeled by admins
- **PR authors**: PR is successfully merged

Points unlock progression levels (Novice → Experienced → Professional → Ultimate → Transcendental) with exclusive profile frames and recognition.

**This is NOT a payment system** - it's a gamification layer to celebrate contributions and build reputation within the community.

## Code Conventions

### Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `refactor:` - Code refactoring
- `test:` - Test additions/changes
- `chore:` - Build/tooling changes
- `style:` - Code style/formatting

**Always sign commits:** `git commit -s`

### Branch Naming

- `feat/issue-XXX-description` - Features
- `fix/issue-XXX-description` - Bug fixes
- `docs/description` - Documentation
- `refactor/description` - Refactoring
- `test/description` - Tests

### Pull Request Protocol

1. **Reference the issue** in PR description: `Closes #XXX`
2. **All CI checks must pass** before merge
3. **Request changes must be resolved** before merge
4. **Tests required** for new functionality
5. **Documentation updated** if behavior changes
6. **i18n files updated** if adding user-facing text (EN + ES)

### Code Review Process

**As an AI agent reviewing code:**
1. Check for security issues (XSS, SQL injection, etc.)
2. Verify test coverage
3. Check code style consistency
4. Verify i18n completeness (EN + ES)
5. Look for code duplication opportunities
6. Suggest improvements, don't just approve

**When receiving review feedback:**
1. Address ALL requested changes
2. Push changes (don't force push unless necessary)
3. Comment when changes are complete
4. Request re-review if needed

## Common Workflows

### Issue → PR → Merge Workflow

1. **Issue created** with type and priority labels
2. **Admin reviews**, adds Bounty Hunter label if eligible
3. **Developer claims** (comment on issue or open draft PR with `pr:draft`)
4. **PR created** with `Closes #XXX`, `pr:risk-*`, and self-attestation labels
5. **CI checks run** automatically
6. **Code review** by maintainers or AI
7. **Changes addressed** if requested
8. **Merge** when approved and checks pass
9. **Points awarded** if Bounty Hunter eligible

### PR Lifecycle Workflow (Automated)

1. Contributor creates PR with `Closes #N`, `pr:risk-*`, and self-attestation labels
2. `pr:draft` (if draft) or `pr:checks-pending` (CI running)
3. `pr:checks-failed` (CI fails) → contributor fixes → back to `pr:checks-pending`
4. `pr:needs-review` (CI green, waiting for human review)
5. `pr:changes-requested` (maintainer requests changes) → contributor fixes → back to `pr:checks-pending`
6. `pr:approved` (human approvals meet risk-level threshold)
7. `pr:merged` (merged to main)

OR: `pr:blocked` (merge conflicts or other blocker — needs contributor action)

## File Locations

**Java Backend:**
- Main code: `quarkus-app/src/main/java/com/scanales/homedir/`
- Resources: `quarkus-app/src/main/resources/`
- Tests: `quarkus-app/src/test/java/com/scanales/homedir/`

**Frontend:**
- Templates: `quarkus-app/src/main/resources/templates/`
- CSS: `quarkus-app/src/main/resources/META-INF/resources/css/`
- JS: `quarkus-app/src/main/resources/META-INF/resources/js/`

**i18n:**
- `quarkus-app/src/main/resources/messages/i18n.properties` (English default)
- `quarkus-app/src/main/resources/messages/i18n_es.properties` (Spanish)
- `quarkus-app/src/main/java/com/scanales/homedir/config/AppMessages.java` (Java interface)

**Configuration:**
- `quarkus-app/src/main/resources/application.properties`

**Documentation:**
- English: `docs/en/`
- Spanish: `docs/es/`

## Testing

**Run tests:**
```bash
cd quarkus-app
./mvnw test
```

**Run specific test:**
```bash
./mvnw test -Dtest=YourTestClass
```

**JavaScript tests:**
```bash
node --test tests/js/your-test.test.js
```

## i18n Protocol

When adding user-facing text:

1. Add key to `i18n.properties` (default/fallback)
2. Add key to `i18n_en.properties` (English)
3. Add key to `i18n_es.properties` (Spanish)
4. Add method to `AppMessages.java` if used in Java code
5. Use in template: `{i18n:your_key_name}`
6. Run i18n validation: CI checks this automatically

## Security Considerations

**Always:**
- Sanitize user input
- Escape HTML output (use `window.HomeDirUtils.escapeHtml`)
- Validate on server-side
- Use parameterized queries (no string concatenation)
- Check for XSS, SQL injection, command injection
- Don't commit secrets (gitleaks hook will block)

**Never:**
- Trust user input
- Use `innerHTML` with unsanitized data
- Disable security checks
- Skip authentication/authorization
- Commit API keys, passwords, tokens

## Resources

- [CONTRIBUTING.md](CONTRIBUTING.md) - Full contribution guidelines
- [GOVERNANCE.md](GOVERNANCE.md) - Project governance
- [README.md](README.md) - Project overview
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) - Community standards
- [SECURITY.md](SECURITY.md) - Security policy

## Questions?

- Open a [Discussion](https://github.com/os-santiago/homedir/discussions)
- Join [Discord](https://discord.gg/3eawzc9ybc)
- Check [documentation](docs/en/README.md)

---

**Remember**: This is a **volunteer-driven learning space**. Be patient, collaborative, and educational in your contributions. The goal is to help people learn and grow, not just to ship features.
