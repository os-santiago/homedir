# Parent/Child/Epic Quick Reference

Quick guide for using the Parent/Child/Epic issue system. For full details, see [PARENT_CHILD_EPIC_STANDARD.md](./PARENT_CHILD_EPIC_STANDARD.md).

## When to Use Each Type

| Type | Use When | Timeline | Example |
|------|----------|----------|---------|
| **Epic** | Strategic initiative with multiple features | Weeks to months | "Q2 2026 - Observability Platform Modernization" |
| **Parent** | Feature requiring multiple coordinated tasks | Days to weeks | "Implement distributed tracing for API layer" |
| **Child** | Single concrete task or bug fix | Hours to days | "Add trace context propagation to AuthService" |

## Required Fields by Type

### Epic
- Title: `[EPIC] <Initiative> - <Timeframe>`
- Labels: `epic`, `area:*`, `priority:*`
- Must include: Overview, Scope, Progress Tracking, Timeline

### Parent
- Title: `[PARENT] <Feature description>`
- Labels: `parent-issue`, type, `area:*`, `priority:*`
- Must include: Parent reference, Context, Objective, Child Issues list

### Child
- Title: Descriptive (no prefix)
- Labels: `child-issue`, type, `area:*`
- Must include: Parent reference (MANDATORY), Objective, Acceptance Criteria

## Linking Issues

Always include parent reference:

```markdown
## Parent
- Parent issue: #123
```

For parent/epic issues, track children:

```markdown
## Child Issues
- [ ] #124 - Task description
- [ ] #125 - Task description
```

## Closure Rules

| Type | Close When |
|------|------------|
| **Child** | Acceptance criteria met, PR merged |
| **Parent** | ALL children closed (or explicitly deferred) |
| **Epic** | ALL parent/child issues closed |

## Common Mistakes to Avoid

❌ **Don't**: Create child issues without linking to parent
✅ **Do**: Always include `## Parent` section with parent issue number

❌ **Don't**: Close parent while children are still open
✅ **Do**: Close all children first, or document deferral explicitly

❌ **Don't**: Mix multiple issues in one PR
✅ **Do**: One child issue = One PR (atomic changes)

## Templates

Create new issues using GitHub templates:

1. Go to `Issues` → `New Issue`
2. Select appropriate template:
   - **Epic Issue** - for strategic initiatives
   - **Parent Issue** - for features with multiple tasks
   - **Child Issue** - for tasks within a parent
3. Fill all required fields
4. Add labels as specified above

## References

- Full standard: [PARENT_CHILD_EPIC_STANDARD.md](./PARENT_CHILD_EPIC_STANDARD.md)
- Definition of Ready/Done: [DEFINITION_OF_READY_DONE.md](./DEFINITION_OF_READY_DONE.md)
