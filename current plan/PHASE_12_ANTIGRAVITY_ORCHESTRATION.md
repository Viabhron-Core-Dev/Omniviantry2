# PHASE 12: Antigravity Orchestration (Parallel Sub-Agents, DAG Task Distribution & Multi-Agent PR/Issue Flow)

## 1. Architectural Vision & Integration with Omnivian Core

Phase 12 elevates Omnivian from a single-turn, sequential LLM tool runner (`AgenticTurnExecutor`) into a **parallel multi-agent orchestrator** ("Antigravity Engine"). 

In this architecture, complex tasks (such as refactoring an entire module, adding end-to-end tests, or executing multi-step feature additions) are decomposed into a Directed Acyclic Graph (DAG) of atomic sub-tasks. Dedicated specialized sub-agents (**Architect**, **Coder**, **Reviewer**) execute tasks concurrently under strict mobile resource controls, creating structured local Issues and Pull Requests in Room DB without corrupting live workspace files.

```
                             [User Prompt / High-Impact Task]
                                            │
                                            ▼
                           [AntigravityOrchestrator (Master)]
                                            │
               ┌────────────────────────────┴────────────────────────────┐
               ▼                                                         ▼
     [Architect Sub-Agent]                                      [Task DAG Builder]
     • Inspects repository                                      • Nodes & Dependencies
     • Generates WorkspaceIssueEntities                         • Concurrency Semaphore (2)
               │                                                         │
               └────────────────────────────┬────────────────────────────┘
                                            │
                                            ▼
                             [Heterogeneous Model Dispatcher]
                             (Links to FallbackChainRouter)
                                            │
                    ┌───────────────────────┴───────────────────────┐
                    ▼                                               ▼
           [High-Speed / Free Tier]                        [Frontier Reasoning]
           • Cerebras / Groq / SambaNova                   • Gemini 2.5 Pro / Claude
           • Local GGUF (llama.cpp)                        • Complex architecture audits
                    │                                               │
                    └───────────────────────┬───────────────────────┘
                                            │
                                            ▼
                                   [Coder Sub-Agent]
                                   • Pulls unassigned Issue
                                   • Generates Unified Diff
                                   • Stashes in WorkspacePullRequestEntity
                                            │
                                            ▼
                                  [Reviewer Sub-Agent]
                                  • Adversarial verification
                                  • Lints syntax & checks credentials
                                  • Approves or requests revisions
                                            │
                                            ▼
                                [Human-in-the-Loop Gate]
                                • Review & Merge Diff UI
                                • PatchApplier merges to disk
                                            │
                                            ▼
                                [LogKeeper Diagnostic Hub]
                                • Zero credentials, full telemetry
```

---

## 2. Mini-Phase Breakdown

