import os
import shutil
import tempfile
import uuid
from pathlib import Path

import pytest


_BASE_TMP = Path(__file__).resolve().parents[1] / ".tmp" / "pytest-work"


def _make_tempdir(prefix: str) -> Path:
    """Create a workspace-scoped temp directory with relaxed permissions."""
    _BASE_TMP.mkdir(parents=True, exist_ok=True)
    path = _BASE_TMP / f"{prefix}-{uuid.uuid4().hex}"
    path.mkdir(parents=True, exist_ok=True)
    return path


@pytest.fixture(scope="session", autouse=True)
def _force_repo_tempdir():
    """Ensure tests use a writable temp directory inside the repo workspace."""
    _BASE_TMP.mkdir(parents=True, exist_ok=True)
    previous_env = {var: os.environ.get(var) for var in ("TMPDIR", "TEMP", "TMP")}
    old_tempdir = tempfile.tempdir

    for var in ("TMPDIR", "TEMP", "TMP"):
        os.environ[var] = str(_BASE_TMP)
    tempfile.tempdir = str(_BASE_TMP)

    try:
        yield
    finally:
        tempfile.tempdir = old_tempdir
        for var, value in previous_env.items():
            if value is None:
                os.environ.pop(var, None)
            else:
                os.environ[var] = value


@pytest.fixture
def tmp_path():
    """Provide a tmp_path fixture that avoids OS-protected temp folders."""
    path = _make_tempdir("tmp")
    try:
        yield path
    finally:
        shutil.rmtree(path, ignore_errors=True)
