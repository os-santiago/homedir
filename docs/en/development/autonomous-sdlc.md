# Autonomous SDLC Operating Model

This document defines the issue-driven autonomous SDLC for HomeDir.

## Principle

Automation is autonomous only inside repository rules. It must never bypass, weaken,
or overwrite branch protection, repository rulesets, required checks, required
reviews, secret handling, or deployment gates.

If a rule blocks progress, the automation reports the blocker and moves the issue
to a human-needed state.

The autonomous SDLC must be managed by the SSH server, not by a developer
workstation. Local machines may bootstrap or update the server, but the normal
listener, worker, credentials, logs, queue, GitHub authentication, SCC runtime,
and repository worktree all live on the server.

## Trigger

An issue is eligible only when all conditions are true:

- The issue is open.
- The issue author is `scanalesespinoza`.
- The issue has the `ready-to-implement` label.
- The issue does not have an active terminal or in-progress automation label.
- The issue metadata is clear enough to implement and validate.

OpenClaw may act as the event listener. The worker may also poll GitHub as a
fallback. Both paths must apply the same eligibility rules.

## Lifecycle Labels

Use these labels for automation state:

| Label | Meaning |
| --- | --- |
| `ready-to-implement` | Human-approved trigger for autonomous implementation |
| `scc-running` | The SCC worker has claimed the issue |
| `scc-pr-open` | A pull request was opened for the issue |
| `scc-merged` | The PR was merged and the `Production Release` workflow succeeded |
| `scc-failed` | Automation failed after allowed retries |
| `needs-human` | A repository rule, unclear requirement, security concern, or repeated failure requires human action |

Existing `wos-review` triage can remain separate. It should not trigger
implementation unless `ready-to-implement` is also present.

## Worker Flow

1. Acquire a lock so only one worker instance mutates the repository at a time.
2. Fetch eligible issues from GitHub.
3. Claim one issue by adding `scc-running`.
4. Create or reset a clean local worktree from `origin/main`.
5. Create branch `scc/issue-<number>-<slug>`.
6. Run `scc -yq` with the controlled implementation prompt.
7. Require a PR branch and pull request, never a direct push to `main`.
8. Run local validation appropriate to the change.
9. Push the branch and create/update a PR with `Closes #<issue>`.
10. Enable normal auto-merge only when repository protection allows it.
11. Monitor CI and release workflows.
12. Comment the result and update lifecycle labels.

## Server Ownership

The VPS is the system of record for autonomous execution:

- Runtime user: `homedir-sdlc`
- Runtime repo checkout: `/home/homedir-sdlc/.local/share/homedir-sdlc/worktrees/homedir`
- Worker state: `/home/homedir-sdlc/.local/state/homedir-sdlc`
- Worker logs: `/home/homedir-sdlc/.local/state/homedir-sdlc/logs/worker.log`
- SCC install: `/home/homedir-sdlc/.local/share/sc-agent-cli`
- SCC wrapper: `/home/homedir-sdlc/.local/bin/scc`
- Worker service: `homedir-sdlc-worker.service`
- Worker schedule: `homedir-sdlc-worker.timer`

The server must be able to recover after reboot without this workstation:

- systemd starts the timer.
- `gh` auth and SCC provider credentials are configured on the server under the `homedir-sdlc` account.
- The worker fetches from GitHub directly.
- Branches and PRs are pushed from the server.
- GitHub Actions owns release and deployment after merge.
- The worker reconciles closed SDLC issues and only applies `scc-merged`
  after the closing PR is merged and the matching `Production Release`
  run for the merge commit succeeds.

The worker must exit without processing issues when server-side GitHub
authentication is missing. It must not rely on a workstation `gh` session,
browser login, mounted Windows credential store, or local environment variable.

Use `platform/scripts/homedir-sdlc-bootstrap.sh` on the server for first install
or repair. It installs Ansible locally, pulls this repository, and applies the
local runner playbook against `localhost`.

When the SSH account does not have passwordless root/sudo access, use
`platform/scripts/homedir-sdlc-user-bootstrap.sh` instead. That mode installs
the runner under the SSH user's home directory:

- Platform checkout: `$HOME/.local/share/homedir-platform`
- SCC checkout: `$HOME/.local/share/sc-agent-cli`
- Node runtime: `$HOME/.local/opt/node-*`
- Worker wrapper: `$HOME/.local/bin/homedir-sdlc-worker.sh`
- Worker env: `$HOME/.config/homedir-sdlc/env`
- User units: `$HOME/.config/systemd/user/homedir-sdlc-worker.*`
- Logs and state: `$HOME/.local/state/homedir-sdlc`

This still satisfies server ownership because the normal worker runtime,
credentials, logs, queue, and repository worktree remain on the SSH server.

For production operation, prefer a dedicated non-root account named
`homedir-sdlc`. Root may bootstrap, repair, or rotate credentials, but the
worker service and timer should run as `homedir-sdlc`, not as root.

## Non-Bypass Rules

The worker and SCC prompt must forbid:

- `git push origin main`
- `git push --force` against protected branches
- `gh pr merge --admin`
- GitHub API changes to branch protection
- GitHub API changes to repository rulesets
- Disabling or removing required status checks
- Printing, echoing, or embedding tokens in logs
- Editing secrets or secret files unless a human explicitly requested a rotation

Allowed operations:

- Create implementation branches.
- Commit and push branch changes.
- Create or update pull requests.
- Add status comments and automation labels.
- Request or enable normal auto-merge when branch protection permits it.

## Approval and Merge

If `main` requires one approval, automation must satisfy that rule with normal
GitHub mechanisms. SCC must not approve its own PR and must not use administrator
bypass. Valid options are:

- A separate GitHub App or bot identity reviews only policy-compliant PRs.
- A human reviewer approves.
- Repository rules explicitly define an approved automation path.

Until one of those options exists, autonomous implementation can open PRs, but
full autonomous merge is intentionally blocked by governance.

## Failure Handling

Move to `needs-human` when:

- Requirements are ambiguous.
- Local validation repeatedly fails.
- CI fails after the configured retry limit.
- Required review is missing and no compliant automation identity is available.
- Branch protection or rulesets block merge.
- Production release verification fails after merge.
- The issue requests security-policy changes, secret access, or bypass behavior.

Move to `scc-failed` only for execution failures that do not need clarification,
such as missing tools, provider errors, or repository checkout failures.

## Audit Trail

Every cycle must leave:

- Issue comment with branch, PR, validation, and final state.
- Branch name tied to issue number.
- PR body with `Closes #<issue>`.
- Conventional commit or squash title.
- Worker logs under `/var/log/homedir-sdlc-worker.log`.
- State under `/var/lib/homedir-sdlc`.

## Deployment Boundary

The autonomous SDLC does not deploy directly. It merges through the normal PR
path. The existing `Production Release` GitHub Actions workflow owns build,
image publishing, SSH deployment, fallback timer reconciliation, and production
health verification.
