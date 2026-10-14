package com.example.engine.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspacePullRequestDao {
    @Query("SELECT * FROM workspace_pull_requests WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun getPullRequestsForWorkspace(workspaceId: String): Flow<List<WorkspacePullRequestEntity>>

    @Query("SELECT * FROM workspace_pull_requests WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    suspend fun getPullRequestsList(workspaceId: String): List<WorkspacePullRequestEntity>

    @Query("SELECT * FROM workspace_pull_requests WHERE id = :id LIMIT 1")
    suspend fun getPullRequestById(id: String): WorkspacePullRequestEntity?

    @Query("UPDATE workspace_pull_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePullRequest(pr: WorkspacePullRequestEntity)

    @Query("DELETE FROM workspace_pull_requests WHERE id = :id")
    suspend fun deletePullRequest(id: String)
}
