# AI-Driven SDLC: Gap Analysis & Improvement Plan

## Document Purpose

This document consolidates findings from:
1. **Internal Investigation** (2026-07-08): Hands-on testing and diagnostics
2. **ChatGPT Evaluation**: Deep architectural analysis
3. **Gemini Evaluation**: Cloud-native and ADev perspective

It provides a prioritized, actionable improvement plan to transform the HomeDir AI-driven SDLC from a promising but non-functional prototype into a robust, production-grade autonomous development platform.

---

## Executive Summary

### Current State Assessment

**Vision Quality**: ⭐⭐⭐⭐⭐ Excellent
- Autonomy within governance
- Server-owned operation
- Issue-driven workflow
- Non-destructive by design

**Implementation Reality**: ⭐⭐ Critical Issues
- Worker deployed but **non-functional** (0% E2E success)
- Configuration errors blocking execution
- Over-concentrated responsibilities in Bash scripts
- Weak persistence and state management
- Security based on prompts, not enforcement

### Critical Gap: **The system has a solid vision but cannot execute end-to-end flows.**

### Strategic Direction

**Transform from**: Script-based automation with advanced logic
**Transform to**: Event-driven, policy-governed, evidence-based orchestration platform

**Timeline**: 3-4 months (4 phases)
**Immediate Priority**: Restore operational capability (Phase 0: 1-2 weeks)

---

## Gap Analysis: Consolidated Findings

### 1. Operational Failures (P0 - Blocking)

| Issue | Evidence | Impact | Root Cause |
|-------|----------|--------|------------|
| **Worker Cannot Execute** | `mkdir: Permission denied` on `/var/lib`, `/var/log` | 🔴 Complete SDLC flow impossible | Hardcoded system paths, user lacks privileges |
| **GitHub CLI Not in PATH** | `bash: gh: command not found` | 🔴 Cannot fetch issues or create PRs | systemd service environment incomplete |
| **SCC Model Invalid** | `404: granite-3-2-8b-instruct-cpu not found` | 🔴 AI agent cannot be invoked | Model doesn't exist in NVIDIA catalog |
| **API Key Mismatch** | `401: expected sk-*, received nvapi-*` | 🔴 Wrong provider credentials | Profile/env variable inconsistency |
| **Label Workflow Gaps** | Issues stuck at `scc-accepted`, missing `scc-queued` | 🟡 Manual intervention required | Auto-promotion logic incomplete |

**Impact**: Zero successful E2E executions post-configuration changes.

### 2. Architectural Debt (P1 - Major)

#### 2.1 Monolithic Worker Script
**Finding** (ChatGPT): `homedir-sdlc-worker.sh` concentrates 18+ responsibilities:
- Configuration, logging, alerts, heartbeat
- GitHub interaction, admission, state machine
- Persistence, prompt building, agent execution
- Git manipulation, PR creation, check evaluation
- Review evaluation, coverage evaluation, remediation
- Merge logic, release verification, event dispatch

**Consequences**:
- Impossible to unit test effectively
- Bash errors leave partial state
- High coupling to external systems
- Difficult to evolve without breaking flows
- Cannot scale to multiple repositories

**Evidence**: 1,750+ line Bash script with global variables and distributed side effects.

#### 2.2 Labels as State Machine
**Finding** (ChatGPT): States represented by multiple concurrent labels without formal FSM:
```
scc-running + scc-pr-open + scc-waiting-checks + scc-failing-checks + 
scc-under-review + scc-coverage-gap + scc-approved + needs-human + scc-failed
```

**Problems**:
- Impossible states: `scc-approved` + `scc-failing-checks`
- Difficult reconciliation after state loss
- Manual label changes can corrupt workflow
- No single source of truth

**Evidence**: Label combinations observed during issue #1047 testing showed inconsistent progressions.

#### 2.3 Weak Persistence
**Finding** (ChatGPT): State stored in JSON files per issue/PR:
```
~/.local/state/homedir-sdlc/issues/issue-1047.json
~/.local/state/homedir-sdlc/prs/pr-1092.json
```

**Limitations**:
- No transactions
- Concurrent write risks
- Difficult to query across entities
- No schema versioning
- Weak correlation between issue→run→PR→release
- Cannot rebuild state if directory lost

**Evidence**: No database, only file-based persistence.

#### 2.4 Partial Idempotency
**Finding** (ChatGPT): Operations follow `query → decide → mutate GitHub → write local` without distributed transaction.

**Failure Mode**:
1. Add label to GitHub ✅
2. Comment on issue ❌ (fails)
3. Write local state ❌ (skipped)
4. Next execution repeats, adds duplicate comment

**Evidence**: No idempotency keys, event deduplication, or command tracking.

