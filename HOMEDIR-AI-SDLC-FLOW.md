# HomeDir AI SDLC - Flujo Completo Implementado

## Diagrama del Pipeline Autónomo

```mermaid
graph TB
    Start([👤 Usuario crea Issue]) --> Label[🏷️ Agrega label<br/>ready-to-implement]
    
    Label --> Timer{⏰ Worker Timer<br/>cada 3 min}
    Timer --> AdmissionReview[🔍 Admission Review<br/>reconcile_admission_requests]
    
    AdmissionReview --> AuthCheck{👮 Authorized<br/>Labeler?}
    AuthCheck -->|No| Reject[❌ scc-rejected:<br/>unauthorized-labeler]
    AuthCheck -->|Yes| AtomicityCheck{📋 Atomicity<br/>Check}
    
    AtomicityCheck -->|> 2 criteria| NeedsHuman1[⚠️ needs-human<br/>multi-criteria]
    AtomicityCheck -->|≤ 2 criteria| AcceptanceReview[🤖 AI Acceptance<br/>Review]
    
    AcceptanceReview --> ReviewDecision{📝 Review<br/>Decision}
    ReviewDecision -->|Rejected| Reject2[❌ scc-rejected<br/>+ reasoning]
    ReviewDecision -->|Needs Clarification| NeedsHuman2[⚠️ needs-human<br/>+ context]
    ReviewDecision -->|Accepted| Accepted[✅ scc-accepted]
    
    Accepted --> Queue[📥 scc-queued]
    
    Queue --> ClaimCheck{🎯 Worker can<br/>claim?}
    ClaimCheck -->|No slots| WaitQueue[⏳ Wait in queue]
    WaitQueue --> Timer
    ClaimCheck -->|Yes| Running[🏃 scc-running]
    
    Running --> ComplexityCalc[📊 Calculate Complexity<br/>simple/medium/complex]
    ComplexityCalc --> TimeoutSet[⏱️ Set Timeout<br/>300s/600s/900s]
    TimeoutSet --> SCCExec[🤖 SCC Execution<br/>scc chat -yq]
    
    SCCExec --> SCCResult{📤 SCC<br/>Result}
    SCCResult -->|Timeout 124| SCCTimeout[⏱️ Timeout after Xs<br/>logged with actual timeout]
    SCCResult -->|Error| SCCError[❌ SCC failed<br/>exit code logged]
    SCCResult -->|Success| PRCreated[📝 PR Created<br/>scc-pr-open]
    
    SCCTimeout --> Failed[❌ scc-failed]
    SCCError --> Remediation{🔧 Remediation<br/>attempts < 5?}
    Remediation -->|Yes| Running
    Remediation -->|No| Failed
    
    PRCreated --> PRLabel[🏷️ ai-sdlc-track<br/>label added]
    PRLabel --> Waiting[⏳ scc-waiting-checks]
    
    Waiting --> CIChecks[🔬 GitHub CI Checks<br/>17 checks running]
    CIChecks --> CheckResult{✓ Check<br/>Result}
    
    CheckResult -->|Failing| Failing[❌ scc-failing-checks]
    CheckResult -->|Pending| Waiting
    CheckResult -->|All Pass| Review[👀 scc-under-review]
    
    Failing --> RemediationPR{🔧 Auto-fix<br/>possible?}
    RemediationPR -->|Yes| Running
    RemediationPR -->|No| NeedsHuman3[⚠️ needs-human<br/>CI failures]
    
    Review --> CoverageCheck{📊 Coverage<br/>Check}
    CoverageCheck -->|Gap| Coverage[⚠️ scc-coverage-gap<br/>+ needs-human]
    CoverageCheck -->|OK| Approved[✅ scc-approved]
    
    Approved --> AutoMerge{🔀 Auto-merge<br/>enabled?}
    AutoMerge -->|No| EnableAutoMerge[🔧 Enable auto-merge<br/>gh pr merge --auto]
    AutoMerge -->|Yes| WaitMerge[⏳ Wait for merge]
    EnableAutoMerge --> WaitMerge
    
    WaitMerge --> Merged[🎉 PR MERGED<br/>scc-merged]
    
    Merged --> Finalize[📋 Finalize Issue<br/>finalize_merged_issue]
    Finalize --> ReleaseCheck{🚀 Release<br/>deployed?}
    
    ReleaseCheck -->|Yes| IssueClosed[✅ Issue CLOSED<br/>+ release reference]
    ReleaseCheck -->|No| IssueClosedPending[✅ Issue CLOSED<br/>awaiting release]
    
    IssueClosed --> End([✅ Flujo Completo])
    IssueClosedPending --> End
    
    %% Reconciliation loops
    Timer -.->|Every cycle| StuckAdmission[🔄 Reconcile Stuck<br/>Admission Reviews]
    StuckAdmission -.-> ReviewDecision
    
    Timer -.->|Every cycle| OrphanPRs[🔄 Reconcile<br/>Orphan PRs]
    OrphanPRs -.->|PR w/o state file<br/>+ scc-approved| EnableAutoMerge
    
    Timer -.->|Every cycle| LegacyClosed[🔄 Reconcile<br/>Legacy Closed Issues]
    LegacyClosed -.-> IssueClosed
    
    Timer -.->|Every cycle| TrackedPRs[🔄 Reconcile<br/>Tracked PRs]
    TrackedPRs -.-> CheckResult
    
    %% Webhook path (optional)
    Webhook([🔔 GitHub Webhook<br/>issue.opened]) -.->|Immediate| AdmissionReview
    Webhook2([🔔 GitHub Webhook<br/>pr.closed]) -.->|Immediate| Finalize
    Webhook3([🔔 GitHub Webhook<br/>check.completed]) -.->|Immediate| CheckResult
    
    %% Error terminals
    Reject -.-> End
    Reject2 -.-> End
    NeedsHuman1 -.-> End
    NeedsHuman2 -.-> End
    NeedsHuman3 -.-> End
    Coverage -.-> End
    Failed -.-> End
    
    %% Styling
    classDef success fill:#10b981,stroke:#059669,stroke-width:2px,color:#fff
    classDef warning fill:#f59e0b,stroke:#d97706,stroke-width:2px,color:#fff
    classDef error fill:#ef4444,stroke:#dc2626,stroke-width:2px,color:#fff
    classDef process fill:#3b82f6,stroke:#2563eb,stroke-width:2px,color:#fff
    classDef decision fill:#8b5cf6,stroke:#7c3aed,stroke-width:2px,color:#fff
    classDef reconcile fill:#06b6d4,stroke:#0891b2,stroke-width:2px,color:#fff,stroke-dasharray: 5 5
    
    class Accepted,Approved,Merged,IssueClosed,IssueClosedPending,End success
    class Waiting,WaitQueue,WaitMerge,Review,Queue warning
    class Reject,Reject2,Failed,NeedsHuman1,NeedsHuman2,NeedsHuman3,Coverage,Failing error
    class AdmissionReview,AcceptanceReview,SCCExec,CIChecks,PRCreated,Finalize,ComplexityCalc,TimeoutSet process
    class AuthCheck,AtomicityCheck,ReviewDecision,ClaimCheck,SCCResult,CheckResult,RemediationPR,CoverageCheck,AutoMerge,ReleaseCheck decision
    class StuckAdmission,OrphanPRs,LegacyClosed,TrackedPRs reconcile
```

