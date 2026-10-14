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
    val breakdown: ContextBudgetBreakdown? = null,
    val warnings: List<String> = emptyList()
)

/**
 * High-resolution segmented breakdown of the active context budget.
 */
data class ContextBudgetBreakdown(
    val modelName: String,
    val totalLimit: Int,
    val systemAndRulesTokens: Int,
    val rollingSummaryTokens: Int,
    val activeMessagesTokens: Int,
    val reservedHeadroomTokens: Int,
    val totalUsedTokens: Int,
    val usagePercentage: Float
)

/**
 * Mini-Phase 11.1 & Phase 14: Dynamic Context Window Compressor & Token Budget Engine.
 * Accurately estimates token usage, enforces dynamic model-specific profiles, and eliminates
 * context drift across Cloud APIs and Local GGUF models.
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

        // DeepSeek & OpenRouter
        "deepseek-r1" to ModelContextProfile("deepseek-r1", 64_000, 8192),
        "deepseek-v3" to ModelContextProfile("deepseek-v3", 64_000, 8192),
        "llama-3.3-70b-versatile" to ModelContextProfile("llama-3.3-70b-versatile", 128_000, 8192),
        "mixtral-8x7b-32768" to ModelContextProfile("mixtral-8x7b-32768", 32_768, 4096),

        // Local GGUF defaults
        "local-default" to ModelContextProfile("local-default", 4096, 2048, targetHeadroomTokens = 512, isLocalGguf = true)
    )

    fun getProfileForModel(modelName: String, localContextOverride: Int? = null): ModelContextProfile {
        val lower = modelName.lowercase()
        val baseProfile = when {
            lower.contains("gemini") -> ModelContextProfile(modelName, 1_000_000, 8192)
            lower.contains("claude") -> ModelContextProfile(modelName, 200_000, 8192)
            lower.contains("gpt-4") -> ModelContextProfile(modelName, 128_000, 4096)
            lower.contains("deepseek") -> ModelContextProfile(modelName, 64_000, 8192)
            lower.contains("local") || lower.contains("gguf") -> {
                val ctx = localContextOverride ?: 2048
                val headroom = (ctx * 0.20f).toInt().coerceIn(256, 1024)
                ModelContextProfile(modelName, ctx, ctx - headroom, targetHeadroomTokens = headroom, isLocalGguf = true)
            }
            else -> {
                MODEL_PROFILES.entries.firstOrNull { lower.contains(it.key) }?.value
                    ?: ModelContextProfile(modelName, 32_768, 4096)
            }
        }

        if (baseProfile.isLocalGguf && localContextOverride != null && localContextOverride > 0) {
            val headroom = (localContextOverride * 0.20f).toInt().coerceIn(256, 1024)
            return baseProfile.copy(
                maxContextTokens = localContextOverride,
                maxOutputTokens = localContextOverride - headroom,
                targetHeadroomTokens = headroom
            )
        }

        return baseProfile
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
     * Analyzes and optimizes message history to fit safely within the model's dynamic token budget.
     */
    fun prepareContext(
        messages: List<OmniMessage>,
        modelName: String,
        systemPromptOverride: String? = null,
        localContextOverride: Int? = null,
        rollingSummary: String? = null
    ): Pair<List<OmniMessage>, ContextBudgetReport> {
        val profile = getProfileForModel(modelName, localContextOverride)
        val maxAllowedTokens = profile.maxContextTokens - profile.targetHeadroomTokens

        val systemTokens = systemPromptOverride?.let { estimateTokens(it) } ?: 0
        val summaryTokens = rollingSummary?.let { estimateTokens(it) } ?: 0
        val baseNonMessageTokens = systemTokens + summaryTokens
        val originalMessageTokens = estimateMessagesTokens(messages)
        val originalTotalTokens = originalMessageTokens + baseNonMessageTokens

        if (originalTotalTokens <= maxAllowedTokens) {
            val breakdown = ContextBudgetBreakdown(
                modelName = modelName,
                totalLimit = profile.maxContextTokens,
                systemAndRulesTokens = systemTokens,
                rollingSummaryTokens = summaryTokens,
                activeMessagesTokens = originalMessageTokens,
                reservedHeadroomTokens = profile.targetHeadroomTokens,
                totalUsedTokens = originalTotalTokens,
                usagePercentage = (originalTotalTokens.toFloat() / profile.maxContextTokens.toFloat()).coerceIn(0f, 1f)
            )

            val report = ContextBudgetReport(
                originalEstimatedTokens = originalTotalTokens,
                compressedEstimatedTokens = originalTotalTokens,
                tokenLimit = profile.maxContextTokens,
                compressionRatio = 1.0f,
                isTruncated = false,
                preservedMessageCount = messages.size,
                prunedMessageCount = 0,
                breakdown = breakdown
            )
            return Pair(messages, report)
        }

        // Context Exceeded: Execute Intelligent Pruning
        val warnings = mutableListOf<String>()
        warnings.add("Context tokens ($originalTotalTokens) exceeded safe budget ($maxAllowedTokens) for $modelName. Sliding window pruning applied.")
        LogKeeper.log("ContextBudgetManager", "PruningTriggered", "Pruning from $originalTotalTokens to <= $maxAllowedTokens tokens.")

        val pruned = SlidingWindowPruner.prune(
            messages = messages,
            targetTokenLimit = maxAllowedTokens,
            systemTokens = baseNonMessageTokens
        )

        val prunedMessageTokens = estimateMessagesTokens(pruned)
        val finalTokens = prunedMessageTokens + baseNonMessageTokens
        val prunedCount = (messages.size - pruned.size).coerceAtLeast(0)

        val breakdown = ContextBudgetBreakdown(
            modelName = modelName,
            totalLimit = profile.maxContextTokens,
            systemAndRulesTokens = systemTokens,
            rollingSummaryTokens = summaryTokens,
            activeMessagesTokens = prunedMessageTokens,
            reservedHeadroomTokens = profile.targetHeadroomTokens,
            totalUsedTokens = finalTokens,
            usagePercentage = (finalTokens.toFloat() / profile.maxContextTokens.toFloat()).coerceIn(0f, 1f)
        )

        val report = ContextBudgetReport(
            originalEstimatedTokens = originalTotalTokens,
            compressedEstimatedTokens = finalTokens,
            tokenLimit = profile.maxContextTokens,
            compressionRatio = if (originalTotalTokens > 0) finalTokens.toFloat() / originalTotalTokens.toFloat() else 1.0f,
            isTruncated = true,
            preservedMessageCount = pruned.size,
            prunedMessageCount = prunedCount,
            breakdown = breakdown,
            warnings = warnings
        )

        LogKeeper.log(
            "ContextBudgetManager",
            "PruningComplete",
            "Retained ${pruned.size} messages (~$finalTokens tokens, pruned $prunedCount messages, preserved summary & pinned rules)."
        )
        return Pair(pruned, report)
    }
}
