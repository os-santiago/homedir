# ADEV.md

Upstream source of truth: `https://github.com/scanalesespinoza/adev`

## Mission
A-Dev is the canonical operating doctrine for model- and agent-assisted software delivery. It consolidates the transferable system extracted from real execution, especially the high-friction operational lessons gathered in Homedir.

## Applicability
- Use this file as the upstream source of truth for all downstream `ADEV.md` copies.
- Keep downstream copies synchronized with this file unless a repository requires an explicitly documented local overlay.
- Treat Homedir as the proving ground and A-Dev as the reusable operating system.

## Non-Negotiable Rules
1. Default mode: each iteration must ship from a dedicated branch and a single atomic PR with a clear objective.
2. Every change must land through a PR; direct pushes to `main` are not allowed.
3. Commits must be atomic and use Conventional Commits.
4. Do not mix refactor, feature, visual changes, infrastructure, doctrine updates, and release mechanics in the same PR unless batch delivery is explicitly requested.
5. Prefer new canonical assets over broad rewrites of stable files when codifying new lessons.
6. Every framework claim must map to a repository asset, validated release flow, or real operational evidence.
7. Every failure worth remembering must become one of: a guardrail, a case study, a checklist item, a starter-kit update, or a durable rule.
8. English is the only valid language for committed repository content unless a bilingual mirror is explicitly required.
9. If another person or agent is already changing a file locally, avoid parallel edits on that file unless coordination is explicit.
10. Do not institutionalize guessed workflows. If the repository or evidence does not support a claim, mark it as a gap.
11. Run the narrowest validation that proves the change is sound for its scope before commit.
12. CI must be green before merge.
13. Do not advance to a new iteration without validating the previous one in production when the change has production impact.
14. Versioning and tagging happen on a manual initiative cadence; do not assume a tag or public release for every PR or minor change.
15. When versioning, update version references across the repository, starting with the canonical version source such as `pom.xml` when applicable.
16. Every result must be followed through to deployment verification, not left at analysis or local edits only.
17. For new features, endpoints, or APIs, use a mandatory 3-iteration incremental rollout: hidden or non-production, progressive integration, then legacy cleanup after production validation.
18. If the project is multilingual, all visible text must be implemented through language bundles or equivalent localization assets; avoid globally hardcoded text in templates, JS, backend code, and UI messages.
19. If batch delivery is explicitly requested, multiple atomic iterations may live in one PR, but each stage still requires explicit validation and a rollback point.
20. Every PR failure, integration block, or incident caused by a change must be closed by incorporating the resulting learning into this upstream rule set or another durable canonical asset.
21. Every change touching templates, visible copy, i18n, public routes, or admin views must update or create tests for the affected behavior in the same iteration.
22. Before opening a PR, review changes against common security and CodeQL patterns such as logs with input or paths, redirects, auth or session, persistence, and input-derived URLs; sanitize or encapsulate those cases in the same iteration.
23. Marketing automation and social publishing require staged rollout: internal drafts, controlled approval or scheduling, then autopublishing only after production validation.
24. Automated marketing may use only real and verifiable product data; never invent numbers, milestones, or claims.
25. Every integration with external publishing or automation channels must use secrets managed outside the repository, deduplication, channel rate limiting, and a global kill switch before any production scheduler is enabled.
26. Every iteration, batch, or objective must end with an updated handoff in the shared workspace and an open or updated PR when that workspace model is part of the repository operating system.
27. The shared workspace must remain consistent at relevant checkpoints, including `LATEST.txt`, `HANDOFF.md`, `state.json`, `SESSION-LOG.md`, and `DECISIONS.md` when those assets exist in the repo.
28. Before requesting approval, merge, or production promotion, complete the quality tasks needed to sustain high PR success: local validation, targeted tests, risk preflight, and updated verification notes.
29. Every PR should be configured with auto-merge when the repository workflow supports it, unless an explicit documented blocker prevents it.
30. Every change must finish in a PR at the close of an iteration or objective; do not leave completed work only in a local branch, local handoff, or chat transcript.
31. Every approved and merged PR to `main` must end with operational cleanup: verify the merge, update handoff if used, and remove no-longer-needed source branches.
32. Any local branch already merged into `main` must be deleted during cleanup unless it is still attached to an active worktree or another documented hold condition.
33. PR quality, release gates, and production-promotion validations belong to the SDLC and delivery operation, not to the user-facing product, unless explicitly scoped otherwise.
34. If there is an active PRD or product roadmap, iterations must prioritize visible end-user value and avoid diverting scope toward internal tooling, release evidence, or operational layers that are outside the agreed product.
35. Keep canonical public content understandable without requiring private chat context.
36. The strongest material in this system is disciplined learning under failure, delivery pressure, and verification, not abstract optimism.

## Stage Model
### Stage 1: Canon
- Convert operational evidence into reusable doctrine.
- Favor evidence indexes, guardrails, and failure-derived case studies.

### Stage 2: Productize the method
- Improve starter-kit adoption paths, rituals, templates, and reusable operating assets.
- Make A-Dev portable beyond the original proving ground.

