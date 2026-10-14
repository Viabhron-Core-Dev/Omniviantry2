package com.example.engine.orchestrator

import android.content.Context
import com.example.engine.brain.PatchApplier
import com.example.engine.db.AppDatabase
import com.example.engine.fs.LocalFileManager
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WorkspacePrManager {

    sealed class MergeResult {
        data class Success(val prId: String, val hunksApplied: Int, val targetFile: String) : MergeResult()
        data class Failure(val prId: String, val reason: String) : MergeResult()
    }

    suspend fun mergePullRequest(
        context: Context,
        prId: String
    ): MergeResult = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val prDao = db.workspacePullRequestDao()
        val issueDao = db.workspaceIssueDao()

        val pr = prDao.getPullRequestById(prId)
        if (pr == null) {
            val failure = MergeResult.Failure(prId, "Pull Request #$prId not found.")
            LogKeeper.log("WorkspacePrManager", "MergeError", failure.reason)
            return@withContext failure
        }

        LogKeeper.log("WorkspacePrManager", "MergeStarted", "Attempting merge for PR #${pr.id} into '${pr.targetFile}'")

        val patchResult = PatchApplier.applyPatch(
            diffText = pr.diff,
            targetFilePathOverride = pr.targetFile
        )

        when (patchResult) {
            is PatchApplier.PatchResult.Success -> {
                prDao.updateStatus(prId, "merged")
                
                // If there is a matching issue with same title or target, close it
                val issues = issueDao.getIssuesList(pr.workspaceId)
                val matchingIssue = issues.firstOrNull { 
                    it.title.equals(pr.title.removePrefix("PR: ").trim(), ignoreCase = true) ||
                    (it.targetFile != null && it.targetFile == pr.targetFile)
                }
                if (matchingIssue != null) {
                    issueDao.updateStatus(matchingIssue.id, "closed")
                    LogKeeper.log("WorkspacePrManager", "IssueClosed", "Closed associated issue #${matchingIssue.id}")
                }

                // Refresh file tree
                LocalFileManager.refreshFileTree()
                LogKeeper.log("WorkspacePrManager", "MergeSuccess", "PR #${pr.id} merged cleanly (${patchResult.hunksApplied} hunks)")

                MergeResult.Success(
                    prId = prId,
                    hunksApplied = patchResult.hunksApplied,
                    targetFile = patchResult.relativePath
                )
            }
            is PatchApplier.PatchResult.Failure -> {
                LogKeeper.log("WorkspacePrManager", "MergeFailure", "PR #${pr.id} failed to apply: ${patchResult.reason}")
                MergeResult.Failure(prId, patchResult.reason)
            }
        }
    }

    suspend fun closePullRequest(context: Context, prId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).workspacePullRequestDao().updateStatus(prId, "closed")
        LogKeeper.log("WorkspacePrManager", "PRClosed", "Closed PR #$prId without merging")
    }
}
