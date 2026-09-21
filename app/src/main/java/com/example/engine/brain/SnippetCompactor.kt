package com.example.engine.brain

import com.example.utils.LogKeeper

/**
 * Snippet Compactor.
 * Automatically shortens large code blocks, file contents, and tool outputs
 * into focused, high-information windows when context budget is constrained.
 */
object SnippetCompactor {

    /**
     * Compacts a large string by keeping the top N and bottom N lines with a summary divider.
     */
    fun compact(content: String, maxLines: Int = 20): String {
        val lines = content.lines()
        if (lines.size <= maxLines) return content

        val keepTop = maxLines / 2
        val keepBottom = maxLines / 2
        val omittedCount = lines.size - (keepTop + keepBottom)

        if (omittedCount <= 2) return content

        val topLines = lines.take(keepTop)
        val bottomLines = lines.takeLast(keepBottom)

        val builder = StringBuilder()
        topLines.forEach { builder.appendLine(it) }
        builder.appendLine("\n// ... [$omittedCount lines omitted by Context Budget Compactor] ...\n")
        bottomLines.forEach { builder.appendLine(it) }

        LogKeeper.log("SnippetCompactor", "Compacted", "Omitted $omittedCount lines from large snippet (${lines.size} -> ${topLines.size + bottomLines.size} lines)")
        return builder.toString()
    }

    /**
     * Compacts large JSON payloads or tool outputs.
     */
    fun compactJsonOutput(rawJson: String, maxChars: Int = 1000): String {
        if (rawJson.length <= maxChars) return rawJson
        val truncated = rawJson.take(maxChars)
        val omitted = rawJson.length - maxChars
        return "$truncated\n... [Truncated $omitted chars to preserve token budget]"
    }
}