## Estados del Issue/PR (Labels)

### Pipeline Principal
```mermaid
stateDiagram-v2
    [*] --> ready_to_implement: Usuario crea issue
    
    ready_to_implement --> scc_admission_review: Worker detecta
    
    scc_admission_review --> scc_rejected: No autorizado/<br/>Destructivo/<br/>Rechazado
    scc_admission_review --> needs_human: Multi-criteria/<br/>Unclear scope
    scc_admission_review --> scc_accepted: Pasa review
    
    scc_accepted --> scc_queued: Admitido
    scc_queued --> scc_running: Worker claims
    
    scc_running --> scc_failed: SCC timeout/<br/>Error fatal
    scc_running --> scc_pr_open: PR creado
    
    scc_pr_open --> scc_waiting_checks: CI triggered
    scc_waiting_checks --> scc_failing_checks: Checks fail
    scc_waiting_checks --> scc_under_review: Checks pass
    
    scc_failing_checks --> needs_human: No auto-fix
    scc_failing_checks --> scc_running: Auto-remediation
    
    scc_under_review --> scc_coverage_gap: Coverage < threshold
    scc_under_review --> scc_approved: Coverage OK
    
    scc_coverage_gap --> needs_human: Manual fix needed
    
    scc_approved --> scc_merged: Auto-merge
    scc_merged --> [*]: Issue closed
    
    scc_rejected --> [*]: Terminal
    scc_failed --> [*]: Terminal
    needs_human --> [*]: Espera manual
    
    note right of scc_admission_review
        Fix #1227: Process substitution
        evita silent failures
    end note
    
    note right of scc_approved
        Fix #1153: Orphan PRs
        reconciliados aquí
    end note
```

