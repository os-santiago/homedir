# Label Triage Quick Reference

**Version**: 1.0  
**For**: Maintainers & Contributors  
**Last Updated**: 2026-06-23

## 🚀 Quick Start

### For Maintainers: 3-Step Triage

1. **Add Type** (required): `type:bug`, `type:feature`, `type:docs`, or `type:question`
2. **Add Priority** (required): `priority:P0` (critical) → `priority:P3` (low)
3. **Add Extras** (optional): `collab:*`, `domain:*`, `state:*`

### For Contributors: Finding Issues

```bash
# Good first issues
gh issue list --label "collab:good-first-issue" --state open

# High-priority bugs
gh issue list --label "type:bug,priority:P1" --state open

# Documentation needs
gh issue list --label "type:docs,collab:help-wanted" --state open
```

## 📋 Label Categories

### Type (Choose ONE)

| Label | When to Use | Example |
|-------|-------------|---------|
| `type:bug` | Something broken, error, regression | "Login fails with 500 error" |
| `type:feature` | New capability, enhancement | "Add dark mode support" |
| `type:docs` | Documentation fix/improvement | "Update API reference for v2.0" |
| `type:question` | Clarification, how-to, discussion | "How to configure SSL?" |

### Priority (Choose ONE)

| Label | Impact | Urgency | SLA | Example |
|-------|--------|---------|-----|---------|
| `priority:P0` | Critical | Immediate | 4 hours | Production down, data loss, security breach |
| `priority:P1` | High | Today | 1 day | Major feature broken, significant user impact |
| `priority:P2` | Medium | This week | 1 week | Important but not blocking, minor bugs |
| `priority:P3` | Low | Backlog | Best effort | Nice-to-have, polish, tech debt |

**Decision matrix**:

|  | **Blocks users** | **Workaround exists** | **Nice-to-have** |
|--|------------------|----------------------|------------------|
| **Affects many** | P0 | P1 | P2 |
| **Affects few** | P1 | P2 | P3 |
| **Edge case** | P2 | P3 | P3 |

### State (Optional)

| Label | When to Use | Action |
|-------|-------------|--------|
| `state:duplicate` | Exact duplicate of another issue | Link to original, close |
| `state:wontfix` | Will not address (by design, out of scope) | Explain rationale, close |
| `state:invalid` | Not a valid issue (spam, unclear, off-topic) | Request clarification or close |

### Collaboration (Optional)

| Label | When to Use | Requirements |
|-------|-------------|--------------|
| `collab:good-first-issue` | Suitable for newcomers | Clear scope, good repro steps, low complexity |
| `collab:help-wanted` | Need expert help or extra hands | Describe needed expertise in comments |

### Domain (Optional)

| Label | Context |
|-------|---------|
| `domain:hackathon` | Related to hackathon events |
| `domain:evento` | Related to community events |
| `domain:codex` | AI/knowledge base integration |

### Automation (System Use Only)

| Label | Purpose |
|-------|---------|
| `automation:wos-review` | Triggers WOS delegation hook (auto-applied by CI) |

## 🎯 Common Triage Scenarios

### Scenario 1: Bug Report

**Issue**: "Application crashes when uploading files > 10MB"

**Labels**:
- `type:bug` (something is broken)
- `priority:P1` (major feature broken, affects many users)
- `domain:codex` (if related to file upload in codex module)

### Scenario 2: Feature Request

**Issue**: "Add export to PDF feature"

**Labels**:
- `type:feature` (new capability)
- `priority:P2` (nice to have, not blocking)
- `collab:help-wanted` (if implementation unclear or needs design input)

### Scenario 3: Good First Issue

**Issue**: "Fix typo in README.md line 42"

**Labels**:
- `type:docs` (documentation fix)
- `priority:P3` (low urgency)
- `collab:good-first-issue` (trivial, clear scope)

### Scenario 4: Duplicate

**Issue**: "Login broken on mobile" (already reported as #123)

**Labels**:
- `state:duplicate` (exact duplicate)
- *Comment*: "Duplicate of #123" and close

### Scenario 5: Critical Production Issue

**Issue**: "Database connection pool exhausted, all requests timing out"

**Labels**:
- `type:bug` (critical failure)
- `priority:P0` (production down)
- *Action*: Immediate assignment, escalate to on-call

## ❌ Deprecated Labels (DO NOT USE)

These labels are deprecated. Use canonical equivalents instead:

| Old Label | Use Instead |
|-----------|-------------|
| `bug`, `error` | `type:bug` |
| `enhancement`, `mejora` | `type:feature` |
| `documentation` | `type:docs` |
| `question`, `pregunta` | `type:question` |
| `duplicate` | `state:duplicate` |
| `wontfix`, `no solucionar` | `state:wontfix` |
| `invalid`, `no valido` | `state:invalid` |
| `good first issue`, `buen primer issue` | `collab:good-first-issue` |
| `help wanted`, `Se necesita ayuda` | `collab:help-wanted` |

## 🔍 Searching and Filtering

### GitHub Web UI

**Find high-priority bugs**:
```
is:issue is:open label:"type:bug" label:"priority:P1"
```

**Find good first issues**:
```
is:issue is:open label:"collab:good-first-issue"
```

**Find issues needing help**:
```
is:issue is:open label:"collab:help-wanted" no:assignee
```

### GitHub CLI

**List all open bugs by priority**:
```bash
gh issue list --label "type:bug" --state open --json number,title,labels
```

**Bulk relabel (maintainer only)**:
```bash
# Add canonical label to all issues with legacy label
gh issue list --label "bug" --limit 1000 --json number \
  | jq -r '.[].number' \
  | xargs -I {} gh issue edit {} --add-label "type:bug"
```

## 🛠️ Proposing New Labels

If you need a label not in the canonical taxonomy:

1. **Check** if an existing label fits (see full taxonomy in `LABEL_TAXONOMY.md`)
2. **Open issue** with justification
3. **Provide**:
   - Proposed name (format: `<category>:<value>`)
   - Description
   - Use case (why existing labels are insufficient)

## 📚 References

- **Full Taxonomy**: [LABEL_TAXONOMY.md](./LABEL_TAXONOMY.md)
- **Migration Plan**: [LABEL_MIGRATION_RUNBOOK.md](./LABEL_MIGRATION_RUNBOOK.md)
- **GitHub Docs**: https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work

---

**Questions?** Ask in Discussions or tag `@maintainers` in an issue.
