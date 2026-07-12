# Pipeline Orchestrator - Documentation

## Overview

The Pipeline Orchestrator automatically creates and queues the next issue in a pipeline sequence when the current issue completes. This enables autonomous multi-step workflows without manual intervention.

**File**: `platform/scripts/pipeline-orchestrator.sh`

## How It Works

```
Issue #1 closes → Orchestrator detects completion → Reads pipeline YAML → Creates Issue #2 → Auto-queues Issue #2
```

### Workflow Integration

1. **Issue Closure**: When worker closes an issue via `finalize_merged_issue()`
2. **Orchestrator Trigger**: Worker calls `pipeline-orchestrator.sh <issue_number>`
3. **Pipeline Lookup**: Orchestrator searches `.github/pipelines/*.yaml` for matching pipeline
4. **Next Issue Creation**: Orchestrator creates next issue in sequence
5. **Auto-Queue**: New issue gets `scc-accepted,scc-queued` labels for immediate processing

## Pipeline YAML Format

### Basic Structure

```yaml
name: Pipeline Name
description: Brief description of this pipeline
repository: os-santiago/homedir

issues:
  - id: step-1
    title: "[category] Issue title"
    body: |
      ## Problem Statement
      Description of what needs to be done
      
      ## Acceptance Criteria
      - [ ] Criterion 1
      - [ ] Criterion 2
      
      ## Complexity
      - [x] Simple (1 file, <10 lines)
    labels:
      - documentation
      - ready-to-implement
  
  - id: step-2
    title: "[category] Next issue title"
    body: |
      ## Problem Statement
      Description of step 2
      
      ## Acceptance Criteria
      - [ ] Criterion 1
    labels:
      - documentation
      - ready-to-implement
    depends_on:
      - step-1
```

### Required Fields

#### Pipeline Level
- `name`: Pipeline name (for logging/identification)
- `description`: Brief description of pipeline purpose
- `repository`: Target repository (e.g., `os-santiago/homedir`)
- `issues`: Array of issue definitions

#### Issue Level
- `id`: Unique identifier within pipeline (used for `depends_on`)
- `title`: Issue title (will be used in `gh issue create`)
- `body`: Issue body in markdown format (use `|` for multi-line)
- `labels`: Array of label names

### Optional Fields

- `depends_on`: Array of issue `id`s that must complete before this issue

## Label Handling

### Auto-Creation (Fix #1141)

The orchestrator automatically creates missing labels:

```bash
# Before creating issue, orchestrator validates labels
validate_and_ensure_labels() {
  # 1. Check if label exists
  # 2. If not, create with default color (FBCA04)
  # 3. If creation fails, skip label with warning
  # 4. Fallback to 'ready-to-implement' if all fail
}
```

### Label Best Practices

**Standard Labels** (always exist):
- `ready-to-implement` ✅
- `documentation` ✅
- `bug` ✅
- `enhancement` ✅

**Custom Labels** (auto-created if missing):
- Pipeline-specific labels
- Category labels
- Priority labels

**Example**:
```yaml
labels:
  - documentation          # Standard - always exists
  - ready-to-implement     # Standard - always exists
  - pipeline-automation    # Custom - will be auto-created
```

### Logging

Orchestrator logs all label operations:

```
[2026-07-12T20:00:00Z] [pipeline-orchestrator] labels (raw): documentation,ready-to-implement,custom-label
[2026-07-12T20:00:01Z] [pipeline-orchestrator] label exists: documentation
[2026-07-12T20:00:01Z] [pipeline-orchestrator] label exists: ready-to-implement
[2026-07-12T20:00:02Z] [pipeline-orchestrator] label does not exist: custom-label, attempting to create...
[2026-07-12T20:00:03Z] [pipeline-orchestrator] created label: custom-label
[2026-07-12T20:00:03Z] [pipeline-orchestrator] labels (validated): documentation,ready-to-implement,custom-label
```

## Pipeline Files Location

**Directory**: `.github/pipelines/`

