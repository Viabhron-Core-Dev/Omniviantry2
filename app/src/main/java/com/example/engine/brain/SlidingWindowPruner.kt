package com.example.engine.brain

import com.example.ui.chat.OmniMessage
import com.example.utils.LogKeeper

/**
 * Intelligent Sliding Window Pruner.
 * Preserves the system prompt, active tool execution chains, and recent dialog turns
 * while gracefully compacting or truncating older conversational history.
 */
object SlidingWindowPruner {

    fun prune(
        messages: List<OmniMessage>,
        targetTokenLimit: Int,
        systemTokens: Int
    ): List<OmniMessage> {
        if (messages.isEmpty()) return emptyList()

        val availableBudget = targetTokenLimit - systemTokens
        if (availableBudget <= 200) {
            // Severe emergency: return only latest user message
            val lastUser = messages.lastOrNull { it.role == "user" }
            return if (lastUser != null) listOf(lastUser) else messages.takeLast(1)
        }

        // 1. Always keep the first message if it provides core context
        val firstMessage = messages.firstOrNull()
        val hasPinnedFirst = firstMessage != null && (firstMessage.role == "system" || firstMessage.role == "user")

        // 2. Identify the most recent messages (working backwards)
        val result = mutableListOf<OmniMessage>()
        var currentTokenSum = if (hasPinnedFirst) ContextBudgetManager.estimateTokens(firstMessage?.content) + 4 else 0

        val reversedCandidates = messages.reversed()

        for (msg in reversedCandidates) {
            if (hasPinnedFirst && msg == firstMessage) continue

            val msgTokens = ContextBudgetManager.estimateTokens(msg.content) + 4
            
            // Check if adding this message fits in budget
            if (currentTokenSum + msgTokens <= availableBudget) {
                result.add(0, msg)
                currentTokenSum += msgTokens
            } else {
                // If it's an oversized tool result or assistant message, try compacting it
                val contentStr = msg.content
                if (contentStr != null && (msg.role == "tool" || contentStr.contains("```"))) {
                    val compacted = SnippetCompactor.compact(contentStr, maxLines = 15)
                    val compactedTokens = ContextBudgetManager.estimateTokens(compacted) + 4
                    if (currentTokenSum + compactedTokens <= availableBudget) {
                        result.add(0, msg.copy(content = compacted))
                        currentTokenSum += compactedTokens
                        continue
                    }
                }
                
                // Budget reached; stop taking older messages
                break
            }
        }

        if (hasPinnedFirst && firstMessage != null && !result.contains(firstMessage)) {
            result.add(0, firstMessage)
        }

        // If pruning emptied the list, preserve at least the latest user query
        if (result.isEmpty()) {
            val fallback = messages.lastOrNull() ?: return emptyList()
            result.add(fallback)
        }

        LogKeeper.log("SlidingWindowPruner", "PrunedHistory", "Reduced ${messages.size} messages to ${result.size} messages")
        return result
    }
}
