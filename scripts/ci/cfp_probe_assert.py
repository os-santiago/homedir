#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Assert CFP/community probe thresholds.")
    parser.add_argument("--report", required=True, help="Probe output file path")
    parser.add_argument("--max-error-rate", type=float, default=12.0, help="Maximum global error rate percent")
    parser.add_argument("--max-429", type=int, default=250, help="Maximum total HTTP 429 count")
    parser.add_argument("--max-timeouts", type=int, default=120, help="Maximum total timeout count (-1 status)")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    content = Path(args.report).read_text(encoding="utf-8")

    error_match = re.search(r"error_rate=([0-9]+(?:\.[0-9]+)?)%", content)
    if not error_match:
        print("ERROR: could not parse global error_rate from probe report", file=sys.stderr)
        return 1
    error_rate = float(error_match.group(1))

    status_lines = re.findall(r"status=\[([^\]]*)\]", content)
    total_429 = 0
    total_timeout = 0
    for status_blob in status_lines:
        for token in [t.strip() for t in status_blob.split(",") if t.strip()]:
            if ":" not in token:
                continue
            key, value = token.split(":", 1)
            try:
                count = int(value.strip())
            except ValueError:
                continue
            key = key.strip()
            if key == "429":
                total_429 += count
            if key == "-1":
                total_timeout += count

    print(
        f"probe_assert_summary error_rate={error_rate:.2f}% "
        f"total_429={total_429} total_timeouts={total_timeout}"
    )

    failures: list[str] = []
    if error_rate > args.max_error_rate:
        failures.append(f"error_rate {error_rate:.2f}% > {args.max_error_rate:.2f}%")
    if total_429 > args.max_429:
        failures.append(f"429 count {total_429} > {args.max_429}")
    if total_timeout > args.max_timeouts:
        failures.append(f"timeout count {total_timeout} > {args.max_timeouts}")

    if failures:
        print("probe_assert_result=FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("probe_assert_result=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
