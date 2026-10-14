package com.example.engine.brain

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.ui.chat.ChatMessage
import com.example.ui.chat.MessageRole
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enterprise-grade Rolling Conversation Summarization Engine.
 * Mitigates context drift across long-running multi-turn sessions for both Cloud APIs and Local GGUF models.
 *
 * When older messages slide out of the active context window, this manager summarizes the pruned turns
 * and prepends a structured, high-density summary ("## Conversation History Summary") that travels
 * permanently in the system prompt alongside pinned architectural memories.
 */
object RollingSummaryManager {

    private const val MAX_SUMMARY_TOKENS = 1200
    private const val MAX_SUMMARY_BULLETS = 15

    /**
     * Retrieves the active rolling summary for the given session.
     */
    suspend fun getRollingSummary(context: Context, sessionId: String): String? = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val settings = db.chatSettingsDao().getSettings(sessionId)
            settings?.rollingSummary?.trim()?.ifBlank { null }
        } catch (e: Exception) {
            LogKeeper.log("RollingSummaryManager", "GetError", "Failed to get summary: ${e.message}")
            null
        }
    }

    /**
     * Updates or sets the rolling summary for the given session.
     */
    suspend fun saveRollingSummary(
        context: Context,
        sessionId: String,
        summary: String?,
        summaryUpToMessageId: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.chatSettingsDao().updateRollingSummary(sessionId, summary?.trim(), summaryUpToMessageId)
            LogKeeper.log("RollingSummaryManager", "SavedSummary", "Session '$sessionId' summary updated (${summary?.length ?: 0} chars)")
        } catch (e: Exception) {
            LogKeeper.log("RollingSummaryManager", "SaveError", "Failed to save summary: ${e.message}")
        }
    }

    /**
     * Clears the rolling summary for the given session.
     */
    suspend fun clearRollingSummary(context: Context, sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.chatSettingsDao().clearRollingSummary(sessionId)
            LogKeeper.log("RollingSummaryManager", "Cleared", "Cleared rolling summary for session '$sessionId'")
        } catch (e: Exception) {
            LogKeeper.log("RollingSummaryManager", "ClearError", "Failed to clear summary: ${e.message}")
        }
    }

    /**
     * Analyzes full message history vs. currently retained active window messages.
     * Identifies messages that were pruned out and generates an incremental rolling summary.
     */
    suspend fun checkAndTriggerRollingSummarization(
        context: Context,
        sessionId: String,
        fullHistory: List<ChatMessage>,
        retainedHistory: List<ChatMessage>
    ) = withContext(Dispatchers.IO) {
        try {
            if (fullHistory.size <= retainedHistory.size || retainedHistory.isEmpty()) {
                return@withContext
            }

            val db = AppDatabase.getInstance(context)
            val currentSettings = db.chatSettingsDao().getSettings(sessionId)
            val currentSummary = currentSettings?.rollingSummary
            val lastSummarizedId = currentSettings?.summaryUpToMessageId

            // Retained history IDs
            val retainedIds = retainedHistory.map { it.id }.toSet()

            // Pruned candidates: in fullHistory, NOT in retainedHistory
            val prunedCandidates = fullHistory.filter { !retainedIds.contains(it.id) }
            if (prunedCandidates.isEmpty()) return@withContext

            // Find where to resume summarizing
            val startIndex = if (lastSummarizedId != null) {
                val idx = prunedCandidates.indexOfFirst { it.id == lastSummarizedId }
                if (idx != -1) idx + 1 else 0
            } else {
                0
            }

            val newMessagesToSummarize = prunedCandidates.drop(startIndex)
            if (newMessagesToSummarize.isEmpty()) return@withContext

            // Generate condensed summary of the new pruned batch
            val updatedSummary = distillAndMergeSummary(
                currentSummary = currentSummary,
                messagesToSummarize = newMessagesToSummarize
            )

            val latestPrunedId = newMessagesToSummarize.last().id
            saveRollingSummary(context, sessionId, updatedSummary, latestPrunedId)

            LogKeeper.log(
                "RollingSummaryManager",
                "CompactionComplete",
                "Compacted ${newMessagesToSummarize.size} pruned turns into rolling summary for session '$sessionId'"
            )
        } catch (e: Exception) {
            LogKeeper.log("RollingSummaryManager", "CompactionError", "Error during rolling summarization: ${e.message}")
        }
    }

    /**
     * Deterministic, high-yield summarizer and distiller.
     * Extracts goals, solutions, decisions, and system outputs into a clean bulleted digest.
     */
    fun distillAndMergeSummary(
        currentSummary: String?,
        messagesToSummarize: List<ChatMessage>
    ): String {
        val newBulletPoints = mutableListOf<String>()

        messagesToSummarize.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> {
                    val text = msg.text.trim()
                    if (text.isNotBlank()) {
                        val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: text
                        val cleanGoal = if (firstLine.length > 120) firstLine.take(120) + "..." else firstLine
                        newBulletPoints.add("User Objective: $cleanGoal")
                    }
                }
                MessageRole.AI -> {
                    val text = msg.text.trim()
                    if (text.isNotBlank() && text != "Thinking...") {
                        // Extract decisions or key takeaways
                        val decisionLines = text.lines().filter { line ->
                            line.contains("decided", ignoreCase = true) ||
                            line.contains("created", ignoreCase = true) ||
                            line.contains("implemented", ignoreCase = true) ||
                            line.contains("updated", ignoreCase = true) ||
                            line.contains("fixed", ignoreCase = true) ||
                            line.contains("configured", ignoreCase = true) ||
                            line.contains("architecture", ignoreCase = true)
                        }

                        if (decisionLines.isNotEmpty()) {
                            decisionLines.take(2).forEach { dl ->
                                val clean = dl.trim().removePrefix("-").removePrefix("*").trim()
                                val truncated = if (clean.length > 140) clean.take(140) + "..." else clean
                                newBulletPoints.add("Action Taken: $truncated")
                            }
                        } else {
                            val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: text
                            val cleanAns = if (firstLine.length > 120) firstLine.take(120) + "..." else firstLine
                            newBulletPoints.add("AI Output: $cleanAns")
                        }
                    }
                }
                MessageRole.TOOL -> {
                    val toolName = msg.toolName ?: "Tool"
                    val status = msg.toolStatus ?: "COMPLETED"
                    newBulletPoints.add("Tool Execution: $toolName ($status)")
                }
                else -> {}
            }
        }

        val existingLines = currentSummary
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && !it.startsWith("##") && !it.startsWith("This summary") }
            ?: emptyList()

        val allBullets = (existingLines + newBulletPoints).distinct()

        // Constrain total bullets to avoid summary bloat
        val prunedBullets = if (allBullets.size > MAX_SUMMARY_BULLETS) {
            allBullets.takeLast(MAX_SUMMARY_BULLETS)
        } else {
            allBullets
        }

        return buildString {
            append("## Conversation History Summary (Compacted Previous Turns)\n")
            append("The following summary preserves key objectives, decisions, and system actions from earlier turns pruned to stay within the context window:\n")
            prunedBullets.forEach { bullet ->
                val formatted = if (bullet.startsWith("-") || bullet.startsWith("•")) bullet else "- $bullet"
                append(formatted).append("\n")
            }
        }.trim()
    }
}
