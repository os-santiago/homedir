from pathlib import Path
import re


def test_initial_scc_prompt_uses_literal_issue_coverage_heading() -> None:
    worker = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()

    assert "section named ## Issue Coverage" in worker
    assert "section named ## Issue Coverage truthfully maps" in worker
    assert not re.search(r"^\s*-\s+.*`## Issue Coverage`", worker, re.MULTILINE)


def test_open_pr_detection_does_not_use_broad_body_search() -> None:
    worker = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()

    assert '--search "${issue} in:title,body"' not in worker
    assert "Autonomous SCC implementation for issue #" in worker


def test_scc_output_is_captured_in_worker_log() -> None:
    worker = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()

    assert '| tee -a "${LOGFILE}"' in worker
    assert 'return "${PIPESTATUS[0]}"' in worker
    assert "run_scc_checked()" in worker
    assert "set +e" not in worker


def test_remediation_prompt_uses_issue_title_not_pr_title() -> None:
    worker = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()

    assert "issue_title_for_prompt()" in worker
    assert 'issue_title="$(issue_title_for_prompt "${issue}" "${pr_title}")"' in worker
    assert 'run_scc_on_existing_pr "${issue}" "${issue_title}"' in worker
    assert 'run_scc_on_existing_pr "${issue}" "${pr_title}"' not in worker


def test_coverage_remediation_must_act_and_keep_gap_label_on_noop() -> None:
    worker = Path("platform/scripts/homedir-sdlc-worker.sh").read_text()

    assert "make the missing code, test, workflow, or documentation changes" in worker
    assert "update it directly with gh pr edit" in worker
    assert "Do not stop at analysis, recommendations, or requests for approval" in worker
    assert 'set_flow_labels "${issue}" "${PR_LABEL}" "${UNDER_REVIEW_LABEL}" "${COVERAGE_GAP_LABEL}"' in worker
