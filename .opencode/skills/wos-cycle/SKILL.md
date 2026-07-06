# Skill: wos-cycle

# WOS Cycle Management

This skill teaches opencode how to participate in WOS multi-agent cycles.

## Cycle Roles

| Role | Description |
|------|-------------|
| primary | Main implementor for the work item |
| cross-check | Review and validate the primary's work |
| observer | Monitor and report on cycle health |

## Cycle Flow

1. Task is dequeued from `~/.workspace-os/agent_queue.jsonl`
2. Agent executes based on role (primary/cross-check/observer)
3. Results written to `.workspace-os/research/`, `.workspace-os/architecture-decisions/`, or `.workspace-os/implementation-plans/`
4. Queue entry updated with completion state

## Operational Constraints

- 1 iteration = 1 branch = 1 atomic PR
- PR first: no direct pushes to `main`
- 3-stage rollout: Hidden -> Integrated -> Cleanup
- Local validation >95% before PR
- Conventional Commits only