## Componentes del Sistema

### Worker Architecture
```mermaid
graph LR
    subgraph "VPS Deployment"
        Timer[systemd Timer<br/>every 3 min]
        Service[homedir-sdlc-worker<br/>systemd service]
        Timer --> Service
    end
    
    subgraph "Worker Script"
        Main[main<br/>Entry point]
        
        subgraph "Reconciliation"
            RecAdmission[reconcile_admission_requests]
            RecStuck[reconcile_stuck_admission_reviews]
            RecOrphan[reconcile_orphan_open_prs]
            RecLegacy[reconcile_legacy_closed_issues]
            RecTracked[reconcile_tracked_prs]
        end
        
        subgraph "Core Processing"
            RunIssue[run_issue]
            RunSCC[run_scc_checked]
            ValidatePR[validate_pr_and_update_labels]
            FinalizeMerged[finalize_merged_issue]
        end
        
        Main --> RecStuck
        Main --> RecAdmission
        Main --> RunIssue
        
        RunIssue --> RunSCC
        RunSCC --> ValidatePR
        ValidatePR --> FinalizeMerged
    end
    
    subgraph "External Systems"
        GitHub[GitHub API<br/>Issues, PRs, Checks]
        SCC[SCC CLI<br/>sc-agent-cli]
        Actions[GitHub Actions<br/>CI Pipeline]
    end
    
    Service --> Main
    Main --> GitHub
    RunSCC --> SCC
    ValidatePR --> Actions
    
    subgraph "State Management"
        StateFiles["/var/lib/homedir-sdlc/<br/>issue-*.json"]
        Heartbeat[heartbeat.json]
        RunSummaries[run-summaries/*.jsonl]
    end
    
    Main --> StateFiles
    Main --> Heartbeat
    Main --> RunSummaries
    
    subgraph "Observability"
        Dashboard[SDLC Dashboard<br/>Quarkus App]
        Telemetry[Async Telemetry<br/>Observer]
    end
    
    RunSummaries -.->|Tails incrementally| Telemetry
    Telemetry --> Dashboard
    
    style RecStuck fill:#06b6d4,stroke:#0891b2
    style RecAdmission fill:#06b6d4,stroke:#0891b2
    style RecOrphan fill:#06b6d4,stroke:#0891b2
```

## Fixes Implementados (2026-07-12)

