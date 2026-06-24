# Workspace OS Debug Flag for Cycle Work Traceability

## Overview
The `--debug` flag for `workspace cycle work` command provides detailed traceability during long WOS cycles, addressing visibility gaps when processing many work items.

## Issue
[#936](https://github.com/os-santiago/homedir/issues/936) - During long WOS cycles (e.g., 138 work items), we lacked visibility into:
- Time spent per operation (git, API calls, file writes)
- Which agent is processing which issue
- Queue state changes
- Why checkpoints pass/fail
- Minute-by-minute activity breakdown

## Solution
Implemented in [workspace-os PR #123](https://github.com/os-santiago/workspace-os/pull/123)

### Usage
```bash
workspace cycle work --debug --duration-minutes 30 --label "my-cycle" --objective "Fix bugs"
```

### Features

#### 1. CLI Integration
- `--debug` flag added to `workspace cycle work` command
- Enables detailed logging with DEBUG level (default is INFO)

#### 2. Detailed Logging
Each log entry includes:
- **Timestamp**: ISO 8601 format with microsecond precision
- **Log Level**: DEBUG, INFO, WARN, ERROR
- **Context**: Agent name, work item ID, operation type
- **Message**: Human-readable description
- **Metadata**: Additional structured data (JSON format)

#### 3. Log Output
- **File**: `.workspace-os/debug-logs/cycle-{timestamp}.log`
- **Stdout**: Also streamed to console when `--debug` enabled
- **Format**: `[timestamp] LEVEL [context] message {metadata}`

Example:
```
[2026-06-23T23:14:59+00:00] INFO  [op=cycle_start]               Starting cycle work window: duration=30.0min
[2026-06-23T23:15:01+00:00] INFO  [agent=claude, item=1, op=assignment] Assigned work item to agent
[2026-06-23T23:15:45+00:00] INFO  [item=1, op=work_complete]     Work item completed: success in 44.2s
[2026-06-23T23:16:30+00:00] INFO  [op=checkpoint]                Checkpoint 1: PASS - all checks passed
```

#### 4. Operation Timing
Tracks duration for:
- Git operations (commit, push, pull, status)
- API calls (GitHub API, external services)
- File I/O operations (read, write, parse)
- Agent execution time
- Checkpoint validation time

#### 5. Agent Assignment Tracking
Logs include:
- Work item number → Agent name mapping
- Role assignment (primary, cross-check, observer)
- Agent start/completion time per work item
- Success/failure outcome per agent

#### 6. Queue State Transitions
Logs queue metrics:
- Queue depth (pending work items)
- Active workers count
- Pending items count
- Utilization percentage

#### 7. Checkpoint Details
For each checkpoint:
- Checkpoint ID and iteration number
- Pass/fail status
- Reason (specific failing checks if failed)
- Number of work items completed
- Categories with failures (health, stability, security, quality)

#### 8. End-of-Cycle Summary
Automatically generated summary includes:
- **Total Duration**: Wall clock time for entire cycle
- **Time by Operation Type**: Breakdown with percentages
  - work_complete, git_commit, api_call, etc.
- **Time by Agent**: Per-agent time with percentages
  - claude, opencode, antigravity, etc.
- **Work Items by Outcome**: Count of success/failure
- **API Call Count**: Total external API calls made

Example summary:
```
=== Cycle Debug Summary ===
Total Duration: 1800.00s

Time by Operation Type:
  work_complete: 1200.50s (66.7%)
  git_commit: 150.25s (8.3%)
  api_call: 50.10s (2.8%)

Time by Agent:
  claude: 800.30s (44.5%)
  opencode: 400.20s (22.2%)

Work Items by Outcome:
  success: 15
  failure: 1

API Calls: 42
===========================
```

## Implementation Details

### Module: `debug_logging.py`
- **DebugLogger**: Main logging class with context tracking
- **OperationTimer**: Track operation start/end times
- **CycleSummary**: Accumulate and render cycle statistics
- **LogLevel**: Enum for DEBUG, INFO, WARN, ERROR

### Integration Points
- `run_cycle_work_window` (sequential mode)
- `run_cycle_work_window_continuous` (continuous mode)
- Both modes support `--debug` flag consistently

### Testing
15 comprehensive test cases covering:
- Disabled logger behavior
- Log file creation and formatting
- Log level filtering
- Operation timing
- Work item tracking
- Queue state logging
- Checkpoint logging
- Summary accumulation and rendering

All tests pass:
```bash
cd workspace-os
pytest tests/test_debug_logging.py -v
# 15 passed in 0.14s
```

## Use Cases

### 1. Troubleshooting Stuck/Slow Cycles
When a cycle seems stuck:
```bash
# Run with debug flag
workspace cycle work --debug --duration-minutes 60

# Check the log file
cat .workspace-os/debug-logs/cycle-*.log | grep -E "queue_state|work_complete"
```

Identify:
- Which work items are taking longest
- If queue is underutilized
- If specific agents are slower

### 2. Agent Performance Analysis
Compare agent performance:
```bash
# After cycle completes, check summary
cat .workspace-os/debug-logs/cycle-*.log | tail -30
```

Metrics:
- Time spent per agent
- Success rate per agent
- Average work item duration

### 3. Resource Usage Optimization
Identify bottlenecks:
```bash
# Find slowest operation types
grep "Completed" .workspace-os/debug-logs/cycle-*.log | sort -k 10 -nr | head -20
```

Optimize:
- Reduce git operations if they dominate
- Cache API calls if excessive
- Parallelize slow file operations

### 4. Checkpoint Failure Diagnosis
When checkpoints fail:
```bash
# Find all failed checkpoints
grep "FAIL" .workspace-os/debug-logs/cycle-*.log
```

Shows:
- Exact failing check names
- Categories (health, stability, security, quality)
- Iteration when failure occurred

## Acceptance Criteria (✅ All Met)
- ✅ `workspace cycle work --debug` enables detailed logging
- ✅ Logs written to timestamped file under `.workspace-os/debug-logs/`
- ✅ Each log entry has timestamp, context (agent/work item/op type)
- ✅ End-of-cycle summary shows time breakdown and outcomes
- ✅ Log level configurable (default: INFO, --debug enables DEBUG)
- ✅ Tests validate log output structure

## Status
- **Implementation**: ✅ Complete
- **Repository**: workspace-os
- **PR**: [#123](https://github.com/os-santiago/workspace-os/pull/123)
- **Branch**: `feat/issue-115-debug-flag`
- **Tests**: ✅ All passing (15/15)
- **Linting**: ✅ Clean (ruff)
- **Documentation**: ✅ This file

## References
- homedir issue: [#936](https://github.com/os-santiago/homedir/issues/936)
- workspace-os PR: [#123](https://github.com/os-santiago/workspace-os/pull/123)
- workspace-os issue: [#115](https://github.com/os-santiago/workspace-os/issues/115)
- Implementation commit: `660e600`
