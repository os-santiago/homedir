# WOS Debug Flag Implementation

**Issue:** [#936](https://github.com/os-santiago/homedir/issues/936)  
**Implementation PR:** [workspace-os#123](https://github.com/os-santiago/workspace-os/pull/123)  
**Status:** Implemented (PR open for review)

## Overview

The `--debug` flag adds detailed traceability to WOS cycle work operations, enabling visibility into agent assignments, operation timing, queue state, and checkpoint results during long-running cycles.

## Usage

```bash
workspace cycle work --duration-minutes 30 --debug
```

When enabled:
- Logs written to `.workspace-os/debug-logs/cycle-{timestamp}.log`
- Real-time output streamed to stdout
- End-of-cycle summary generated with time breakdowns and outcomes

## Implementation Details

### CLI Integration
- Added `--debug` argument to `workspace cycle work` command
- Parameter passed through to `run_cycle_work_window_continuous()`

### Debug Logging Module
New module: `workspace_os.debug_logging`

**Key Components:**
- `DebugLogger`: Main logging class with context-aware log methods
- `OperationTimer`: Tracks operation start/end times
- `CycleSummary`: Accumulates and renders summary statistics
- `LogLevel`: DEBUG, INFO, WARN, ERROR

### Logged Information

1. **Operation Timing**
   - Git operations (commit, push, pull)
   - API calls (GitHub, external services)
   - File I/O operations
   - Agent execution time

2. **Agent Assignment**
   - Work item ID → agent name mapping
   - Role assignment (primary, cross-check, observer)
   - Assignment timestamp

3. **Queue State**
   - Queue depth (pending work items)
   - Active workers count
   - Utilization percentage

4. **Checkpoint Results**
   - Checkpoint ID and timestamp
   - Pass/fail status
   - Detailed failure reasons
   - Health/stability/security/quality gate results

5. **Cycle Summary** (end-of-cycle)
   - Total duration
   - Time by operation type (with percentages)
   - Time by agent (with percentages)
   - Work items by outcome (success/failure/skipped)
   - Total API call count

## Log Format

Each log entry includes:
```
[ISO_TIMESTAMP] LEVEL [context] message {metadata_json}
```

**Example:**
```
[2026-06-24T03:00:00+00:00] INFO  [agent=claude, item=1, op=assignment] Assigned work item to agent {"role": "primary"}
[2026-06-24T03:05:30+00:00] INFO  [item=1, op=work_complete] Work item completed: success in 330.50s {"outcome": "success"}
[2026-06-24T03:05:31+00:00] DEBUG [op=queue_state] Queue state: depth=5, active=3, pending=2 {"utilization_pct": 60.0}
```

## Test Coverage

- 15 new tests in `tests/test_debug_logging.py`
- All tests passing
- No regressions in existing cycle tests

**Test Categories:**
- Logger initialization and file creation
- Message formatting and context fields
- Log level filtering
- Operation timing tracking
- Work item assignment/completion logging
- Queue state logging
- Checkpoint result logging
- Summary accumulation and rendering
- Context manager support

## Use Cases

### 1. Troubleshooting Stuck Cycles
When a cycle appears stalled, debug logs show:
- Last completed work item
- Active agent operations
- Queue state (empty? backlog?)
- Checkpoint failures blocking progress

### 2. Performance Analysis
Identify bottlenecks:
- Which operation types consume most time?
- Are agents idle or overutilized?
- API call frequency and latency

### 3. Agent Performance Comparison
Compare agent efficiency:
- Time per work item by agent
- Success rate per agent
- Avg duration per agent

### 4. Capacity Planning
Determine optimal worker count:
- Queue utilization patterns
- Active vs idle worker ratios
- Work item throughput rates

## Configuration

**Log Level**: Set via `min_level` parameter (default: DEBUG)  
**Log Directory**: `.workspace-os/debug-logs/` (created automatically)  
**Stdout Stream**: Enabled when `--debug` flag present

## Example Cycle Summary

```
=== Cycle Debug Summary ===
Total Duration: 1800.00s

Time by Operation Type:
  agent_execute: 1500.00s (83.3%)
  git_commit: 180.00s (10.0%)
  api_call: 90.00s (5.0%)
  checkpoint: 30.00s (1.7%)

Time by Agent:
  claude: 900.00s (50.0%)
  opencode: 600.00s (33.3%)

Work Items by Outcome:
  success: 25
  failure: 2

API Calls: 47
===========================
```

## References

- Implementation: [workspace-os PR #123](https://github.com/os-santiago/workspace-os/pull/123)
- Issue: [homedir #936](https://github.com/os-santiago/homedir/issues/936)
- Code: `workspace-os/src/workspace_os/debug_logging.py`
- Tests: `workspace-os/tests/test_debug_logging.py`
