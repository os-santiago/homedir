# Load Test Utilities

This folder provides lightweight, dependency-free scripts to validate HomeDir concurrency capacity.

## `community_capacity_probe.py`

Quick synthetic probe that simulates logged-out traffic patterns for:

- `/`
- `/comunidad`
- `/api/community/content?view=featured&limit=10`

### Usage

```bash
python3 tools/load-test/community_capacity_probe.py --base-url http://127.0.0.1:8080 --users 400 --duration 180
```

### Recommended runbook

1. Run against staging first (`--users 200`, then `--users 400`).
2. Confirm:
   - `error_rate` near `0%`
   - no significant `429` spikes on Community API
   - acceptable `p95_ms` for HTML/API endpoints
3. Repeat after any change to:
   - rate limiting
   - community API query logic
   - infrastructure runtime limits

### Notes

- This tool intentionally uses only Python stdlib (no k6/wrk dependency).
- For production, prefer off-peak windows and a lower first pass (`--users 100`) before full load.