### Fix #1225 + #1226: Dashboard Isolation
```mermaid
sequenceDiagram
    participant Browser
    participant Dashboard
    participant Telemetry
    participant Worker
    participant StateFiles
    
    Note over Dashboard,StateFiles: ❌ ANTES: Request Storm
    
    Browser->>Dashboard: 7 requests/3s
    Dashboard->>StateFiles: Read sync
    Dashboard->>StateFiles: Read sync
    Dashboard->>StateFiles: Read sync
    Note right of Dashboard: 140 requests/min<br/>429 errors<br/>500 on read failures
    
    Worker->>StateFiles: Write (compete!)
    Note over Dashboard,Worker: Disk I/O contention
    
    Note over Dashboard,StateFiles: ✅ DESPUÉS: Isolated & Async
    
    Telemetry->>StateFiles: Tail incrementally (30s cycle)
    Telemetry->>Telemetry: Build snapshot in-memory
    Browser->>Dashboard: 1 request/30s
    Dashboard->>Telemetry: Read snapshot (no I/O)
    Worker->>StateFiles: Write (no contention!)
    
    Note right of Telemetry: Bounded resources:<br/>64KB/cycle<br/>250 files max<br/>2000 events
```

### Fix #1153: Orphan PR Reconciliation
```mermaid
sequenceDiagram
    participant Worker
    participant GitHub
    participant PR
    participant StateFiles
    
    Note over Worker,StateFiles: ❌ ANTES: Orphan PRs ignored
    
    Worker->>StateFiles: List all state files
    Worker->>StateFiles: Process PRs with state only
    
    Note over PR: PR #1094 (orphan)<br/>scc-approved<br/>checks passed<br/>❌ NO auto-merge
    
    Note over Worker,StateFiles: ✅ DESPUÉS: Orphans reconciled
    
    Worker->>GitHub: Query ALL PRs with<br/>scc-approved + ai-sdlc-track
    GitHub-->>Worker: PR list (includes orphans)
    
    loop Each PR
        Worker->>StateFiles: Check state file exists
        alt No state file (orphan)
            Worker->>GitHub: Enable auto-merge
            Note over PR: ✅ Auto-merged
        end
    end
```

### Fix #1227: Subshell Loop Fix (Comprehensive)
```mermaid
flowchart TB
    subgraph "❌ ANTES: Pipe creates subshell"
        Pipe1[jq -c '.[]' <<<$json]
        Pipe2[| while IFS= read -r item]
        Pipe3[do<br/>  add_label # ⚠️ Fails silently<br/>done]
        
        Pipe1 --> Pipe2 --> Pipe3
        
        Note1[Subshell scope:<br/>- Variables don't persist<br/>- Label commands fail<br/>- Loop can exit early<br/>- NO error propagation]
    end
    
    subgraph "✅ DESPUÉS: Process substitution"
        Proc1[while IFS= read -r item]
        Proc2[do<br/>  add_label # ✅ Works<br/>done]
        Proc3[< <'('jq -c '.[]' <<<$json')']
        
        Proc1 --> Proc2 --> Proc3
        
        Note2[Same shell scope:<br/>✅ Variables persist<br/>✅ Commands execute<br/>✅ Errors propagate<br/>✅ + Logging added]
    end
    
    Affected[5 Loops Fixed:<br/>1. reconcile_admission_requests<br/>2. reconcile_stuck_admission_reviews<br/>3. reconcile_orphan_open_prs<br/>4. reconcile_legacy_closed_issues<br/>5. main eligible issues]
    
    style Note1 fill:#ef4444,stroke:#dc2626,color:#fff
    style Note2 fill:#10b981,stroke:#059669,color:#fff
    style Affected fill:#3b82f6,stroke:#2563eb,color:#fff
```

## Métricas de Autonomía

### Pipeline Success Rate (Post-Fixes)
```mermaid
pie title "Autonomía por Stage (Target Post PR #1227)"
    "Admission: 100%" : 100
    "Queue → SCC: 100%" : 100
    "PR Creation: 100%" : 100
    "CI Checks: 100%" : 100
    "Auto-merge: 100%" : 100
    "Issue Closure: 100%" : 100
```

