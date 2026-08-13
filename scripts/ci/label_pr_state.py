#!/usr/bin/env python3
"""Auto-assign PR state labels based on CI check results and human review status.

Replaces the deprecated scc-* label system with expressive pr:* labels.
Key differences from the old system:
  - Counts HUMAN approvals only (bots are ignored)
  - Risk-based approval thresholds (pr:risk-low=1, medium=2, high=2, critical=3)
  - Only assigns pr:approved when human approval count meets the risk threshold
  - Detects merge conflicts and assigns pr:blocked
"""

import os
import sys
import time

try:
    from github import Github, Auth
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

# ─── New pr:* state labels ──────────────────────────────────────────────
LABEL_DRAFT = "pr:draft"
LABEL_CHECKS_PENDING = "pr:checks-pending"
LABEL_CHECKS_FAILED = "pr:checks-failed"
LABEL_NEEDS_REVIEW = "pr:needs-review"
LABEL_CHANGES_REQUESTED = "pr:changes-requested"
LABEL_APPROVED = "pr:approved"
LABEL_MERGED = "pr:merged"
LABEL_BLOCKED = "pr:blocked"

# All state labels this script manages (mutually exclusive)
STATE_LABELS = {
    LABEL_DRAFT, LABEL_CHECKS_PENDING, LABEL_CHECKS_FAILED,
    LABEL_NEEDS_REVIEW, LABEL_CHANGES_REQUESTED, LABEL_APPROVED,
    LABEL_MERGED, LABEL_BLOCKED,
}

# Legacy labels to clean up during migration
LEGACY_LABELS = {
    "wip-pr",
    "scc-waiting-checks", "scc-failing-checks", "scc-under-review",
    "scc-approved", "scc-merged", "scc-pr-open", "scc-running",
    "scc-failed", "scc-queued", "scc-rejected", "scc-rejected:unauthorized-labeler",
    "scc-coverage-gap", "scc-admission-review", "scc-accepted",
}

# Risk labels and their required human approval counts
RISK_APPROVALS = {
    "pr:risk-low": 1,
    "pr:risk-medium": 2,
    "pr:risk-high": 2,
    "pr:risk-critical": 3,
}
DEFAULT_REQUIRED_APPROVALS = 2  # Fallback when no risk label is set

# Bot users whose reviews do NOT count toward approval
BOT_REVIEWERS = {
    "github-actions[bot]", "dependabot[bot]", "copilot-pull-request-reviewer",
    "coderabbitai", "github-advanced-security[bot]", "github-openai-bot",
    "semantic-release-bot", "renovate-bot", "allcontributors[bot]",
}


def is_bot_reviewer(login: str) -> bool:
    """Check if a reviewer login is a bot."""
    if not login:
        return True
    if login in BOT_REVIEWERS:
        return True
    return login.endswith("[bot]") or login.endswith("-bot") or login.endswith("-ai")


def get_required_approvals(pr) -> int:
    """Determine required human approvals based on pr:risk-* label."""
    current_labels = [label.name for label in pr.get_labels()]
    for risk_label, count in RISK_APPROVALS.items():
        if risk_label in current_labels:
            return count
    return DEFAULT_REQUIRED_APPROVALS


def count_human_approvals(pr) -> tuple:
    """Count unique human approvals and detect changes-requested.

    Returns (approval_count, has_changes_requested).
    Uses the latest review per user (GitHub review semantics).
    """
    reviews = pr.get_reviews()
    if reviews.totalCount == 0:
        return 0, False

    # Build a map of reviewer → latest review state
    # Reviews are returned chronologically; later entries override earlier ones
    latest_by_user = {}
    for review in reviews:
        login = review.user.login if review.user else ""
        if is_bot_reviewer(login):
            continue
        # Only track meaningful review states; COMMENTED does not override
        # a prior APPROVED or CHANGES_REQUESTED (GitHub review semantics).
        if review.state in ("APPROVED", "CHANGES_REQUESTED", "DISMISSED"):
            latest_by_user[login] = review.state

    approval_count = sum(1 for state in latest_by_user.values() if state == "APPROVED")
    has_changes_requested = any(
        state == "CHANGES_REQUESTED" for state in latest_by_user.values()
    )
    return approval_count, has_changes_requested


