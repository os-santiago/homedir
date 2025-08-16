# Contributing to EventFlow

Thanks for your interest in contributing! We keep development **simple and fast** with a trunk-based approach:

- ✅ **Always use Pull Requests** — no direct pushes to `main`.
- 🔒 **`main` is protected** — required reviews and passing checks.
- 🌲 **Trunk-Based Development** — short-lived branches, frequent merges.
- 🚨 **Define incidents before branching/merging** — classify and document the incident first.

---

## Principles

1. **Pull Requests only**  
   All changes must come through a PR. Direct pushes to `main` are blocked.

2. **Protected main**  
   `main` is the single trunk. It requires:
   - Passing CI (build + tests + linters)
   - At least one approval
   - **Squash & Merge** to keep a linear history

3. **Small, short-lived branches**  
   Keep work slices to hours/days. Use **feature flags** for incremental delivery.

4. **Incidents are defined up front**  
   If you’re fixing an urgent problem, first open an **Incident issue** (see below) and reference it in your branch and PR.

---

## Workflow (TL;DR)

1. **Open an Issue** (bug/feature/incident) and agree on scope.
2. **Create a short-lived branch** from `main`:
   - `feat/<short-slug>`
   - `fix/<short-slug>`
   - `hotfix/INC-<id>-<short-slug>` (for P0/P1 incidents)
   - `docs/<short-slug>` or `chore/<short-slug>`
3. **Commit** using [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat: …`, `fix: …`, `docs: …`, `chore: …`, `refactor: …`, `test: …`
4. **Open a PR early** (Drafts welcome). Link to the Issue/Incident.
5. **Green checks + review** → address feedback.
6. **Squash & Merge** to `main`.
7. **Post-merge**: watch CI/CD and error reporting.

---

## Incident Definition & Handling

Before creating a branch or asking for a merge on urgent items, **create an Incident issue** that includes:

- **Severity (P0–P3)**, impact, affected versions
- **Timeline/context**, steps to reproduce, relevant logs
- **Workaround** (if any)
- **Exit criteria** (how we’ll confirm resolution)

**Severity guide**

- **P0 – Critical**: total outage, data loss, active security issue  
  _Action_: `hotfix/INC-<id>-<slug>` → prioritized PR → immediate release
- **P1 – High**: major degradation, no reasonable workaround  
  _Action_: similar to P0; can wait for a short window
- **P2 – Medium**: important bug, workable workaround  
  _Action_: `fix/<slug>`; schedule next release
- **P3 – Low**: edge cases, copy/UI minor  
  _Action_: `chore/` or `fix/` with lower priority

Always reference the Incident in branch name and PR description (e.g., `Refs INC-42`).

---

## Pull Request Requirements

Include a clear description (problem → solution) and the checklist:

- [ ] Links Issue/Incident (`Closes #123` / `Refs INC-42`)
- [ ] CI is green (build + tests + linters)
- [ ] Screenshots/clip for UI/UX changes
- [ ] Rollout/feature flag notes if applicable
- [ ] Docs updated when needed (README/CHANGELOG/etc.)

**Tips**
- Prefer multiple small PRs over one large PR.
- Don’t mix broad refactors with functional changes.
- Keep commits meaningful; the squash commit message should tell the story.

---

## Development Quickstart

```bash
# Update trunk and create a branch
git checkout main && git pull --ff-only
git switch -c feat/<short-slug>

# Do the work, then commit using Conventional Commits
git add .
git commit -m "feat: add sticky navigation to Talk Detail"

# Push and open a Draft PR
git push -u origin feat/<short-slug>
```

## How Your PR Passes

Follow these quick steps before opening a pull request:

1. **Install prerequisites** – Java 21 and Maven 3.9 or newer.
2. **Format code** – `mvn -f quarkus-app/pom.xml spotless:apply`.
3. **Check dependencies** – `mvn -f quarkus-app/pom.xml enforcer:enforce`.
4. **Commit with Conventional Commits** (see table below).
5. **Push and open the PR** – fix issues reported by CI and re-push.

### Conventional Commit examples

| Commit message | Use when |
|----------------|---------|
| `feat: add new endpoint` | introducing a feature |
| `fix: handle null user` | fixing a bug |
| `docs: update README` | documentation only |
| `chore: update deps` | maintenance or tooling |

If CI reports a failure, apply the suggested command (usually formatting or dependency check), commit the fix and push again.

### Test coverage & mutation

- Ejecuta `./dev/pr-check.sh` para compilar, correr tests y generar `quarkus-app/target/site/jacoco/index.html`.
- Mantén **≥ 70 %** de líneas y ramas en el diff de tu PR. El gating es progresivo: primera semana `warn`, luego `enforcing`.
- Opcional: `mvn -f quarkus-app/pom.xml org.pitest:pitest-maven:1.16.1:mutationCoverage -DtargetClasses='io.eventflow.*'`.
- Si falla el gate de cobertura, agrega o ajusta tests de las clases tocadas y vuelve a ejecutar.