**Naming Convention**: `<pipeline-name>.yaml`

**Examples**:
- `readme-docs.yaml` - Documentation pipeline
- `e2e-autonomy-test.yaml` - E2E test pipeline
- `test-label-validation.yaml` - Test pipeline

## Creating a New Pipeline

### Step 1: Create YAML File

Create `.github/pipelines/my-pipeline.yaml`:

```yaml
name: My Feature Pipeline
description: Multi-step implementation of feature X
repository: os-santiago/homedir

issues:
  - id: setup
    title: "[feature-x] Setup infrastructure"
    body: |
      ## Problem Statement
      Setup required infrastructure for feature X
      
      ## Acceptance Criteria
      - [ ] Infrastructure provisioned
      - [ ] Configuration files created
    labels:
      - enhancement
      - ready-to-implement
  
  - id: implementation
    title: "[feature-x] Implement core logic"
    body: |
      ## Problem Statement
      Implement core feature X logic
      
      ## Acceptance Criteria
      - [ ] Core logic implemented
      - [ ] Unit tests passing
    labels:
      - enhancement
      - ready-to-implement
    depends_on:
      - setup
  
  - id: documentation
    title: "[feature-x] Add documentation"
    body: |
      ## Problem Statement
      Document feature X
      
      ## Acceptance Criteria
      - [ ] README updated
      - [ ] API docs added
    labels:
      - documentation
      - ready-to-implement
    depends_on:
      - implementation
```

### Step 2: Create First Issue Manually

```bash
gh issue create --repo os-santiago/homedir \
  --title "[feature-x] Setup infrastructure" \
  --label "enhancement,ready-to-implement" \
  --body "$(cat <<'EOF'
## Problem Statement
Setup required infrastructure for feature X

## Acceptance Criteria
- [ ] Infrastructure provisioned
- [ ] Configuration files created
EOF
)"
```

### Step 3: Let Automation Run

1. Worker processes issue #1
2. PR created, CI passes, auto-merges
3. Issue #1 closes
4. Orchestrator detects closure
5. Orchestrator creates issue #2 automatically
6. Issue #2 queued with `scc-accepted,scc-queued`
7. Repeat until all issues complete

## Pipeline Discovery

### How Orchestrator Finds Pipelines

The orchestrator searches for the completed issue in all pipeline YAMLs:

```bash
find_pipeline_for_issue() {
  # Search all .github/pipelines/*.yaml files
  # Match by:
  #   1. Issue number in metadata (if present)
  #   2. Issue title match in YAML
}
```

### Title Matching

Orchestrator matches completed issue title against pipeline YAML:

```bash
# If completed issue title is:
"[docs] Add license badge to README"

# Orchestrator searches for exact match in pipeline YAML:
title: "[docs] Add license badge to README"
```

**Important**: Titles must match EXACTLY (case-sensitive)

## Dependencies

### Sequential Execution

Use `depends_on` for sequential steps:

```yaml
issues:
  - id: step-1
    # ...
  
  - id: step-2
    depends_on:
      - step-1  # Step 2 waits for step 1
  
  - id: step-3
    depends_on:
      - step-2  # Step 3 waits for step 2
```

### Parallel Execution

Omit `depends_on` for parallel steps:

```yaml
issues:
  - id: task-a
    # ...
  
  - id: task-b
    # No depends_on - can run in parallel
  
  - id: task-c
    # No depends_on - can run in parallel
  
  - id: final
    depends_on:
      - task-a
      - task-b
      - task-c  # Waits for all parallel tasks
```

## Error Handling

### Missing Label

**Before Fix #1141**:
```
Error: could not add label: 'custom-label' not found
Pipeline stalled ❌
```

**After Fix #1141**:
```
Label 'custom-label' not found
Auto-creating with default color...
Label 'custom-label' created ✅
Issue created successfully ✅
```

### Missing Pipeline

If no pipeline found for completed issue:

```bash
log "no pipeline found for issue #${completed_issue}"
# Orchestrator exits gracefully (no error)
```

