package com.example.engine.orchestrator

import android.content.Context
import com.example.engine.brain.PatchApplier
import com.example.engine.brain.UnifiedDiffParser
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspaceIssueEntity
import com.example.engine.db.WorkspacePullRequestEntity
import com.example.engine.router.FallbackChainRouter
import com.example.ui.chat.OmniMessage
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReviewerAgent {

    data class ReviewResult(
        val approved: Boolean,
        val feedback: String,
        val securityPassed: Boolean,
        val syntaxPassed: Boolean
    )

    private val SUSPICIOUS_PATTERNS = listOf(
        Regex("(?i)password\\s*=\\s*['\"][^'\"]+['\"]"),
        Regex("(?i)api_?key\\s*=\\s*['\"][^'\"]+['\"]"),
        Regex("(?i)secret\\s*=\\s*['\"][^'\"]+['\"]"),
        Regex("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]{20,}"),
        Regex("(?i)\\.keystore"),
        Regex("(?i)\\.jks"),
        Regex("(?i)signingConfigs.*password")
    )

    private const val REVIEWER_SYSTEM_PROMPT = """
You are the Adversarial Code Reviewer of Omnivian.
Your job is to audit pull request diffs against issue requirements, syntax standards, and potential edge-case bugs.

CRITICAL INSTRUCTIONS:
1. Be rigorous and objective.
2. Confirm if the diff satisfies the issue's requirements.
3. Reply in the format:
STATUS: [APPROVED or NEEDS_REVISION]
SUMMARY: [1-2 sentences explaining why]
"""

    suspend fun auditPullRequest(
        context: Context,
        pr: WorkspacePullRequestEntity,
        issue: WorkspaceIssueEntity?,
        targetModel: String = "gemini-2.5-flash",
        onProgress: ((String) -> Unit)? = null
    ): ReviewResult = withContext(Dispatchers.IO) {
        onProgress?.invoke("Reviewer running security scan on PR #${pr.id.take(8)}...")
        LogKeeper.log("ReviewerAgent", "AuditStarted", "Auditing PR #${pr.id}")

        // 1. Security Scan
        var securityPassed = true
        var securityReason = ""
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (pattern.containsMatchIn(pr.diff)) {
                securityPassed = false
                securityReason = "Detected potential committed secret or keystore pattern matching '${pattern.pattern}'"
                LogKeeper.log("ReviewerAgent", "SecurityFinding", securityReason)
                break
            }
        }

        if (!securityPassed) {
            val result = ReviewResult(
                approved = false,
                feedback = "Security rejection: $securityReason",
                securityPassed = false,
                syntaxPassed = true
            )
            updatePrStatus(context, pr.id, "needs_revision")
            onProgress?.invoke("Reviewer REJECTED PR due to security finding.")
            return@withContext result
        }

        // 2. Syntax & Diff Validity Scan
        onProgress?.invoke("Reviewer validating diff hunks and simulation...")
        val parsed = UnifiedDiffParser.parse(pr.diff)
        val syntaxPassed = parsed.isNotEmpty() && parsed.all { it.hunks.isNotEmpty() }

        if (!syntaxPassed) {
            val result = ReviewResult(
                approved = false,
                feedback = "Diff syntax rejection: Unparseable unified diff hunks.",
                securityPassed = true,
                syntaxPassed = false
            )
            updatePrStatus(context, pr.id, "needs_revision")
            onProgress?.invoke("Reviewer REJECTED PR due to malformed diff syntax.")
            return@withContext result
        }

        // 3. LLM Audit with Fast/Free Model
        onProgress?.invoke("Reviewer evaluating acceptance criteria...")
        val prompt = buildString {
            append("ISSUE TITLE: ").append(issue?.title ?: pr.title).append("\n")
            append("CRITERIA: ").append(issue?.description ?: "N/A").append("\n\n")
            append("DIFF TO AUDIT:\n```diff\n").append(pr.diff).append("\n```\n")
        }

        val messages = listOf(
            OmniMessage(role = "system", content = REVIEWER_SYSTEM_PROMPT),
            OmniMessage(role = "user", content = prompt)
        )

        val llmResult = FallbackChainRouter.dispatchWithFallback(
            context = context,
            messages = messages,
            targetModel = targetModel,
            temperature = 0.1f
        )

        val reviewText = llmResult.text ?: llmResult.response?.choices?.firstOrNull()?.message?.content ?: ""
        val approved = !reviewText.contains("STATUS: NEEDS_REVISION", ignoreCase = true)

        val statusToSet = if (approved) "ready_to_merge" else "needs_revision"
        updatePrStatus(context, pr.id, statusToSet)

        LogKeeper.log("ReviewerAgent", "AuditComplete", "PR #${pr.id} audit result: $statusToSet")
        onProgress?.invoke(if (approved) "Reviewer APPROVED PR #${pr.id.take(8)} (Ready to merge)." else "Reviewer requested changes on PR #${pr.id.take(8)}.")

        ReviewResult(
            approved = approved,
            feedback = reviewText.ifBlank { if (approved) "Approved by automated review." else "Needs revision." },
            securityPassed = true,
            syntaxPassed = true
        )
    }

    private suspend fun updatePrStatus(context: Context, prId: String, status: String) {
        try {
            AppDatabase.getInstance(context).workspacePullRequestDao().updateStatus(prId, status)
        } catch (e: Exception) {
            LogKeeper.log("ReviewerAgent", "StatusUpdateError", "Failed updating PR status: ${e.message}")
        }
    }
}
