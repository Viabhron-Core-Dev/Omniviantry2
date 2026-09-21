package com.example.engine.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY createdAt ASC")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServerEntity)

    @Update
    suspend fun updateServer(server: McpServerEntity)

    @Delete
    suspend fun deleteServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
}
