package com.example.engine.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceIssueDao {
    @Query("SELECT * FROM workspace_issues WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun getIssuesForWorkspace(workspaceId: String): Flow<List<WorkspaceIssueEntity>>

    @Query("SELECT * FROM workspace_issues WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    suspend fun getIssuesList(workspaceId: String): List<WorkspaceIssueEntity>

    @Query("SELECT * FROM workspace_issues WHERE id = :id LIMIT 1")
    suspend fun getIssueById(id: String): WorkspaceIssueEntity?

    @Query("UPDATE workspace_issues SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveIssue(issue: WorkspaceIssueEntity)

    @Query("DELETE FROM workspace_issues WHERE id = :id")
    suspend fun deleteIssue(id: String)
}
