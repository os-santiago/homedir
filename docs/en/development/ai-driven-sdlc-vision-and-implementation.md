# AI-Driven SDLC: Vision and Implementation Status

## Document Purpose

This document provides a comprehensive view of the HomeDir AI-driven Software Development Life Cycle (SDLC), including:
1. **Vision**: The intended architecture, capabilities, and operating model
2. **Current Implementation**: What has been built and deployed
3. **Gap Analysis Foundation**: A baseline for identifying discrepancies between vision and reality

This serves as the reference for continuous improvement and gap analysis efforts.

---

## Vision: AI-Driven Autonomous SDLC

### Core Principles

1. **Autonomous Within Governance**
   - Automation operates independently inside repository rules
   - Never bypasses branch protection, rulesets, required checks, or reviews
   - Reports blockers and escalates to humans when rules prevent progress

2. **Server-Owned Operation**
   - All runtime, credentials, logs, and state live on the VPS
   - No dependency on developer workstations for normal operation
   - Survives server reboots without manual intervention

3. **Issue-Driven Workflow**
   - Issues are the source of truth for work requests
   - Human admission via `ready-to-implement` label by authorized users
   - Automated progression through well-defined lifecycle states

4. **Non-Destructive by Design**
   - Works on feature branches, never directly on `main`
   - Creates PRs that follow normal review and CI processes
   - Auto-merge only when all checks pass and protection rules allow

### Architecture Vision

```
┌─────────────────────────────────────────────────────────────────┐
│                         GitHub Repository                        │
│  ┌────────────┐    ┌──────────┐    ┌─────────────────────────┐ │
│  │   Issues   │───▶│   PRs    │───▶│  Production Release     │ │
│  │            │    │          │    │  (GitHub Actions)       │ │
│  └────────────┘    └──────────┘    └─────────────────────────┘ │
│         │                │                      │                │
└─────────│────────────────│──────────────────────│────────────────┘
          │                │                      │
          │                │                      │
          ▼                ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    VPS (72.60.141.165)                           │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              GitHub Webhook Listener                        │ │
│  │  - Receives issue/PR/check events                          │ │
│  │  - Routes to appropriate handlers                          │ │
│  │  - Triggers worker for admission events                    │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│                              ▼                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │          OpenClaw Listener (Admission Gateway)             │ │
│  │  - Validates authorized labelers                           │ │
│  │  - Promotes ready-to-implement → scc-queued                │ │
│  │  - Rejects unauthorized attempts                           │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│                              ▼                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              SDLC Worker (homedir-sdlc user)               │ │
│  │                                                             │ │
│  │  Event-driven (webhook) + Polling fallback (timer)         │ │
│  │                                                             │ │
│  │  ┌─────────────────────────────────────────────────────┐  │ │
│  │  │ 1. Fetch eligible issues (scc-queued)               │  │ │
│  │  │ 2. Claim issue → scc-running                        │  │ │
│  │  │ 3. Create clean worktree from main                  │  │ │
│  │  │ 4. Create branch: scc/issue-<N>-<slug>              │  │ │
│  │  │ 5. Execute SCC with issue context                   │  │ │
│  │  │ 6. Run local validation                             │  │ │
│  │  │ 7. Push branch + create/update PR                   │  │ │
│  │  │ 8. Monitor checks → scc-waiting-checks              │  │ │
│  │  │ 9. Remediate failures → scc-under-review            │  │ │
│  │  │ 10. Mark approved → scc-approved                    │  │ │
│  │  │ 11. Enable auto-merge (if rules allow)              │  │ │
│  │  │ 12. Monitor merge + release                         │  │ │
│  │  │ 13. Close issue → scc-merged                        │  │ │
│  │  └─────────────────────────────────────────────────────┘  │ │
│  │                                                             │ │
│  │  State: ~/.local/state/homedir-sdlc/                       │ │
│  │  Logs:  ~/.local/state/homedir-sdlc/logs/                 │ │
│  │  Repo:  ~/.local/share/homedir-sdlc/worktrees/homedir     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│                              ▼                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    SCC (AI Agent)                          │ │
│  │  - Provider: Configurable (NVIDIA, OpenAI, Anthropic,     │ │
│  │              RHOAI, etc.)                                  │ │
│  │  - Throttling: Auto-configured per provider               │ │
│  │  - Timeout: Configurable for slow providers               │ │
│  │  - Tools: git, read_file, write_file, run_shell, etc.     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

### Lifecycle State Machine

```
  ┌─────────────────────┐
  │   Issue Created     │
  └──────────┬──────────┘
             │
             │ Authorized labeler adds ready-to-implement
             ▼
  ┌─────────────────────┐
  │ scc-admission-review│
  └──────────┬──────────┘
             │
             ├──(authorized)──▶ scc-queued
             │
             └─(unauthorized)─▶ scc-rejected + scc-rejected:unauthorized-labeler
             
  ┌─────────────────────┐
  │    scc-queued       │
  └──────────┬──────────┘
             │
             │ Worker claims
             ▼
  ┌─────────────────────┐
  │    scc-running      │
  └──────────┬──────────┘
             │
             │ PR created
             ▼
  ┌─────────────────────┐
  │    scc-pr-open      │
  └──────────┬──────────┘
             │
             ├──(checks pending)──▶ scc-waiting-checks
             │
             ├──(checks failed)───▶ scc-failing-checks + scc-under-review
             │
             ├──(review feedback)─▶ scc-under-review
             │
             ├──(coverage gap)────▶ scc-coverage-gap + scc-under-review
             │
             │ (all clear)
             ▼
  ┌─────────────────────┐
  │   scc-approved      │◀─── Auto-merge enabled (if rules allow)
  └──────────┬──────────┘
             │
             │ PR merged
             ▼
  ┌─────────────────────┐
  │  Production Release │
  │  workflow running   │
  └──────────┬──────────┘
             │
             │ Release verified
             ▼
  ┌─────────────────────┐
  │   scc-merged        │
  │   Issue closed      │
  └─────────────────────┘

  Error paths:
  - Ambiguous requirements → needs-human
  - Repeated failures → scc-failed
  - Security/policy block → needs-human
