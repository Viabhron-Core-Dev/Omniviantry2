package com.example.engine.brain

import com.example.ui.chat.OmniMessage
import com.example.utils.LogKeeper

/**
 * Model Context Window Profile specifying limits and thresholds.
 */
data class ModelContextProfile(
    val modelId: String,
    val maxContextTokens: Int,
    val maxOutputTokens: Int,
    val targetHeadroomTokens: Int = 4096,
    val isLocalGguf: Boolean = false
)

/**
 * Result of a context budget analysis and compression run.
 */
data class ContextBudgetReport(
    val originalEstimatedTokens: Int,
    val compressedEstimatedTokens: Int,
    val tokenLimit: Int,
    val compressionRatio: Float,
    val isTruncated: Boolean,
    val preservedMessageCount: Int,
    val prunedMessageCount: Int,
    val warnings: List<String> = emptyList()
)

/**
 * Mini-Phase 11.1: Context Window Compressor & Token Budget Engine.
 * Accurately estimates token usage, manages memory budgets, and prevents 400/429
 * Context Window Exceeded exceptions across Cloud and Local LLMs.
 */
object ContextBudgetManager {

    private val MODEL_PROFILES = mapOf(
        // Gemini Models
        "gemini-2.5-pro" to ModelContextProfile("gemini-2.5-pro", 1_000_000, 8192),
        "gemini-2.5-flash" to ModelContextProfile("gemini-2.5-flash", 1_000_000, 8192),
        "gemini-1.5-pro" to ModelContextProfile("gemini-1.5-pro", 2_000_000, 8192),
        "gemini-1.5-flash" to ModelContextProfile("gemini-1.5-flash", 1_000_000, 8192),
        
        // Anthropic Claude
        "claude-3-7-sonnet" to ModelContextProfile("claude-3-7-sonnet", 200_000, 8192),
        "claude-3-5-sonnet" to ModelContextProfile("claude-3-5-sonnet", 200_000, 8192),
        "claude-3-5-haiku" to ModelContextProfile("claude-3-5-haiku", 200_000, 8192),

        // OpenAI
        "gpt-4o" to ModelContextProfile("gpt-4o", 128_000, 4096),
        "gpt-4o-mini" to ModelContextProfile("gpt-4o-mini", 128_000, 4096),
        "o1" to ModelContextProfile("o1", 200_000, 100_000),
        "o3-mini" to ModelContextProfile("o3-mini", 200_000, 100_000),

        // DeepSeek & Groq / OpenRouter
        "deepseek-r1" to ModelContextProfile("deepseek-r1", 64_000, 8192),
        "deepseek-v3" to ModelContextProfile("deepseek-v3", 64_000, 8192),
        "llama-3.3-70b-versatile" to ModelContextProfile("llama-3.3-70b-versatile", 128_000, 8192),
        "mixtral-8x7b-32768" to ModelContextProfile("mixtral-8x7b-32768", 32_768, 4096),

        // Local GGUF defaults
        "local-default" to ModelContextProfile("local-default", 4096, 2048, targetHeadroomTokens = 512, isLocalGguf = true)
    )

    fun getProfileForModel(modelName: String): ModelContextProfile {
        val lower = modelName.lowercase()
        for ((key, profile) in MODEL_PROFILES) {
            if (lower.contains(key)) return profile
        }
        return when {
            lower.contains("gemini") -> ModelContextProfile(modelName, 1_000_000, 8192)
            lower.contains("claude") -> ModelContextProfile(modelName, 200_000, 8192)
            lower.contains("gpt-4") -> ModelContextProfile(modelName, 128_000, 4096)
            lower.contains("deepseek") -> ModelContextProfile(modelName, 64_000, 8192)
            lower.contains("local") || lower.contains("gguf") -> ModelContextProfile(modelName, 4096, 2048, 512, true)
            else -> ModelContextProfile(modelName, 32_768, 4096) // Safe universal default
        }
    }

    /**
     * Fast, lightweight token estimation (~3.8 characters per token heuristic with code block weighting).
     */
    fun estimateTokens(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        var tokenCount = 0
        val lines = text.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // Code block lines or JSON syntax have higher token density
            val factor = if (trimmed.startsWith("```") || trimmed.startsWith("{") || trimmed.startsWith("\"") || trimmed.contains("val ") || trimmed.contains("fun ")) 3.2 else 3.8
            tokenCount += (trimmed.length / factor).toInt().coerceAtLeast(1)
        }
        return tokenCount.coerceAtLeast(1)
    }

    fun estimateMessagesTokens(messages: List<OmniMessage>): Int {
        var total = 0
        for (msg in messages) {
            total += 4 // overhead per message (role + framing)
            total += estimateTokens(msg.content)
            msg.tool_calls?.forEach { tc ->
                total += 6 + estimateTokens(tc.function.name) + estimateTokens(tc.function.arguments)
            }
        }
        return total
    }

    /**
     * Analyzes and optimizes message history to fit safely within the model's token headroom.
     */
    fun prepareContext(
        messages: List<OmniMessage>,
        modelName: String,
        systemPromptOverride: String? = null
    ): Pair<List<OmniMessage>, ContextBudgetReport> {
        val profile = getProfileForModel(modelName)
        val maxAllowedTokens = profile.maxContextTokens - profile.targetHeadroomTokens
        val originalTokens = estimateMessagesTokens(messages) + (systemPromptOverride?.let { estimateTokens(it) } ?: 0)

        if (originalTokens <= maxAllowedTokens) {
            val report = ContextBudgetReport(
                originalEstimatedTokens = originalTokens,
                compressedEstimatedTokens = originalTokens,
                tokenLimit = profile.maxContextTokens,
                compressionRatio = 1.0f,
                isTruncated = false,
                preservedMessageCount = messages.size,
                prunedMessageCount = 0
            )
            return Pair(messages, report)
        }

        // Context Exceeded: Execute Intelligent Pruning
        val warnings = mutableListOf<String>()
        warnings.add("Context tokens ($originalTokens) exceeded safe budget ($maxAllowedTokens) for $modelName. Pruning applied.")
        LogKeeper.log("ContextBudgetManager", "PruningTriggered", "Pruning from $originalTokens to <= $maxAllowedTokens tokens.")

        val pruned = SlidingWindowPruner.prune(
            messages = messages,
            targetTokenLimit = maxAllowedTokens,
            systemTokens = systemPromptOverride?.let { estimateTokens(it) } ?: 0
        )

        val finalTokens = estimateMessagesTokens(pruned) + (systemPromptOverride?.let { estimateTokens(it) } ?: 0)
        val prunedCount = (messages.size - pruned.size).coerceAtLeast(0)

        val report = ContextBudgetReport(
            originalEstimatedTokens = originalTokens,
            compressedEstimatedTokens = finalTokens,
            tokenLimit = profile.maxContextTokens,
            compressionRatio = if (originalTokens > 0) finalTokens.toFloat() / originalTokens.toFloat() else 1.0f,
            isTruncated = true,
            preservedMessageCount = pruned.size,
            prunedMessageCount = prunedCount,
            warnings = warnings
        )

        LogKeeper.log("ContextBudgetManager", "PruningComplete", "Retained ${pruned.size} messages (~$finalTokens tokens, saved ${originalTokens - finalTokens} tokens).")
        return Pair(pruned, report)
    }
}