### Stage 3: Authority and publishability
- Expand the manuscript, proof packaging, and positioning.
- Strengthen external credibility without diluting the framework.

## Operating Flow
1. Sync with `origin/main` and open a dedicated branch with explicit scope.
2. Define the exact scope for the current iteration, stage, or PR.
3. Choose the delivery mode: default one-iteration-one-PR, or explicit batch delivery.
4. Implement only the agreed scope for the iteration or current batch stage.
5. For new features, endpoints, or APIs, use the incremental rollout sequence: hidden or unused -> integrated or consumed -> legacy cleanup or deprecation.
6. In batch delivery mode, create a restore point at the start of the batch and maintain checkpoints by stage.
7. Validate the changed surface with the narrowest meaningful build, test, or review step.
8. Commit atomically.
9. Push the branch.
10. Update the shared workspace handoff before requesting review or changing assistant or session, when that workspace model exists.
11. Create or update the PR with summary, why, scope in/out, validation, production verification plan, rollback plan, and follow-up stage when relevant.
12. Enable auto-merge when checks are ready and approval requirements are satisfied.
13. Monitor PR validation and any required release or production workflows.
14. Before merge or production promotion, run and record validations focused on scope, including targeted UI, i18n, backend, and operational tests when applicable.
15. If a quality gate, readiness, or release-evidence need appears, solve it in the SDLC and delivery layer through scripts, CI, operational docs, runbooks, or shared handoff unless product scope explicitly says otherwise.
16. After approval and merge, verify the deployed behavior in production or the highest relevant target environment.
17. After merge verification, delete local branches already merged into `main` as part of routine cleanup.
18. Update handoff again with merge result, verification result, and cleanup when the repo uses a shared workspace model.
19. If production fails, stop new iterations, revert or roll back to a stable version, and open a corrective PR with root cause and prevention.

## Evidence Rules
1. Prefer proof chains of the form: incident -> decision -> guardrail -> reusable asset.
2. When using project-specific source material such as Homedir, extract the transferable principle; do not let project-specific detail replace doctrine.
3. Case studies should show conflict, constraint, decision, evidence, and reusable lesson.
4. Starter-kit assets should tell a practitioner what to do on day 0, in the first week, and before the first production release.
5. Traceability matters: roadmap, doctrine, templates, runbooks, and releases should agree with each other.

## Operational Lessons Consolidated From Homedir
1. If a rule, template, or automation contradicts the actual repository flow, fix the rule or documentation first before institutionalizing the error.
2. Document and automate only commands, workflows, and assumptions backed by the repository or real operation; if context is missing, mark it explicitly as `TODO`.
3. For scripts, operations, and disaster recovery, syntactic validation is not enough: run real smoke tests in the environment that matters and separate harness failures from functional failures.
4. For performance work, compare apples to apples against a concrete baseline, measure latency, error, and payload, and state uncertainty when fixtures, traces, or production data are missing.
5. Always prioritize the highest-leverage fix demonstrated by evidence; avoid broad optimizations or redesigns without measurement that justifies them.
6. For visual customizations by event or context, apply branding overrides only within that scope and preserve the global product identity outside it.
7. For backup and disaster recovery, validate full restorability of the service; a backup is not sufficient unless the service can actually be reconstructed with tested procedure and artifacts.
8. Keep workstreams clean: branch from a stable base, avoid mixing unrelated changes, and revalidate whenever flags, commands, or tools change.
9. In multilingual pages, tests must pin the expected locale explicitly and validate the corresponding localized content.
10. When the narrative, hierarchy, or copy of a view changes, review sibling tests of the same resource as well so outdated expectations do not survive until CI.
11. When a refactor changes the UI interaction model, update tests in the same iteration to validate the new observable behavior and remove dependencies on legacy markup.
12. If a change introduces new logs about routes, identifiers, or values derived from input or state, record only sanitized labels and not absolute paths or raw values.
13. When shared services already write fields to logs, treat those fields as indirect security sinks and use constant or sanitized identifiers for auxiliary attributes.
14. If workflows or checks emit runtime or deprecation warnings, treat them as operational debt: update supported actions or dependencies before adding more capability.
15. In social marketing, validate drafts and claims internally before linking external channels; higher-risk networks should remain in explicit-approval mode until sustained quality is proven.
16. When an admin or public view derives summaries or statuses from optional codes, re-sanitize the value after each derived assignment and cover the exact production state in tests to avoid null-handling defects.
17. Do not turn PR stabilization, production-promotion steps, or rollout tracking needs into product features unless there is an explicit business requirement.
18. Production releases should not depend on a single container registry; keep at least one secondary registry operational for push and pull.

## Homedir Overlay
- Homedir is the main proving ground for this doctrine.
- When operating specifically in Homedir, keep the shared handoff workspace current and verify public-site behavior after merge.
- Homedir-specific validation targets such as `/`, `/comunidad`, `/eventos`, and `/proyectos` remain valid as project-level checks, not universal A-Dev requirements.

## Editorial Positioning
1. Homedir is the proving ground. A-Dev is the transferable system.
2. The book, doctrine, and starter kit should not depend on private chat context to make sense.
3. The most valuable content in this repository is disciplined learning under failure, delivery pressure, and verification.
