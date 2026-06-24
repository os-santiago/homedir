# Parent/Child/Epic Issue Standard

## Overview

This document defines the canonical standard for structuring and tracking complex multi-issue initiatives using Parent/Child/Epic relationships. This enables clear traceability for both human teams and AI agents.

## Definitions

### Epic
A **strategic initiative** spanning multiple features or improvements, typically aligned to a quarter or major milestone.

- Timeline: Weeks to months
- Scope: Multiple features/systems/teams
- Contains: Multiple parent issues or direct child issues
- Example: "Q2 2026 - Observability Platform Modernization"

### Parent Issue
A **tactical initiative** representing a complete feature or significant improvement requiring multiple coordinated tasks.

- Timeline: Days to weeks
- Scope: Single feature or bounded improvement
- Contains: Multiple child issues
- Reports to: Epic (optional) or standalone
- Example: "Implement distributed tracing for API layer"

### Child Issue
A **concrete task** that is part of a larger parent issue. Self-contained and independently completable.

- Timeline: Hours to days
- Scope: Single task, bug fix, or sub-feature
- Contains: Nothing (leaf node)
- Reports to: Parent issue (mandatory)
- Example: "Add trace context propagation to AuthService"

## Relationship Rules

### Mandatory Fields for All Issues

Every issue must include:

```markdown
## Parent
- Parent issue: #<number> or "None (root issue)"
```

### Epic Structure

**Epic issues must include:**

```markdown
## Epic Overview
Brief strategic context and business value.

## Scope
What this epic includes and excludes.

## Progress Tracking
- [ ] #<parent-1> - Parent issue title
- [ ] #<parent-2> - Parent issue title
- [ ] #<child-direct> - Direct child (if no intermediate parent)

## Acceptance Criteria
High-level outcomes that define epic completion.

## Timeline
Target start/end dates or milestone.
```

**Epic closure rules:**
- Epic closes when ALL tracked parent/child issues are closed
- Progress section updated regularly (at least weekly)
- Final summary added linking all completed work

### Parent Issue Structure

**Parent issues must include:**

```markdown
## Parent
- Parent issue: #<epic-number> or "None (root issue)"

## Context
What problem this solves and why it matters.

## Objective
Clear, measurable goal for this parent issue.

## Scope
**In scope**
- Specific items covered

**Out of scope**
- Explicit boundaries

## Child Issues
- [ ] #<child-1> - Child issue title
- [ ] #<child-2> - Child issue title

## Acceptance Criteria
Specific, testable criteria for completion.

## Deliverables
Concrete outputs expected.
```

**Parent closure rules:**
- Parent closes when ALL child issues are closed
- Child issues section kept up-to-date
- Can close with some children deferred if:
  - Deferred children moved to separate parent
  - Reason documented in closure comment
  - Original objectives met despite scope reduction

### Child Issue Structure

**Child issues must include:**

```markdown
## Parent
- Parent issue: #<parent-number>

## Context
Brief context specific to this task.

## Objective
What this specific task accomplishes.

## Acceptance Criteria
Clear criteria for completion.

## Dependencies
- Blocks: #<issue> (this must complete before that issue)
- Blocked by: #<issue> (that must complete before this issue)
- Related: #<issue> (related but not blocking)
```

**Child closure rules:**
- Child can close independently when acceptance criteria met
- Must link to PR(s) that resolved it
- Closure automatically updates parent progress
- If scope changes, update parent issue and document

## Naming Conventions

### Epic Titles
Format: `[EPIC] <Strategic initiative> - <Timeframe/Milestone>`

Examples:
- `[EPIC] Observability Platform Modernization - Q2 2026`
- `[EPIC] Security Hardening Initiative - H1 2026`
- `[EPIC] Developer Experience Improvements - 2026-Q3`

### Parent Titles
Format: `[PARENT] <Feature/improvement description>`

Examples:
- `[PARENT] Implement distributed tracing for API layer`
- `[PARENT] Migrate authentication to OIDC`
- `[PARENT] Add rate limiting to public endpoints`

### Child Titles
Format: Standard descriptive title (no prefix needed)

Examples:
- `Add trace context propagation to AuthService`
- `Update OIDC configuration schema`
- `Implement token bucket algorithm for rate limiter`

## Labels

### Required Labels by Type

**Epic:**
- `epic`
- One area label: `area:backend`, `area:frontend`, `area:devops`, `area:docs`, `area:governance`
- One priority label: `priority:critical`, `priority:high`, `priority:medium`, `priority:low`

**Parent:**
- `parent-issue`
- One type label: `enhancement`, `feature`, `refactor`, `documentation`
- One area label (same as epic)
- One priority label (same or lower than epic)