### 3. Security Gaps (P1 - Major)

#### 3.1 Prompt-Based Security
**Finding** (ChatGPT): Security relies on telling SCC what not to do:
- Regex scanning for dangerous patterns (`delete production`, `bypass checks`)
- Prompt instructions forbid actions
- SCC configured with `unlimited` permissions

**Problem**: **A prompt is not a security boundary.**

**Evidence**: No sandbox, no filesystem restrictions, full environment access.

#### 3.2 No Policy Engine
**Finding** (Both evaluators): Risk assessment is manual regex, not policy-driven.

**Missing**:
- Path-based access control
- Risk classification (low/medium/high)
- Pre-execution approval for sensitive changes
- Command allowlisting
- Network restrictions
- Resource limits

**Evidence**: Worker can modify any file in worktree, execute any command.

#### 3.3 Unlimited Tool Access
**Finding** (Investigation): SCC permissions set to `unlimited` in config.

**Risk**: AI agent has unrestricted access to:
- Entire filesystem in worktree
- All shell commands
- Network access
- Environment variables (including secrets)

**Evidence**: `platform/scripts/homedir-sdlc-worker.sh:45` sets `SCC_PERMISSIONS=unlimited`.

### 4. Quality Assurance Gaps (P2 - Important)

#### 4.1 Coverage Validation is Declarative
**Finding** (ChatGPT): System validates what SCC *claims* to have done, not what actually changed.

**Current**: Parse PR body for `## Issue Coverage` checklist.
**Problem**: Agent can write convincing explanation without actual implementation.

**Missing**:
- Structural evidence (files changed, symbols added)
- Test evidence (new tests, coverage increase)
- Runtime evidence (integration test results)

**Evidence**: No code diff analysis, no test-to-criteria mapping.

#### 4.2 Optional Local Validation
**Finding** (ChatGPT): Worker declares:
```
"Worker validation command not configured; GitHub checks are required before approval."
```

**Risk**: Broken code pushed to PR before CI runs.

**Evidence**: `HOMEDIR_SDLC_VALIDATION_COMMAND` unset in config.

#### 4.3 Text-Based Testing
**Finding** (ChatGPT): Tests verify script contains certain strings:
```python
assert "run_scc_checked()" in worker
assert "scc_args+=(--clear)" in worker
```

**Missing**:
- State transition tests
- Admission security tests
- Deduplication tests
- PR creation validation
- Remediation cycle tests
- Recovery after reboot

**Evidence**: `tests/unit/test_sdlc_worker_prompt.py` contains only regex assertions.

### 5. Observability Gaps (P2 - Important)

#### 5.1 Missing Thought Logging
**Finding** (Gemini): No structured storage of AI reasoning.

**Current**: Only final code output saved.
**Missing**: Chain-of-thought that led to design decisions.

**Value**: Debugging, auditing, learning from agent decisions.

#### 5.2 Insufficient Metrics
**Finding** (ChatGPT): No measurement of:
- First-pass CI success rate
- Average remediation attempts
- Post-merge defects
- Criteria coverage with evidence
- Changes without tests

**Evidence**: No metrics collection, only success/failure logs.

### 6. Documentation Drift (P2 - Important)

**Finding** (ChatGPT): Inconsistencies between docs and implementation:

| Aspect | Documentation | Reality |
|--------|--------------|---------|
| Admission flow | Direct via `ready-to-implement` | Adds `scc-admission-review` + `scc-accepted` |
| State paths | `/srv`, `/var/lib` | `/home/homedir-sdlc/.local/` |
| Model config | NVIDIA granite | Model doesn't exist |
| Auto-promotion | Automatic `scc-queued` | Manual addition required |

**Impact**: Operators cannot trust documentation for troubleshooting.

---

## Improvement Plan: 4-Phase Roadmap

### Phase 0: Operational Recovery (P0 - Week 1-2)

**Objective**: Restore E2E functionality with current architecture.

**Success Criteria**: 5 consecutive successful E2E flows on low-risk issues.

#### Tasks

1. **Fix Worker Environment** (3 days)
   - [ ] Correct all path variables to user-space
   - [ ] Fix systemd `EnvironmentFile` loading
   - [ ] Add `/home/homedir-sdlc/.local/bin` to PATH
   - [ ] Create user-writable state directories
   - [ ] Validate worker can execute basic commands
   - **Assignee**: DevOps/SRE
   - **Validation**: `homedir-sdlc-worker.sh` runs without permission errors