```

### Key Components (Vision)

#### 1. GitHub Webhook Listener
- **Purpose**: Real-time event processing
- **Events**: issues, pull_request, pull_request_review, check_run, check_suite
- **Actions**:
  - Issue labeled → admission gateway
  - PR opened/updated → track state
  - Checks completed → trigger remediation if needed
  - PR merged → verify release

#### 2. OpenClaw Admission Gateway
- **Purpose**: Secure, authorized issue admission
- **Validation**:
  - Repository matches target
  - Issue is open
  - Label is `ready-to-implement`
  - Labeler is in authorized list
- **Actions**:
  - Authorized → promote to `scc-queued`, wake worker
  - Unauthorized → mark `scc-rejected:unauthorized-labeler`

#### 3. SDLC Worker
- **Trigger Modes**:
  - Event-driven (webhook) - immediate
  - Polling fallback (timer) - every 3 minutes
- **Concurrency**: Single instance via lock file
- **Responsibilities**:
  - Issue claiming and lifecycle management
  - SCC execution orchestration
  - PR creation and updates
  - Check monitoring and remediation
  - Release verification
  - State persistence

#### 4. SCC (AI Agent)
- **Model Providers**:
  - NVIDIA NIM (llama-3.3-70b, nemotron variants)
  - Red Hat OpenShift AI (gpt-oss-20b, gpt-oss-120b)
  - OpenAI (gpt-4o, o1)
  - Anthropic (claude-sonnet-4, opus-4)
  - Local (Ollama)
- **Configuration**:
  - Profile-based (easy switching)
  - Environment-based API keys
  - Configurable timeouts and throttling
  - Provider-specific optimizations

#### 5. State Persistence
- **Heartbeat**: JSON with status, timestamp, detail
- **Issue State**: Per-issue metadata files
- **PR Tracking**: PR-to-issue mapping
- **Run Summaries**: Success/failure history
- **Logs**: Structured worker and listener logs

### Expected Performance (Vision)

| Metric | Target |
|--------|--------|
| Admission latency (webhook) | < 5 seconds |
| Worker wakeup (event-driven) | < 10 seconds |
| Worker wakeup (polling fallback) | < 3 minutes |
| Simple issue (add log statement) | 2-5 minutes |
| Medium issue (fix bug, add test) | 10-20 minutes |
| Complex issue (refactor, multiple files) | 30-60 minutes |
| Remediation cycle (failed check) | 5-10 minutes |
| End-to-end (issue → merged PR) | 15-90 minutes |

### Security & Governance (Vision)

1. **Authorized Admission Only**
   - Configurable list of authorized labelers
   - Unauthorized attempts rejected and logged

2. **Repository Rules Respected**
   - Never bypasses branch protection
   - Never uses --admin or --force on protected branches
   - Waits for required reviews
   - Respects required status checks

3. **Secrets Protection**
   - API keys in environment files (not in git)
   - No token values in logs
   - Secure file permissions (mode 600)

4. **Audit Trail**
   - Every action logged with timestamp
   - Issue comments document workflow
   - PR bodies reference issues
   - Worker logs retained

---

## Current Implementation Status

### Deployed Components

#### ✅ GitHub Webhook Integration
- **Status**: Deployed and functional
- **Location**: `/home/homedir-sdlc/.local/bin/homedir-github-webhook.py`
- **Service**: `homedir-github-webhook.service`
- **Evidence**: Webhook logs show event processing at `/home/homedir-sdlc/.local/state/homedir-sdlc/logs/github-webhook.log`

#### ✅ OpenClaw Admission Listener
- **Status**: Deployed and functional
- **Location**: `/home/homedir-sdlc/.local/bin/homedir-sdlc-openclaw-listener.sh`
- **Evidence**: Successfully processes `ready-to-implement` → `scc-queued` transitions
- **Validation**: Authorized labeler checks working

#### ✅ SDLC Worker Script
- **Status**: Deployed but **non-functional**
- **Location**: `/home/homedir-sdlc/.local/bin/homedir-sdlc-worker.sh`
- **Service**: `homedir-sdlc-worker.service`
- **Timer**: `homedir-sdlc-worker.timer` (active, 3-minute interval)

#### ✅ SCC Installation
- **Status**: Installed but **misconfigured**
- **Location**: `/home/homedir-sdlc/.local/share/sc-agent-cli`
- **Wrapper**: `/home/homedir-sdlc/.local/bin/scc`
- **Config**: `/home/homedir-sdlc/.sc-agent/config.json`

### Configuration Files

#### Environment Configuration
- **Main**: `/etc/homedir.env` - Contains SDLC webhook and trigger config
- **User**: `/home/homedir-sdlc/.config/homedir-sdlc/env` - User-specific SDLC settings
- **Status**: Partially configured

#### SCC Configuration
- **Location**: `/home/homedir-sdlc/.sc-agent/config.json`
- **Active Profile**: `rhoai-20b` (Red Hat OpenShift AI)
- **Profiles Available**:
  - `rhoai-20b`: gpt-oss-20b (configured, not validated)
  - `nvidia-granite-cpu`: ibm/granite-3-2-8b-instruct-cpu (DOES NOT EXIST in NVIDIA catalog)
- **Issues**: API keys partially configured, some models reference non-existent endpoints

### Critical Gaps Identified

#### 1. 🔴 Worker Execution Failures
**Symptom**: Worker service fails with permission denied errors
**Root Cause**:
- Attempts to create directories in `/var/lib/homedir-sdlc` (permission denied)
- Attempts to create logs in `/var/log` (permission denied)
- Environment variables for user-space paths not fully honored

**Evidence**:
```
mkdir: Permission denied (repeated on /var/lib/homedir-sdlc, /var/log)
```

**Partial Fix Applied**:
```bash
export HOMEDIR_SDLC_STATE_DIR=/home/homedir-sdlc/.local/state/homedir-sdlc
export HOMEDIR_SDLC_LOGFILE=/home/homedir-sdlc/.local/state/homedir-sdlc/logs/worker.log
export HOMEDIR_SDLC_WORKDIR=/home/homedir-sdlc/.local/share/homedir-sdlc/worktrees/homedir
```
**Status**: Incomplete - worker still attempts system paths

#### 2. 🔴 GitHub CLI Not in PATH
**Symptom**: Worker cannot execute `gh` commands
**Root Cause**:
- `gh` installed at `/home/homedir-sdlc/.local/bin/gh`
- PATH environment not including user bin when executed via systemd/su

**Evidence**:
```bash
bash: line 1: gh: command not found
```

**Status**: Not fixed

#### 3. 🔴 SCC Model Configuration Errors
**Symptom**: SCC fails with 404 errors or authentication errors
**Root Causes**:
1. **Granite model does not exist**: `ibm/granite-3-2-8b-instruct-cpu` not in NVIDIA catalog
   - Available: `ibm/granite-3.0-8b-instruct`, `ibm/granite-3.0-3b-a800m-instruct`
2. **API key mismatch**: RHOAI expects `sk-*` format but receives NVIDIA `nvapi-*`
3. **Profile/environment inconsistency**: Worker uses `SCC_PROFILE` but env sets `HOMEDIR_SDLC_SCC_PROFILE`

**Evidence**:
```
Error: API Error 404: 404 page not found
Error: API Error 401: Authentication Error, LiteLLM Virtual Key expected
```

**Partial Fix Applied**:
- Changed `activeProfile` to `rhoai-20b`
- Added `export SCC_PROFILE=rhoai-20b` to env

**Status**: Not validated end-to-end

#### 4. 🔴 Label Workflow Incomplete
**Symptom**: Issues with `scc-accepted` not automatically moved to `scc-queued`
**Root Cause**: Admission workflow expects specific event sequence but doesn't handle all cases

**Evidence**: Issue #1047 stuck with `scc-accepted` + `ready-to-implement` but missing `scc-queued`

**Status**: Manual intervention required

#### 5. 🟡 Missing Auto-Queue Promotion
**Symptom**: After admission acceptance, `scc-queued` label not added automatically
**Expected**: Admission gateway should add `scc-queued` after successful `scc-accepted`
**Actual**: Manual label addition required

**Status**: Workflow gap

#### 6. 🟡 Systemd Service Environment Issues
**Symptom**: Environment variables from user config not loaded in systemd service context
**Root Cause**: Service file uses `EnvironmentFile=%h/.config/homedir-sdlc/env` but variables not propagating

**Evidence**: Worker logs show default values instead of configured values

**Status**: Systemd unit configuration incomplete

### Test Results Summary

#### Test Issue #1084 (Documentation Canary)
- **Label**: `scc-accepted` + `ready-to-implement`
- **Result**: Processed but produced no changes
- **Model Used**: Unknown (likely granite-cpu which doesn't exist → 404)
- **Status**: Failed silently

#### Test Issue #1091 (Console Log Test)
- **Label**: `ready-to-implement` → `scc-queued`
- **Result**: PR #1092 created successfully
- **Model Used**: Unknown (pre-investigation, likely NVIDIA llama with long timeout)
- **Duration**: ~23 minutes
- **Status**: Successful (but slow, model unknown)

#### Test Issue #1047 (Trending Scraper Bug)
- **Label**: `ready-to-implement` → `scc-queued` (manual)
- **Result**: Multiple failures
  1. 401 error - wrong API key
  2. 404 error - granite model doesn't exist
  3. Permission denied errors
- **Status**: Failed, never executed with correct configuration

### Performance Reality

| Metric | Current Reality | Vision Target | Gap |
|--------|----------------|---------------|-----|
| Admission latency | ~5 seconds | < 5 seconds | ✅ Met |
| Worker wakeup (event) | Not working | < 10 seconds | 🔴 Broken |
| Worker wakeup (polling) | Timer active but worker fails | < 3 minutes | 🔴 Broken |
| Simple issue execution | Unknown (never succeeded with known config) | 2-5 minutes | 🔴 Unknown |
| End-to-end success rate | 0% (post-investigation) | > 90% | 🔴 Critical |

---

## Implementation Status Matrix

| Component | Vision | Deployed | Functional | Validated | Gap Level |
|-----------|--------|----------|------------|-----------|-----------|
| GitHub Webhook Listener | ✅ | ✅ | ✅ | ✅ | 🟢 None |
| Admission Gateway | ✅ | ✅ | ✅ | ✅ | 🟢 None |
| Worker Script | ✅ | ✅ | ❌ | ❌ | 🔴 Critical |
| Worker Service/Timer | ✅ | ✅ | ❌ | ❌ | 🔴 Critical |
| SCC Installation | ✅ | ✅ | ❌ | ❌ | 🔴 Critical |
| SCC Model Config | ✅ | ⚠️ | ❌ | ❌ | 🔴 Critical |
| Path/Environment Setup | ✅ | ⚠️ | ❌ | ❌ | 🔴 Critical |
| State Persistence | ✅ | ✅ | ⚠️ | ❌ | 🟡 Partial |
| Logging Infrastructure | ✅ | ✅ | ⚠️ | ⚠️ | 🟡 Partial |
| Auto-merge Logic | ✅ | ❓ | ❓ | ❌ | 🟡 Unknown |
| Release Verification | ✅ | ❓ | ❓ | ❌ | 🟡 Unknown |
| Remediation Cycles | ✅ | ❓ | ❓ | ❌ | 🟡 Unknown |

Legend:
- ✅ Complete/Yes
- ⚠️ Partial
- ❌ Missing/Broken
- ❓ Unknown
- 🟢 None (working as intended)
- 🟡 Partial (some issues)
- 🔴 Critical (blocking)

---

## Critical Blockers for E2E Success

### Blocker 1: Worker Cannot Execute (Priority: P0)
**Impact**: Complete SDLC flow impossible
**Components Affected**: Worker service, SCC execution, PR creation
**Required Fixes**:
1. Fix directory permissions (use user-space paths consistently)
2. Ensure `gh` is in PATH for systemd service execution
3. Validate environment variable loading in service context

### Blocker 2: SCC Configuration Invalid (Priority: P0)
**Impact**: AI agent cannot be invoked
**Components Affected**: Code generation, PR content quality
**Required Fixes**:
1. Remove/replace `nvidia-granite-cpu` profile (model doesn't exist)
2. Validate RHOAI gpt-oss-20b configuration end-to-end
3. Ensure API key environment variables correct for active profile
4. Test SCC execution manually before worker integration

### Blocker 3: Label Workflow Gaps (Priority: P1)
**Impact**: Issues stuck in admission, manual intervention required
**Components Affected**: Issue progression, automation reliability
**Required Fixes**:
1. Implement auto-promotion from `scc-accepted` → `scc-queued`
2. Handle edge cases (re-labeling, label removal, race conditions)
3. Document and enforce label state machine

---

## Recommendations for Gap Analysis

### Phase 1: Foundation Repair (Week 1)
1. **Fix worker execution environment**
   - Correct all path variables
   - Fix systemd service environment loading
   - Add `gh` to PATH
   - Test worker can execute basic commands

2. **Validate SCC configuration**
   - Remove invalid model references
   - Test RHOAI gpt-oss-20b manually
   - Document working model configurations
   - Create SCC smoke test script

### Phase 2: E2E Validation (Week 2)
1. **Execute controlled E2E test**
   - Simple issue (add console.log)
   - Monitor every step
   - Document actual timings
   - Capture all logs

2. **Measure performance baselines**
   - Simple vs medium vs complex issues
   - Model latency per provider
   - Success/failure rates

### Phase 3: Remediation & Polish (Week 3-4)
1. **Implement missing workflows**
   - Auto-queue promotion
   - Label state machine enforcement
   - Edge case handling

2. **Harden observability**
   - Structured logging
   - Metrics collection
   - Alert definitions

---

## Appendix A: Configuration Inventory

### Environment Files
1. `/etc/homedir.env` (system-wide)
   - SDLC webhook configuration
   - Trigger labels
   - Timeouts
   - Repository settings

2. `/home/homedir-sdlc/.config/homedir-sdlc/env` (user-specific)
   - API keys (NVIDIA, RHOAI)
   - SCC profile selection
   - Worker state directories
   - User-specific overrides

### SCC Configuration
**File**: `/home/homedir-sdlc/.sc-agent/config.json`

**Current Active Profile**: `rhoai-20b`

**Known Working Profiles**: None validated

**Profiles to Remove**: `nvidia-granite-cpu` (model doesn't exist)

**Profiles to Validate**:
- `rhoai-20b`: gpt-oss-20b @ RHOAI
- Potentially add: NVIDIA llama-3.3-70b, OpenAI gpt-4o, Anthropic claude-sonnet-4

### State Directories
- Worker state: `~/.local/state/homedir-sdlc/`
- Issue metadata: `~/.local/state/homedir-sdlc/issues/`
- PR tracking: `~/.local/state/homedir-sdlc/prs/`
- Run summaries: `~/.local/state/homedir-sdlc/run-summaries/`
- Worker logs: `~/.local/state/homedir-sdlc/logs/worker.log`
- Listener logs: `~/.local/state/homedir-sdlc/logs/openclaw-listener.log`
- GitHub webhook logs: `~/.local/state/homedir-sdlc/logs/github-webhook.log`

---

## Appendix B: Investigation Timeline

### 2026-07-08 Investigation Session

**Objective**: Test E2E flow with RHOAI gpt-oss-20b

**Key Events**:
1. Attempted to test with Granite CPU model → 404 (model doesn't exist)
2. Discovered worker permission issues
3. Found GitHub CLI not in PATH
4. Identified SCC configuration mismatches
5. Applied partial fixes
6. Multiple test attempts with issue #1047
7. All E2E attempts failed

**Configuration Changes Applied**:
- Added user-space directory paths to env file
- Changed SCC activeProfile to `rhoai-20b`
- Added `export SCC_PROFILE=rhoai-20b`
- Created missing state directories manually

**Outcome**: Unable to validate E2E flow. Worker execution remains blocked.

**Evidence Preserved**: 
- Worker logs: `/home/homedir-sdlc/.local/state/homedir-sdlc/logs/worker.log`
- GitHub webhook logs showing admission workflow
- Test issue #1047 with complete label history

---

## Document Metadata

- **Version**: 1.0
- **Date**: 2026-07-08
- **Author**: AI SDLC Investigation Team
- **Status**: Initial Baseline
- **Next Review**: After Phase 1 fixes
- **Related Documents**:
  - `docs/en/development/autonomous-sdlc.md` (Operating Model)
  - `platform/README.md` (Platform Overview)
