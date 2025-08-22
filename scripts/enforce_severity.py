#!/usr/bin/env python3
import json
import os
import sys

analysis_path = sys.argv[1] if len(sys.argv) > 1 else "reports/analysis.sarif"
baseline_path = sys.argv[2] if len(sys.argv) > 2 else "config/quality/baseline.sarif"


def load_sarif(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {"runs": []}
    except json.JSONDecodeError:
        print(f"Failed to parse SARIF file: {path}", file=sys.stderr)
        return {"runs": []}


baseline = load_sarif(baseline_path)
analysis = load_sarif(analysis_path)


def iter_results(sarif):
    for run in sarif.get("runs", []):
        for result in run.get("results", []):
            rule = result.get("ruleId")
            loc = result.get("locations", [{}])[0]
            phys = loc.get("physicalLocation", {})
            file = phys.get("artifactLocation", {}).get("uri")
            line = phys.get("region", {}).get("startLine")
            yield rule, file, line, result


baseline_set = {(rule, file, line) for rule, file, line, _ in iter_results(baseline)}

new_results = []
for rule, file, line, result in iter_results(analysis):
    if (rule, file, line) not in baseline_set:
        level = result.get("level", "note")
        message = result.get("message", {}).get("text", "")
        new_results.append(
            {
                "ruleId": rule,
                "file": file,
                "line": line,
                "level": level,
                "message": message,
            }
        )

severity_map = {"error": "High", "warning": "Medium"}
counts = {"High": 0, "Medium": 0, "Low": 0}
for r in new_results:
    sev = severity_map.get(r["level"], "Low")
    r["severity"] = sev
    counts[sev] += 1

summary_path = os.getenv("GITHUB_STEP_SUMMARY")
if summary_path:
    with open(summary_path, "a", encoding="utf-8") as f:
        f.write("### Static Analysis Summary\n")
        f.write(
            f"- New: {counts['High']} High / {counts['Medium']} Medium / {counts['Low']} Low\n"
        )
        if new_results:
            f.write("\n| Severity | Rule | Location | Message |\n")
            f.write("|---|---|---|---|\n")
            order = {"High": 0, "Medium": 1, "Low": 2}
            for r in sorted(new_results, key=lambda x: order[x["severity"]])[:3]:
                loc = f"{r['file']}:{r['line']}" if r.get("file") else ""
                msg = r.get("message", "").replace("|", r"\|")
                f.write(
                    f"| {r['severity']} | {r.get('ruleId','')} | {loc} | {msg} |\n"
                )

if counts["High"] > 0:
    print(f"Found {counts['High']} High severity issue(s).")
    sys.exit(2)
sys.exit(0)
