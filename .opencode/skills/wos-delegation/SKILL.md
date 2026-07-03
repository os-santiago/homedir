# Skill: wos-delegation

# WOS Delegation

Workspace OS (WOS) is an agent orchestration framework that manages multi-agent cycles for issue-driven development. This skill teaches opencode how to participate in and delegate through WOS.

## Agent Capability Matrix

| Agent | Best For |
|-------|----------|
| opencode | CLI tool interaction, file ops, code generation, refactoring, DevOps scripts |
| claude | Complex reasoning, architecture decisions, code review, documentation |
| antigravity | Experimental, brainstorming, creative tasks, novel solutions |

## WOS Routing Rules

When you receive a task via WOS queue delegation:
- **Implementation tasks** → opencode (code generation, file ops, git/gh, tests)
- **Architecture analysis** → claude (complex reasoning, documentation, code review)
- **Experimental/creative** → antigravity (novel solutions, brainstorming)

## WOS Delegation Flow

1. Issues labeled `wos-review` trigger WOS delegation
2. WOS creates research artifacts under `.workspace-os/research/`
3. Architecture decisions go under `.workspace-os/architecture-decisions/`
4. Implementation plans go under `.workspace-os/implementation-plans/`

## WOS Artifacts

| Artifact | Path | Description |
|----------|------|-------------|
| Agent Queue | `~/.workspace-os/agent_queue.jsonl` | JSONL queue of WOS work items |
| Global Memory | `~/.workspace-os/global-memory.sqlite3` | Persisted cross-project knowledge |
| WOS Sources | `config/workspace.sources.json` | Source configuration |
| Shared Knowledge | `config/.workspace-os/shared_knowledge/` | Cross-agent shared knowledge |
| Research | `.workspace-os/research/*.md` | Phase findings |
| ADRs | `.workspace-os/architecture-decisions/*.md` | Architecture decisions |
| Plans | `.workspace-os/implementation-plans/*.md` | Implementation plans |

## Operational Constraints

- 1 iteration = 1 branch = 1 atomic PR
- PR first: no direct pushes to `main`
- 3-stage rollout: Hidden -> Integrated -> Cleanup
- Local validation >95% before PR
- Conventional Commits only

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| WOS_ROUTING_DEBUG | false | Enable routing decision logging |
| WOS_ROLE_ROTATION_CYCLE | 9 | Work items per full role rotation |
| WOS_CYCLE_TIMEOUT | 300s | Agent task timeout |

## WOS Agent Queue Entry Format

```json
{
  "task_id": "cycle-work-{N}-{role}",
  "agent": "opencode|claude|antigravity",
  "workspace": "homedir",
  "prompt": "Task description",
  "state": "running|completed|failed",
  "queued_at": "ISO timestamp",
  "started_at": "ISO timestamp",
  "completed_at": "ISO timestamp|null",
  "duration_seconds": null,
  "returncode": null,
  "error": null,
  "metadata": {
    "work_item_number": 1,
    "role": "primary|cross-check|observer",
    "issue_number": 997
  }
}
```

## Logging

Use structured logging for all WOS operations:
`[timestamp] [LEVEL] [agent:opencode] [work_item:N] [op:type]`
