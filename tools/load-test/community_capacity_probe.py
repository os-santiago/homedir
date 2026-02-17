#!/usr/bin/env python3
"""
Simple concurrency probe for HomeDir public/community traffic.

No external dependencies required.
"""

from __future__ import annotations

import argparse
import random
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from statistics import mean


@dataclass
class EndpointStats:
    latencies_ms: list[float] = field(default_factory=list)
    status_counts: Counter = field(default_factory=Counter)

    def record(self, status: int, latency_ms: float) -> None:
        self.status_counts[status] += 1
        self.latencies_ms.append(latency_ms)


class ProbeRunner:
    def __init__(self, base_url: str, users: int, duration_s: int, timeout_s: float, think_ms: int) -> None:
        self.base_url = base_url.rstrip("/")
        self.users = max(1, users)
        self.duration_s = max(1, duration_s)
        self.timeout_s = max(1.0, timeout_s)
        self.think_ms = max(0, think_ms)
        self.stop_at = time.monotonic() + self.duration_s
        self.lock = threading.Lock()
        self.stats: dict[str, EndpointStats] = defaultdict(EndpointStats)
        self.total_requests = 0
        self.total_errors = 0

        self.sequence = [
            ("/", 1.0),
            ("/comunidad", 1.0),
            ("/api/community/content?view=featured&limit=10", 2.0),
        ]

    def run(self) -> None:
        threads = [threading.Thread(target=self._vu_loop, name=f"vu-{i}", daemon=True) for i in range(self.users)]
        start = time.monotonic()
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        elapsed = max(0.001, time.monotonic() - start)
        self._print_summary(elapsed)

    def _vu_loop(self) -> None:
        while time.monotonic() < self.stop_at:
            for path, _weight in self.sequence:
                if time.monotonic() >= self.stop_at:
                    return
                self._request(path)
            if self.think_ms > 0:
                sleep_ms = random.randint(int(self.think_ms * 0.5), int(self.think_ms * 1.5))
                time.sleep(sleep_ms / 1000.0)

    def _request(self, path: str) -> None:
        url = urllib.parse.urljoin(self.base_url + "/", path.lstrip("/"))
        t0 = time.perf_counter()
        status = -1
        try:
            req = urllib.request.Request(
                url=url,
                headers={
                    "User-Agent": "homedir-capacity-probe/1.0",
                    "Accept": "application/json,text/html,*/*",
                },
            )
            with urllib.request.urlopen(req, timeout=self.timeout_s) as res:
                res.read(1024)
                status = int(res.status)
        except urllib.error.HTTPError as ex:
            status = int(ex.code)
        except Exception:
            status = -1
        latency_ms = (time.perf_counter() - t0) * 1000.0
        with self.lock:
            self.stats[path].record(status, latency_ms)
            self.total_requests += 1
            if status < 200 or status >= 400:
                self.total_errors += 1

    def _print_summary(self, elapsed_s: float) -> None:
        print("=== HomeDir Community Capacity Probe ===")
        print(f"base_url={self.base_url}")
        print(f"users={self.users} duration_s={self.duration_s} timeout_s={self.timeout_s:.1f}")
        print(f"total_requests={self.total_requests} elapsed_s={elapsed_s:.2f} rps={self.total_requests / elapsed_s:.2f}")
        print(f"error_rate={(self.total_errors / max(1, self.total_requests)) * 100:.2f}%")
        print("")
        for path in sorted(self.stats.keys()):
            s = self.stats[path]
            lat = sorted(s.latencies_ms)
            p50 = _pct(lat, 50)
            p95 = _pct(lat, 95)
            p99 = _pct(lat, 99)
            avg = mean(lat) if lat else 0.0
            status_str = ", ".join(f"{k}:{v}" for k, v in sorted(s.status_counts.items(), key=lambda x: x[0]))
            print(
                f"path={path} count={len(lat)} avg_ms={avg:.1f} p50_ms={p50:.1f} p95_ms={p95:.1f} p99_ms={p99:.1f} status=[{status_str}]"
            )


def _pct(values: list[float], pct: int) -> float:
    if not values:
        return 0.0
    idx = int((pct / 100.0) * len(values))
    if idx >= len(values):
        idx = len(values) - 1
    return values[idx]


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Synthetic concurrency probe for HomeDir Community.")
    p.add_argument("--base-url", default="http://127.0.0.1:8080", help="Base URL to target.")
    p.add_argument("--users", type=int, default=100, help="Concurrent virtual users.")
    p.add_argument("--duration", type=int, default=120, help="Duration in seconds.")
    p.add_argument("--timeout", type=float, default=8.0, help="Per-request timeout in seconds.")
    p.add_argument("--think-ms", type=int, default=500, help="Average think time between user loops.")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    runner = ProbeRunner(
        base_url=args.base_url,
        users=args.users,
        duration_s=args.duration,
        timeout_s=args.timeout,
        think_ms=args.think_ms,
    )
    runner.run()


if __name__ == "__main__":
    main()
