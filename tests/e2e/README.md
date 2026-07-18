# SDLC E2E Test Suite

Comprehensive end-to-end testing framework for the HomeDir AI SDLC pipeline using **real GitHub issues**.

## Overview

This test suite validates the complete autonomous SDLC workflow using existing real issues through production deployment, including:

1. **Admission Gateway** - Issue acceptance/rejection based on criteria
2. **Queue Management** - FIFO queue with global concurrency limits
3. **Worker Processing** - AI agent (SCC) execution
4. **PR Creation** - Automated pull request generation
5. **CI/CD Checks** - Build, test, and quality gates
6. **Auto-Merge** - Automatic merging when all requirements met
7. **Production Deployment** - Release workflow execution

## Two Ingestion Modes

### 1. Atomic Issues (Label-based)
**Use for**: Simple, self-contained tasks that can be completed in one PR.

**Flow**:
```
Issue created → ready-to-implement label → 
Admission evaluation → scc-accepted/scc-rejected →
scc-queued → scc-running → PR created → 
Checks → Auto-merge → Deployed → scc-merged
```

**Characteristics**:
- Single issue → single PR
- Evaluated by admission gate (complexity, atomicity, clarity)
- Direct queue admission if accepted
- Fast feedback loop (5-60 min typical)

**Test Example**:
```bash
./sdlc-e2e-orchestrator.sh simple
```

### 2. Orchestrator Mode (Sub-issue decomposition)
**Use for**: Complex initiatives that need to be broken down into smaller tasks.

**Flow**:
```
Epic issue created → ready-to-implement label →
Admission evaluation → Orchestrator triggered →
Sub-issues created (atomic tasks) →
Each sub-issue follows atomic flow →
Parent issue tracks progress →
Closes when all sub-issues merged
```

**Characteristics**:
- One epic → multiple sub-issues
- Automatic decomposition by AI
- Parallel execution of sub-issues
- Progressive completion tracking
- Longer duration (hours to days)

**Test Example**:
```bash
./sdlc-e2e-orchestrator.sh custom \
  --title "[e2e-test] Implement user authentication system" \
  --body "Create a complete authentication system with login, logout, session management, and password reset. This should be broken down into smaller atomic tasks."
```

## Admission Evaluation

All issues (atomic and orchestrator) are evaluated before entering the queue:

**Acceptance Criteria**:
- ✅ Clear, actionable requirements
- ✅ Appropriate scope (atomic) or eligible for decomposition (complex)
- ✅ No ambiguity in expected outcome
- ✅ Authorized labeler
- ✅ Technical feasibility

**Rejection Reasons**:
- ❌ Vague or incomplete requirements
- ❌ Too broad without clear decomposition path
- ❌ Requires human judgment or policy decisions
- ❌ Unauthorized labeler
- ❌ Technical impossibility

**Labels Applied**:
- `scc-accepted` - Passed admission, ready for queue
- `scc-rejected` - Failed admission with reason
- `scc-needs-decomposition` - Triggers orchestrator mode
- `needs-human` - Requires human intervention

## Test Suite Structure

### Scripts

#### 1. `sdlc-e2e-orchestrator.sh`
Main orchestrator that executes complete E2E test flows.

**Usage**:
```bash
# Run predefined test cases
./sdlc-e2e-orchestrator.sh simple    # 5-10 min expected
./sdlc-e2e-orchestrator.sh medium    # 15-30 min expected
./sdlc-e2e-orchestrator.sh complex   # 30-60 min expected

# Run custom test
./sdlc-e2e-orchestrator.sh custom \
  --title "Your test title" \
  --body "Detailed description of what to implement"
```

**Features**:
- Creates test issue automatically
- Triggers admission with `ready-to-implement` label
- Monitors all pipeline phases
- Records timing for each phase
- Validates successful completion
- Provides detailed summary

#### 2. `phase-validators.sh`
Individual validators for each pipeline phase.

**Usage**:
```bash
# Validate specific phase
./phase-validators.sh admission 1234
./phase-validators.sh queue 1234
./phase-validators.sh running 1234
./phase-validators.sh pr 1234
./phase-validators.sh checks 567
./phase-validators.sh merge 567
./phase-validators.sh automerge 567
./phase-validators.sh deployment 567

# Validate complete E2E
./phase-validators.sh e2e 1234

# Get current phase status
./phase-validators.sh status 1234
```

#### 3. `monitor-dashboard.sh`
Real-time SDLC dashboard monitoring during tests.

**Usage**:
```bash
# Watch dashboard
./monitor-dashboard.sh watch

# Watch with issue highlighting
./monitor-dashboard.sh watch 1234

# Watch with issue and PR highlighting
./monitor-dashboard.sh watch 1234 567

# Get JSON snapshot
./monitor-dashboard.sh snapshot

# Check API health
./monitor-dashboard.sh health

# Custom dashboard URL
./monitor-dashboard.sh --url https://sdlc.example.com watch
```

