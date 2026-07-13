# Queue and Pipeline Strategy

## Overview

The AI SDLC system uses two complementary mechanisms for issue processing:

1. **Individual Queue Processing** (95% of cases) - FIFO queue for independent issues
2. **Pipeline Orchestration** (5% of cases) - Sequential processing for complex multi-step work

---

## Individual Queue Processing (Default)

### Purpose
Handle independent issues that don't require specific execution order.

### Behavior
- **Queue**: `scc-queued` label marks issues ready for processing
- **Ordering**: FIFO (First-In-First-Out) by creation date - **oldest issue processed first**
- **Concurrency**: **ONE issue at a time globally** to prevent resource contention
- **Automatic**: Issues advance through queue as they complete

### Flow
```
Issue created → Admission review → Auto-label ready-to-implement → 
Auto-queue (scc-queued) → Worker picks OLDEST issue → Process → Complete → 
Pick next OLDEST issue
```

### Use Cases
- ✅ Bug fixes (independent corrections)
- ✅ Documentation updates
- ✅ Dependency updates
- ✅ Feature additions (that don't depend on other work)
- ✅ Refactorings (that are self-contained)

### Example
```bash
# Backlog (sorted by createdAt):
#1098 [2026-07-09] "Add license badge" ← NEXT (oldest)
#1107 [2026-07-10] "Fix typo in docs"
#1109 [2026-07-10] "Update dependencies"
#1114 [2026-07-10] "Add contributing guide"

# Worker processes #1098 first (oldest), then #1107, etc.
```

---

## Pipeline Orchestration (Special Cases)

### Purpose
Handle complex issues that MUST be divided into sequential dependent steps.

### When to Use Pipelines
- ❌ **NOT for independent tasks** (use individual queue instead)
- ✅ **ONLY when**:
  - Issue is too complex to solve atomically
  - Steps have technical dependencies (step N requires step N-1)
  - Order of execution is critical for correctness

### How It Works

#### 1. Manual Pipeline Creation
Complex issue identified → Create `.github/pipelines/<name>.yaml`:

```yaml
name: Database Migration Pipeline
description: Multi-step schema migration requiring specific order
repository: os-santiago/homedir

issues:
  - id: add-column
    title: "Add new_column to users table"
    body: |
      ## Problem Statement
      Schema needs new_column for upcoming feature
      
      ## Acceptance Criteria
      - [ ] Add migration to create new_column
      - [ ] Run migration in dev
    labels:
      - database
      - migration
      - ready-to-implement
    
  - id: migrate-data
    title: "Migrate existing data to new_column"
    body: |
      ## Problem Statement
      Existing rows need default values in new_column
      
      ## Acceptance Criteria
      - [ ] Backfill new_column for all existing rows
    labels:
      - database
      - migration
      - ready-to-implement
    depends_on:
      - add-column  # ← BLOCKS until step 1 completes
    
  - id: remove-old-column
    title: "Remove old_column from users table"
    body: |
      ## Problem Statement
      old_column is now redundant after migration
      
      ## Acceptance Criteria
      - [ ] Create migration to drop old_column
      - [ ] Run migration in dev
    labels:
      - database
      - migration
      - ready-to-implement
    depends_on:
      - migrate-data  # ← BLOCKS until step 2 completes
```

#### 2. Orchestrator Execution

```
Step 1 completes (PR merged, issue closed) →
  Orchestrator detects pipeline YAML →
  Creates Step 2 issue →
  Auto-queues with scc-accepted + scc-queued →
  Worker processes Step 2 →
  Step 2 completes →
  Orchestrator creates Step 3 →
  ...
```

**Key Point**: Issues are **CREATED sequentially**, not queued in advance.

### Use Cases

#### ✅ Database Migrations
```
1. Add column
2. Migrate data
3. Remove old column
```
**Why pipeline**: Wrong order = data loss

#### ✅ API Refactoring
```
1. Add new endpoint with versioning
2. Migrate frontend to new endpoint
3. Deprecate old endpoint
4. Remove old endpoint
```
**Why pipeline**: Step 2 before step 1 = broken app

#### ✅ Feature Rollout
```
1. Add feature flag
2. Implement feature (disabled)
3. Add tests
4. Enable in dev
5. Enable in prod
```
**Why pipeline**: Control rollout safety

#### ❌ NOT for This (Use Individual Queue)
```
- "Add 3 documentation sections"
  → 3 independent issues, process in parallel
  
- "Fix 5 typos in different files"
  → 5 independent issues, FIFO queue is fine
  
- "Update dependencies: React, Vue, Angular"
  → 3 independent issues, order doesn't matter
```

---

## Implementation Details

### Worker Queue Processing

**File**: `platform/scripts/homedir-sdlc-worker.sh` (lines 2093-2116)

```bash
# Fetch all queued issues
gh issue list --label "scc-queued" --json number,title,createdAt

# Sort by createdAt (oldest first)
sorted_issues="$(jq 'sort_by(.createdAt) | .[0:1]')"  # Take ONLY 1 issue

# Process the oldest issue
run_issue "${issue_json}"
```

**Key Configuration**:
- `.[0:1]` ensures **only 1 issue processes at a time**
- `sort_by(.createdAt)` ensures **oldest issue goes first** (FIFO)

### Pipeline Orchestrator

**File**: `platform/scripts/pipeline-orchestrator.sh`

**Trigger**: Called by worker after issue closes (line 1662-1668)

```bash
finalize_merged_issue() {
  # ... close issue ...
  
  # Check if issue is part of a pipeline
  "${orchestrator_script}" "${issue_number}"
}
```

**Logic**:
1. Search `.github/pipelines/*.yaml` for closed issue title
2. If match found → parse YAML → find next issue in sequence
3. Create next issue with `gh issue create`
4. Auto-queue: `gh issue edit --add-label "scc-accepted,scc-queued"`
5. Worker picks it up on next cycle (3 min timer)

### Label Auto-Creation

**Fix**: PR #1231 (merged)

**Function**: `validate_and_ensure_labels()` in orchestrator

**Behavior**: Pipeline YAML can reference non-existent labels; orchestrator auto-creates them with default color `FBCA04` before issue creation.

---

## Queue Management

### Admission Flow

```
Issue created →
  Webhook event: issue-opened →
  Admission review (Python validation) →
  Status: accepted/needs-human/rejected →
  
  IF accepted:
    add_label "scc-accepted"
    User adds "ready-to-implement" →
    Worker admits to queue →
    add_label "scc-queued"
```

### State Transitions

```
scc-queued → scc-running → scc-pr-open → scc-waiting-checks → 
scc-approved → scc-merged → Issue closed
```

**Terminal States**:
- `scc-failed` - Autonomous processing failed
- `needs-human` - Requires manual intervention
- `scc-rejected` - Admission denied

### Concurrency Control

**Global Limit**: 1 issue at a time

**Why**: 
- Prevents resource exhaustion (SCC agent timeout)
- Avoids merge conflicts (multiple PRs touching same files)
- Simplifies monitoring and debugging
- Ensures predictable queue progression

**Implementation**: `.[0:1]` array slice in worker main loop

---

## Migration from Backlog

### Current State
~200 existing issues in various states:
- Some have `scc-accepted`
- Some have `ready-to-implement`
- Some are in terminal states
- Most are independent (don't need pipelines)

### Migration Strategy

#### 1. Process Existing Issues Individually
```bash
# Issues already marked scc-accepted will be picked up by worker
# in FIFO order (oldest first)

# No pipeline YAML needed - they're independent
```

#### 2. Identify Complex Issues for Pipelines
```bash
# Manually review backlog
# Find issues that should be split into sequential steps
# Create pipeline YAML for those specific cases
```

#### 3. Let Queue Drain Naturally
```bash
# Worker processes 1 issue every ~10-20 minutes
# ~200 issues = ~2000-4000 minutes = ~1.5-3 days
# Asynchronous, autonomous, no intervention needed
```

---

## Monitoring

### Queue Status
```bash
# Check queued issues (oldest first)
gh issue list --repo os-santiago/homedir \
  --label "scc-queued" \
  --state open \
  --json number,title,createdAt \
  | jq 'sort_by(.createdAt)'
```

### Currently Running
```bash
# Check what's being processed
gh issue list --repo os-santiago/homedir \
  --label "scc-running" \
  --state open
```

### Pipeline Status
```bash
# List all pipelines
ls .github/pipelines/*.yaml

# Check pipeline for specific issue
grep -l "Issue Title" .github/pipelines/*.yaml
```

### Worker Health
```bash
# Check worker logs on VPS
ssh homedir-sdlc@vps
tail -f ~/.local/state/homedir-sdlc/logs/worker.log

# Check heartbeat
cat ~/.local/state/homedir-sdlc/heartbeat.json
```

---

## Best Practices

### ✅ DO Use Individual Queue For
- Independent bug fixes
- Documentation updates
- Simple feature additions
- Dependency updates
- Refactorings without dependencies

### ✅ DO Use Pipelines For
- Database migrations
- Multi-step refactorings with dependencies
- Coordinated rollouts
- Breaking changes requiring specific order

### ❌ DON'T Use Pipelines For
- "Batch" work that's actually independent (split into separate issues instead)
- Issues that CAN run in parallel (FIFO queue is faster)
- Simple multi-criterion issues (split into atomic issues instead)

### ❌ DON'T Create Artificial Issues
- **NEVER** create test/fake issues for validation
- **ALWAYS** use real issues from backlog
- **Exception**: User explicitly requests creating specific issue

---

## FAQ

### Q: How fast does the queue process?
**A**: ~1 issue per 10-20 minutes (depends on complexity, CI time, review time)

### Q: Can I process multiple issues in parallel?
**A**: No - by design, 1 issue at a time globally to prevent conflicts and resource exhaustion

### Q: What if I have 50 independent issues?
**A**: Perfect for FIFO queue - they'll process sequentially, oldest first, autonomously

### Q: When should I create a pipeline YAML?
**A**: Only when steps MUST execute in specific order (e.g., migrations, coordinated refactors)

### Q: Can I prioritize an issue?
**A**: Currently no - queue is strict FIFO by creation date. Workaround: close/reopen to reset createdAt (not recommended)

### Q: What if worker crashes mid-processing?
**A**: Reconciliation loop detects orphaned PRs and resumes work on next cycle

### Q: How do I know if an issue is stuck?
**A**: Check for `needs-human` label or issues with `scc-running` for >1 hour

---

## Related Documentation

- [Autonomous SDLC Overview](autonomous-sdlc.md)
- [Pipeline Orchestrator](../../../PIPELINE-ORCHESTRATOR.md)
- [Worker Architecture](worker-architecture.md)
- [Admission Review](admission-review.md)

---

**Last Updated**: 2026-07-12  
**Status**: Active  
**Version**: 1.0
