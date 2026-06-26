# OpenCode Skills Directory

This directory contains OpenCode skill definitions for Workspace OS (WOS) orchestration.

## Skills

### wos-cycle
Orchestrates long-running workspace improvement cycles with checkpoint-based progress tracking and multi-agent coordination.

**Use when:**
- Starting a new WOS improvement iteration
- Resuming interrupted improvement cycles
- Coordinating multi-phase workspace improvements

**Key capabilities:**
- Checkpoint-based state management
- Agent health monitoring
- Adaptive execution based on resource constraints
- Resume capability from last valid checkpoint

See [wos-cycle/skill.md](./skills/wos-cycle/skill.md) for detailed documentation.

### wos-delegation
Delegates workspace improvement tasks to appropriate agents (opencode, claude, antigravity) based on task characteristics.

**Use when:**
- Parallelizing independent improvement tasks
- Routing tasks based on agent specialization
- Coordinating multi-agent workflows

**Agent routing:**
- **opencode**: Code analysis, refactoring, pattern detection
- **claude**: Documentation, design decisions, reasoning
- **antigravity**: Architectural work, gap discovery, strategic assessment

See [wos-delegation/skill.md](./skills/wos-delegation/skill.md) for detailed documentation.

## Integration with Workspace OS

These skills are designed to work with the Workspace OS configuration in `.workspace-os/` and `workspace.sources.json`.

### Directory Structure
```
.opencode/
├── README.md                    # This file
└── skills/
    ├── wos-cycle/
    │   └── skill.md             # Cycle orchestration documentation
    └── wos-delegation/
        └── skill.md             # Delegation strategy documentation
```

### Configuration Example
See `config/workspace.sources.example.json` for a complete workspace configuration example.

## Development

When adding new skills:
1. Create a subdirectory under `skills/`
2. Add a `skill.md` documenting purpose, usage, and integration
3. Update this README with the new skill
4. Test the skill in a WOS cycle before committing

## References
- Workspace OS configuration: `.workspace-os/`
- Workspace sources: `workspace.sources.json`
- WOS validation: `docs/en/validation/webhook-wos-discord-validation.md`