**Features**:
- Live worker status
- Pipeline stage summary with counts
- Active issues and PRs
- Metrics (autonomy, auto-merge, throughput)
- Anomaly detection
- Color-coded health indicators
- Auto-refresh (configurable)

#### 4. `test-suite.sh`
Comprehensive test suite runner.

**Usage**:
```bash
# Run smoke tests only
./test-suite.sh smoke

# Run quick tests (2 tests, ~10-15 min)
./test-suite.sh quick

# Run standard suite (4 tests, ~30-45 min)
./test-suite.sh standard

# Run full suite (6 tests, ~90-120 min)
./test-suite.sh full

# Run custom tests only
./test-suite.sh custom
```

## Test Cases

### Atomic Flow Tests

#### Simple: Documentation Update
**Expected Duration**: 5-10 minutes
**Description**: Add version badge to README.md

**Success Criteria**:
- ✅ Issue accepted within 60s
- ✅ Queued within 180s
- ✅ Worker claims within 180s
- ✅ PR created with correct changes
- ✅ All checks pass
- ✅ Auto-merged
- ✅ Deployed to production

#### Medium: Bug Fix with Tests
**Expected Duration**: 15-30 minutes
**Description**: Fix null pointer exception with error handling and tests

**Success Criteria**:
- Same as simple, plus:
- ✅ Test coverage included
- ✅ No regressions detected

#### Complex: Refactoring
**Expected Duration**: 30-60 minutes
**Description**: Refactor authentication middleware to async/await

**Success Criteria**:
- Same as medium, plus:
- ✅ Multiple files updated
- ✅ Comprehensive test updates
- ✅ No breaking changes

### Orchestrator Flow Tests

#### Epic: User Authentication System
**Expected Duration**: 2-6 hours
**Description**: Complete auth system broken into sub-issues

**Expected Sub-issues**:
1. Add login endpoint (atomic)
2. Add logout endpoint (atomic)
3. Implement session management (atomic)
4. Add password reset flow (atomic)
5. Add authentication tests (atomic)

**Success Criteria**:
- ✅ Epic triggers orchestrator mode
- ✅ Sub-issues created automatically
- ✅ Each sub-issue follows atomic flow
- ✅ Parent issue tracks completion
- ✅ Parent closes when all merged

### Rejection Tests

#### Invalid: Ambiguous Requirements
**Expected**: Rejected with `scc-rejected:ambiguous-requirements`

#### Invalid: Too Broad
**Expected**: Either rejected or triggers orchestrator decomposition

#### Invalid: Unauthorized Labeler
**Expected**: Rejected with `scc-rejected:unauthorized-labeler`

## Expected Performance Metrics

Based on `docs/en/development/ai-driven-sdlc-vision-and-implementation.md`:

| Metric | Target | Current Reality |
|--------|--------|----------------|
| Admission latency | < 5s | ✅ Met (~5s) |
| Worker wakeup (event-driven) | < 10s | ⚠️ Varies |
| Worker wakeup (polling fallback) | < 3min | ⚠️ Varies |
| Simple issue (doc update) | 2-5 min | 🔍 Testing |
| Medium issue (bug fix + tests) | 10-20 min | 🔍 Testing |
| Complex issue (refactor) | 30-60 min | 🔍 Testing |
| End-to-end success rate | > 90% | 🔍 Testing |

## Environment Variables

```bash
# Repository
export HOMEDIR_SDLC_REPO="os-santiago/homedir"

# Timeouts (seconds)
export E2E_TIMEOUT_ADMISSION=60
export E2E_TIMEOUT_QUEUE=180
export E2E_TIMEOUT_RUNNING=1800
export E2E_TIMEOUT_PR_CREATION=120
export E2E_TIMEOUT_CHECKS=600
export E2E_TIMEOUT_MERGE=300
export E2E_TIMEOUT_DEPLOYMENT=600

# Dashboard
export SDLC_DASHBOARD_URL="http://localhost:8080"
export E2E_DASHBOARD_REFRESH=5

# VPS (for advanced validation)
export SDLC_VPS_HOST="homedir-sdlc@72.60.141.165"
```

## Prerequisites

### Required Tools
- `gh` (GitHub CLI) - authenticated
- `jq` (JSON processor)
- `curl` (HTTP client)
- `bash` 4.0+

### Installation
```bash
# macOS
brew install gh jq

# Linux (Debian/Ubuntu)
apt-get install gh jq curl

# Authenticate GitHub CLI
gh auth login

# Make scripts executable
chmod +x tests/e2e/*.sh
```

### Access Requirements
- GitHub repository access (os-santiago/homedir)
- Permission to create issues and PRs
- Optional: SSH access to VPS for advanced validation