### Mini-Phase 12.1: Antigravity Task DAG & Concurrency Dispatcher
* **`AntigravityOrchestrator.kt`**:
  * Core engine coordinating background coroutines with a `SupervisorJob` bound to the workspace lifecycle.
  * Task DAG engine (`AntigravityTaskNode`) supporting node statuses (`PENDING`, `QUEUED`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`).
  * Strict mobile guardrails: Concurrency limiter via `kotlinx.coroutines.sync.Semaphore(permits = 2)` to eliminate Out-Of-Memory (OOM) crashes and CPU throttling on mobile devices.
  * Real-time `StateFlow<AntigravityExecutionState>` streaming progress back to both Chat and Workspace UI.
* **LogKeeper Integration**:
  * `LogKeeper.log("AntigravityOrchestrator", "DAGInitialized", "Initialized DAG with N task nodes")`
  * `LogKeeper.log("AntigravityOrchestrator", "TaskStateTransition", "Task $taskId transitioned to $status")`

---

### Mini-Phase 12.2: Tiered Sub-Agent Roles (Architect, Coder, Reviewer)
* **Architect (`ArchitectAgent.kt`)**:
  * Reads user intent, project blueprints, and active workspace files.
  * Decomposes high-level requirements into atomic, actionable `WorkspaceIssueEntity` entries stored in Room DB.
  * Writes explicit acceptance criteria and target file paths for each issue.
* **Coder (`CoderAgent.kt`)**:
  * Claims an open `WorkspaceIssueEntity`.
  * Analyzes target files via `SnippetCompactor` and `SurgicalEditor`.
  * Generates clean unified diffs (`UnifiedDiffParser`) without modifying disk files directly.
  * Packages changes into a `WorkspacePullRequestEntity` in Room DB.
* **Reviewer (`ReviewerAgent.kt`)**:
  * Audits the generated diff against the issue's acceptance criteria.
  * Executes security heuristics (scans for hardcoded tokens, passwords, keystore references).
  * Runs syntax and bracket verification.
  * Marks PR status as `READY_TO_MERGE` or attaches revision feedback to the issue.
* **LogKeeper Integration**:
  * `LogKeeper.log("AntigravityRoles", "IssueCreated", "Architect created issue: ${issue.title}")`
  * `LogKeeper.log("AntigravityRoles", "PRGenerated", "Coder generated PR #${pr.id} with N hunks")`
  * `LogKeeper.log("AntigravityRoles", "AuditResult", "Reviewer audit: passed=${review.passed}")`

---

### Mini-Phase 12.3: Zero-Cost Workload Distribution via Fallback Router
* **Intelligent Workload Balancing**:
  * Integrates with `FallbackChainRouter` (Phase 9) to route tasks dynamically by complexity:
    * *Architect*: High-reasoning models (Gemini 2.5 Pro / Claude Sonnet) for robust system design.
    * *Coder*: High-throughput coding models (Mistral Codestral, DeepSeek-Coder, or Cerebras Llama 3.3 70B at 1,500 tps).
    * *Reviewer*: Lightweight audit models (Gemini Flash or local on-device `.gguf` via `LlamaEngine`).
  * Rate-limit backoff: Automatic token-bucket tracking with backoff if a free provider returns HTTP 429.
* **LogKeeper Integration**:
  * `LogKeeper.log("AntigravityRouter", "ProviderSelected", "Dispatched task $id to provider $provider")`
  * `LogKeeper.log("AntigravityRouter", "RateLimitBackoff", "Provider $provider 429; backing off ${delayMs}ms")`

---

### Mini-Phase 12.4: Human-in-the-Loop PR & Issue Management
* **Database & Repository Layer**:
  * Extends `WorkspaceIssueDao` and `WorkspacePullRequestDao` with reactive `Flow` queries.
  * Supports status lifecycle: `OPEN` $\to$ `IN_PROGRESS` $\to$ `RESOLVED` / `CLOSED`.
* **Atomic Patch Application**:
  * Connects `WorkspacePullRequestEntity.diff` to `PatchApplier` and `SurgicalEditor`.
  * Merges approved diffs atomically to disk; rolls back on failure with zero partial writes.
* **LogKeeper Integration**:
  * `LogKeeper.log("AntigravityPR", "MergeSuccess", "Merged PR #${pr.id} into workspace disk")`
  * `LogKeeper.log("AntigravityPR", "MergeFailure", "Failed to merge PR #${pr.id}; rolled back cleanly")`

---

### Mini-Phase 12.5: Antigravity UI & Timeline Components
* **`AntigravityTimelineCard.kt`**:
  * Compact, modern Material 3 collapsible status card displayed in the Chat timeline when multi-agent tasks run.
  * Live status pills: Shows active sub-agents, running task names, and elapsed times.
* **`PullRequestReviewSheet.kt`**:
  * Side-by-side or unified diff preview with syntax-colored hunks (green additions, red deletions).
  * One-tap actions: "Approve & Merge", "Request Revision", "Discard".
* **Workspace Issues & PR Tabs Integration**:
  * Seamless display inside the full-screen Workspace file manager tabs.

---

### Mini-Phase 12.6: Security, Credential Immunity & Error Recovery
* **Enforced Credential Immunity**:
  * Reviewer agent specifically verifies that no API keys, tokens, or keystore files are introduced in generated diffs.
* **Safe Error Recovery**:
  * Failed sub-agent tasks automatically report errors to the Master Orchestrator, which can re-dispatch up to 2 times before surfacing options to the user.
* **Zero Bloat Verification**:
  * All temporary diff buffers and AST trees are cleared upon task completion or cancellation.
