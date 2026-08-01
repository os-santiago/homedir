"""Tests for issue #1230: worker must handle label application failures in
admission reconciliation and avoid infinite duplicate-comment loops.

Validates that:
1. add_label returns non-zero and logs an ERROR on failure (no silent abort)
2. reconcile_stuck_admission_reviews adds the terminal label BEFORE removing
   scc-admission-review, so a failed add_label leaves the issue in review
   for retry instead of dropping it into a labelless limbo
3. reconcile_admission_requests skips issues already in scc-admission-review
   (prevents duplicate comments every worker cycle)
4. reconcile_admission_requests skips issues with scc-rejected (terminal state)
"""

from pathlib import Path
import re

WORKER = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()


def test_add_label_returns_nonzero_on_failure() -> None:
    """add_label must return 1 on failure so callers can branch on it."""
    # The function body should use `if ! gh issue edit ...` and `return 1`
    add_label_body = re.search(
        r"add_label\(\)\s*\{.*?\n\}", WORKER, re.DOTALL
    )
    assert add_label_body, "add_label function not found"
    body = add_label_body.group(0)
    assert "return 1" in body, "add_label must return 1 on failure"
    assert "return 0" in body, "add_label must return 0 on success"
    assert "ERROR" in body, "add_label must log an ERROR on failure"
    assert "2>&1" in body, "add_label must redirect stderr to capture errors"


def test_reconcile_stuck_adds_terminal_label_before_removing_review() -> None:
    """In every case branch, add_label (terminal) must come before
    remove_label (admission-review) so that a failed add leaves the issue
    in scc-admission-review for retry."""
    # Extract the case block in reconcile_stuck_admission_reviews
    case_block = re.search(
        r'case "\$\{status\}" in\s*'
        r'accepted\)(.*?)'
        r'needs-human\)(.*?)'
        r'\*\)(.*?)'
        r';;\s*esac',
        WORKER,
        re.DOTALL,
    )
    assert case_block, "case block in reconcile_stuck_admission_reviews not found"

    for branch_name, branch_body in [
        ("accepted", case_block.group(1)),
        ("needs-human", case_block.group(2)),
        ("rejected", case_block.group(3)),
    ]:
        add_pos = branch_body.find("add_label")
        remove_pos = branch_body.find("remove_label")
        assert add_pos != -1, f"{branch_name} branch: add_label not found"
        assert remove_pos != -1, f"{branch_name} branch: remove_label not found"
        assert add_pos < remove_pos, (
            f"{branch_name} branch: add_label must come before remove_label "
            f"(add at {add_pos}, remove at {remove_pos})"
        )
        # Must guard with if add_label ... else log ERROR
        assert "if add_label" in branch_body, (
            f"{branch_name} branch: must guard with 'if add_label'"
        )
        assert "ERROR" in branch_body, (
            f"{branch_name} branch: must log ERROR on add_label failure"
        )


def test_reconcile_admission_requests_skips_already_in_review() -> None:
    """reconcile_admission_requests must skip issues already in
    scc-admission-review to prevent duplicate comments (issue #1230)."""
    # Find the skip block for ADMISSION_REVIEW_LABEL in reconcile_admission_requests
    assert "ADMISSION_REVIEW_LABEL}" in WORKER, (
        "reconcile_admission_requests must check ADMISSION_REVIEW_LABEL"
    )
    # There should be a skip with a log message mentioning "already in"
    assert re.search(
        r'issue_has_label.*ADMISSION_REVIEW_LABEL.*\n.*already in.*ADMISSION_REVIEW_LABEL',
        WORKER,
        re.DOTALL,
    ), "reconcile_admission_requests must skip issues already in admission review"


def test_reconcile_admission_requests_skips_rejected() -> None:
    """reconcile_admission_requests must skip issues with scc-rejected
    (was missing from the terminal-state skip list)."""
    # The terminal-state skip list must include REJECTED_LABEL before MERGED_LABEL
    skip_block = re.search(
        r'issue_has_label "\$\{labels\}" "\$\{REJECTED_LABEL\}"'
        r'.*?issue_has_label "\$\{labels\}" "\$\{MERGED_LABEL\}"',
        WORKER,
        re.DOTALL,
    )
    assert skip_block, (
        "reconcile_admission_requests must include REJECTED_LABEL in "
        "terminal-state skip list before MERGED_LABEL"
    )


def test_policy_branches_in_reconcile_stuck_guard_add_label() -> None:
    """Policy-driven auto-approval branches must also guard add_label calls."""
    # All add_label calls in reconcile_stuck_admission_reviews should be
    # inside if-blocks (no bare add_label calls)
    stuck_section = re.search(
        r'reconcile_stuck_admission_reviews\(\).*?^\}',
        WORKER,
        re.DOTALL | re.MULTILINE,
    )
    assert stuck_section, "reconcile_stuck_admission_reviews function not found"
    stuck_body = stuck_section.group(0)

    # Find all add_label calls
    add_label_calls = re.findall(r'add_label "\$\{number\}"', stuck_body)
    # Each should be preceded by "if add_label" on the same or prior line
    bare_calls = re.findall(r'(?<!if )add_label "\$\{number\}"', stuck_body)
    # Remove the ones that are part of "if add_label"
    truly_bare = [c for c in bare_calls if "if " + c not in stuck_body]
    assert len(truly_bare) == 0, (
        f"Found unguarded add_label calls in reconcile_stuck_admission_reviews: {truly_bare}"
    )


def test_policy_branches_in_reconcile_admission_requests_guard_add_label() -> None:
    """Policy-driven branches in reconcile_admission_requests must also guard
    add_label calls (parallel coverage to the stuck-review test)."""
    requests_section = re.search(
        r"reconcile_admission_requests\(\)(.*?)\n\}",
        WORKER,
        re.DOTALL,
    )
    assert requests_section, "reconcile_admission_requests function not found"
    requests_body = requests_section.group(1)

    add_label_calls = re.findall(r'add_label "\$\{number\}"', requests_body)
    assert len(add_label_calls) > 0, (
        "reconcile_admission_requests must contain add_label calls"
    )
    bare_calls = re.findall(r'(?<!if )add_label "\$\{number\}"', requests_body)
    truly_bare = [c for c in bare_calls if "if " + c not in requests_body]
    assert len(truly_bare) == 0, (
        f"Found unguarded add_label calls in reconcile_admission_requests: {truly_bare}"
    )
