import ast
import json
import sys
from pathlib import Path

import pytest


def _get_load_sarif():
    """Load the load_sarif function from the script without executing it."""
    script_path = Path(__file__).resolve().parents[1] / "scripts" / "enforce_severity.py"
    source = script_path.read_text()
    module = ast.parse(source, filename=str(script_path))
    func_node = next(n for n in module.body if isinstance(n, ast.FunctionDef) and n.name == "load_sarif")
    module_code = ast.Module(body=[func_node], type_ignores=[])
    namespace = {"json": json, "sys": sys}
    exec(compile(module_code, filename=str(script_path), mode="exec"), namespace)
    return namespace["load_sarif"]


load_sarif = _get_load_sarif()


def test_load_sarif_invalid_json(tmp_path, capsys):
    """load_sarif should handle invalid JSON gracefully."""
    sarif_file = tmp_path / "invalid.sarif"
    sarif_file.write_text("{ invalid json")

    result = load_sarif(str(sarif_file))

    captured = capsys.readouterr()
    assert result == {"runs": []}
    assert "Failed to parse SARIF file" in captured.err
