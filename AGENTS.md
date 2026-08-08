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
- `wip-pr` - Someone is working on this (has a PR or draft PR)
- `needs-human` - Requires human decision or intervention

**Resolution Labels:**
- `duplicate` - Issue/PR already exists
- `invalid` / `no valido` - Doesn't seem right
- `wontfix` / `no solucionar` - Won't be worked on

### Pull Request Labels

**Workflow Status:**
- `wip-pr` - Work in progress
- `ready-to-implement` - Ready for review/merge

**Automated SDLC Labels** (managed by automation):

These labels track the automated AI SDLC workflow state:

- `ai-sdlc-track` - AI SDLC is tracking this PR
- `ai-sdlc-assist` - AI SDLC may assist this PR when safe
- `scc-queued` - Authorized AI SDLC queue entry
- `scc-running` - Autonomous worker has claimed this issue
- `scc-pr-open` - Autonomous worker opened a PR
- `scc-waiting-checks` - Waiting for CI checks or review
- `scc-under-review` - Under automated review remediation
- `scc-approved` - Passed checks and review feedback
- `scc-merged` - Successfully merged or completed
- `scc-failed` - Failed and needs inspection
- `scc-failing-checks` - Has failing CI checks
- `scc-coverage-gap` - Lacks issue coverage evidence
- `scc-rejected` - Rejected from AI SDLC queue
- `scc-rejected:unauthorized-labeler` - Labeler not authorized
- `scc-accepted` - Initial admission criteria passed
- `scc-admission-review` - Reviewing initial admission criteria

### Label Usage Protocol for AI Agents

**When creating issues:**
1. Add appropriate **type label** (`bug`, `feature-request`, `documentation`, etc.)
2. Add **priority label** if urgent (`priority:P0`, `priority:P1`)
3. Do NOT add Bounty Hunter labels yourself - human admins will add these if eligible
4. Add `help wanted` if the issue needs human attention
5. Add `needs-human` if a human decision is required

**When creating PRs:**
1. **Always** reference the issue: `Closes #XXX` or `Refs #XXX`
2. Add `wip-pr` if still working on it (draft PR)
3. Do NOT add `scc-*` labels - these are managed by automation
4. Let CI and human reviewers add other labels as needed

**When working on existing issues:**
1. Check for `wip-pr` label - someone may already be working on it
2. Respect `needs-human` label - don't proceed without human approval
3. Pay attention to priority labels - handle P0/P1 before P2/P3
4. Check for `scc-running` - AI worker may have claimed it

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
3. **Developer claims** (add `wip-pr` label or comment)
4. **PR created** with `Closes #XXX`
5. **CI checks run** automatically
6. **Code review** by maintainers or AI
7. **Changes addressed** if requested
8. **Merge** when approved and checks pass
9. **Points awarded** if Bounty Hunter eligible

### AI SDLC Workflow (Automated)

1. Issue labeled `ready-to-implement` by authorized human
2. `scc-queued` → `scc-running` (worker claims)
3. `scc-pr-open` (PR created)
4. `scc-waiting-checks` (CI running)
5. `scc-under-review` (addressing feedback)
6. `scc-approved` (ready to merge)
7. `scc-merged` (completed)

OR: `scc-failed` / `scc-failing-checks` / `scc-coverage-gap` (needs human intervention)

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
- `quarkus-app/src/main/resources/i18n.properties` (default/fallback)
- `quarkus-app/src/main/resources/i18n_en.properties` (English)
- `quarkus-app/src/main/resources/i18n_es.properties` (Spanish)
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
