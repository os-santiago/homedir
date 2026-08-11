#!/usr/bin/env python3
"""Auto-assign PR state labels based on CI check results and review status."""

import os
import sys

try:
    from github import Github, Auth
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

# State labels (managed by this script)
LABEL_WIP = "wip-pr"
LABEL_WAITING = "scc-waiting-checks"
LABEL_FAILING = "scc-failing-checks"
LABEL_UNDER_REVIEW = "scc-under-review"
LABEL_APPROVED = "scc-approved"
LABEL_MERGED = "scc-merged"

# All state labels this script manages
STATE_LABELS = {
    LABEL_WIP, LABEL_WAITING, LABEL_FAILING,
    LABEL_UNDER_REVIEW, LABEL_APPROVED, LABEL_MERGED,
}


def get_pr_state_label(pr, event_name: str, review_state: str) -> str:
    """Determine the correct state label for a PR."""
    # PR is merged
    if pr.merged:
        return LABEL_MERGED

    # PR is closed (not merged) — no state label needed
    if pr.state == "closed" and not pr.merged:
        return ""  # Will clear all state labels

    # PR is draft
    if pr.draft:
        return LABEL_WIP

    # Review changes requested
    if review_state == "changes_requested":
        return LABEL_UNDER_REVIEW

    # Check CI status via check runs on the PR head SHA
    repo = pr.base.repo
    commits = pr.get_commits()
    if commits.totalCount == 0:
        return LABEL_WAITING

    last_commit = commits[commits.totalCount - 1]
    sha = last_commit.sha

    # Get check runs for the commit (PyGithub: commit.get_check_runs())
    commit = repo.get_commit(sha)
    check_runs = commit.get_check_runs()

    if check_runs.totalCount == 0:
        # No check runs yet — might be early in the pipeline
        return LABEL_WAITING

    all_completed = True
    any_failed = False
    for run in check_runs:
        if run.status != "completed":
            all_completed = False
            break
        if run.conclusion in ("failure", "cancelled", "timed_out", "action_required"):
            any_failed = True

    if not all_completed:
        return LABEL_WAITING

    if any_failed:
        return LABEL_FAILING

    # All checks passed — check review status
    reviews = pr.get_reviews()
    if reviews.totalCount > 0:
        latest_review = reviews[reviews.totalCount - 1]
        if latest_review.state == "approved":
            return LABEL_APPROVED
        elif latest_review.state == "changes_requested":
            return LABEL_UNDER_REVIEW

    # Checks pass, no blocking review — approved
    return LABEL_APPROVED


def apply_label(pr, target_label: str):
    """Apply the target state label, removing all other state labels."""
    current_labels = [label.name for label in pr.get_labels()]
    labels_to_remove = STATE_LABELS - {target_label} if target_label else STATE_LABELS

    for label in labels_to_remove:
        if label in current_labels:
            try:
                pr.remove_from_labels(label)
                print(f"  Removed: {label}")
            except Exception as e:
                print(f"  Failed to remove {label}: {e}")

    if target_label and target_label not in current_labels:
        try:
            # Check if the label exists in the repo
            repo = pr.base.repo
            try:
                repo.get_label(target_label)
            except Exception:
                # Create it if it doesn't exist
                repo.create_label(
                    name=target_label,
                    color="FBCA04",
                    description="Auto-assigned PR state label",
                )
                print(f"  Created label: {target_label}")

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
    event_name = os.environ.get("EVENT_NAME", "")
    review_state = os.environ.get("REVIEW_STATE", "")

    if not all([github_token, repository]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)

    github = Github(auth=Auth.Token(github_token))
    repo = github.get_repo(repository)

    if pr_number:
        pr = repo.get_pull(int(pr_number))
        target = get_pr_state_label(pr, event_name, review_state)
        print(f"PR #{pr.number}: state={pr.state}, draft={pr.draft}, merged={pr.merged}")
        print(f"  Target label: {target or '(clear all)'}")
        apply_label(pr, target)
    else:
        # Label all open PRs
        prs = repo.get_pulls(state="open")
        for pr in prs:
            target = get_pr_state_label(pr, event_name, review_state)
            print(f"PR #{pr.number}: target={target or '(clear)'}")
            apply_label(pr, target)

    sys.exit(0)


if __name__ == "__main__":
    main()
