package com.example.engine.orchestrator

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspaceIssueEntity
import com.example.engine.db.WorkspacePullRequestEntity
import com.example.utils.LogKeeper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

object AntigravityOrchestrator {

    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    // Strict Mobile Safeguard: Limit concurrent background LLM agents to 2
    private val concurrencySemaphore = Semaphore(permits = 2)

    private val _state = MutableStateFlow<AntigravityExecutionState?>(null)
    val state: StateFlow<AntigravityExecutionState?> = _state.asStateFlow()

    /**
     * Spawns an Antigravity multi-agent swarm:
     * 1. Architect decomposes the objective into atomic issues.
     * 2. Builds a Directed Acyclic Graph (DAG) of Coder and Reviewer tasks.
     * 3. Dispatches tasks concurrently with Semaphore(2) concurrency gating.
     */
    fun startSwarm(
        context: Context,
        sessionId: String,
        workspaceId: String,
        objective: String
    ): Job {
        cancel() // Cancel any previously running swarm

        val job = orchestratorScope.launch {
            LogKeeper.log("AntigravityOrchestrator", "SwarmStarted", "Objective: '$objective' for session: $sessionId")

            val initialTasks = listOf(
                AntigravityTaskNode(
                    id = "arch-1",
                    title = "Architect: System & Task Decomposition",
                    role = AntigravityRole.ARCHITECT,
                    status = AntigravityTaskStatus.RUNNING,
                    progressMessage = "Analyzing codebase and decomposing requirements..."
                )
            )

            _state.value = AntigravityExecutionState(
                sessionId = sessionId,
                objective = objective,
                tasks = initialTasks,
                isRunning = true,
                activeCount = 1,
                completedCount = 0,
                totalCount = 1
            )

            val startTime = System.currentTimeMillis()

            try {
                // Phase 1: Architect decomposes objective
                val issues = concurrencySemaphore.withPermit {
                    ArchitectAgent.planIssues(
                        context = context,
                        workspaceId = workspaceId,
                        objective = objective,
                        onProgress = { msg ->
                            updateTaskProgress("arch-1", msg)
                        }
                    )
                }

                markTaskComplete("arch-1", System.currentTimeMillis() - startTime)
                LogKeeper.log("AntigravityOrchestrator", "ArchitectComplete", "Generated ${issues.size} issues")

                if (issues.isEmpty()) {
                    finishSwarm(success = false, "Architect could not generate actionable tasks.")
                    return@launch
                }

                // Phase 2: Build DAG of Coder and Reviewer tasks
                val dagTasks = mutableListOf<AntigravityTaskNode>()
                // Keep the completed architect task
                _state.value?.tasks?.firstOrNull()?.let { dagTasks.add(it) }

                issues.forEachIndexed { index, issue ->
                    val coderTaskId = "coder-${issue.id.take(6)}"
                    val reviewerTaskId = "reviewer-${issue.id.take(6)}"

                    // Coder task depends on Architect
                    dagTasks.add(
                        AntigravityTaskNode(
                            id = coderTaskId,
                            title = "Coder: ${issue.title}",
                            role = AntigravityRole.CODER,
                            dependencies = setOf("arch-1"),
                            targetFile = issue.targetFile,
                            issueId = issue.id,
                            status = AntigravityTaskStatus.QUEUED,
                            progressMessage = "Waiting for worker slot..."
                        )
                    )

                    // Reviewer task depends on Coder task
                    dagTasks.add(
                        AntigravityTaskNode(
                            id = reviewerTaskId,
                            title = "Reviewer: Audit ${issue.title}",
                            role = AntigravityRole.REVIEWER,
                            dependencies = setOf(coderTaskId),
                            targetFile = issue.targetFile,
                            issueId = issue.id,
                            status = AntigravityTaskStatus.PENDING,
                            progressMessage = "Waiting for coder implementation..."
                        )
                    )
                }

                _state.value = _state.value?.copy(
                    tasks = dagTasks,
                    totalCount = dagTasks.size
                )

                // Phase 3: Execute tasks respecting dependencies and concurrency
                executeDag(context, issues)

            } catch (e: CancellationException) {
                LogKeeper.log("AntigravityOrchestrator", "SwarmCancelled", "Swarm execution was cancelled by user")
                markRemainingTasksCancelled()
            } catch (e: Exception) {
                LogKeeper.log("AntigravityOrchestrator", "SwarmError", "Error during swarm: ${e.message}")
                finishSwarm(success = false, e.message ?: "Unknown error")
            }
        }

        activeJob = job
        return job
    }