### Pipeline Complete

If completed issue is last in pipeline:

```bash
log "no next issue in pipeline (pipeline complete)"
# Orchestrator exits gracefully
```

## Examples

### Example 1: Documentation Pipeline

**File**: `.github/pipelines/readme-badges.yaml`

```yaml
name: README Badges Pipeline
description: Add all standard badges to README
repository: os-santiago/homedir

issues:
  - id: license
    title: "[docs] Add license badge to README"
    body: |
      ## Acceptance Criteria
      - [ ] Add Apache-2.0 license badge
    labels:
      - documentation
      - ready-to-implement
  
  - id: build
    title: "[docs] Add build status badge to README"
    body: |
      ## Acceptance Criteria
      - [ ] Add GitHub Actions workflow status badge
    labels:
      - documentation
      - ready-to-implement
    depends_on:
      - license
  
  - id: coverage
    title: "[docs] Add code coverage badge to README"
    body: |
      ## Acceptance Criteria
      - [ ] Add code coverage badge
    labels:
      - documentation
      - ready-to-implement
    depends_on:
      - build
```

### Example 2: Feature Implementation

**File**: `.github/pipelines/user-auth.yaml`

```yaml
name: User Authentication Feature
description: Complete user authentication implementation
repository: os-santiago/homedir

issues:
  - id: backend
    title: "[auth] Implement backend authentication"
    body: |
      ## Acceptance Criteria
      - [ ] JWT token generation
      - [ ] Login/logout endpoints
      - [ ] Unit tests passing
    labels:
      - enhancement
      - ready-to-implement
      - backend
  
  - id: frontend
    title: "[auth] Implement frontend authentication"
    body: |
      ## Acceptance Criteria
      - [ ] Login form component
      - [ ] Auth state management
      - [ ] Protected routes
    labels:
      - enhancement
      - ready-to-implement
      - frontend
    depends_on:
      - backend
  
  - id: e2e-tests
    title: "[auth] Add E2E authentication tests"
    body: |
      ## Acceptance Criteria
      - [ ] Login flow test
      - [ ] Protected route test
      - [ ] Logout test
    labels:
      - testing
      - ready-to-implement
    depends_on:
      - frontend
```

## Troubleshooting

### Issue Not Auto-Created

**Check**:
1. Pipeline YAML exists in `.github/pipelines/`
2. Completed issue title matches pipeline YAML exactly
3. Check worker logs for orchestrator errors
4. Verify `depends_on` dependencies are satisfied

**Debug**:
```bash
# Test orchestrator manually
./platform/scripts/pipeline-orchestrator.sh <completed_issue_number>

# Check logs
tail -f /var/lib/homedir-sdlc/logs/worker.log | grep orchestrator
```

### Label Creation Failed

**Symptoms**:
```
WARNING: could not create label 'custom-label', skipping
```

**Causes**:
- Label name too long (>50 chars)
- Invalid characters in label name
- GitHub API rate limit

**Resolution**:
- Use shorter label names
- Use alphanumeric + dash/underscore only
- Wait for rate limit reset

### Pipeline Not Found

**Symptoms**:
```
no pipeline found for issue #1234
```

**Causes**:
- Title mismatch (case-sensitive)
- YAML file not in `.github/pipelines/`
- YAML syntax error

**Resolution**:
1. Verify title matches exactly
2. Check YAML is valid (`yamllint .github/pipelines/my-pipeline.yaml`)
3. Ensure file is committed to repository

## Best Practices

### 1. Atomic Issues

Each pipeline issue should be:
- Self-contained (1-2 acceptance criteria)
- Completable in 1 PR
- Simple enough for autonomous implementation

### 2. Clear Dependencies

Use `depends_on` only when truly sequential:
- ✅ Backend → Frontend → Tests
- ❌ Unnecessary sequential constraints

### 3. Label Strategy

**Use standard labels when possible**:
```yaml
labels:
  - documentation        # Standard
  - ready-to-implement  # Standard
```

