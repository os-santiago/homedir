"""Regression tests for label inference in scripts/ci/validate_issue_metadata.py.

The script imports PyGithub at module level, so the pure inference helpers and the
constants they rely on are extracted with ast, following the approach already used
by tests/test_enforce_severity.py.
"""

import ast
import re
from pathlib import Path
from typing import Optional

import pytest


SCRIPT_PATH = (
    Path(__file__).resolve().parents[1] / "scripts" / "ci" / "validate_issue_metadata.py"
)

_WANTED_FUNCTIONS = {
    "keyword_matches",
    "infer_type_from_body",
    "infer_priority_from_body",
}


def _load_inference_namespace():
    """Execute only the constants and pure helpers, skipping module side effects."""
    module = ast.parse(SCRIPT_PATH.read_text(), filename=str(SCRIPT_PATH))

    body = [
        node
        for node in module.body
        if (isinstance(node, ast.Assign))
        or (isinstance(node, ast.FunctionDef) and node.name in _WANTED_FUNCTIONS)
    ]

    namespace = {"re": re, "Optional": Optional}
    exec(compile(ast.Module(body=body, type_ignores=[]), str(SCRIPT_PATH), "exec"), namespace)
    return namespace


_NS = _load_inference_namespace()

keyword_matches = _NS["keyword_matches"]
infer_type_from_body = _NS["infer_type_from_body"]
infer_priority_from_body = _NS["infer_priority_from_body"]
TITLE_PREFIX_MAP = _NS["TITLE_PREFIX_MAP"]
BODY_KEYWORD_MAP = _NS["BODY_KEYWORD_MAP"]
SEVERITY_PRIORITY_MAP = _NS["SEVERITY_PRIORITY_MAP"]


# --- Defect 1: strongest evidence must win, not dictionary order -------------------


def test_type_picks_label_with_most_matches():
    """A DevOps body must not be typed as a bug just because 'bug' is declared first."""
    body = (
        "this deployment workflow in our ci/cd pipeline is a devops task. "
        "the startup log shows an error and the mount can fail."
    )
    # 'bug' matches 2 keywords, 'platform-maintenance' matches 4.
    assert infer_type_from_body(body) == "platform-maintenance"


def test_type_still_detects_a_real_bug():
    body = "the page crashes with an exception and a stack trace; steps to reproduce below."
    assert infer_type_from_body(body) == "bug"


def test_type_returns_none_without_evidence():
    assert infer_type_from_body("please consider this at some point.") is None


def test_type_tie_break_is_deterministic():
    """Equal scores resolve to declaration order, so repeated runs agree."""
    body = "this readme has a typo and the build can fail with an error."
    assert infer_type_from_body(body) == infer_type_from_body(body)


# --- Defect 2: keywords must match whole words only -------------------------------


@pytest.mark.parametrize(
    "text,keyword",
    [
        ("add a workflow for releases", "low"),
        ("the page loads slowly", "low"),
        ("this will allow bulk export", "low"),
        ("see the highlights section", "high"),
        ("follow the setup guide", "low"),
    ],
)
def test_keyword_does_not_match_inside_words(text, keyword):
    assert keyword_matches(text, keyword) is False


@pytest.mark.parametrize(
    "text,keyword",
    [
        ("this is a low priority item", "low"),
        ("impact is high for all users", "high"),
        ("marked as s1 by the reporter", "s1"),
        ("caused by a ci/cd misconfiguration", "ci/cd"),
        ("results in data loss on restart", "data loss"),
    ],
)
def test_keyword_matches_whole_words(text, keyword):
    assert keyword_matches(text, keyword) is True


def test_priority_not_escalated_by_substring():
    """'highlights' must not read as 'high' and escalate a cosmetic issue to P1."""
    assert infer_priority_from_body("fix typo in the highlights section") is None


def test_priority_unaffected_by_workflow_wording():
    assert infer_priority_from_body("add a workflow to allow bulk export") is None


# --- Priority severity resolution --------------------------------------------------


def test_priority_uses_most_severe_match():
    """With several severity keywords present, the most severe one wins."""
    body = "high user impact, and it is a security hole that leaks tokens."
    assert infer_priority_from_body(body) == "priority:P0"


def test_priority_detects_plain_severity():
    assert infer_priority_from_body("severity: medium, workaround exists") == "priority:P2"


# --- Defect 3: [devops] title prefix ------------------------------------------------


def test_devops_prefix_is_recognized():
    assert "[devops]" in TITLE_PREFIX_MAP
    assert TITLE_PREFIX_MAP["[devops]"] == ("platform-maintenance", "priority:P3")


def test_title_prefixes_map_to_known_labels():
    """Every prefix must resolve to labels the validator actually accepts."""
    valid_priorities = {"priority:P0", "priority:P1", "priority:P2", "priority:P3"}
    for prefix, (type_label, priority) in TITLE_PREFIX_MAP.items():
        assert prefix.startswith("[") and prefix.endswith("]")
        assert prefix == prefix.lower(), f"{prefix} must be lowercase to match title.lower()"
        assert priority in valid_priorities
        assert isinstance(type_label, str) and type_label
