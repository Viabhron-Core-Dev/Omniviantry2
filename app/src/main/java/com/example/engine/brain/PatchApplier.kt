package com.example.engine.brain

import com.example.engine.fs.FileHistoryEngine
import com.example.engine.fs.LocalFileManager
import com.example.engine.tools.WorkspacePathResolver
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PatchApplier: Applies unified diff hunks to files with fuzzy line offset matching,
 * in-memory atomic pre-validation, revision history snapshots, and telemetry.
 */
object PatchApplier {

    sealed class PatchResult {
        data class Success(
            val file: File,
            val relativePath: String,
            val hunksApplied: Int,
            val newContent: String
        ) : PatchResult()

        data class Failure(
            val reason: String,
            val conflictedHunk: Int? = null,
            val relativePath: String? = null
        ) : PatchResult()
    }

    data class SimulationResult(
        val success: Boolean,
        val newContent: String?,
        val conflictedHunkIndex: Int?,
        val errorMessage: String?
    )

    /**
     * Applies a unified diff string to a specified target file or resolves paths from diff headers.
     */
    suspend fun applyPatch(
        diffText: String,
        targetFilePathOverride: String? = null,
        maxFuzz: Int = 3
    ): PatchResult = withContext(Dispatchers.IO) {
        val parsedFiles = UnifiedDiffParser.parse(diffText)
        if (parsedFiles.isEmpty()) {
            val failure = PatchResult.Failure("No valid diff or hunks detected in input.")
            LogKeeper.log("PatchApplier", "PatchFailed", failure.reason)
            return@withContext failure
        }

        val parsed = parsedFiles.first()
        val path = targetFilePathOverride?.takeIf { it.isNotBlank() } ?: parsed.targetPath
        if (path.isBlank()) {
            val failure = PatchResult.Failure("Target file path could not be resolved from diff or arguments.")
            LogKeeper.log("PatchApplier", "PatchFailed", failure.reason)
            return@withContext failure
        }

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            val err = targetFileRes.exceptionOrNull()?.message ?: "Invalid path: $path"
            val failure = PatchResult.Failure(err, relativePath = path)
            LogKeeper.log("PatchApplier", "PatchFailed", err)
            return@withContext failure
        }

        val file = targetFileRes.getOrThrow()
        if (!file.exists()) {
            val failure = PatchResult.Failure("File '$path' does not exist.", relativePath = path)
            LogKeeper.log("PatchApplier", "PatchFailed", failure.reason)
            return@withContext failure
        }

        if (file.isDirectory) {
            val failure = PatchResult.Failure("Target '$path' is a directory, not a file.", relativePath = path)
            LogKeeper.log("PatchApplier", "PatchFailed", failure.reason)
            return@withContext failure
        }

        val relPath = WorkspacePathResolver.getRelativePath(file)
        val originalText = file.readText()

        // Step 1: In-memory simulation and dry run with fuzzy offset matching
        val simulation = simulatePatch(originalText, parsed.hunks, maxFuzz, file.name)
        if (!simulation.success || simulation.newContent == null) {
            val hunkNum = (simulation.conflictedHunkIndex ?: 0) + 1
            val errorMsg = simulation.errorMessage ?: "Hunk #$hunkNum conflict in '$relPath'"
            LogKeeper.log(
                "PatchApplier",
                "HunkConflict",
                "Failed to match hunk header in file ${file.name} at line $hunkNum: $errorMsg"
            )
            return@withContext PatchResult.Failure(
                reason = errorMsg,
                conflictedHunk = hunkNum,
                relativePath = relPath
            )
        }

        val finalContent = simulation.newContent

