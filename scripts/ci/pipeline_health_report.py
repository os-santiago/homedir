#!/usr/bin/env python3
"""Compute rolling health for PR Validation and Production Release workflows."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def parse_iso8601(value: str) -> dt.datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return dt.datetime.fromisoformat(value)


def read_token() -> str:
    token = os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN")
    if token:
        return token.strip()
    try:
        proc = subprocess.run(
            ["gh", "auth", "token"],
            check=True,
            text=True,
            capture_output=True,
        )
        token = proc.stdout.strip()
        if token:
            return token
    except Exception:
        pass
    raise RuntimeError("Missing GH_TOKEN/GITHUB_TOKEN and unable to read `gh auth token`.")


def api_get_json(url: str, token: str) -> dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "homedir-pipeline-health",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub API error {exc.code} for {url}: {body}") from exc


def fetch_recent_runs(repo: str, workflow_file: str, token: str, days: int) -> list[dict[str, Any]]:
    cutoff = utc_now() - dt.timedelta(days=days)
    runs: list[dict[str, Any]] = []
    page = 1
    while page <= 10:
        workflow_ref = urllib.parse.quote(workflow_file, safe="")
        url = (
            f"https://api.github.com/repos/{repo}/actions/workflows/{workflow_ref}/runs"
            f"?per_page=100&page={page}"
        )
        data = api_get_json(url, token)
        batch = data.get("workflow_runs", [])
        if not batch:
            break
        keep_page = False
        for run in batch:
            created = parse_iso8601(run["created_at"])
            if created >= cutoff:
                keep_page = True
                runs.append(run)
        if not keep_page:
            break
        page += 1
    return runs


def summarize(runs: list[dict[str, Any]], *, exclude_skipped: bool) -> dict[str, Any]:
    considered = []
    for run in runs:
        conclusion = (run.get("conclusion") or "").lower()
        if exclude_skipped and conclusion == "skipped":
            continue
        considered.append(run)
    total = len(considered)
    success = sum(1 for r in considered if (r.get("conclusion") or "").lower() == "success")
    failure = sum(1 for r in considered if (r.get("conclusion") or "").lower() == "failure")
    cancelled = sum(1 for r in considered if (r.get("conclusion") or "").lower() == "cancelled")
    rate = round((success * 100.0 / total), 1) if total else 0.0
    return {
        "total": total,
        "success": success,
        "failure": failure,
        "cancelled": cancelled,
        "success_rate": rate,
    }


def write_md(path: str, report: dict[str, Any], days: int, pr_threshold: float, rel_threshold: float) -> None:
    pr = report["pr_validation"]
    rel = report["production_release"]
    lines = [
        "# Pipeline Health Report",
        "",
        f"- Window: last **{days} days**",
        f"- Generated at: `{report['generated_at']}`",
        "",
        "| Workflow | Success | Failure | Cancelled | Total | Success rate | Threshold |",
        "|---|---:|---:|---:|---:|---:|---:|",
        (
            f"| PR Validation | {pr['success']} | {pr['failure']} | {pr['cancelled']} | "
            f"{pr['total']} | {pr['success_rate']}% | {pr_threshold}% |"
        ),
        (
            f"| Production Release | {rel['success']} | {rel['failure']} | {rel['cancelled']} | "
            f"{rel['total']} | {rel['success_rate']}% | {rel_threshold}% |"
        ),
        "",
        f"- Overall status: **{report['status'].upper()}**",
    ]
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def maybe_write_outputs(report: dict[str, Any]) -> None:
    output_path = os.getenv("GITHUB_OUTPUT")
    if not output_path:
        return
    pr = report["pr_validation"]
    rel = report["production_release"]
    with open(output_path, "a", encoding="utf-8") as fh:
        fh.write(f"status={report['status']}\n")
        fh.write(f"pr_success_rate={pr['success_rate']}\n")
        fh.write(f"release_success_rate={rel['success_rate']}\n")
        fh.write(f"pr_total={pr['total']}\n")
        fh.write(f"release_total={rel['total']}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, help="owner/repo")
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--pr-threshold", type=float, default=95.0)
    parser.add_argument("--release-threshold", type=float, default=95.0)
    parser.add_argument("--output-json", default="pipeline-health.json")
    parser.add_argument("--output-md", default="pipeline-health.md")
    parser.add_argument("--enforce", action="store_true")
    args = parser.parse_args()

    token = read_token()
    pr_runs = fetch_recent_runs(args.repo, "pr-check.yml", token, args.days)
    rel_runs = fetch_recent_runs(args.repo, "release.yml", token, args.days)

    pr_summary = summarize(pr_runs, exclude_skipped=False)
    rel_summary = summarize(rel_runs, exclude_skipped=True)

    warn = (
        pr_summary["success_rate"] < args.pr_threshold
        or rel_summary["success_rate"] < args.release_threshold
    )
    status = "warn" if warn else "ok"

    report = {
        "generated_at": utc_now().isoformat().replace("+00:00", "Z"),
        "window_days": args.days,
        "repo": args.repo,
        "thresholds": {
            "pr_validation_success_rate": args.pr_threshold,
            "production_release_success_rate": args.release_threshold,
        },
        "pr_validation": pr_summary,
        "production_release": rel_summary,
        "status": status,
    }

    with open(args.output_json, "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=True, indent=2)
        fh.write("\n")
    write_md(args.output_md, report, args.days, args.pr_threshold, args.release_threshold)
    maybe_write_outputs(report)

    print(
        (
            f"Pipeline health status={status} "
            f"PR={pr_summary['success_rate']}% ({pr_summary['success']}/{pr_summary['total']}) "
            f"Release={rel_summary['success_rate']}% ({rel_summary['success']}/{rel_summary['total']})"
        )
    )
    if warn and args.enforce:
        print("Threshold not met and --enforce enabled.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
