package com.example.engine.orchestrator

import android.content.Context
import com.example.engine.brain.UnifiedDiffParser
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspaceIssueEntity
import com.example.engine.db.WorkspacePullRequestEntity
import com.example.engine.router.FallbackChainRouter
import com.example.engine.tools.WorkspacePathResolver
import com.example.ui.chat.OmniMessage
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object CoderAgent {

    private const val CODER_SYSTEM_PROMPT = """
You are the Senior Staff Coder of Omnivian.
Your job is to implement code changes for a specific issue by outputting a standard Unified Diff.

CRITICAL INSTRUCTIONS:
1. Output MUST be formatted as a standard git unified diff:
--- a/path/to/file.ext
+++ b/path/to/file.ext
@@ -line,count +line,count @@
 context line
-removed line
+added line
 context line

2. Follow exact unified diff syntax:
   - Begin with `--- a/<file>` and `+++ b/<file>`
   - Each hunk must begin with `@@ -x,y +a,b @@`
   - Unchanged context lines must start with a single space ` `
   - Deletions must start with `-`
   - Insertions must start with `+`
3. NEVER commit credentials, API keys, or keystores.
4. Output the diff inside a standard ```diff ... ``` code block.
"""

    suspend fun implementIssue(
        context: Context,
        issue: WorkspaceIssueEntity,
        targetModel: String = "gemini-2.5-pro",
        onProgress: ((String) -> Unit)? = null
    ): WorkspacePullRequestEntity? = withContext(Dispatchers.IO) {
        onProgress?.invoke("Coder reading file context for '${issue.title}'...")
        LogKeeper.log("CoderAgent", "ImplementationStarted", "Starting implementation for issue: ${issue.id}")

        val targetPath = issue.targetFile
        var currentFileContent = ""
        var relativePath = targetPath ?: "unknown.txt"

        if (!targetPath.isNullOrBlank()) {
            val resolvedFile = WorkspacePathResolver.resolve(targetPath).getOrNull()
            if (resolvedFile != null && resolvedFile.exists()) {
                currentFileContent = try {
                    resolvedFile.readText()
                } catch (e: Exception) {
                    ""
                }
                relativePath = WorkspacePathResolver.getRelativePath(resolvedFile)
            }
        }

        val promptBuilder = StringBuilder()
        promptBuilder.append("ISSUE TITLE: ").append(issue.title).append("\n")
        promptBuilder.append("SPECIFICATION & CRITERIA:\n").append(issue.description).append("\n\n")
        promptBuilder.append("TARGET FILE: ").append(relativePath).append("\n")

        if (currentFileContent.isNotBlank()) {
            promptBuilder.append("EXISTING FILE CONTENT:\n```\n")
            // Provide up to first 400 lines to avoid token overflow
            val truncated = currentFileContent.lines().take(400).joinToString("\n")
            promptBuilder.append(truncated).append("\n```\n")
        } else {
            promptBuilder.append("TARGET FILE DOES NOT EXIST YET. Create it as a new file diff (--- /dev/null +++ b/$relativePath).\n")
        }

        onProgress?.invoke("Coder generating surgical unified diff...")

        val messages = listOf(
            OmniMessage(role = "system", content = CODER_SYSTEM_PROMPT),
            OmniMessage(role = "user", content = promptBuilder.toString())
        )

        val result = FallbackChainRouter.dispatchWithFallback(
            context = context,
            messages = messages,
            targetModel = targetModel,
            temperature = 0.1f
        )

        val rawOutput = result.text ?: result.response?.choices?.firstOrNull()?.message?.content ?: ""
        val diffText = extractDiff(rawOutput)

        if (diffText.isBlank()) {
            LogKeeper.log("CoderAgent", "DiffExtractionFailed", "Could not extract diff from response for issue: ${issue.id}")
            return@withContext null
        }

        // Validate diff using UnifiedDiffParser
        val parsedHunks = UnifiedDiffParser.parse(diffText)
        if (parsedHunks.isEmpty()) {
            LogKeeper.log("CoderAgent", "DiffParseFailed", "Diff syntax was invalid for issue: ${issue.id}")
        }

        val pr = WorkspacePullRequestEntity(
            id = UUID.randomUUID().toString(),
            workspaceId = issue.workspaceId,
            title = "PR: ${issue.title}",
            description = "Automated implementation for issue '${issue.title}'.\n${issue.description}",
            targetFile = relativePath,
            diff = diffText,
            status = "open",
            createdAt = System.currentTimeMillis()
        )

        AppDatabase.getInstance(context).workspacePullRequestDao().savePullRequest(pr)
        LogKeeper.log("CoderAgent", "PRCreated", "Saved Pull Request: #${pr.id} for issue: ${issue.id}")
        onProgress?.invoke("Coder generated PR #${pr.id.take(8)} (${parsedHunks.sumOf { it.hunks.size }} hunks).")
        pr
    }

    private fun extractDiff(raw: String): String {
        var text = raw.trim()
        if (text.contains("```diff")) {
            return text.substringAfter("```diff").substringBefore("```").trim()
        }
        if (text.contains("```")) {
            val block = text.substringAfter("```").substringBefore("```").trim()
            if (block.contains("@@") || block.contains("---")) {
                return block
            }
        }
        if (text.contains("--- a/") || text.contains("--- /dev/null") || text.contains("@@ ")) {
            return text
        }
        return ""
    }
}
