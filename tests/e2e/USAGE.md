## Quick Usage Guide - SDLC E2E Tests

### Prerequisites
```bash
# Install dependencies
brew install gh jq  # macOS
# or
apt-get install gh jq  # Linux

# Authenticate GitHub CLI
gh auth login

# Make scripts executable
chmod +x tests/e2e/*.sh
```

### Step 1: Find a Real Issue to Test

```bash
cd tests/e2e

# List all eligible issues (open, not already in SDLC flow)
./run-e2e-test.sh --list
```

**Example output:**
```
Eligible issues (open, not in SDLC flow):

  #1256   Fix null pointer in user profile endpoint
  #1257   Add version badge to README
  #1258   Refactor authentication middleware
```

### Step 2: Run E2E Test on Selected Issue

```bash
# Run test on issue #1257
./run-e2e-test.sh 1257
```

This will:
1. ✅ Show issue information
2. ✅ Validate issue eligibility
3. ✅ Add `ready-to-implement` label (triggers admission)
4. ✅ Monitor through all phases:
   - Admission review → `scc-accepted`
   - Queue admission → `scc-queued`
   - Worker processing → `scc-running`
   - PR creation → `scc-pr-open`
   - CI checks → `scc-waiting-checks`
   - Auto-merge → PR merged
   - Production deployment → `scc-merged`
5. ✅ Show final summary with result

### Step 3: Monitor in Real-Time (Optional)

Open a second terminal:

```bash
cd tests/e2e

# Watch dashboard with issue highlighting
./monitor-dashboard.sh watch 1257
```

This shows live:
- Worker status and heartbeat
- Pipeline stages with counts
- Active issues and PRs
- Metrics (autonomy, throughput)
- Anomalies

### Step 4: Validate Specific Phases (Optional)

```bash
# Check current status
./phase-validators.sh status 1257

# Validate specific phase
./phase-validators.sh admission 1257
./phase-validators.sh running 1257
./phase-validators.sh pr 1257

# Validate complete E2E flow
./phase-validators.sh e2e 1257
```

## Common Scenarios

### Test a Simple Issue (Expected: 5-10 min)
```bash
# Find a simple doc update or minor fix
./run-e2e-test.sh --list | grep -i "readme\|typo\|doc"

# Run test
./run-e2e-test.sh 1257
```

### Test a Medium Issue (Expected: 15-30 min)
```bash
# Find a bug fix with tests
./run-e2e-test.sh --list | grep -i "bug\|fix\|error"

# Run test
./run-e2e-test.sh 1258
```

### Test with Custom Timeout
```bash
# Complex refactoring - give it more time
./run-e2e-test.sh --timeout 3600 1259  # 60 minutes
```

### Watch Multiple Issues
```bash
# Terminal 1: Test issue #1257
./run-e2e-test.sh 1257

# Terminal 2: Test issue #1258
./run-e2e-test.sh 1258

# Terminal 3: Monitor dashboard (highlights both)
./monitor-dashboard.sh watch
```

## Troubleshooting

### Issue stuck in admission
```bash
# Check labels
gh issue view 1257 --json labels

# Should show: scc-accepted or scc-rejected
# If stuck, check admission gateway logs
```

### Worker not processing
```bash
# Check dashboard
./monitor-dashboard.sh health

# Check worker status
curl -s http://localhost:8080/api/sdlc/heartbeat | jq '.'
```

### PR checks failing
```bash
# Worker should auto-remediate
# Monitor for scc-under-review label
./phase-validators.sh checks <PR_NUMBER>
```

### Need to abort test
```bash
# Remove ready-to-implement label
gh issue edit 1257 --remove-label "ready-to-implement"

# Or mark as needs-human
gh issue edit 1257 --add-label "needs-human"
```

## Expected Timings

Based on issue complexity:

| Type | Expected Duration | Example |
|------|------------------|---------|
| Simple | 5-10 min | Add console.log, fix typo |
| Medium | 15-30 min | Bug fix with tests |
| Complex | 30-60 min | Refactor multiple files |

## What to Watch For

### Success Indicators ✅
- Admission accepted within 60s
- Queue admission within 3 min
- Worker claims within 3 min
- PR created with correct changes
- Checks pass (or auto-remediated)
- Auto-merge enabled
- PR merged automatically
- Issue closed with `scc-merged`

### Warning Signs ⚠️
- Admission > 60s
- Queue admission > 5 min
- Worker not claiming
- PR not created after 30 min
- Checks failing repeatedly
- Auto-merge not enabled

### Failure States ❌
- `scc-rejected` - Issue rejected
- `scc-failed` - Worker failed
- `needs-human` - Requires intervention

## Dashboard Access

### Local Development
```bash
# Start Quarkus app
cd quarkus-app
./mvnw quarkus:dev

# Access dashboard
open http://localhost:8080/sdlc/dashboard
```

### Production VPS
```
https://homedir.example.com/sdlc/dashboard
```

## Quick Reference

```bash
# List eligible issues
./run-e2e-test.sh --list

# Run E2E test
./run-e2e-test.sh <issue_number>

# Monitor dashboard
./monitor-dashboard.sh watch [issue_number]

# Validate phase
./phase-validators.sh <phase> <issue|pr>

# Check status
./phase-validators.sh status <issue>
```

## Need Help?

1. Check the full README: `./README.md`
2. View phase validator help: `./phase-validators.sh --help`
3. View monitor help: `./monitor-dashboard.sh --help`
4. Check worker logs (if VPS access):
   ```bash
   ssh homedir-sdlc@72.60.141.165 \
     "tail -100 ~/.local/state/homedir-sdlc/logs/worker.log"
   ```