def get_ci_status(pr) -> str:
    """Determine CI check status.

    Returns 'pending', 'failed', or 'passed'.
    """
    repo = pr.base.repo
    commits = pr.get_commits()
    if commits.totalCount == 0:
        return "pending"

    last_commit = commits[commits.totalCount - 1]
    sha = last_commit.sha

    commit = repo.get_commit(sha)
    check_runs = commit.get_check_runs()

    if check_runs.totalCount == 0:
        return "pending"

    all_completed = True
    any_failed = False
    for run in check_runs:
        if run.status != "completed":
            all_completed = False
            break
        if run.conclusion in ("failure", "cancelled", "timed_out", "action_required"):
            any_failed = True

    if not all_completed:
        return "pending"
    if any_failed:
        return "failed"
    return "passed"


def get_pr_state_label(pr) -> tuple:
    """Determine the correct state label for a PR.

    Returns (label, approval_count, required_approvals, has_changes_requested)
    so callers can log without re-issuing duplicate API calls.
    """
    # PR is merged
    if pr.merged:
        return LABEL_MERGED, 0, 0, False

    # PR is closed (not merged) — no state label needed
    if pr.state == "closed" and not pr.merged:
        return "", 0, 0, False

    # PR is draft
    if pr.draft:
        return LABEL_DRAFT, 0, 0, False

    # Check for merge conflicts — GitHub may return None while computing
    mergeable = pr.mergeable
    if mergeable is None:
        time.sleep(2)
        pr = pr.base.repo.get_pull(pr.number)
        mergeable = pr.mergeable
    if mergeable is False:
        return LABEL_BLOCKED, 0, 0, False

    # Check CI status
    ci_status = get_ci_status(pr)
    if ci_status == "pending":
        return LABEL_CHECKS_PENDING, 0, 0, False
    if ci_status == "failed":
        return LABEL_CHECKS_FAILED, 0, 0, False

    # CI passed — check human review status
    approval_count, has_changes_requested = count_human_approvals(pr)

    if has_changes_requested:
        return LABEL_CHANGES_REQUESTED, approval_count, 0, True

    required = get_required_approvals(pr)
    if approval_count >= required:
        return LABEL_APPROVED, approval_count, required, False

    # CI green, no changes requested, but not enough approvals yet
    return LABEL_NEEDS_REVIEW, approval_count, required, False


def apply_label(pr, target_label: str):
    """Apply the target state label, removing all other state and legacy labels."""
    current_labels = [label.name for label in pr.get_labels()]

    # Remove all state labels except the target
    labels_to_remove = STATE_LABELS - {target_label} if target_label else STATE_LABELS
    # Also clean up legacy labels
    labels_to_remove = labels_to_remove | LEGACY_LABELS

    for label in labels_to_remove:
        if label in current_labels:
            try:
                pr.remove_from_labels(label)
                print(f"  Removed: {label}")
            except Exception as e:
                print(f"  Failed to remove {label}: {e}")

    if target_label and target_label not in current_labels:
        try:
            pr.add_to_labels(target_label)
            print(f"  Applied: {target_label}")
        except Exception as e:
            print(f"  Failed to apply {target_label}: {e}")
    elif target_label and target_label in current_labels:
        print(f"  Already set: {target_label}")


def main():
    github_token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("REPOSITORY")
    pr_number = os.environ.get("PR_NUMBER")

    if not all([github_token, repository]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)

    github = Github(auth=Auth.Token(github_token))
    repo = github.get_repo(repository)

    if pr_number:
        pr = repo.get_pull(int(pr_number))
        target, approvals, required, changes_req = get_pr_state_label(pr)
        print(f"PR #{pr.number}: state={pr.state}, draft={pr.draft}, merged={pr.merged}")
        print(f"  Human approvals: {approvals}/{required}, changes_requested: {changes_req}")
        print(f"  Target label: {target or '(clear all)'}")
        apply_label(pr, target)
    else:
        prs = repo.get_pulls(state="open")
        for pr in prs:
            target, _, _, _ = get_pr_state_label(pr)
            print(f"PR #{pr.number}: target={target or '(clear)'}")
            apply_label(pr, target)

    sys.exit(0)


if __name__ == "__main__":
    main()
