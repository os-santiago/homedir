# WOS Cycle Skill

## Purpose
Orchestrate long-running workspace improvement cycles with checkpoint-based progress tracking, agent coordination, and adaptive execution.

## When to Use
- Starting a new WOS improvement iteration
- Resuming an interrupted improvement cycle
- Monitoring ongoing workspace improvement processes
- Coordinating multi-agent improvement workflows

## Cycle Lifecycle

### 1. Initialization
- Load workspace state and configuration
- Identify improvement opportunities from backlog
- Establish cycle objectives and constraints
- Create initial checkpoint

### 2. Execution
- Break down improvements into atomic tasks
- Delegate tasks to appropriate agents via `wos-delegation`
- Track progress with periodic checkpoints
- Monitor agent health and resource usage

### 3. Checkpoint Management
- Capture workspace state at regular intervals
- Record completed work and validation results
- Update learning model based on outcomes
- Persist cycle state for resumption

### 4. Completion
- Validate all improvements meet ADEV requirements
- Generate cycle summary report
- Update workspace knowledge base
- Archive cycle artifacts

## Cycle Configuration
```json
{
  "cycle_id": "workspace-improvement-YYYYMMDD-N",
  "workspace": "workspace-name",
  "objectives": [
    "reduce agent overhead",
    "improve context compaction",
    "enhance traceability"
  ],
  "constraints": {
    "max_duration": "2h",
    "max_agents": 5,
    "required_validations": ["tests", "linting", "type-check"]
  },
  "checkpoint_interval": "15m"
}
```

## Checkpoint Format
```json
{
  "checkpoint_id": "cycle-id-checkpoint-N",
  "timestamp": "ISO8601",
  "elapsed_time": "duration",
  "completed_tasks": ["task-1", "task-2"],
  "in_progress": ["task-3"],
  "blocked": [],
  "metrics": {
    "files_changed": 10,
    "tests_added": 5,
    "validations_passed": 3
  },
  "next_actions": ["action-1", "action-2"]
}
```

## Integration Points

### Input Sources
- Workspace git status and recent commits
- Open GitHub issues tagged for WOS review
- Improvement backlog from .workspace-os/
- Previous cycle learnings

### Output Artifacts
- Checkpoint files in .workspace-os/journal/
- Cycle summary reports
- Updated improvement plans
- Learning model updates

## Error Handling
- **Agent Failure**: Record failure, attempt recovery or skip task
- **Validation Failure**: Roll back checkpoint, analyze root cause
- **Resource Exhaustion**: Pause cycle, create recovery checkpoint
- **Conflict Detection**: Halt cycle, request human intervention

## Metrics Tracked
- Cycle duration (logical and wall-clock time)
- Agent utilization and efficiency
- Checkpoint frequency and stability
- Validation success rate
- Context window usage
- Token budget consumption

## Resume Capability
Cycles can be paused and resumed using the most recent valid checkpoint. Resume process:
1. Load checkpoint state
2. Verify workspace matches checkpoint state
3. Resume from next planned action
4. Continue normal cycle execution
