package com.example.engine.orchestrator

enum class AntigravityRole {
    ARCHITECT,
    CODER,
    REVIEWER
}

enum class AntigravityTaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class AntigravityTaskNode(
    val id: String,
    val title: String,
    val role: AntigravityRole,
    val dependencies: Set<String> = emptySet(),
    val targetFile: String? = null,
    val issueId: String? = null,
    val prId: String? = null,
    val status: AntigravityTaskStatus = AntigravityTaskStatus.PENDING,
    val progressMessage: String? = null,
    val errorMessage: String? = null,
    val durationMs: Long = 0L
)

data class AntigravityExecutionState(
    val sessionId: String,
    val objective: String,
    val tasks: List<AntigravityTaskNode>,
    val isRunning: Boolean,
    val activeCount: Int,
    val completedCount: Int,
    val totalCount: Int,
    val startedAt: Long = System.currentTimeMillis()
)
