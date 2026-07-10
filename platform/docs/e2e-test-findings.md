# E2E Testing Findings - ADEV SDLC Optimizations

**Date**: 2026-07-09
**Context**: Testing ADEV optimizations deployed in PR #1097

## Test Issues Created

### Issue #1098: [test-e2e] Simple doc update test
- **Status**: `scc-failed` 
- **Failure reason**: NVIDIA NIM API degraded (400 error)
- **Validation**: ✅ ADEV timeout classification detected: "Issue complexity: simple, timeout: 300s"
- **Log evidence**: Worker log line 14 shows correct classification

### Issue #1099: [test-e2e-2] Add version badge to README
- **Status**: `scc-queued` but not processed
- **Labels**: `ready-to-implement`, `scc-accepted`, `scc-queued`
- **Expected**: Worker should pick up and process
- **Actual**: Worker not processing after 4+ minutes (expected: 3-minute cycles)

## ADEV Features Validation Status

| Feature | Status | Evidence |
|---------|--------|----------|
| Dynamic timeout classification | ✅ Working | Log: "Issue complexity: simple, timeout: 300s" |
| Admission gate (atomicity check) | ✅ Working | Issue #1099 passed (1 criterion) |
| scc-accepted workflow | ✅ Working | Both issues got accepted |
| scc-queued admission | ⚠️ Manual | Had to add label manually for #1099 |
| Worker issue processing | ❌ Blocked | Worker not picking up queued issues |
| Scoped validation | ⏸️ Not tested | Requires successful SCC execution |
| ADEV prompt instructions | ⏸️ Not tested | Requires successful SCC execution |

## Root Cause Analysis

### Issue #1098 Failure
- **External dependency**: NVIDIA NIM API degraded
- **Not a code issue**: Worker and classification logic worked correctly
- **Evidence**: API returned 400 with "DEGRADED function cannot be invoked"

### Issue #1099 Not Processing
Multiple potential causes:

1. **Worker state**: Issue #1047 was stuck in `scc-running` state
   - Released by changing to `needs-human`
   - May have caused single-issue worker lock

2. **Admission reconciliation timing**: 
   - Issue got `scc-accepted` from GitHub Actions workflow
   - Worker's `reconcile_admission_requests()` may have already processed it in prior cycle
   - Manual `scc-queued` label added to force queue admission
   - Still not processed after 4 minutes (should be 3-minute cycles)

3. **Worker may be stopped or degraded**:
   - Cannot verify via SSH (key authentication issues)
   - GitHub Actions showing activity (workflow runs completing)
   - But no SDLC worker activity visible

## SCC CLI Optimizations

✅ **Successfully implemented and committed**:

1. **Context loading optimization** (commit a8e4654)
   - Conditional loading based on query keywords
   - Skip for conversational queries
   - Impact: 12KB+ saved on "Hello" queries

2. **Self-heal optimization** (commit a8e4654)
   - Skip for conversational responses
   - Prevent unnecessary tool execution loops

3. **Context caching** (commit ccd2377)
   - In-memory cache after first load
   - Eliminates repeated file I/O

4. **Metrics tracking** (commit ccd2377)
   - `SC_DEBUG_METRICS=1` for observability
   - Context loading decisions
   - Self-heal activation tracking

5. **Expanded project detection** (commit ccd2377)
   - 50+ additional keywords
   - File extensions, build tools, DevOps terms

6. **Documentation** (commit d5f1d0f)
   - README updated with SC_DEBUG_METRICS
   - Context loading behavior explained

### SCC CLI Testing Status
- ✅ Code compiled successfully
- ✅ All tests passing (55/55)
- ⏸️ Runtime validation blocked (NVIDIA API degraded, no Ollama models available)
- ✅ Code inspection confirms optimizations present in dist/

## Recommendations

### Immediate Actions
1. **SSH access**: Fix SSH key authentication to access VPS worker logs
2. **Worker health check**: Verify systemd timer status and recent execution
3. **API provider**: Consider RHOAI fallback while NVIDIA NIM is degraded
4. **Worker lock**: Investigate if worker is processing but stuck

### Testing Strategy
1. Wait for NVIDIA API recovery OR configure RHOAI as fallback
2. Create fresh test issue after worker health confirmed
3. Monitor with `platform/scripts/monitor-issue-e2e.sh`
4. Validate full flow: admission → queue → processing → PR → merge

### Worker Investigation Checklist
```bash
# Once SSH access restored:
ssh homedir-sdlc@72.60.141.165

# Check timer status
systemctl --user status homedir-sdlc-worker.timer

# Check last execution
journalctl --user -u homedir-sdlc-worker.service -n 50

# Check worker log
tail -100 /home/homedir-sdlc/.local/state/homedir-sdlc/logs/worker.log

# Check for hung processes
ps aux | grep scc
ps aux | grep homedir-sdlc-worker

# Check if worktree is locked
ls -la /home/homedir-sdlc/.local/share/homedir-sdlc/worktrees/homedir/.git/
```

## Conclusion

**ADEV optimizations are correctly deployed and functioning** where testable:
- ✅ Timeout classification working
- ✅ Admission gate working  
- ✅ Issue acceptance workflow working

**Blocked by external factors**:
- ❌ NVIDIA NIM API degraded (prevents SCC execution)
- ❌ Worker appears stopped/hung (prevents E2E completion)
- ❌ SSH access issues (prevents diagnostics)

**Next step**: Restore SSH access and investigate worker health before continuing E2E testing.
