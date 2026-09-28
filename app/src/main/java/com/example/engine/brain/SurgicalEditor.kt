package com.example.engine.brain

import com.example.engine.fs.FileHistoryEngine
import com.example.engine.fs.LocalFileManager
import com.example.engine.tools.WorkspacePathResolver
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SurgicalEditor: High-precision search-and-replace engine for source files.
 * Provides strict uniqueness verification, line-ending normalization,
 * multi-chunk atomic pre-validation with rollback, and automated revision history snapshotting.
 */
object SurgicalEditor {

    data class EditChunk(
        val targetContent: String,
        val replacementContent: String
    )

    sealed class SurgicalEditResult {
        data class Success(
            val chunksApplied: Int,
            val relativePath: String,
            val newContent: String
        ) : SurgicalEditResult()

        data class Failure(
            val reason: String,
            val chunkIndex: Int? = null
        ) : SurgicalEditResult()
    }

    /**
     * Applies a single contiguous surgical edit to a file.
     */
    suspend fun applyEdit(
        path: String,
        targetContent: String,
        replacementContent: String
    ): SurgicalEditResult = applyMultiEdit(path, listOf(EditChunk(targetContent, replacementContent)))

    /**
     * Applies multiple surgical edit chunks atomically.
     * If any chunk fails validation (not found or non-unique), the entire operation is aborted
     * in memory before any disk modification occurs.
     */
    suspend fun applyMultiEdit(
        path: String,
        chunks: List<EditChunk>
    ): SurgicalEditResult = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) {
            val failure = SurgicalEditResult.Failure("No replacement chunks provided.")
            LogKeeper.log("SurgicalEditor", "EditFailed", failure.reason)
            return@withContext failure
        }

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            val err = targetFileRes.exceptionOrNull()?.message ?: "Invalid file path: $path"
            val failure = SurgicalEditResult.Failure(err)
            LogKeeper.log("SurgicalEditor", "EditFailed", err)
            return@withContext failure
        }

        val file = targetFileRes.getOrThrow()
        if (!file.exists()) {
            val failure = SurgicalEditResult.Failure("File '$path' does not exist.")
            LogKeeper.log("SurgicalEditor", "EditFailed", failure.reason)
            return@withContext failure
        }

        if (file.isDirectory) {
            val failure = SurgicalEditResult.Failure("Target path '$path' is a directory, not a file.")
            LogKeeper.log("SurgicalEditor", "EditFailed", failure.reason)
            return@withContext failure
        }

        val relPath = WorkspacePathResolver.getRelativePath(file)

        try {
            val originalText = file.readText()

            // In-memory simulation and validation pass
            val simulationResult = simulateEdits(originalText, chunks, relPath)
            if (simulationResult is SimulationResult.Failure) {
                val failure = SurgicalEditResult.Failure(simulationResult.reason, simulationResult.chunkIndex)
                LogKeeper.log("SurgicalEditor", "EditFailed", "Validation failed for $relPath: ${simulationResult.reason}")
                return@withContext failure
            }

            val finalContent = (simulationResult as SimulationResult.Success).content

            // Save revision snapshot before touching disk
            FileHistoryEngine.saveRevision(file)

            // Commit validated content to disk
            file.writeText(finalContent)
            LocalFileManager.refreshFileTree()

            LogKeeper.log(
                "SurgicalEditor",
                "EditSuccess",
                "Applied ${chunks.size} replacement chunk(s) to $relPath"
            )

            SurgicalEditResult.Success(
                chunksApplied = chunks.size,
                relativePath = relPath,
                newContent = finalContent
            )
        } catch (e: Exception) {
            val errorMsg = "Exception during surgical edit on '$relPath': ${e.message}"
            LogKeeper.log("SurgicalEditor", "EditFailed", errorMsg)
            SurgicalEditResult.Failure(errorMsg)
        }
    }

    private sealed class SimulationResult {
        data class Success(val content: String) : SimulationResult()
        data class Failure(val reason: String, val chunkIndex: Int) : SimulationResult()
    }

    /**
     * Executes atomic dry-run simulation across all chunks sequentially.
     */
    private fun simulateEdits(
        initialText: String,
        chunks: List<EditChunk>,
        relPath: String
    ): SimulationResult {
        var currentText = initialText

        for ((index, chunk) in chunks.withIndex()) {
            val chunkNum = index + 1
            val rawTarget = chunk.targetContent
            val rawReplacement = chunk.replacementContent

            if (rawTarget.isEmpty()) {
                return SimulationResult.Failure(
                    "Error in Chunk #$chunkNum: Target content cannot be empty.",
                    index
                )
            }

            // Step 1: Direct literal search
            var activeTarget = rawTarget
            var count = countOccurrences(currentText, activeTarget)

            // Step 2: Line-ending normalization fallback if exact match fails
            if (count == 0 && (currentText.contains("\r\n") || rawTarget.contains("\r\n") || currentText.contains("\n"))) {
                val normalizedBuffer = currentText.replace("\r\n", "\n")
                val normalizedTarget = rawTarget.replace("\r\n", "\n")
                val normalizedCount = countOccurrences(normalizedBuffer, normalizedTarget)

                if (normalizedCount > 0) {
                    currentText = normalizedBuffer
                    activeTarget = normalizedTarget
                    count = normalizedCount
                }
            }

            // Step 3: Strict uniqueness verification
            if (count == 0) {
                return SimulationResult.Failure(
                    "Error in Chunk #$chunkNum: Target content not found in '$relPath'. Use 'view_file' to check line numbers and whitespace.",
                    index
                )
            }

            if (count > 1) {
                return SimulationResult.Failure(
                    "Error in Chunk #$chunkNum: Target content matched $count occurrences in '$relPath'. It must be unique (include more context lines).",
                    index
                )
            }

            // Step 4: Apply replacement in working buffer
            currentText = replaceFirstLiteral(currentText, activeTarget, rawReplacement)
        }

        return SimulationResult.Success(currentText)
    }

    /**
     * Counts literal occurrences of target in text without regex evaluation.
     */
    private fun countOccurrences(text: String, target: String): Int {
        if (target.isEmpty() || text.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(target, index)
            if (index != -1) {
                count++
                index += target.length
            } else {
                break
            }
        }
        return count
    }

    /**
     * Replaces the first literal occurrence of target with replacement in text.
     */
    private fun replaceFirstLiteral(text: String, target: String, replacement: String): String {
        val index = text.indexOf(target)
        if (index == -1) return text
        return buildString(text.length - target.length + replacement.length) {
            append(text, 0, index)
            append(replacement)
            append(text, index + target.length, text.length)
        }
    }
}
