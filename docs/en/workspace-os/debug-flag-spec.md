# WOS Debug Flag Specification

## Issue
[#936](https://github.com/os-santiago/homedir/issues/936) - Implement --debug flag for cycle work traceability

## Problem
During long WOS cycles (e.g., 138 work items but only 1 PR visible), operators lack visibility into:
- Time spent per operation (git, API calls, file writes)
- Which agent is processing which issue
- Queue state changes over time
- Why checkpoints pass or fail
- Minute-by-minute activity breakdown

This creates blind spots when diagnosing:
- Stuck or slow cycles
- Agent performance bottlenecks
- Resource usage patterns
- Checkpoint failure root causes

## Solution
Add `--debug` flag to `workspace cycle work` command with structured logging and cycle summary reporting.

### Implementation Target
- **Repository**: workspace-os (https://github.com/scanalesespinoza/workspace-os)
- **Target Files**: 
  - `src/workspace_os/cli.py` (command definition)
  - `src/workspace_os/cycle.py` (logging instrumentation)
  - `src/workspace_os/logging/debug.py` (new logging module)

### Feature Components

#### 1. CLI Integration
```bash
workspace cycle work --debug               # Enable DEBUG level logging
workspace cycle work --debug --log-level=INFO  # Override log level
```

**Parameters**:
- `--debug`: Boolean flag to enable detailed logging (default: False)
- `--log-level`: Optional override (DEBUG, INFO, WARN, ERROR; default: INFO, --debug sets DEBUG)

#### 2. Logging Infrastructure

**Log Output Locations**:
- File: `.workspace-os/debug-logs/cycle-{timestamp}.log`
- Stdout: Stream when --debug enabled

**Log Structure**:
```
[2026-06-23T14:32:01.234Z] [DEBUG] [agent:opencode] [work_item:42] [op:git_commit] Starting commit operation
[2026-06-23T14:32:01.456Z] [DEBUG] [agent:opencode] [work_item:42] [op:git_commit] Completed (duration: 222ms, status: success)
[2026-06-23T14:32:02.100Z] [WARN]  [agent:claude]   [work_item:43] [op:github_api] Rate limit approaching (remaining: 45/5000)
```

**Context Fields** (every log line):
- Timestamp (ISO 8601 with milliseconds)
- Log Level (DEBUG, INFO, WARN, ERROR)
- Agent Name (opencode, claude, antigravity)
- Work Item ID (sequential number within cycle)
- Operation Type (git_commit, github_api, file_write, checkpoint, etc.)

#### 3. Operation Timing

**Tracked Operations**:
- `git_commit`: Git commit operations
- `git_push`: Git push operations  
- `github_api`: GitHub API calls (issue fetch, PR creation, comment posting)
- `file_write`: File I/O operations
- `checkpoint`: Checkpoint creation and validation
- `queue_transition`: Queue state changes
- `agent_assignment`: Work item to agent assignment

**Timing Metrics**:
- Start timestamp
- End timestamp
- Duration (milliseconds)
- Status (success, failure, timeout)

#### 4. Queue State Tracking

Log queue transitions:
```
[2026-06-23T14:32:01.234Z] [DEBUG] [queue] Queue state: pending=12, active=8, completed=20, max_workers=32
[2026-06-23T14:32:05.678Z] [DEBUG] [queue] Work item #42 assigned to agent opencode
[2026-06-23T14:32:10.123Z] [DEBUG] [queue] Work item #42 completed (outcome: success, duration: 4445ms)
```

#### 5. Checkpoint Details

Log checkpoint pass/fail with reasons:
```
[2026-06-23T14:35:00.000Z] [INFO]  [checkpoint:iteration-12] Starting checkpoint evaluation
[2026-06-23T14:35:01.234Z] [DEBUG] [checkpoint:iteration-12] Test suite: PASSED (72/72 tests)
[2026-06-23T14:35:01.567Z] [DEBUG] [checkpoint:iteration-12] Linting: PASSED (0 errors, 3 warnings)
[2026-06-23T14:35:02.890Z] [ERROR] [checkpoint:iteration-12] Type check: FAILED (mypy found 2 errors)
[2026-06-23T14:35:02.891Z] [WARN]  [checkpoint:iteration-12] CHECKPOINT FAILED (reason: type_check_errors)
```

#### 6. Cycle Summary Report

At cycle completion, emit structured summary showing:
- Total duration and work item counts
- Time breakdown by operation type (git, API, file I/O, checkpoints, queue wait)
- Time breakdown by agent
- Work items by outcome (success/failed with reasons)
- API call count and rate limit status
- Checkpoint failures with details

## Expected Impact
- **Troubleshooting**: Pinpoint exact operation causing cycle slowdown
- **Agent Performance**: Identify which agents handle which work efficiently
- **Resource Usage**: Track API call consumption and rate limit status
- **Checkpoint Diagnosis**: Immediately see why specific checkpoints failed
- **Audit Trail**: Complete minute-by-minute log of cycle execution

## Acceptance Criteria
- [ ] `workspace cycle work --debug` enables DEBUG level logging
- [ ] Logs written to timestamped file under `.workspace-os/debug-logs/`
- [ ] Each log entry has timestamp, log level, agent, work item, operation type
- [ ] Operation timing captured with start/end/duration/status
- [ ] Queue state transitions logged
- [ ] Checkpoint pass/fail logged with reasons
- [ ] End-of-cycle summary shows time breakdown by operation and agent
- [ ] Summary shows work item outcomes and failure reasons
- [ ] Summary shows API call count and rate limit status
- [ ] Tests validate log output structure and summary format
- [ ] Documentation updated in workspace-os README

## Testing Plan
1. **Unit Tests**:
   - Log format validation
   - Context field extraction
   - Timestamp formatting
   - Summary calculation accuracy

2. **Integration Tests**:
   - Run cycle with --debug flag
   - Verify log file created in correct location
   - Validate all operations logged
   - Confirm summary matches actual cycle execution

3. **Performance Tests**:
   - Measure overhead of debug logging
   - Ensure <5% performance impact
   - Verify log file size remains manageable (<10MB per cycle)

## Implementation Notes

### Log File Rotation
- Keep last 10 debug logs by default
- Configurable via `WOS_DEBUG_LOG_RETENTION` env var
- Automatic cleanup of old logs on cycle start

### Performance Considerations
- Use buffered I/O for log writes
- Async log writing to avoid blocking cycle execution
- Structured logging library (e.g., structlog) for performance

### Security
- Sanitize sensitive data (API tokens, passwords) from logs
- Mark debug logs in .gitignore
- Document log file location for operators

## Related Documentation
- [WOS Operations Runbook](operations-runbook.md)
- [Cycle Batch Assignment](cycle-batch-assignment.md)
- Issue #922 (Operations Runbook)

## Implementation Timeline
1. **Phase 1**: Core logging infrastructure (`logging/debug.py` module)
2. **Phase 2**: CLI flag and log file management
3. **Phase 3**: Operation timing instrumentation
4. **Phase 4**: Cycle summary report generation
5. **Phase 5**: Tests and documentation

## References
- homedir issue: [#936](https://github.com/os-santiago/homedir/issues/936)
- workspace-os implementation branch: `feat/debug-flag`