2. **Validate SCC Configuration** (2 days)
   - [ ] Remove `nvidia-granite-cpu` profile (model doesn't exist)
   - [ ] Test RHOAI `gpt-oss-20b` manually with `scc` CLI
   - [ ] Document working model configurations
   - [ ] Create SCC smoke test script
   - [ ] Update env files with correct API key format
   - **Assignee**: AI/ML Engineer
   - **Validation**: `scc -yq "Say hello"` succeeds with RHOAI

3. **Create Diagnostic Tool** (2 days)
   ```bash
   homedir-sdlc doctor
   ```
   - [ ] Check GitHub auth (`gh auth status`)
   - [ ] Check SCC provider connectivity
   - [ ] Verify model exists in catalog
   - [ ] Test repository access
   - [ ] Validate worktree permissions
   - [ ] Check systemd environment propagation
   - [ ] Verify state/log directory access
   - [ ] Test workflow of release workflow
   - **Assignee**: Platform Engineer
   - **Validation**: Doctor command exits 0 when healthy

4. **Automated E2E Canary** (3 days)
   - [ ] Create test issue template (simple, deterministic)
   - [ ] Script canary execution
   - [ ] Capture all logs and artifacts
   - [ ] Publish canary result as GitHub artifact
   - [ ] Document expected timings per issue complexity
   - **Assignee**: QA/Automation Engineer
   - **Validation**: Canary passes 5 times consecutively

5. **Fix Label Workflow** (2 days)
   - [ ] Implement auto-promotion `scc-accepted` → `scc-queued`
   - [ ] Handle edge cases (re-labeling, removal, races)
   - [ ] Document label state machine with diagram
   - **Assignee**: Backend Engineer
   - **Validation**: Issue progresses without manual intervention

**Deliverables**:
- Functional worker (end-to-end)
- `homedir-sdlc doctor` diagnostic tool
- Automated canary suite
- Performance baseline (simple/medium/complex issues)

**Estimated Effort**: 12 engineering days (1-2 weeks with 2 engineers)

---

### Phase 1: Architectural Refactoring (P1 - Week 3-6)

**Objective**: Extract domain logic from Bash, formalize state machine, introduce durable persistence.

**Success Criteria**: System can rebuild state after worktree deletion and server restart.

#### Tasks

1. **Formalize State Machine** (5 days)
   - [ ] Document all states explicitly:
     ```
     REQUESTED → ADMISSION_REVIEW → QUEUED → CLAIMED → 
     IMPLEMENTING → PR_OPEN → VALIDATING → REMEDIATING → 
     APPROVED → MERGE_PENDING → RELEASE_PENDING → COMPLETED
     ```
   - [ ] Define transition table (state × event → next state)
   - [ ] Implement state validation (impossible combinations rejected)
   - [ ] Make labels a *projection* of state, not the state itself
   - **Assignee**: Architect + Backend Engineer
   - **Validation**: State transitions documented and tested

2. **Introduce SQLite Persistence** (7 days)
   ```sql
   CREATE TABLE issues (id, number, state, created_at, updated_at);
   CREATE TABLE runs (id, issue_id, started_at, completed_at, outcome);
   CREATE TABLE transitions (run_id, from_state, to_state, event, timestamp);
   CREATE TABLE pull_requests (id, issue_id, pr_number, sha, created_at);
   CREATE TABLE agent_executions (id, run_id, model, prompt_tokens, completion_tokens);
   CREATE TABLE validation_results (id, run_id, validator, passed, output);
   CREATE TABLE release_verifications (id, pr_id, merge_sha, release_run_id, verified_at);
   CREATE TABLE events (id, delivery_id, event_type, payload, processed_at);
   CREATE TABLE leases (resource_id, holder, acquired_at, expires_at);
   ```
   - [ ] Schema design and migration scripts
   - [ ] Replace JSON file writes with DB inserts
   - [ ] Add transaction support for state changes
   - [ ] Implement query layer for reconciliation
   - [ ] Preserve existing JSON for rollback compatibility
   - **Assignee**: Backend Engineer
   - **Validation**: State survives directory deletion

3. **Add Idempotency** (3 days)
   - [ ] Generate idempotency keys:
     ```
     issue:1087:admission:v1
     issue:1087:implementation:run-03
     pr:1088:review:sha-abc123
     pr:1088:remediation:sha-abc123:checks-failure
     ```
   - [ ] Check before executing commands:
     - "Was this command already completed for this key?"
   - [ ] Store: `event_delivery_id`, `command_id`, `correlation_id`, `causation_id`
   - **Assignee**: Backend Engineer
   - **Validation**: Duplicate webhook delivery doesn't create duplicate comments

4. **Extract Worker Core to Python/Go** (10 days)
   ```
   sdlc/
   ├── domain/
   │   ├── issue.py
   │   ├── pull_request.py
   │   ├── run.py
   │   ├── state.py
   │   └── transitions.py
   ├── application/
   │   ├── admit_issue.py
   │   ├── implement_issue.py
   │   ├── reconcile_pr.py
   │   ├── remediate_pr.py
   │   └── verify_release.py
   ├── adapters/
   │   ├── github/
   │   ├── git/
   │   ├── scc/
   │   └── notifications/
   ├── infrastructure/
   │   ├── database.py
   │   ├── locking.py
   │   └── telemetry.py
   └── cli.py
   ```
   - [ ] Choose language (Python recommended for rapid development)
   - [ ] Create modular structure
   - [ ] Migrate one use case at a time (admit → implement → reconcile)
   - [ ] Keep Bash as systemd launcher only
   - [ ] Add type hints and validation
   - **Assignee**: 2 Backend Engineers
   - **Validation**: Core logic in typed language with unit tests

5. **Contract Tests for Adapters** (5 days)
   - [ ] GitHub adapter contract tests
   - [ ] SCC adapter contract tests
   - [ ] Git adapter contract tests
   - [ ] Mock GitHub API for integration tests
   - **Assignee**: QA Engineer + Backend Engineer
   - **Validation**: Adapter tests green, independent of external services

**Deliverables**:
- Formal state machine
- SQLite database schema
- Idempotency implementation
- Core worker in Python/Go (not Bash)
- 80%+ test coverage on domain logic

**Estimated Effort**: 30 engineering days (4 weeks with 2 engineers)

---

### Phase 2: Security & Quality (P1/P2 - Week 7-12)

**Objective**: Implement policy-based security, evidence-based quality gates, sandbox execution.

**Success Criteria**: High-risk issue requires human approval; `scc-approved` derives from verified evidence.

#### Tasks

1. **Policy Engine** (7 days)
   ```yaml
   riskPolicies:
     low:
       allowedPaths:
         - docs/**
         - tests/**
       autoMerge: true
     
     medium:
       allowedPaths:
         - src/**
         - frontend/**
       requiresReview: true
     
     high:
       paths:
         - .github/workflows/**
         - platform/**
         - migrations/**
         - security/**
       humanApprovalBeforeExecution: true
   
   forbidden:
     - "**/*.key"
     - "**/.env"
     - "production-secrets/**"
   ```
   - [ ] Risk classifier by paths changed
   - [ ] Pre-execution approval workflow for high-risk
   - [ ] Policy violation detection
   - [ ] Policy audit log
   - **Assignee**: Security Engineer + Backend Engineer
   - **Validation**: Platform change requires manual approval

2. **Sandbox Execution** (10 days)
   **Option A: Docker/Podman Container**
   ```bash
   podman run --rm \
     --network=none \
     --read-only \
     --tmpfs /tmp \
     --cap-drop=ALL \
     -v /home/homedir-sdlc/worktrees/issue-1047:/workspace:rw \
     -e SCC_PROFILE=rhoai-20b \
     scc-runner:latest \
     scc -yq "Implement issue 1047"
   ```
   
   **Option B: Kubernetes Job** (future, Phase 3)
   ```yaml
   apiVersion: batch/v1
   kind: Job
   metadata:
     name: scc-issue-1047-run-3
   spec:
     template:
       spec:
         securityContext:
           runAsNonRoot: true
           fsGroup: 1000
         containers:
         - name: scc
           image: scc-runner:latest
           resources:
             limits:
               memory: "2Gi"
               cpu: "2"
   ```
   
   - [ ] Choose sandboxing approach (container immediate, k8s future)
   - [ ] Restrict filesystem to worktree only
   - [ ] No network access (or allowlist only GitHub/model API)
   - [ ] Command allowlist (git, npm, mvn, etc.)
   - [ ] Separate read/write GitHub tokens
   - [ ] CPU, memory, time limits
   - **Assignee**: Platform Engineer + Security Engineer
   - **Validation**: Sandboxed SCC cannot access parent filesystem

3. **Evidence Engine** (8 days)
   ```json
   {
     "criterion": "User can toggle custom speaker photo",
     "declaredEvidence": ["PR body item 1"],
     "codeEvidence": [
       "ProfileResource.java:143 - toggleCustomPhoto endpoint",
       "profile.html:87 - checkbox UI"
     ],
     "testEvidence": [
       "ProfileResourceTest.java:211 - testToggleCustomPhoto",
       "ProfileIntegrationTest.java:98 - testPhotoVisibility"
     ],
     "runtimeEvidence": [
       "integration-test/profile-visibility: PASSED"
     ],
     "status": "verified"
   }
   ```
   - [ ] Extract changed files and symbols from PR diff
   - [ ] Map test files to source files
   - [ ] Detect tests added/modified in PR
   - [ ] Run integration tests, capture results
   - [ ] Generate coverage matrix: criterion → code → test → runtime
   - [ ] Publish matrix as PR check
   - **Assignee**: 2 Backend Engineers
   - **Validation**: `scc-approved` only when evidence complete

4. **Mandatory Validation** (5 days)
   ```yaml
   validationProfiles:
     docs:
       - markdownlint
       - link-check
     
     backend:
       - ./mvnw test
       - ./mvnw verify
     
     frontend:
       - npm ci
       - npm test
       - npm run build
     
     platform:
       - shellcheck
       - systemd-analyze verify
   ```
   - [ ] Define validation profiles by path patterns
   - [ ] Select profile based on files changed
   - [ ] Run validation before pushing PR
   - [ ] Fail fast if validation fails
   - **Assignee**: DevOps Engineer
   - **Validation**: Broken code doesn't reach PR

5. **Issue Contract Enforcement** (3 days)
   ```markdown
   ## Problem
   [Required]
   
   ## Expected Behavior
   [Required]
   
   ## Acceptance Criteria
   - [ ] Criterion 1 [Required]
   - [ ] Criterion 2
   
   ## Risk Level
   - [ ] Low (docs, tests)
   - [ ] Medium (features, fixes)
   - [ ] High (platform, security, migrations)
   
   ## Validation Command
   [Optional but recommended]
   ```
   - [ ] Parse issue body for required sections
   - [ ] Reject admission if incomplete
   - [ ] Guide user to improve issue structure
   - [ ] Map risk level to policy profile
   - **Assignee**: Backend Engineer
   - **Validation**: Incomplete issues rejected with helpful message

**Deliverables**:
- Policy engine with risk classification
- Sandboxed SCC execution
- Evidence-based approval (not prompt-based)
- Mandatory pre-PR validation
- Structured issue contract

**Estimated Effort**: 33 engineering days (5-6 weeks with 2 engineers)

---

### Phase 3: Observability & Scalability (P2/P3 - Week 13-20)

**Objective**: Production-grade observability, multi-repository support, concurrent execution.

**Success Criteria**: System handles 10 concurrent issues across 3 repositories with full observability.

#### Tasks

1. **Thought Logging & Audit Trail** (5 days)
   ```json
   {
     "executionId": "run-1047-3",
     "timestamp": "2026-07-10T14:23:11Z",
     "step": "implementation",
     "thought": "The issue requests adding a toggle checkbox. I will:\n1. Add checkbox to profile.html\n2. Create POST endpoint /api/profile/toggle-photo\n3. Add PhotoVisibility enum\n4. Write unit and integration tests",
     "context": {
       "filesRead": ["ProfileResource.java", "profile.html"],
       "symbols": ["PhotoVisibility", "customPhotoEnabled"]
     },
     "action": "write_file",
     "result": "success"
   }
   ```
   - [ ] Capture chain-of-thought from SCC
   - [ ] Store reasoning with code changes
   - [ ] Link thoughts to final code
   - [ ] Make searchable for debugging
   - **Assignee**: AI/ML Engineer
   - **Validation**: Can replay agent reasoning for any past run

2. **Comprehensive Metrics** (7 days)
   **Reliability Metrics**:
   - % issues completed E2E
   - % duplicate events
   - % executions recovered from failure
   - % state inconsistencies
   - Time in `needs-human`
   
   **Quality Metrics**:
   - First-pass CI success rate
   - Average remediation attempts per PR
   - Post-merge defects
   - % criteria with verified evidence
   - % changes without tests
   
   **Autonomy Metrics**:
   - % completed without human intervention
   - % correctly escalated
   - % incorrectly escalated
   - Time issue → PR
   - Time PR → release
   
   **Efficiency Metrics**:
   - Tokens/cost per issue
   - Cost per approved PR
   - Model latency
   - CI latency
   - No-op executions
   
   **Security Metrics**:
   - Admission attempts rejected
   - Policy violations detected
   - Forbidden commands blocked
   - Forbidden paths accessed
   - Bypass attempts
   
   - [ ] Instrument all transition points
   - [ ] Export to Prometheus/OpenTelemetry
   - [ ] Create Grafana dashboards
   - [ ] Set up alerts for anomalies
   - **Assignee**: Platform Engineer + SRE
   - **Validation**: Dashboards show real-time SDLC health

3. **Multi-Repository Support** (10 days)
   - [ ] Generalize worker to accept `--repo` parameter
   - [ ] Per-repository configuration
   - [ ] Per-repository policy profiles
   - [ ] Repository discovery from config
   - [ ] Separate worktrees per repository
   - [ ] Shared database with repo-scoped queries
   - **Assignee**: 2 Backend Engineers
   - **Validation**: One worker instance handles 3 repositories

4. **Concurrent Execution** (8 days)
   - [ ] Job queue (in-memory or Redis/NATS)
   - [ ] Worker pool (N concurrent runners)
   - [ ] Lease-based work claiming
   - [ ] Per-repository concurrency limits
   - [ ] Per-model concurrency limits (respect API rate limits)
   - [ ] Graceful shutdown and work requeue
   - **Assignee**: Backend Engineer + Platform Engineer
   - **Validation**: 10 issues processed concurrently

5. **Advanced Orchestration** (10 days)
   - [ ] Priority queue (urgent vs. normal)
   - [ ] Budget tracking (tokens/cost per repo/day)
   - [ ] Model selection strategy (fast vs. quality)
   - [ ] Cancellation support
   - [ ] Pause/resume support
   - [ ] Work stealing for load balancing
   - **Assignee**: 2 Backend Engineers
   - **Validation**: High-priority security fix jumps queue

**Deliverables**:
- Thought logging and audit trail
- Full metrics and dashboards
- Multi-repository capability
- Concurrent execution with queue
- Advanced orchestration features

**Estimated Effort**: 40 engineering days (6-8 weeks with 2 engineers)

---

## Prioritization Matrix

| Phase | Priority | Effort | Impact | Risk if Delayed |
|-------|----------|--------|--------|-----------------|
| **Phase 0: Operational Recovery** | P0 | 12 days | 🔴 Critical | System unusable, zero value |
| **Phase 1: Architectural Refactoring** | P1 | 30 days | 🟠 High | Technical debt compounds, hard to maintain |
| **Phase 2: Security & Quality** | P1/P2 | 33 days | 🟠 High | Security incidents, quality issues in production |
| **Phase 3: Observability & Scalability** | P2/P3 | 40 days | 🟡 Medium | Limited visibility, cannot scale beyond proof-of-concept |

**Total Estimated Effort**: 115 engineering days (~5.5 months with 2 full-time engineers)

**Recommended Staffing**:
- **Phase 0**: 2 engineers (Platform + AI/ML)
- **Phase 1**: 2 engineers (Backend + Architect)
- **Phase 2**: 2-3 engineers (Backend + Security + DevOps)
- **Phase 3**: 2 engineers (Backend + Platform/SRE)

---

## Success Criteria by Phase

### Phase 0: Operational Recovery
- ✅ `homedir-sdlc doctor` passes all checks
- ✅ 5 consecutive E2E canary runs succeed
- ✅ Simple issue → PR in < 10 minutes
- ✅ Medium issue → PR in < 30 minutes
- ✅ Worker logs show no permission errors
- ✅ SCC executes with valid model

### Phase 1: Architectural Refactoring
- ✅ State machine formally documented
- ✅ SQLite database schema deployed
- ✅ Core worker in Python/Go (not Bash)
- ✅ System recovers after worktree deletion
- ✅ Duplicate events don't cause duplicate actions
- ✅ 80%+ unit test coverage on domain logic

### Phase 2: Security & Quality
- ✅ Platform changes require human approval
- ✅ SCC runs in sandbox, cannot access parent filesystem
- ✅ `scc-approved` only with verified evidence matrix
- ✅ Policy violations logged and blocked
- ✅ Incomplete issues rejected at admission

### Phase 3: Observability & Scalability
- ✅ All metrics dashboards operational
- ✅ Worker handles 3+ repositories
- ✅ 10 concurrent issues processed successfully
- ✅ Thought logs available for debugging
- ✅ Alert fires within 5 min of anomaly

---

## Risk Mitigation

### Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Phase 0 uncovers deeper issues** | Medium | High | Allocate buffer week; escalate blockers daily |
| **Python/Go migration introduces bugs** | Medium | Medium | Incremental migration; keep Bash fallback; extensive testing |
| **SQLite performance insufficient** | Low | Medium | Start with SQLite; plan PostgreSQL migration if needed |
| **Sandbox breaks legitimate workflows** | Medium | Medium | Gradual rollout; allowlist tuning; escape hatch for debugging |
| **Multi-repo increases complexity** | High | High | Single-repo perfect first; generalize only when needed |

### Organizational Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Team capacity changes** | Medium | High | Document everything; knowledge sharing sessions; pair programming |
| **Priorities shift mid-project** | Medium | High | Phase 0 is non-negotiable; phases 1-3 can be reordered or deferred |
| **Budget constraints** | Low | Medium | Phase 0-1 are MVP; Phase 2-3 can be delayed if needed |

---

## Dependencies

### External Dependencies
- **RHOAI API stability**: Worker depends on reliable model API
  - Mitigation: Multi-provider fallback (NVIDIA, OpenAI as backup)
- **GitHub API rate limits**: Heavy usage during reconciliation
  - Mitigation: Caching, smart polling intervals, webhook preference
- **Systemd/Linux environment**: User-space systemd required
  - Mitigation: Container-based execution path as alternative

### Internal Dependencies
- **Security team review** (Phase 2): Policy definitions, sandbox approval
- **Architecture review** (Phase 1): State machine and refactoring approach
- **DevOps team** (Phase 0): VPS access, configuration changes

---

## Metrics & KPIs

### North Star Metric
**Autonomous Completion Rate**: % of admitted issues that reach `scc-merged` without human intervention.

**Target**:
- Phase 0: 50% (many failures expected)
- Phase 1: 70% (better reliability)
- Phase 2: 85% (quality gates reduce failures)
- Phase 3: 90% (mature system)

### Supporting KPIs

**Speed**:
- P90 time: issue → PR < 20 minutes
- P90 time: PR → merge < 2 hours
- P90 time: merge → release < 10 minutes

**Quality**:
- First-pass CI success rate > 80%
- Post-merge defects < 5%
- Evidence coverage > 90%

**Efficiency**:
- Cost per merged PR < $2 (token cost)
- No-op executions < 10%

**Security**:
- Unauthorized admission attempts = 0
- Policy violations in production = 0

---

## Communication Plan

### Weekly Progress Report
**Audience**: Engineering leadership
**Format**: 
- Completed tasks
- Current blockers
- Next week plan
- Risk updates

### Bi-Weekly Demo
**Audience**: Stakeholders + team
**Format**:
- Live demonstration of new capabilities
- Metrics dashboard review
- Upcoming features preview

### Incident Reviews
**Trigger**: Any production failure, security incident, or data loss
**Participants**: Engineering team + security + leadership
**Output**: Incident report, action items, timeline

---

## Appendix A: Technology Recommendations

### Phase 0 (Immediate)
- **No new tech**: Fix existing Bash/Python stack
- **Tools**: Shellcheck, Bash strict mode, better logging

### Phase 1 (Refactoring)
- **Language**: **Python** (recommended) or Go
  - Python: Faster development, rich ecosystem, team familiarity
  - Go: Better performance, concurrency, single binary
- **Database**: **SQLite** initially (simple, sufficient for single-node)
- **Testing**: pytest (Python) or Go testing framework
- **Migration**: Alembic (Python) or golang-migrate (Go)

### Phase 2 (Security)
- **Sandbox**: **Podman/Docker** containers (immediate) → Kubernetes Jobs (future)
- **Policy Engine**: OPA (Open Policy Agent) or custom YAML-based
- **Evidence**: Custom Python/Go analysis + existing tools (pytest, mvn, npm)

### Phase 3 (Scale)
- **Queue**: Redis or NATS (lightweight, easy to deploy)
- **Metrics**: Prometheus + Grafana
- **Tracing**: OpenTelemetry
- **Multi-node** (optional): PostgreSQL replaces SQLite

---

## Appendix B: Code Structure Proposal (Phase 1)

```
homedir-sdlc/
├── pyproject.toml              # Python packaging
├── requirements.txt
├── tests/
│   ├── unit/
│   ├── integration/
│   └── e2e/
├── sdlc/
│   ├── __init__.py
│   ├── domain/
│   │   ├── __init__.py
│   │   ├── issue.py           # Issue entity
│   │   ├── pull_request.py    # PR entity
│   │   ├── run.py             # Execution run
│   │   ├── state.py           # State enum
│   │   ├── transitions.py     # State machine
│   │   └── events.py          # Domain events
│   ├── application/
│   │   ├── __init__.py
│   │   ├── admit_issue.py     # Admission use case
│   │   ├── implement_issue.py # Implementation use case
│   │   ├── reconcile_pr.py    # PR reconciliation
│   │   ├── remediate_pr.py    # Remediation use case
│   │   └── verify_release.py  # Release verification
│   ├── adapters/
│   │   ├── __init__.py
│   │   ├── github/            # GitHub API wrapper
│   │   ├── git/               # Git operations
│   │   ├── scc/               # SCC agent wrapper
│   │   └── notifications/     # Discord, email, etc.
│   ├── infrastructure/
│   │   ├── __init__.py
│   │   ├── database.py        # SQLite connection & queries
│   │   ├── locking.py         # Distributed locks
│   │   ├── telemetry.py       # Metrics & logging
│   │   └── config.py          # Configuration loading
│   └── cli.py                 # CLI entrypoint
├── scripts/
│   ├── homedir-sdlc-worker.sh # Bash launcher (minimal)
│   └── homedir-sdlc-doctor.sh # Diagnostic tool
└── config/
    ├── schema.sql             # SQLite schema
    └── policies.yaml          # Risk policies
```

---

## Appendix C: State Machine Definition (Phase 1)

### States

```python
from enum import Enum

class IssueState(Enum):
    # Pre-admission
    REQUESTED = "requested"                  # ready-to-implement added
    ADMISSION_REVIEW = "admission_review"    # Checking authorization
    REJECTED = "rejected"                    # Unauthorized or invalid
    
    # Execution
    QUEUED = "queued"                        # scc-queued, ready to claim
    CLAIMED = "claimed"                      # Worker claimed, creating worktree
    IMPLEMENTING = "implementing"            # SCC executing
    
    # PR lifecycle
    PR_OPEN = "pr_open"                      # PR created
    VALIDATING = "validating"                # Checks running
    REMEDIATING = "remediating"              # Fixing failures
    APPROVED = "approved"                    # Ready to merge
    
    # Post-merge
    MERGE_PENDING = "merge_pending"          # Merge requested
    RELEASE_PENDING = "release_pending"      # Waiting for release workflow
    COMPLETED = "completed"                  # Successfully released
    
    # Terminal errors
    FAILED = "failed"                        # Execution error
    HUMAN_REQUIRED = "human_required"        # Needs human intervention
```

### Transition Table (excerpt)

| Current State | Event | Condition | Next State |
|---------------|-------|-----------|------------|
| REQUESTED | admission.reviewed | authorized = true | QUEUED |
| REQUESTED | admission.reviewed | authorized = false | REJECTED |
| QUEUED | worker.claimed | lease acquired | CLAIMED |
| CLAIMED | scc.started | - | IMPLEMENTING |
| IMPLEMENTING | scc.completed | changes exist | PR_OPEN |
| IMPLEMENTING | scc.failed | retries available | IMPLEMENTING |
| IMPLEMENTING | scc.failed | retries exhausted | FAILED |
| PR_OPEN | checks.started | - | VALIDATING |
| VALIDATING | checks.passed | evidence complete | APPROVED |
| VALIDATING | checks.failed | retries available | REMEDIATING |
| VALIDATING | checks.failed | retries exhausted | HUMAN_REQUIRED |
| APPROVED | merge.requested | - | MERGE_PENDING |
| MERGE_PENDING | merge.completed | - | RELEASE_PENDING |
| RELEASE_PENDING | release.succeeded | sha matches | COMPLETED |
| RELEASE_PENDING | release.failed | - | HUMAN_REQUIRED |

---

## Appendix D: Quick Wins (Can Start Immediately)

While planning the phases, these can be implemented in parallel:

1. **Update Documentation** (1 day)
   - Align docs with actual implementation
   - Document current state paths
   - Add troubleshooting guide
   - **Owner**: Technical Writer or Engineer

2. **Add Structured Logging** (2 days)
   - Replace echo with structured JSON logs
   - Add correlation IDs to all log lines
   - Make logs machine-parseable
   - **Owner**: Any Engineer

3. **Create Issue Templates** (1 day)
   - GitHub issue template with required sections
   - Auto-label by risk level
   - Validate structure with GitHub Actions
   - **Owner**: DevOps Engineer

4. **Setup Monitoring Alerts** (2 days)
   - Heartbeat stale → alert
   - Worker service down → alert
   - Disk space low → alert
   - **Owner**: SRE/Platform

5. **Improve Error Messages** (2 days)
   - Replace cryptic errors with actionable messages
   - Add "how to fix" suggestions
   - Link to documentation
   - **Owner**: Any Engineer

**Total Quick Wins Effort**: 8 engineering days (can run in parallel with Phase 0)

---

## Document Metadata

- **Version**: 1.0
- **Date**: 2026-07-08
- **Authors**: 
  - Claude Code (consolidation & planning)
  - ChatGPT (architectural analysis)
  - Gemini (ADev & cloud-native perspective)
- **Status**: Proposed - Pending Approval
- **Next Review**: After Phase 0 completion
- **Approvers**: Engineering Leadership, Security Team, Product Owner

---

## Approval & Sign-off

**Recommended Decision**: Approve Phase 0 immediately, review Phases 1-3 after Phase 0 success.

**Phase 0 Go/No-Go Criteria**:
- [ ] Team capacity available (2 engineers for 2 weeks)
- [ ] VPS access confirmed
- [ ] RHOAI API key valid and tested
- [ ] Stakeholders aligned on priority

**Phase 1+ Conditional Approval**:
- Pending Phase 0 success and architecture review

---

**Next Steps**:
1. Review this plan with engineering leadership
2. Get security team input on Phase 2 scope
3. Allocate engineering resources for Phase 0
4. Schedule kickoff meeting
5. Begin Phase 0 execution