**Custom labels for categorization**:
```yaml
labels:
  - documentation
  - ready-to-implement
  - pipeline:feature-x  # Custom category
```

### 4. Title Conventions

**Format**: `[category] Action description`

**Examples**:
- `[docs] Add license badge to README`
- `[feat] Implement user authentication`
- `[test] Add E2E tests for checkout flow`

### 5. Testing Pipelines

Create test pipelines before production use:
1. Small pipeline (2-3 issues)
2. Simple issues (documentation updates)
3. Verify auto-creation works
4. Clean up test issues/labels

## Validation & Testing

### Test Pipeline Template

**File**: `.github/pipelines/test-pipeline.yaml`

```yaml
name: Test Pipeline
description: Validate orchestrator functionality
repository: os-santiago/homedir

issues:
  - id: test-1
    title: "[test] First test issue"
    body: |
      ## Test Issue
      Simple test for pipeline orchestrator
      
      ## Acceptance Criteria
      - [ ] Issue created successfully
    labels:
      - documentation
      - ready-to-implement
      - test-pipeline
  
  - id: test-2
    title: "[test] Second test issue"
    body: |
      ## Test Issue
      Validates auto-creation after test-1
      
      ## Acceptance Criteria
      - [ ] Auto-created by orchestrator
    labels:
      - documentation
      - ready-to-implement
      - test-pipeline
    depends_on:
      - test-1
```

### Validation Steps

1. Create first issue manually
2. Verify worker processes and closes it
3. Check orchestrator logs for creation
4. Verify second issue created automatically
5. Confirm labels auto-created if missing
6. Clean up test issues

## Integration with AI SDLC

### Worker Integration

Orchestrator is called automatically by worker:

**File**: `platform/scripts/homedir-sdlc-worker.sh`

```bash
# After closing issue (line ~1470)
gh issue close "${issue}" -R "${OWNER}/${REPO}" -c "${msg}"

# Call pipeline orchestrator
if [[ -x "${HOME}/platform/scripts/pipeline-orchestrator.sh" ]]; then
  log "calling pipeline orchestrator for issue ${issue}"
  "${HOME}/platform/scripts/pipeline-orchestrator.sh" "${issue}" || true
fi
```

### Label Flow

```
Manual Issue Creation → ready-to-implement
   ↓
Worker Admission → scc-accepted
   ↓
Worker Queue → scc-queued
   ↓
Worker Execution → scc-running
   ↓
PR Creation → scc-pr-open
   ↓
CI Pass → scc-approved
   ↓
Auto-Merge → scc-merged
   ↓
Issue Closure → Orchestrator Triggered
   ↓
Next Issue Created → scc-accepted,scc-queued (auto)
   ↓
Repeat...
```

## Metrics & Monitoring

### Success Metrics

- **Pipeline Completion Rate**: % of pipelines that complete all issues
- **Auto-Creation Success**: % of next issues created without error
- **Label Auto-Creation Rate**: % of labels created vs already existing
- **Time to Next Issue**: Latency from issue close to next issue created

### Monitoring

```bash
# Check orchestrator execution count
grep "calling pipeline orchestrator" /var/lib/homedir-sdlc/logs/worker.log | wc -l

# Check successful creations
grep "created and queued issue" /var/lib/homedir-sdlc/logs/worker.log

# Check label auto-creations
grep "created label:" /var/lib/homedir-sdlc/logs/worker.log
```

## Changelog

### v1.1.0 (2026-07-12) - Fix #1141
- ✅ Auto-create missing labels
- ✅ Fallback to ready-to-implement if all labels fail
- ✅ Comprehensive logging for label operations

### v1.0.0 (Initial)
- ✅ Pipeline YAML parsing
- ✅ Sequential issue creation
- ✅ Dependency resolution
- ✅ Auto-queue created issues

---

**Last Updated**: 2026-07-12  
**Maintainer**: AI SDLC Team  
**Related**: Issue #1141, PR #1231
