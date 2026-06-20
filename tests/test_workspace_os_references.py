"""
Test that workspace-os documentation references are valid and accessible.
"""
import os
import pytest


def test_cycle_batch_assignment_doc_exists():
    """Verify that cycle batch assignment documentation exists."""
    doc_path = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        "docs/en/workspace-os/cycle-batch-assignment.md"
    )
    assert os.path.exists(doc_path), f"Documentation not found at {doc_path}"


def test_workspace_os_reference_structure():
    """Verify workspace-os reference directory structure."""
    docs_base = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        "docs/en"
    )
    workspace_os_dir = os.path.join(docs_base, "workspace-os")

    assert os.path.exists(workspace_os_dir), "workspace-os docs directory should exist"

    # Check that documentation file exists
    batch_doc = os.path.join(workspace_os_dir, "cycle-batch-assignment.md")
    assert os.path.exists(batch_doc), "cycle-batch-assignment.md should exist"


def test_cycle_batch_assignment_doc_content():
    """Verify documentation contains key information."""
    doc_path = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        "docs/en/workspace-os/cycle-batch-assignment.md"
    )

    with open(doc_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Verify key sections exist
    assert "Issue #803" in content or "#803" in content, "Should reference issue #803"
    assert "workspace-os" in content.lower(), "Should reference workspace-os repository"
    assert "batch" in content.lower(), "Should mention batch assignment"
    assert "idle ratio" in content.lower(), "Should mention idle ratio"
    assert "5b3a4e6" in content, "Should reference implementation commit"


@pytest.mark.skipif(
    not os.path.exists("D:/git/workspace-os"),
    reason="workspace-os repository not available"
)
def test_workspace_os_cycle_py_exists():
    """Verify that the actual implementation exists in workspace-os."""
    cycle_path = "D:/git/workspace-os/src/workspace_os/cycle.py"
    assert os.path.exists(cycle_path), f"cycle.py not found at {cycle_path}"


@pytest.mark.skipif(
    not os.path.exists("D:/git/workspace-os/src/workspace_os/cycle.py"),
    reason="workspace-os cycle.py not available"
)
def test_workspace_os_batch_implementation_exists():
    """Verify that batch assignment code exists in workspace-os/cycle.py."""
    cycle_path = "D:/git/workspace-os/src/workspace_os/cycle.py"

    with open(cycle_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Verify batch assignment implementation
    assert "batch_assigned_issues" in content, "Should contain batch_assigned_issues variable"
    assert "Batch-assign issues" in content or "batch-assign" in content.lower(), \
        "Should have batch assignment comment"
    assert "new_work_count" in content, "Should contain new_work_count calculation"
