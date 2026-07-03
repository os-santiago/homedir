# WOS Agent Capabilities

## Agent Capability Matrix

| Agent | Primary Use | Best For | Limitations |
|-------|-------------|----------|-------------|
| opencode | CLI tool interaction, file ops | Code generation, refactoring, DevOps scripts | Not ideal for creative writing, UI design |
| claude | Complex reasoning, analysis | Architecture decisions, code review, documentation | Slower for bulk file operations |
| antigravity | Experimental, brainstorming | Creative tasks, novel solutions | Not for production-critical paths |

## WOS Routing Rules

- **Implementation tasks** → opencode (code generation, file ops, git/gh, tests)
- **Architecture analysis** → claude (complex reasoning, documentation, code review)
- **Experimental/creative** → antigravity (novel solutions, brainstorming)
