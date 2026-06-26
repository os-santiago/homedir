# WOS Delegation Skill

## Purpose
Delegate workspace improvement tasks to appropriate agents (opencode, claude, antigravity) based on task characteristics and current workspace state.

## When to Use
- When a WOS cycle identifies multiple independent improvement tasks
- When parallelizing work across different workspace areas
- When routing tasks based on agent specialization

## Agent Selection Strategy

### OpenCode Agent
- Code analysis and refactoring tasks
- Pattern detection and consistency improvements
- Codebase-wide transformations
- Technical debt reduction

### Claude Agent
- Documentation and explanation tasks
- Design and architecture decisions
- Complex reasoning and planning
- User-facing content creation

### Antigravity Agent
- Architectural work and strategic planning
- Gap discovery and opportunity identification
- Leverage analysis and assessment
- Security audits and compliance reviews

## Delegation Process

1. **Task Analysis**: Examine the improvement task characteristics
2. **Agent Selection**: Choose the most appropriate agent based on task type
3. **Context Preparation**: Gather necessary context for the delegated agent
4. **Execution**: Launch the agent with clear objectives
5. **Result Integration**: Collect and integrate agent outputs

## Input Format
```json
{
  "workspace": "workspace-name",
  "task": "improvement task description",
  "context": {
    "files": ["list", "of", "relevant", "files"],
    "constraints": ["ADEV compliance", "atomic commits"],
    "priority": "high|medium|low"
  }
}
```

## Output Format
```json
{
  "agent": "selected-agent-name",
  "rationale": "why this agent was chosen",
  "delegated_task": "refined task description for agent",
  "expected_outcome": "what the agent should produce"
}
```

## Integration with WOS Cycle
This skill is typically invoked by `wos-cycle` when it identifies tasks that can be parallelized or require specialized agent capabilities.
