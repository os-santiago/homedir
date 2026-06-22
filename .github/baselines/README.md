# Performance Baselines

Baseline P95 latency for load testing critical services.

## Format

```json
{
  "p95_latency": 250.5,
  "last_updated": "2026-06-22",
  "commit": "abc123"
}
```

## When to Update

- After performance improvements
- After infrastructure changes
- After service refactoring

## Process

1. Run: `bash scripts/ci/load-test.sh`
2. Verify P95 < 500ms
3. Update baseline file
4. Commit with explanation
