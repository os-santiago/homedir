"""Static assertions for issue #1230: admission reconciliation must not leave
issues stuck in ``scc-admission-review`` when the terminal label fails to apply.

Root cause: ``add_label`` ran under ``set -euo pipefail`` without error handling,
so a failed ``gh issue edit --add-label`` would abort the worker. The
``needs-human`` and ``rejected`` branches removed the admission-review label
*before* adding the terminal label, so a failed ``add_label`` left the issue
without ``scc-admission-review`` and without the terminal label. On the next
cycle ``reconcile_admission_requests`` re-added ``scc-admission-review`` and
posted a duplicate comment, creating an infinite loop of repetitive comments.

Fix:
1. ``add_label`` now logs errors and returns 1 on failure (instead of aborting
   the worker under ``set -e``).
2. The reconcile case branches add the terminal label *first* and only remove
   the admission-review label and comment on success.
3. ``reconcile_admission_requests`` skips issues that already have
   ``scc-admission-review`` to prevent duplicate comments.
"""

import re
from pathlib import Path

WORKER = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()


# ---------------------------------------------------------------------------
# add_label error handling
# ---------------------------------------------------------------------------


def test_add_label_logs_and_returns_nonzero_on_failure() -> None:
    """add_label must log an ERROR and return 1 on failure instead of letting
    the worker abort silently under set -e."""
    assert "log \"ERROR: add_label failed" in WORKER
    assert "return 1" in WORKER
    # stderr must be redirected so the gh error doesn't leak to the console
    # without being logged.
    add_label_body = re.search(
        r"add_label\(\)\s*\{(.*?)\n\}", WORKER, re.DOTALL
    )
    assert add_label_body is not None
    assert "2>&1" in add_label_body.group(1)


# ---------------------------------------------------------------------------
# reconcile_stuck_admission_reviews: terminal label applied first
# ---------------------------------------------------------------------------


def test_accepted_branch_adds_terminal_before_removing_admission() -> None:
    """The accepted branch must add ACCEPTED_LABEL before removing
    ADMISSION_REVIEW_LABEL, and only remove/comment on success."""
    # Find the accepted) case branch inside reconcile_stuck_admission_reviews
    accepted_block = _extract_case_branch(WORKER, "accepted")
    assert accepted_block is not None
    assert "if add_label" in accepted_block
    assert "ACCEPTED_LABEL" in accepted_block
    assert "remove_label" in accepted_block
    # remove_label must come AFTER add_label (inside the if-success block)
    assert accepted_block.index("add_label") < accepted_block.index("remove_label")


def test_needs_human_branch_adds_terminal_before_removing_admission() -> None:
    """The needs-human branch must add NEEDS_HUMAN_LABEL before removing
    ADMISSION_REVIEW_LABEL (the original bug removed admission first)."""
    needs_human_block = _extract_case_branch(WORKER, "needs-human")
    assert needs_human_block is not None
    assert "if add_label" in needs_human_block
    assert "NEEDS_HUMAN_LABEL" in needs_human_block
    assert "remove_label" in needs_human_block
    assert needs_human_block.index("add_label") < needs_human_block.index("remove_label")


def test_rejected_branch_adds_terminal_before_removing_admission() -> None:
    """The rejected branch must add REJECTED_LABEL before removing
    ADMISSION_REVIEW_LABEL."""
    # The rejected branch is the *) default case
    rejected_block = _extract_case_branch(WORKER, "*")
    assert rejected_block is not None
    assert "if add_label" in rejected_block
    assert "REJECTED_LABEL" in rejected_block
    assert "remove_label" in rejected_block
    assert rejected_block.index("add_label") < rejected_block.index("remove_label")


def test_branches_log_error_on_label_failure() -> None:
    """Each branch must log an ERROR when add_label fails, so the failure is
    visible in worker logs."""
    assert "failed to apply" in WORKER
    assert "leaving in" in WORKER
    assert "for retry" in WORKER


def test_branches_do_not_comment_on_label_failure() -> None:
    """On add_label failure, the branches must NOT call comment_issue (which
    would post a misleading comment). The comment must only appear inside the
    success path (after the if add_label check)."""
    # Extract the needs-human block and verify comment_issue is inside the
    # if-success path, not the else path.
    needs_human_block = _extract_case_branch(WORKER, "needs-human")
    assert needs_human_block is not None
    # Find the else block
    else_match = re.search(r"else\s*\n(.*?)(?:fi|\n\s*\;;)", needs_human_block, re.DOTALL)
    if else_match:
        else_block = else_match.group(1)
        assert "comment_issue" not in else_block, (
            "needs-human branch must not comment on add_label failure"
        )


# ---------------------------------------------------------------------------
# reconcile_admission_requests: skip issues already in admission review
# ---------------------------------------------------------------------------


def test_reconcile_admission_requests_skips_admission_review_issues() -> None:
    """reconcile_admission_requests must skip issues that already have
    ADMISSION_REVIEW_LABEL to prevent duplicate comments every cycle."""
    assert "ADMISSION_REVIEW_LABEL" in WORKER
    # The skip check must appear in reconcile_admission_requests, not just in
    # reconcile_stuck_admission_reviews
    reconcile_admission = _extract_function(WORKER, "reconcile_admission_requests")
    assert reconcile_admission is not None
    assert "ADMISSION_REVIEW_LABEL" in reconcile_admission
    assert "already in" in reconcile_admission


def test_reconcile_admission_requests_skips_rejected_issues() -> None:
    """reconcile_admission_requests must also skip issues with REJECTED_LABEL
    (was missing from the original skip list)."""
    reconcile_admission = _extract_function(WORKER, "reconcile_admission_requests")
    assert reconcile_admission is not None
    assert "REJECTED_LABEL" in reconcile_admission


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _extract_function(text: str, name: str) -> str | None:
    """Extract a bash function body by name."""
    match = re.search(
        rf"{re.escape(name)}\(\)\s*\{{(.*?)\n\}}", text, re.DOTALL
    )
    return match.group(1) if match else None


def _extract_case_branch(text: str, branch: str) -> str | None:
    """Extract a case branch body from the reconcile_stuck_admission_reviews
    function. branch is the case pattern (e.g. 'accepted', 'needs-human', '*')."""
    func = _extract_function(text, "reconcile_stuck_admission_reviews")
    if func is None:
        return None
    # Find the case statement and extract the specific branch
    pattern = rf"({re.escape(branch)})\)\n(.*?)(?:\n\s*;;)"
    match = re.search(pattern, func, re.DOTALL)
    return match.group(2) if match else None