**Child:**
- `child-issue`
- One type label: `bug`, `task`, `enhancement`, `documentation`, `test`
- One area label (same as parent)
- Optional: `good-first-issue` for accessible tasks

## Progress Tracking

### Progress Format in Parent/Epic Issues

Use GitHub task lists for automatic progress calculation:

```markdown
## Progress Tracking

**Overall: X/Y issues completed (Z%)**

### Phase 1: Foundation
- [x] #123 - Completed task
- [x] #124 - Completed task
- [ ] #125 - In progress task

### Phase 2: Implementation
- [ ] #126 - Not started
- [ ] #127 - Not started

### Phase 3: Validation
- [ ] #128 - Not started
```

### Update Frequency

- Epic progress: Updated weekly (minimum)
- Parent progress: Updated when any child changes state
- Status comments: Added on significant milestones

## State Management

### Valid State Transitions

**Epic states:**
1. `open` → Planning, parent issues being created
2. `open` → Active, child work in progress
3. `open` → `closed` → All work complete

**Parent states:**
1. `open` → Planning, child issues being created
2. `open` → Active, children being worked
3. `open` → `closed` → All children complete OR scope reduced with justification

**Child states:**
1. `open` → Ready for work (meets Definition of Ready)
2. `open` → In progress (assignee set, work started)
3. `open` → `closed` → Work complete, PR merged

### Blocked State Handling

When an issue is blocked:
1. Add `status:blocked` label
2. Update issue with:
   ```markdown
   ## Blocked Status
   - Blocked by: #<issue>
   - Reason: <clear explanation>
   - Unblock criteria: <what needs to happen>
   ```
3. Notify parent issue with comment
4. Remove label when unblocked

## Linking and References

### Required Links

**In issue body:**
- Parent reference: `## Parent` section (mandatory)
- Child references: `## Child Issues` or `## Progress Tracking` (for parents/epics)

**In GitHub issue sidebar:**
- Use "Development" to link related PRs
- Use issue references in PR body: `Closes #<child-issue>`

### Cross-References

When issues relate without parent/child relationship:

```markdown
## Related Issues
- Depends on: #<issue> - Must complete before this
- Blocks: #<issue> - This must complete before that
- Related: #<issue> - Related context
- Supersedes: #<issue> - Replaces this older issue
- Superseded by: #<issue> - Replaced by this newer issue
```

## Templates

### Epic Template

```markdown
---
name: Epic Issue
about: Strategic initiative with multiple parent/child issues
title: '[EPIC] <Initiative name> - <Timeframe>'
labels: epic, area:*, priority:*
---

## Parent
- Parent issue: None (root epic)

## Epic Overview
[Brief strategic context and business value]

## Business Value
[Why this matters and expected impact]

## Scope
**In scope**
- [Major component 1]
- [Major component 2]

**Out of scope**
- [Explicit exclusion 1]
- [Explicit exclusion 2]

## Progress Tracking

**Overall: 0/X issues completed (0%)**

### Phase 1: [Phase name]
- [ ] #<parent-1> - [Parent issue title]
- [ ] #<child-1> - [Direct child if no parent]

### Phase 2: [Phase name]
- [ ] #<parent-2> - [Parent issue title]

## Acceptance Criteria
- [ ] [High-level outcome 1]
- [ ] [High-level outcome 2]

## Timeline
- Start date: YYYY-MM-DD
- Target completion: YYYY-MM-DD
- Milestone: [Release version or quarter]

## Success Metrics
- [Metric 1]: [Target]
- [Metric 2]: [Target]

## Dependencies
- External: [External dependencies]
- Internal: [Other epics or major initiatives]

## Risks
| Risk | Impact | Mitigation |
|------|--------|------------|
| [Risk 1] | High/Medium/Low | [Strategy] |
```

### Parent Issue Template

```markdown
---
name: Parent Issue
about: Feature or improvement with multiple child tasks
title: '[PARENT] <Feature/improvement description>'
labels: parent-issue, enhancement, area:*
---

## Parent
- Parent issue: #<epic-number> or "None (root issue)"

## Context
[What problem this solves and why it matters]

## Objective
[Clear, measurable goal]

## Scope
**In scope**
- [Specific item 1]
- [Specific item 2]

**Out of scope**
- [Exclusion 1]
- [Exclusion 2]

## Child Issues

**Overall: 0/X tasks completed (0%)**

- [ ] #<child-1> - [Task description]
- [ ] #<child-2> - [Task description]

## Acceptance Criteria
- [ ] [Criterion 1]
- [ ] [Criterion 2]

## Deliverables
- [Deliverable 1]
- [Deliverable 2]

## Technical Approach
[High-level approach or architecture]

## Dependencies
- Blocks: #<issue>
- Blocked by: #<issue>
- Related: #<issue>

## Testing Strategy
[How this will be validated]

## Documentation Requirements
- [ ] [Doc 1]
- [ ] [Doc 2]

## Rollout Plan
[How this will be deployed/released]
```

