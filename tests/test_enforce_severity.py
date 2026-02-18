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


def _get_iter_results():
    """Load iter_results from the script without running module side effects."""
    script_path = Path(__file__).resolve().parents[1] / "scripts" / "enforce_severity.py"
    source = script_path.read_text()
    module = ast.parse(source, filename=str(script_path))
    func_node = next(
        n for n in module.body if isinstance(n, ast.FunctionDef) and n.name == "iter_results"
    )
    module_code = ast.Module(body=[func_node], type_ignores=[])
    namespace = {}
    exec(compile(module_code, filename=str(script_path), mode="exec"), namespace)
    return namespace["iter_results"]


load_sarif = _get_load_sarif()
iter_results = _get_iter_results()


def test_load_sarif_invalid_json(tmp_path, capsys):
    """load_sarif should handle invalid JSON gracefully."""
    sarif_file = tmp_path / "invalid.sarif"
    sarif_file.write_text("{ invalid json")

    result = load_sarif(str(sarif_file))

    captured = capsys.readouterr()
    assert result == {"runs": []}
    assert "Failed to parse SARIF file" in captured.err


def test_iter_results_handles_empty_locations():
    """iter_results should not crash when SARIF result locations is an empty list."""
    sarif = {
        "runs": [
            {
                "results": [
                    {
                        "ruleId": "example.rule",
                        "locations": [],
                        "message": {"text": "example"},
                    }
                ]
            }
        ]
    }

    rows = list(iter_results(sarif))
    assert len(rows) == 1
    rule, file, line, result = rows[0]
    assert rule == "example.rule"
    assert file is None
    assert line is None
    assert result["message"]["text"] == "example"


def test_iter_results_handles_missing_locations_key():
    """iter_results should handle SARIF results that omit locations entirely."""
    sarif = {
        "runs": [
            {
                "results": [
                    {
                        "ruleId": "example.missing.locations",
                        "message": {"text": "missing locations"},
                    }
                ]
            }
        ]
    }

    rows = list(iter_results(sarif))
    assert len(rows) == 1
    rule, file, line, result = rows[0]
    assert rule == "example.missing.locations"
    assert file is None
    assert line is None
    assert result["message"]["text"] == "missing locations"