## Running Tests with Real Issues

### Important: Use Real Issues Only
This test suite operates on **actual GitHub issues** in the repository. Never create fake or test issues. Instead:

1. Find an existing eligible issue
2. Run the E2E test on that issue
3. Monitor its progress through the SDLC pipeline

### Quick Start
```bash
# 1. List eligible issues for testing
cd tests/e2e
./run-e2e-test.sh --list

# 2. Run E2E test on a real issue (e.g., #1234)
./run-e2e-test.sh 1234

# 3. Monitor in real-time (separate terminal)
./monitor-dashboard.sh watch 1234
```

### Standard Test Flow
```bash
# Terminal 1: Find and test an issue
./run-e2e-test.sh --list           # Find eligible issues
./run-e2e-test.sh 1234             # Run test on issue #1234

# Terminal 2: Monitor dashboard
./monitor-dashboard.sh watch 1234

# Terminal 3: Validate phases
./phase-validators.sh e2e 1234
```

### Debugging Failed Tests
```bash
# 1. Check phase status
./phase-validators.sh status 1234

# 2. Validate specific phase
./phase-validators.sh e2e 1234

# 3. Check dashboard for anomalies
./monitor-dashboard.sh snapshot | jq '.anomalies'

# 4. View issue details
gh issue view 1234 --comments

# 5. Check worker logs (if VPS access available)
ssh homedir-sdlc@72.60.141.165 \
  "tail -100 ~/.local/state/homedir-sdlc/logs/worker.log"
```

## Integration with CI/CD

### GitHub Actions Integration
```yaml
name: SDLC E2E Tests

on:
  schedule:
    - cron: '0 */6 * * *'  # Every 6 hours
  workflow_dispatch:
    inputs:
      suite:
        description: 'Test suite to run'
        required: true
        default: 'standard'
        type: choice
        options:
          - smoke
          - quick
          - standard
          - full

jobs:
  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y gh jq curl
      
      - name: Authenticate GitHub CLI
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | gh auth login --with-token
      
      - name: Run test suite
        run: |
          cd tests/e2e
          ./test-suite.sh ${{ inputs.suite || 'standard' }}
      
      - name: Upload results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-test-results
          path: tests/e2e/*.log
```

## Metrics Collection

The test suite automatically collects:
- Phase transition timings
- Total execution time
- Success/failure rates
- Anomaly counts
- Worker performance metrics

Access via dashboard API:
```bash
# Get metrics for last 7 days
curl -s http://localhost:8080/api/sdlc/metrics?days=7 | jq '.'

# Get current anomalies
curl -s http://localhost:8080/api/sdlc/anomalies | jq '.'

# Get pipeline summary
curl -s http://localhost:8080/api/sdlc/pipeline | jq '.'
```

## Troubleshooting

### Issue stuck in admission
```bash
# Check admission labels
./phase-validators.sh admission 1234

# Check for rejection reasons
gh issue view 1234 --json labels,comments
```

### Worker not processing
```bash
# Check worker status
./monitor-dashboard.sh health

# Check heartbeat age
curl -s http://localhost:8080/api/sdlc/heartbeat | jq '.ageSeconds'

# Check VPS worker service (if access available)
ssh homedir-sdlc@72.60.141.165 "systemctl --user status homedir-sdlc-worker.timer"
```

### PR checks failing
```bash
# Validate checks status
./phase-validators.sh checks 567

# View check details
gh pr checks 567

# Worker should remediate automatically - monitor for scc-under-review label
```

### Auto-merge not triggering
```bash
# Check merge eligibility
./phase-validators.sh merge 567

# Check auto-merge status
./phase-validators.sh automerge 567

# Verify branch protection rules allow auto-merge
```

## Contributing

### Adding New Test Cases
1. Add test function to `test-suite.sh`
2. Use orchestrator or custom flow
3. Define success criteria
4. Update this README

### Extending Validators
1. Add validation function to `phase-validators.sh`
2. Follow existing patterns
3. Return proper exit codes (0=success, 1=failure, 2=pending)

### Improving Dashboard
1. Enhance `monitor-dashboard.sh` rendering
2. Add new metrics or visualizations
3. Maintain color coding standards

## References

- [AI-Driven SDLC Vision](../../docs/en/development/ai-driven-sdlc-vision-and-implementation.md)
- [Autonomous SDLC Docs](../../docs/en/development/autonomous-sdlc.md)
- [Pipeline Orchestrator](../../platform/scripts/PIPELINE-ORCHESTRATOR.md)
- [Worker Script](../../platform/scripts/homedir-sdlc-worker.sh)
- [SDLC Dashboard](../../quarkus-app/src/main/java/com/scanales/homedir/sdlc/)

## License

Same as parent project (HomeDir).
