# Cycle Batch Issue Assignment Optimization

## Issue
[#803](https://github.com/os-santiago/homedir/issues/803) - WOS: Batch issue assignment to reduce 81% idle ratio

## Problem
WOS cycle idle ratio was 81% despite optimizations (max_workers=32, pool=4x, refetch=3x threshold). Root cause: serial issue assignment in completion loop caused burst completions to wait.

## Solution
Implemented batch assignment in workspace-os repository at `src/workspace_os/cycle.py` (lines 1246-1299).

### Implementation
- **Repository**: workspace-os
- **File**: `src/workspace_os/cycle.py`
- **Commit**: 5b3a4e6 "perf(cycle): batch issue assignment to reduce idle ratio from 81% to <40%"
- **PR**: [workspace-os#30](https://github.com/scanalesespinoza/workspace-os/pull/30)
- **Branch**: feat/issue-outcome-tracking

### Key Changes
```python
# Batch-assign issues and queue new work items for all completed futures
# This reduces idle time when multiple futures complete simultaneously
if now_fn() < deadline and completed_futures:
    new_work_count = min(len(completed_futures), max_workers - len(pending_futures))
    batch_assigned_issues = []

    # Pre-assign all issues for this batch to avoid one-by-one assignment overhead
    if available_issues and enable_issue_assignment:
        for _ in range(new_work_count):
            next_assigned_issue = _assign_issue_to_work_item(
                work_item_number + len(batch_assigned_issues), 
                available_issues, 
                assigned_issues, 
                in_progress_issues
            )
            batch_assigned_issues.append(next_assigned_issue)
            if next_assigned_issue:
                cached_unassigned_count -= 1
    else:
        batch_assigned_issues = [None] * new_work_count

    # Queue all new work items in parallel
    for i in range(new_work_count):
        assigned_issue = batch_assigned_issues[i] if i < len(batch_assigned_issues) else None
        # ... queue work item ...
```

## Expected Impact
- Reduce idle ratio from 81% to <40%
- Increase throughput by ~2x when completion bursts occur
- No change to single-completion path

## Status
✅ Implemented in workspace-os  
🔄 Pending merge in workspace-os PR #30  
📋 Tracked in homedir issue #803

## Testing
- Run 30min cycle with 32 workers
- Compare idle_ratio before/after
- Verify no duplicate issue assignments
- Confirm queue depth stays at max_workers during bursts

## References
- homedir issue: [#803](https://github.com/os-santiago/homedir/issues/803)
- workspace-os PR: [#30](https://github.com/scanalesespinoza/workspace-os/pull/30)
- workspace-os commit: 5b3a4e6
- Related commits: bfd834c, 78d646e, 011f5e1