    private suspend fun executeDag(
        context: Context,
        issues: List<WorkspaceIssueEntity>
    ) = coroutineScope {
        val createdPrs = mutableMapOf<String, WorkspacePullRequestEntity>() // issueId -> PR

        // Run Coder tasks in parallel bounded by Semaphore(2)
        val coderJobs = issues.map { issue ->
            launch {
                val coderTaskId = "coder-${issue.id.take(6)}"
                val reviewerTaskId = "reviewer-${issue.id.take(6)}"

                updateTaskStatus(coderTaskId, AntigravityTaskStatus.QUEUED, "Queued behind active permits...")

                val taskStart = System.currentTimeMillis()
                val pr = concurrencySemaphore.withPermit {
                    updateTaskStatus(coderTaskId, AntigravityTaskStatus.RUNNING, "Generating surgical unified diff...")
                    CoderAgent.implementIssue(
                        context = context,
                        issue = issue,
                        onProgress = { msg ->
                            updateTaskProgress(coderTaskId, msg)
                        }
                    )
                }

                if (pr != null) {
                    createdPrs[issue.id] = pr
                    markTaskComplete(coderTaskId, System.currentTimeMillis() - taskStart, prId = pr.id)

                    // Unblock Reviewer task
                    updateTaskStatus(reviewerTaskId, AntigravityTaskStatus.QUEUED, "Queued for audit...")
                    
                    val reviewerStart = System.currentTimeMillis()
                    val reviewResult = concurrencySemaphore.withPermit {
                        updateTaskStatus(reviewerTaskId, AntigravityTaskStatus.RUNNING, "Auditing diff and scanning credentials...")
                        ReviewerAgent.auditPullRequest(
                            context = context,
                            pr = pr,
                            issue = issue,
                            onProgress = { msg ->
                                updateTaskProgress(reviewerTaskId, msg)
                            }
                        )
                    }

                    if (reviewResult.approved) {
                        markTaskComplete(reviewerTaskId, System.currentTimeMillis() - reviewerStart, prId = pr.id)
                    } else {
                        markTaskFailed(reviewerTaskId, reviewResult.feedback)
                    }

                } else {
                    markTaskFailed(coderTaskId, "Failed generating unified diff.")
                    markTaskCancelled(reviewerTaskId, "Cancelled: prerequisite coder task failed.")
                }
            }
        }

        coderJobs.joinAll()
        finishSwarm(success = true, "All sub-agent tasks completed.")
    }

    private fun updateTaskStatus(taskId: String, status: AntigravityTaskStatus, progress: String?) {
        val current = _state.value ?: return
        val updated = current.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(status = status, progressMessage = progress ?: task.progressMessage)
            } else task
        }
        val active = updated.count { it.status == AntigravityTaskStatus.RUNNING }
        val completed = updated.count { it.status == AntigravityTaskStatus.SUCCESS }
        _state.value = current.copy(tasks = updated, activeCount = active, completedCount = completed)
    }

    private fun updateTaskProgress(taskId: String, progress: String) {
        val current = _state.value ?: return
        val updated = current.tasks.map { task ->
            if (task.id == taskId) task.copy(progressMessage = progress) else task
        }
        _state.value = current.copy(tasks = updated)
    }

    private fun markTaskComplete(taskId: String, durationMs: Long, prId: String? = null) {
        val current = _state.value ?: return
        val updated = current.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = AntigravityTaskStatus.SUCCESS,
                    progressMessage = "Completed",
                    durationMs = durationMs,
                    prId = prId ?: task.prId
                )
            } else task
        }
        val active = updated.count { it.status == AntigravityTaskStatus.RUNNING }
        val completed = updated.count { it.status == AntigravityTaskStatus.SUCCESS }
        _state.value = current.copy(tasks = updated, activeCount = active, completedCount = completed)
    }

    private fun markTaskFailed(taskId: String, error: String) {
        val current = _state.value ?: return
        val updated = current.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(status = AntigravityTaskStatus.FAILED, errorMessage = error, progressMessage = "Failed: $error")
            } else task
        }
        _state.value = current.copy(tasks = updated)
    }

    private fun markTaskCancelled(taskId: String, reason: String) {
        val current = _state.value ?: return
        val updated = current.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(status = AntigravityTaskStatus.CANCELLED, progressMessage = reason)
            } else task
        }
        _state.value = current.copy(tasks = updated)
    }

    private fun markRemainingTasksCancelled() {
        val current = _state.value ?: return
        val updated = current.tasks.map { task ->
            if (task.status == AntigravityTaskStatus.RUNNING || task.status == AntigravityTaskStatus.QUEUED || task.status == AntigravityTaskStatus.PENDING) {
                task.copy(status = AntigravityTaskStatus.CANCELLED, progressMessage = "Cancelled by user")
            } else task
        }
        _state.value = current.copy(tasks = updated, isRunning = false, activeCount = 0)
    }

    private fun finishSwarm(success: Boolean, message: String) {
        val current = _state.value ?: return
        _state.value = current.copy(isRunning = false, activeCount = 0)
        LogKeeper.log("AntigravityOrchestrator", "SwarmFinished", "Success: $success - $message")
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        markRemainingTasksCancelled()
    }
}
