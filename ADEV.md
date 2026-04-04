# ADEV.md

Upstream source of truth: `https://github.com/scanalesespinoza/adev`

## Mission
A-Dev is not a traditional writing project. This repository is the empirical codification of model- and agent-assisted software delivery, grounded in repeatable evidence from real execution.

## Non-Negotiable Rules
1. Default mode: 1 stage = 1 dedicated branch + 1 atomic PR with a single clear objective.
2. No direct push to `main`; every change lands through PR review and green checks.
3. Use Conventional Commits and keep each commit atomic.
4. Do not mix framework doctrine, manuscript expansion, starter-kit changes, release mechanics, and website polish in the same PR unless the user explicitly requests batch delivery.
5. Prefer new canonical assets over broad rewrites of stable files when codifying new lessons.
6. Every framework claim must map to a repository asset, validated release flow, or real operational evidence from Homedir.
7. Every failure worth remembering must become one of: a guardrail, a case study, a checklist item, or a starter-kit update.
8. English is the only valid language for committed repository content unless a bilingual mirror is explicitly required.
9. If another person or agent is already changing a file locally, avoid parallel edits on that file unless coordination is explicit.
10. Do not institutionalize guessed workflows. If the repository or evidence does not support a claim, mark it as a gap.
11. Manual release cadence applies here too: do not assume every PR needs a tag or public release.
12. Before commit, run the narrowest validation that proves the change is sound for its scope.
13. If the user asks for batch delivery, multiple atomic iterations may live in one PR, but each stage still needs explicit validation and a rollback point.
14. Any local branch that has already been merged into `main` must be deleted during cleanup; keep local branch state lean and remove merged work once it is no longer needed.

## Stage Model
### Stage 1: Canon
- Convert Homedir evidence into canonical doctrine.
- Favor evidence indexes, guardrails, and failure-derived case studies.

### Stage 2: Productize the method
- Improve starter-kit adoption paths, rituals, and reusable operating assets.
- Make A-Dev portable beyond Homedir.

### Stage 3: Authority and publishability
- Expand the manuscript, proof packaging, and market positioning.
- Strengthen external credibility without diluting the framework.

## Operating Flow
1. Sync with `origin/main` and open a dedicated branch.
2. Define the exact scope for the current stage or PR.
3. Implement only repository-grounded assets for that scope.
4. Validate the changed surface.
5. Commit atomically.
6. Push the branch.
7. Open a PR that includes: summary, why, scope in/out, validation, follow-up stage, and rollback notes if relevant.
8. Do not advance to the next stage PR until the current one is reviewed and stable.
9. After merge verification, delete local branches already merged into `main` as part of routine cleanup.

## Evidence Rules
1. Prefer proof chains of the form: incident -> decision -> guardrail -> reusable asset.
2. When using Homedir as source material, extract the transferable principle; do not let project-specific detail replace doctrine.
3. Case studies should show conflict, constraint, decision, evidence, and reusable lesson.
4. Starter-kit assets should tell a practitioner what to do on day 0, in the first week, and before the first production release.
5. Traceability matters here too: roadmap, doctrine, templates, and releases should agree with each other.

## Editorial Positioning
1. Homedir is the proving ground. A-Dev is the transferable system.
2. The book should not depend on private chat context to make sense.
3. The strongest material in this repository is not abstract optimism; it is disciplined learning under failure, delivery pressure, and verification.
