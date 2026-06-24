# WOS Debug Logging Guide

## Overview

The WOS debug logger provides detailed traceability for cycle work operations. When enabled via the `--debug` flag, it captures:

- Timestamped operations
- Agent assignments
- Operation timing
- Queue state transitions
- Checkpoint pass/fail details
- End-of-cycle summary reports

**Related Issue:** #936

## Usage

### Enable Debug Logging

```bash
workspace cycle work --debug
```

### Log Output

Debug logs are written to:
```
.workspace-os/debug-logs/cycle-{timestamp}.log
```

### Log Levels

- `DEBUG`: Detailed operation-level logs
- `INFO` (default): High-level cycle events
- `WARN`: Checkpoint failures, issues
- `ERROR`: Critical failures

## Log Entry Structure

Each log entry includes:

```
2026-06-23 23:06:37 | INFO  | Assigned to opencode [agent=opencode, work_item=issue-123]
```

Components:
- **Timestamp**: `2026-06-23 23:06:37`
- **Level**: `INFO`
- **Message**: `Assigned to opencode`
- **Context**: `[agent=opencode, work_item=issue-123]`

## Tracked Events

### Agent Assignment
```
INFO | Assigned to claude [agent=claude, work_item=issue-456]
```

### Operation Timing
```
DEBUG | Operation started: git_commit [agent=opencode, work_item=issue-123, op=git_commit]
DEBUG | Operation succeeded: git_commit (2.345s) [agent=opencode, work_item=issue-123, op=git_commit]
```

### Queue Transitions
```
DEBUG | Queue transition: pending -> in_progress [work_item=issue-789]
```

### Checkpoint Results
```
INFO  | Checkpoint validation: PASS
WARN  | Checkpoint tests: FAIL - 3 tests failed
```

### Work Item Outcomes
```
INFO  | Work item succeeded [work_item=issue-111]
INFO  | Work item failed [work_item=issue-222]
```

## Cycle Summary Report

At the end of each cycle, a comprehensive summary is generated:

```
============================================================
CYCLE SUMMARY REPORT
============================================================
Total Duration: 45.67s
Work Items: 5 processed, 4 succeeded, 1 failed
Checkpoints: 3 passed, 0 failed
API Calls: 12

Time by Operation Type:
  git_commit: 15.23s
  api_call: 8.45s
  file_write: 3.12s

Time by Agent:
  opencode: 25.34s
  claude: 18.45s
  antigravity: 1.88s
============================================================
```

## Integration Example

```python
from src.wos.logging import DebugLogger, LogLevel

# Initialize logger
logger = DebugLogger(
    enabled=True,
    log_level=LogLevel.DEBUG,
    stream_to_stdout=True
)

# Start cycle
logger.start_cycle()

# Track operation
logger.start_operation(
    operation_id="op1",
    operation_type="git_commit",
    agent="opencode",
    work_item="issue-123",
    metadata={"branch": "feat/new-feature"}
)

# ... perform operation ...

logger.end_operation("op1", success=True)

# Record agent assignment
logger.record_agent_assignment("issue-123", "opencode")

# Record checkpoint
logger.record_checkpoint("validation", passed=True, reason="All checks passed")

# Record work item outcome
logger.record_work_item_outcome("issue-123", succeeded=True)

# End cycle and generate summary
logger.end_cycle()

# Clean up
logger.close()
```

## Troubleshooting

### No Logs Generated

Ensure debug logging is enabled:
```python
logger = DebugLogger(enabled=True)
```

### Log File Locked

On Windows, ensure you call `logger.close()` to release file handles:
```python
logger.close()
```

### Missing Context Fields

Context fields are optional. Not all log entries will have all fields:
```python
logger.log(LogLevel.INFO, "Message")  # No context
logger.log(LogLevel.INFO, "Message", agent="opencode")  # Only agent
```

## Performance Considerations

- Debug logging adds minimal overhead (~1-2% cycle time)
- Log files are buffered for efficiency
- Rotation is not automatic; manage old logs manually
- Typical log file size: ~1-5 MB per 100 work items

## Environment Variables

Future releases may support:

```bash
export WOS_DEBUG_LOG_DIR=".workspace-os/debug-logs"
export WOS_DEBUG_LOG_LEVEL="INFO"
export WOS_DEBUG_STREAM_STDOUT="true"
```

## Related Documentation

- [WOS Operations Runbook](operations-runbook.md)
- [Agent Routing Validation](../../scripts/wos/routing.py)
- Issue #936: WOS Debug Flag Implementation
