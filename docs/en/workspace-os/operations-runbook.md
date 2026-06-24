# WOS Operations Runbook

## Agent Capability Matrix

| Agent | Primary Use | Best For | Limitations |
|-------|-------------|----------|-------------|
| opencode | CLI tool interaction, file ops | Code generation, refactoring, DevOps scripts | Not ideal for creative writing, UI design |
| claude | Complex reasoning, analysis | Architecture decisions, code review, documentation | Slower for bulk file operations |
| antigravity | Experimental, brainstorming | Creative tasks, novel solutions | Not for production-critical paths |

## Common Error Handling

### wrong_agent Error

**Detection**: Learning model flags `wrong_agent` when confidence >= 0.70.

**Symptoms**:
- Agent fails to complete task with capability mismatch errors
- Repeated routing to agent that lacks required tools
- Task execution time exceeds expected range

**Resolution**:
1. Check agent capability matrix above
2. Verify `task_hint` is being passed correctly
3. Review learning model feedback for pattern
4. If persistent (confidence >= 0.90), adaptive cross-checking activates

**Prevention**:
- Ensure issue descriptions include clear task keywords
- Use `primary_agent` profile setting for specialized workloads
- Monitor routing debug output (`WOS_ROUTING_DEBUG=true`)

### Agent Timeout

**Symptoms**: Task exceeds `WOS_CYCLE_TIMEOUT` (default: 300s).

**Resolution**:
1. Check if agent is overloaded (concurrent tasks)
2. Verify workspace resources (disk, memory)
3. Consider splitting task into smaller units

### Queue Starvation

**Symptoms**: Workers idle while issues remain unassigned.

**Resolution**:
1. Increase `WOS_REFETCH_MULTIPLIER` (default: 4)
2. Check rate limiting on issue source (GitHub API)
3. Verify `max_workers` setting matches available capacity

## Key Operational Metrics

| Metric | Target | Alert Threshold | Description |
|--------|--------|----------------|-------------|
| wrong_agent confidence | < 0.30 | >= 0.70 | Learning model confidence in routing error |
| Cross-check ratio | ~33% | >= 50% | Frequency of cross-check role assignment |
| Queue utilization | > 80% | < 50% | Percentage of workers actively processing |
| Refetch hit rate | > 90% | < 70% | Issues successfully refetched vs requested |
| Cycle completion rate | > 95% | < 80% | Cycles completing without critical errors |

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| WOS_ROUTING_DEBUG | false | Enable routing decision logging |
| WOS_TASK_AWARE_ROUTING | true | Enable keyword-based task routing |
| WOS_REFETCH_MULTIPLIER | 4 | Scale factor for issue refetch pool |
| WOS_ROLE_ROTATION_CYCLE | 9 | Work items per full role rotation |
| WOS_ROUTING_LOG | false | Enable routing decision history logging |
| WOS_ENABLE_LEARNING | false | Enable pattern auto-application |

## Related Documentation

- Agent Routing Validation
- Learning System
- Issue #922 (this document)

## Runbook Checklist

When investigating an operational issue:

1. [ ] Check current metrics against targets
2. [ ] Review recent cycle logs for error patterns
3. [ ] Verify agent availability and capability
4. [ ] Check environment variable configuration
5. [ ] Review learning model feedback
6. [ ] Escalate to maintainers if confidence threshold exceeded
