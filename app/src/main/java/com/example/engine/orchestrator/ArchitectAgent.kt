package com.example.engine.orchestrator

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspaceIssueEntity
import com.example.engine.router.FallbackChainRouter
import com.example.ui.chat.OmniMessage
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ArchitectAgent {

    private const val ARCHITECT_SYSTEM_PROMPT = """
You are the Lead Software Architect of Omnivian.
Your role is to analyze the user's objective, evaluate the project requirements, and decompose the task into 1 to 3 atomic, independent, and actionable engineering issues.

CRITICAL INSTRUCTIONS:
1. Return ONLY a valid JSON array of objects. Do not wrap in markdown or backticks if possible, or use standard ```json ... ``` blocks.
2. Each object MUST contain:
   - "title": Concise, imperative title (e.g. "Add Token Bucket Rate Limiter")
   - "description": Detailed specification, context, and clear acceptance criteria.
   - "targetFile": The primary file path to modify or create (e.g. "app/src/main/java/com/example/engine/rate/TokenBucket.kt").

Example format:
[
  {
    "title": "Create User Preferences DataStore",
    "description": "Implement DataStore repository with Flow exposure for theme and notification flags. Acceptance criteria: Flow is reactive, keys are typed.",
    "targetFile": "app/src/main/java/com/example/data/UserPrefsRepository.kt"
  }
]
"""

    suspend fun planIssues(
        context: Context,
        workspaceId: String,
        objective: String,
        targetModel: String = "gemini-2.5-pro",
        onProgress: ((String) -> Unit)? = null
    ): List<WorkspaceIssueEntity> = withContext(Dispatchers.IO) {
        onProgress?.invoke("Architect analyzing objective and repository requirements...")
        LogKeeper.log("ArchitectAgent", "PlanningStarted", "Planning objective for workspace: $workspaceId")

        val messages = listOf(
            OmniMessage(role = "system", content = ARCHITECT_SYSTEM_PROMPT),
            OmniMessage(role = "user", content = "Workspace Objective: $objective\nDecompose this into atomic issues.")
        )

        val result = FallbackChainRouter.dispatchWithFallback(
            context = context,
            messages = messages,
            targetModel = targetModel,
            temperature = 0.2f
        )

        val responseText = result.text ?: result.response?.choices?.firstOrNull()?.message?.content ?: ""
        if (responseText.isBlank()) {
            LogKeeper.log("ArchitectAgent", "PlanningFailed", "Empty response from provider")
            // Fallback: create single issue matching the objective
            val fallbackIssue = WorkspaceIssueEntity(
                id = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                title = objective.take(60),
                description = objective,
                targetFile = null,
                status = "open",
                createdAt = System.currentTimeMillis()
            )
            AppDatabase.getInstance(context).workspaceIssueDao().saveIssue(fallbackIssue)
            return@withContext listOf(fallbackIssue)
        }

        val parsedIssues = parseIssuesFromJson(responseText, workspaceId)
        val finalIssues = if (parsedIssues.isEmpty()) {
            listOf(
                WorkspaceIssueEntity(
                    id = UUID.randomUUID().toString(),
                    workspaceId = workspaceId,
                    title = objective.take(60),
                    description = responseText.take(500),
                    targetFile = null,
                    status = "open",
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            parsedIssues
        }

        val dao = AppDatabase.getInstance(context).workspaceIssueDao()
        finalIssues.forEach { issue ->
            dao.saveIssue(issue)
            LogKeeper.log("ArchitectAgent", "IssueSaved", "Saved issue: ${issue.title} (target: ${issue.targetFile})")
        }

        onProgress?.invoke("Architect created ${finalIssues.size} actionable issues.")
        finalIssues
    }

    private fun parseIssuesFromJson(rawText: String, workspaceId: String): List<WorkspaceIssueEntity> {
        val issues = mutableListOf<WorkspaceIssueEntity>()
        try {
            var cleanJson = rawText.trim()
            if (cleanJson.contains("```json")) {
                cleanJson = cleanJson.substringAfter("```json").substringBefore("```").trim()
            } else if (cleanJson.contains("```")) {
                cleanJson = cleanJson.substringAfter("```").substringBefore("```").trim()
            }

            val startIndex = cleanJson.indexOf('[')
            val endIndex = cleanJson.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1)
            }

            val jsonArray = JSONArray(cleanJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "Task #${i + 1}")
                val description = obj.optString("description", "")
                val targetFile = obj.optString("targetFile").takeIf { it.isNotBlank() }
                
                issues.add(
                    WorkspaceIssueEntity(
                        id = UUID.randomUUID().toString(),
                        workspaceId = workspaceId,
                        title = title,
                        description = description,
                        targetFile = targetFile,
                        status = "open",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            LogKeeper.log("ArchitectAgent", "JsonParseError", "Failed parsing issues JSON: ${e.message}")
        }
        return issues
    }
}