### Child Issue Template

```markdown
---
name: Child Issue
about: Specific task within a parent issue
title: '<Task description>'
labels: child-issue, task, area:*
---

## Parent
- Parent issue: #<parent-number>

## Context
[Brief context specific to this task]

## Objective
[What this specific task accomplishes]

## Acceptance Criteria
- [ ] [Criterion 1]
- [ ] [Criterion 2]

## Implementation Notes
[Technical details, approach, or constraints]

## Testing Requirements
- [ ] Unit tests added
- [ ] Integration tests updated
- [ ] Manual verification completed

## Documentation
- [ ] Code comments added
- [ ] README updated (if needed)
- [ ] Parent issue updated with progress

## Dependencies
- Blocks: #<issue>
- Blocked by: #<issue>
- Related: #<issue>

## Estimated Effort
[Small (hours) / Medium (1-2 days) / Large (3+ days)]
```

## AI Agent Guidance

### For AI Agents Reading Issues

When parsing issues for planning:

1. **Always check `## Parent` section** to understand hierarchy
2. **Parse progress checklists** to calculate completion percentage
3. **Respect scope boundaries** defined in parent/epic issues
4. **Check blocking relationships** before starting work
5. **Update parent progress** when completing child issues

### For AI Agents Creating Issues

1. **Determine correct level**: Epic vs Parent vs Child
2. **Use correct template** based on issue type
3. **Link to parent immediately** (don't create orphaned children)
4. **Apply correct labels** per type
5. **Fill all required sections** - no placeholders

### For AI Agents Updating Issues

1. **Update parent progress** when child state changes
2. **Add blocking status** if dependencies discovered
3. **Document scope changes** in issue comments
4. **Link related PRs** in issue body
5. **Use conventional comments** for status updates

## Enforcement

### Pull Request Requirements

PRs closing child issues must:
- Reference child issue: `Closes #<child>`
- Meet child's acceptance criteria
- Update parent issue progress (automated via checklist)

PRs closing parent issues must:
- Ensure all child issues are closed first
- Provide completion summary
- Link to all child PRs

### Review Checklist for Issues

Before closing any parent/epic issue, verify:
- [ ] All child issues closed or explicitly deferred
- [ ] All acceptance criteria met
- [ ] Progress tracking shows 100% or documents scope reduction
- [ ] Completion summary added
- [ ] All related PRs linked

## Examples

### Example: Complete Epic Structure

**Epic: `[EPIC] Rate Limiting System - Q2 2026` (#850)**

```markdown
## Parent
- Parent issue: None (root epic)

## Progress Tracking
- [ ] #851 - [PARENT] Design rate limiting architecture
  - [x] #852 - Research token bucket algorithms
  - [x] #853 - Design storage backend for rate limits
  - [ ] #854 - Create architecture decision record
- [ ] #855 - [PARENT] Implement rate limiting middleware
  - [ ] #856 - Implement token bucket algorithm
  - [ ] #857 - Add Redis backend for rate limit storage
  - [ ] #858 - Create rate limit middleware
- [ ] #859 - [PARENT] Add rate limiting to API endpoints
  - [ ] #860 - Apply rate limits to auth endpoints
  - [ ] #861 - Apply rate limits to public API
  - [ ] #862 - Add rate limit headers to responses
```

### Example: Parent with Children

**Parent: `[PARENT] Implement rate limiting middleware` (#855)**

```markdown
## Parent
- Parent issue: #850

## Child Issues
- [ ] #856 - Implement token bucket algorithm
- [ ] #857 - Add Redis backend for rate limit storage
- [ ] #858 - Create rate limit middleware
```

### Example: Child Issue

**Child: `Implement token bucket algorithm` (#856)**

```markdown
## Parent
- Parent issue: #855

## Dependencies
- Blocks: #858 (middleware needs algorithm)
- Related: #852 (references research findings)
```

## Maintenance

### Regular Review

- **Weekly**: Update epic progress tracking
- **Per PR merge**: Update parent progress automatically
- **Monthly**: Review orphaned issues (no parent reference)
- **Quarterly**: Audit epic completion and close stale initiatives

### Cleanup Protocol

For orphaned or stale issues:
1. Identify issues without parent reference (except epics/root parents)
2. Comment tagging creator/assignee
3. Wait 7 days for response
4. Either: assign parent or close as stale

## References

- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md)
- [PR Review Policy](./PR_REVIEW_POLICY.md)
- [Merge Safety Checklist](./templates/merge_safety_checklist.md)

## Changelog

- 2026-06-24: Initial standard created (addresses #842)
