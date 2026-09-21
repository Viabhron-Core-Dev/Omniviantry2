package com.example.engine.brain

import com.example.utils.LogKeeper

/**
 * Parsed reasoning structure.
 */
data class ReasoningParseResult(
    val thoughtContent: String? = null,
    val planContent: String? = null,
    val cleanAnswer: String,
    val hasThinking: Boolean = false,
    val thinkingDurationMs: Long = 0L
)

/**
 * Mini-Phase 11.2: Chain-of-Thought (CoT) & Structured Reasoning Parser.
 * Seamlessly isolates <thought>, <thinking>, <antThinking>, <plan>, and reasoning headers
 * from final clean assistant answers for models like DeepSeek R1, Claude 3.7 Thinking, and Gemini 2.5 Flash Thinking.
 */
object ReasoningParser {

    private val THOUGHT_TAG_REGEX = Regex(
        "(?s)<(?:thought|thinking|antThinking)>(.*?)</(?:thought|thinking|antThinking)>",
        RegexOption.IGNORE_CASE
    )

    private val PLAN_TAG_REGEX = Regex(
        "(?s)<(?:plan|planning)>(.*?)</(?:plan|planning)>",
        RegexOption.IGNORE_CASE
    )

    // Fallback for models outputting unclosed thinking tags during streaming or completion
    private val UNCLOSED_THOUGHT_REGEX = Regex(
        "(?s)<(?:thought|thinking|antThinking)>(.*)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses raw LLM text into separated thought process, plan, and final clean answer.
     */
    fun parse(rawText: String, elapsedMs: Long = 0L): ReasoningParseResult {
        if (rawText.isBlank()) {
            return ReasoningParseResult(cleanAnswer = "")
        }

        var workingText = rawText
        val thoughts = mutableListOf<String>()
        val plans = mutableListOf<String>()

        // 1. Extract closed <thought> / <thinking> tags
        val thoughtMatches = THOUGHT_TAG_REGEX.findAll(workingText).toList()
        for (match in thoughtMatches) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                thoughts.add(content)
            }
        }
        workingText = THOUGHT_TAG_REGEX.replace(workingText, "")

        // 2. Extract closed <plan> tags
        val planMatches = PLAN_TAG_REGEX.findAll(workingText).toList()
        for (match in planMatches) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                plans.add(content)
            }
        }
        workingText = PLAN_TAG_REGEX.replace(workingText, "")

        // 3. Handle unclosed trailing thinking tags (if any)
        val unclosedMatch = UNCLOSED_THOUGHT_REGEX.find(workingText)
        if (unclosedMatch != null && thoughts.isEmpty()) {
            val unclosedContent = unclosedMatch.groupValues[1].trim()
            if (unclosedContent.isNotEmpty()) {
                thoughts.add(unclosedContent)
            }
            workingText = UNCLOSED_THOUGHT_REGEX.replace(workingText, "")
            LogKeeper.log("ReasoningParser", "UnclosedTagHandled", "Parsed unclosed thinking tag successfully")
        }

        val cleanAnswer = workingText.trim()
        val combinedThought = if (thoughts.isNotEmpty()) thoughts.joinToString("\n\n") else null
        val combinedPlan = if (plans.isNotEmpty()) plans.joinToString("\n\n") else null
        val hasThinking = !combinedThought.isNullOrBlank() || !combinedPlan.isNullOrBlank()

        if (hasThinking) {
            LogKeeper.log(
                "ReasoningParser",
                "ReasoningExtracted",
                "Extracted ${combinedThought?.length ?: 0} chars of reasoning, ${combinedPlan?.length ?: 0} chars of plan in ${elapsedMs}ms"
            )
        }

        return ReasoningParseResult(
            thoughtContent = combinedThought,
            planContent = combinedPlan,
            cleanAnswer = cleanAnswer,
            hasThinking = hasThinking,
            thinkingDurationMs = elapsedMs
        )
    }

    /**
     * Cleans raw text for display if only the clean answer is desired.
     */
    fun cleanTextOnly(rawText: String): String {
        return parse(rawText).cleanAnswer
    }
}
