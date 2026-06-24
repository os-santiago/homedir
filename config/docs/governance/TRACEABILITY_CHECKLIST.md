# Traceability Checklist

Use this checklist to ensure proper Parent/Child/Epic linkage and traceability for multi-issue initiatives.

## For Parent Issues

### Creation
- [ ] Title starts with `[PARENT]`
- [ ] Includes `## Parent` section (with epic link if applicable)
- [ ] Context, Problem, and Objective sections filled
- [ ] Scope clearly defined (In scope / Out of scope)
- [ ] Acceptance criteria specific and measurable
- [ ] Priority assigned
- [ ] Labels applied: `parent`, area label(s)

### Maintenance
- [ ] Child issues listed using GitHub task lists
- [ ] Progress tracking updated when child state changes
- [ ] All child issue references use `#issue-number` format
- [ ] Blocked child issues flagged
- [ ] Timeline updated if delays occur

### Closure
- [ ] All child issues verified closed
- [ ] All child PRs verified merged
- [ ] Final progress shows 100%
- [ ] All acceptance criteria checked
- [ ] Integration testing completed
- [ ] Documentation links added
- [ ] Follow-up issues created for out-of-scope items
- [ ] Closure comment documents outcomes and links PRs

## For Child Issues

### Creation
- [ ] Includes `## Parent` section with parent issue link
- [ ] Context explains relationship to parent
- [ ] Acceptance criteria specific to this child
- [ ] Technical details included
- [ ] Testing strategy defined
- [ ] Dependencies documented (if any)
- [ ] Labels applied: `child`, type label, area label

### During Work
- [ ] Issue assigned to contributor
- [ ] Status updated in parent issue (Planned → In Progress)
- [ ] Blockers reported immediately in both child and parent
- [ ] PR created with reference to child AND parent issues

### Closure
- [ ] All acceptance criteria met
- [ ] Tests passing
- [ ] PR merged with `Closes #child-issue` in description
- [ ] Parent issue updated (moved to Completed section)
- [ ] Parent progress tracking updated

## For Epic Issues

### Creation
- [ ] Title starts with `[EPIC]`
- [ ] Strategic context documented
- [ ] Success metrics defined and measurable
- [ ] Timeline with milestones
- [ ] Stakeholders identified
- [ ] Labels applied: `epic`, strategic area

### Maintenance
- [ ] Parent issues listed using GitHub task lists
- [ ] Milestones tracked and updated
- [ ] Stakeholder communication plan followed
- [ ] Risk assessment updated as needed

### Closure
- [ ] All parent issues closed
- [ ] Success metrics validated
- [ ] Stakeholder sign-off documented
- [ ] Retrospective completed
- [ ] Final report or summary published

## For Pull Requests

### Linking
- [ ] PR description includes `Closes #child-issue`
- [ ] PR description includes `Part of #parent-issue` (if applicable)
- [ ] PR title references issue number: `fix: description (#XXX)`
- [ ] If multi-issue PR, all issues listed

### Traceability
- [ ] Commit messages follow conventional commits
- [ ] Commits reference issue numbers where applicable
- [ ] PR passes all CI/CD checks
- [ ] Code review completed

## Cross-Reference Validation

Run these checks before closing parent/epic issues:

### Manual Checks
1. **Parent → Children**: All child issues listed in parent
2. **Children → Parent**: All children reference parent via `## Parent` section
3. **PRs → Issues**: All PRs reference both child and parent
4. **Completion**: All children closed before parent closes
5. **Labels**: Consistent label taxonomy applied

### GitHub Features
- Use GitHub task lists (`- [ ] #123`) for automatic progress tracking
- GitHub automatically calculates completion percentage
- Issue references create bidirectional links

## Common Anti-Patterns to Avoid

### ❌ Don't Do This
- Creating parent issue after children already exist
- Closing parent before all children complete
- Child issues without parent reference
- Mixing multiple unrelated parents in one PR
- Skipping progress updates in parent issue
- Using plain text instead of `#issue` references
- Closing child without updating parent
- Orphaned child issues with deleted or invalid parent references

### ✅ Do This Instead
- Create parent first, then children
- Keep parent open until all children merged
- Always link child to parent in issue body
- One PR per child issue (or clear multi-issue strategy)
- Update parent progress on every child state change
- Use `#123` format for automatic linking
- Update parent immediately when child closes
- Validate parent/child links regularly

## Quick Validation Script

For AI agents or automation, use this validation logic:

```
For each PARENT issue:
  1. Extract all child issue references from body
  2. For each child:
     - Verify child exists and is not deleted
     - Verify child has "## Parent" section referencing this parent
     - Verify child state (open/closed)
  3. Calculate: closed_children / total_children
  4. Verify: if all children closed, parent should close
  5. Verify: parent cannot close if children still open
```

## Related Documentation

- [Parent/Child/Epic Standard](./PARENT_CHILD_EPIC_STANDARD.md) - Complete specification
- [Parent Issue Template](../.github/ISSUE_TEMPLATE/parent_issue.yml) - GitHub template
- [Child Issue Template](../.github/ISSUE_TEMPLATE/child_issue.yml) - GitHub template  
- [Epic Template](../.github/ISSUE_TEMPLATE/epic.yml) - GitHub template
- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) - Quality criteria