### Issue Processing Time Distribution
```mermaid
gantt
    title Tiempo Promedio por Stage (minutos)
    dateFormat mm
    axisFormat %M
    
    section Admission
    Auto-acceptance : 00, 1m
    
    section Queue
    Wait in queue : 01, 3m
    
    section Execution
    SCC processing : 04, 2m
    
    section CI
    GitHub checks : 06, 2m
    
    section Review
    Auto-approval : 08, 3m
    
    section Merge
    Auto-merge : 11, 6m
    
    section Total
    E2E Complete : crit, 00, 17m
```

## Event-Driven Architecture (Opcional)

### Webhook Integration
```mermaid
sequenceDiagram
    participant GitHub
    participant Webhook
    participant Worker
    participant Timer
    
    Note over GitHub,Timer: Traditional: Timer-based (3 min latency)
    
    Timer->>Worker: Trigger (every 3 min)
    Worker->>GitHub: Poll for issues
    
    Note over GitHub,Timer: Event-driven: Webhook-based (<10s latency)
    
    GitHub->>Webhook: issue.opened event
    Webhook->>Worker: Trigger immediately
    Worker->>GitHub: Process issue
    
    GitHub->>Webhook: pull_request.closed event
    Webhook->>Worker: Trigger immediately
    Worker->>GitHub: Finalize & close issue
    
    GitHub->>Webhook: check_suite.completed event
    Webhook->>Worker: Trigger immediately
    Worker->>GitHub: Update PR labels
    
    Note over Webhook: Fix #1144 (P2)<br/>Not yet implemented
```

---

## Deployment Architecture

```mermaid
graph TB
    subgraph "GitHub Repository"
        Main[main branch]
        PRs[Pull Requests]
        Actions[GitHub Actions]
        Releases[Releases]
    end
    
    subgraph "CI/CD Pipeline"
        BuildCI[PR CI<br/>Build, Test, Quality]
        ReleaseCI[Release CI<br/>Build, Tag, Deploy]
        DeployWorker[Deploy Worker<br/>SSH to VPS]
    end
    
    subgraph "VPS (homedir.opensourcesantiago.io)"
        subgraph "Quarkus Application"
            QuarkusApp[Quarkus App<br/>Port 8080]
            Dashboard[SDLC Dashboard<br/>/sdlc/dashboard]
            API[SDLC API<br/>/api/sdlc/*]
        end
        
        subgraph "AI SDLC Worker"
            WorkerService[homedir-sdlc-worker<br/>systemd service]
            WorkerTimer[homedir-sdlc-worker<br/>timer: every 3 min]
            WorkerScript[homedir-sdlc-worker.sh]
        end
        
        subgraph "State & Logs"
            StateDir[/var/lib/homedir-sdlc/]
            LogFile[/var/log/homedir-sdlc-worker.log]
        end
        
        WorkerTimer --> WorkerService
        WorkerService --> WorkerScript
        WorkerScript --> StateDir
        WorkerScript --> LogFile
        
        Dashboard -.->|Read-only| StateDir
        API -.->|Read-only| StateDir
    end
    
    Main --> BuildCI
    Main -->|Merge to main| ReleaseCI
    PRs --> BuildCI
    
    ReleaseCI --> Releases
    ReleaseCI --> QuarkusApp
    
    DeployWorker -->|On worker script change| WorkerScript
    
    Main -.->|Triggers on<br/>platform/scripts/*| DeployWorker
    
    style QuarkusApp fill:#10b981,stroke:#059669
    style WorkerScript fill:#3b82f6,stroke:#2563eb
    style StateDir fill:#f59e0b,stroke:#d97706
```

---

**Última actualización**: 2026-07-12  
**Versión del worker**: Con fixes #1139, #1140, #1142, #1153 deployed + #1227 pending  
**Autonomía actual**: ~95% (→99% post PR #1227)