        try {
            // Step 2: Save rollback revision prior to disk modification
            FileHistoryEngine.saveRevision(file)

            // Step 3: Write new content to disk
            file.writeText(finalContent)
            LocalFileManager.refreshFileTree()

            LogKeeper.log(
                "PatchApplier",
                "PatchSuccess",
                "Applied patch with ${parsed.hunks.size} hunks to file $relPath"
            )

            PatchResult.Success(
                file = file,
                relativePath = relPath,
                hunksApplied = parsed.hunks.size,
                newContent = finalContent
            )
        } catch (e: Exception) {
            val err = "Failed to write patch to file '$relPath': ${e.message}"
            LogKeeper.log("PatchApplier", "PatchFailed", err)
            PatchResult.Failure(err, relativePath = relPath)
        }
    }

    /**
     * Executes in-memory simulation of hunks against source text without disk I/O.
     */
    fun simulatePatch(
        originalText: String,
        hunks: List<UnifiedDiffParser.DiffHunk>,
        maxFuzz: Int = 3,
        fileName: String = "file"
    ): SimulationResult {
        if (hunks.isEmpty()) {
            return SimulationResult(
                success = true,
                newContent = originalText,
                conflictedHunkIndex = null,
                errorMessage = null
            )
        }

        val hasCrLf = originalText.contains("\r\n")
        val normalizedText = originalText.replace("\r\n", "\n")
        val lines = normalizedText.lines().toMutableList()

        var cumulativeDelta = 0

        for ((hunkIndex, hunk) in hunks.withIndex()) {
            val hunkNum = hunkIndex + 1

            // Build expected old lines from context and deletion lines
            val expectedOldLines = mutableListOf<String>()
            val replacementLines = mutableListOf<String>()

            for (dl in hunk.lines) {
                when (dl.type) {
                    UnifiedDiffParser.DiffLineType.CONTEXT -> {
                        expectedOldLines.add(dl.content)
                        replacementLines.add(dl.content)
                    }
                    UnifiedDiffParser.DiffLineType.DELETE -> {
                        expectedOldLines.add(dl.content)
                    }
                    UnifiedDiffParser.DiffLineType.ADD -> {
                        replacementLines.add(dl.content)
                    }
                }
            }

            // Ideal 0-based starting index in current modified lines buffer
            val nominalTargetIdx = (hunk.oldStart - 1) + cumulativeDelta

            // Generate search offsets: 0, 1, -1, 2, -2, ..., maxFuzz, -maxFuzz
            val searchOffsets = mutableListOf<Int>()
            for (f in 0..maxFuzz) {
                if (f == 0) {
                    searchOffsets.add(0)
                } else {
                    searchOffsets.add(f)
                    searchOffsets.add(-f)
                }
            }

            var matchedIndex: Int? = null

            for (offset in searchOffsets) {
                val candidateIdx = nominalTargetIdx + offset
                if (candidateIdx < 0 || candidateIdx + expectedOldLines.size > lines.size) {
                    continue
                }

                // Check match
                var isMatch = true
                for (i in expectedOldLines.indices) {
                    val fileLine = lines[candidateIdx + i]
                    val expectedLine = expectedOldLines[i]

                    // Exact match or trailing whitespace-trimmed match
                    if (fileLine != expectedLine && fileLine.trimEnd() != expectedLine.trimEnd()) {
                        isMatch = false
                        break
                    }
                }

                if (isMatch) {
                    matchedIndex = candidateIdx
                    break
                }
            }

            if (matchedIndex == null) {
                val reason = "Failed to match hunk header in file $fileName at line ${hunk.oldStart} (hunk #$hunkNum) within +/- $maxFuzz line fuzz"
                return SimulationResult(
                    success = false,
                    newContent = null,
                    conflictedHunkIndex = hunkIndex,
                    errorMessage = reason
                )
            }

            // Apply hunk replacement in lines buffer
            // Remove old lines
            for (i in 0 until expectedOldLines.size) {
                lines.removeAt(matchedIndex)
            }
            // Insert replacement lines
            lines.addAll(matchedIndex, replacementLines)

            // Update cumulative line delta
            cumulativeDelta += (replacementLines.size - expectedOldLines.size)
        }

        val joined = lines.joinToString("\n")
        val finalResult = if (hasCrLf) joined.replace("\n", "\r\n") else joined
        return SimulationResult(
            success = true,
            newContent = finalResult,
            conflictedHunkIndex = null,
            errorMessage = null
        )
    }
}
