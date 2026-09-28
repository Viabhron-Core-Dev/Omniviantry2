package com.example.engine.brain

/**
 * UnifiedDiffParser: Parses standard Git / Unified Diff formats into structured
 * DiffFile, DiffHunk, and DiffLine objects.
 */
object UnifiedDiffParser {

    enum class DiffLineType {
        CONTEXT,
        ADD,
        DELETE
    }

    data class DiffLine(
        val type: DiffLineType,
        val content: String,
        val oldLineNumber: Int? = null,
        val newLineNumber: Int? = null
    )

    data class DiffHunk(
        val oldStart: Int,
        val oldLength: Int,
        val newStart: Int,
        val newLength: Int,
        val sectionHeader: String = "",
        val lines: List<DiffLine> = emptyList()
    ) {
        val additionsCount: Int get() = lines.count { it.type == DiffLineType.ADD }
        val deletionsCount: Int get() = lines.count { it.type == DiffLineType.DELETE }
        val contextCount: Int get() = lines.count { it.type == DiffLineType.CONTEXT }
    }

    data class ParsedDiffFile(
        val oldPath: String?,
        val newPath: String?,
        val hunks: List<DiffHunk> = emptyList()
    ) {
        val targetPath: String get() {
            val candidate = newPath ?: oldPath ?: ""
            return candidate
                .removePrefix("a/")
                .removePrefix("b/")
                .removePrefix("/")
                .trim()
        }

        val totalAdditions: Int get() = hunks.sumOf { it.additionsCount }
        val totalDeletions: Int get() = hunks.sumOf { it.deletionsCount }
    }

    private val HUNK_HEADER_REGEX = Regex(
        """^@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@(.*)$"""
    )

    /**
     * Parses unified diff text into a list of [ParsedDiffFile]s.
     */
    fun parse(diffText: String): List<ParsedDiffFile> {
        val cleanText = stripCodeFences(diffText)
        val rawLines = cleanText.lines()

        val files = mutableListOf<ParsedDiffFile>()
        var currentOldPath: String? = null
        var currentNewPath: String? = null
        val currentHunks = mutableListOf<DiffHunk>()

        var currentOldStart = 0
        var currentOldLength = 0
        var currentNewStart = 0
        var currentNewLength = 0
        var currentSection = ""
        var currentHunkLines = mutableListOf<DiffLine>()
        var inHunk = false

        var oldLineTracker = 0
        var newLineTracker = 0

        fun flushHunk() {
            if (inHunk && (currentHunkLines.isNotEmpty() || currentOldLength > 0 || currentNewLength > 0)) {
                currentHunks.add(
                    DiffHunk(
                        oldStart = currentOldStart,
                        oldLength = currentOldLength,
                        newStart = currentNewStart,
                        newLength = currentNewLength,
                        sectionHeader = currentSection,
                        lines = currentHunkLines.toList()
                    )
                )
                currentHunkLines = mutableListOf()
                inHunk = false
            }
        }

        fun flushFile() {
            flushHunk()
            if (currentHunks.isNotEmpty() || currentOldPath != null || currentNewPath != null) {
                files.add(
                    ParsedDiffFile(
                        oldPath = currentOldPath,
                        newPath = currentNewPath,
                        hunks = currentHunks.toList()
                    )
                )
                currentHunks.clear()
                currentOldPath = null
                currentNewPath = null
            }
        }

        for (line in rawLines) {
            val trimmed = line.trimEnd('\r')

            // File headers: --- a/path and +++ b/path
            if (trimmed.startsWith("--- ")) {
                if (inHunk) flushHunk()
                if (currentHunks.isNotEmpty()) flushFile()
                currentOldPath = extractFilePath(trimmed.removePrefix("--- "))
                continue
            }

            if (trimmed.startsWith("+++ ")) {
                currentNewPath = extractFilePath(trimmed.removePrefix("+++ "))
                continue
            }

            // Hunk header: @@ -oldStart[,oldLen] +newStart[,newLen] @@
            val hunkMatch = HUNK_HEADER_REGEX.find(trimmed)
            if (hunkMatch != null) {
                flushHunk()

                currentOldStart = hunkMatch.groupValues[1].toIntOrNull() ?: 1
                currentOldLength = hunkMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
                currentNewStart = hunkMatch.groupValues[3].toIntOrNull() ?: 1
                currentNewLength = hunkMatch.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
                currentSection = hunkMatch.groupValues[5].trim()

                oldLineTracker = currentOldStart
                newLineTracker = currentNewStart
                inHunk = true
                continue
            }

            if (!inHunk) {
                // Ignore lines before first hunk header (e.g. diff --git, index ..., etc.)
                continue
            }

            // Inside a hunk
            if (trimmed.startsWith("\\ No newline at end of file")) {
                continue
            }

            if (trimmed.startsWith("+")) {
                val content = trimmed.substring(1)
                currentHunkLines.add(
                    DiffLine(
                        type = DiffLineType.ADD,
                        content = content,
                        oldLineNumber = null,
                        newLineNumber = newLineTracker++
                    )
                )
            } else if (trimmed.startsWith("-")) {
                val content = trimmed.substring(1)
                currentHunkLines.add(
                    DiffLine(
                        type = DiffLineType.DELETE,
                        content = content,
                        oldLineNumber = oldLineTracker++,
                        newLineNumber = null
                    )
                )
            } else if (trimmed.startsWith(" ")) {
                val content = trimmed.substring(1)
                currentHunkLines.add(
                    DiffLine(
                        type = DiffLineType.CONTEXT,
                        content = content,
                        oldLineNumber = oldLineTracker++,
                        newLineNumber = newLineTracker++
                    )
                )
            } else {
                // Some models emit context lines without a leading space
                currentHunkLines.add(
                    DiffLine(
                        type = DiffLineType.CONTEXT,
                        content = trimmed,
                        oldLineNumber = oldLineTracker++,
                        newLineNumber = newLineTracker++
                    )
                )
            }
        }

        flushFile()
        return files
    }

    private fun extractFilePath(raw: String): String {
        val cleaned = raw.trim()
        val parts = cleaned.split("\t")
        return parts.firstOrNull()?.trim() ?: cleaned
    }

    private fun stripCodeFences(text: String): String {
        var result = text.trim()
        if (result.startsWith("```diff") || result.startsWith("```patch") || result.startsWith("```")) {
            val firstLineEnd = result.indexOf('\n')
            if (firstLineEnd != -1) {
                result = result.substring(firstLineEnd + 1)
            }
        }
        if (result.endsWith("```")) {
            val lastFence = result.lastIndexOf("```")
            if (lastFence != -1) {
                result = result.substring(0, lastFence).trimEnd()
            }
        }
        return result
    }
}
