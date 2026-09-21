package com.example.engine.omniroot.artifact

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactProviderDao {

    @Query("SELECT * FROM artifact_providers ORDER BY priority ASC, createdAt DESC")
    fun getAllFlow(): Flow<List<ArtifactProviderEntity>>

    @Query("SELECT * FROM artifact_providers ORDER BY priority ASC, createdAt DESC")
    suspend fun getAll(): List<ArtifactProviderEntity>

    @Query("SELECT * FROM artifact_providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ArtifactProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArtifactProviderEntity)

    @Update
    suspend fun update(entity: ArtifactProviderEntity)

    @Query("UPDATE artifact_providers SET sessionTokens = :tokens, sessionCalls = :calls, tokenPct = :pct, windowRemainingMs = :remainingMs, lastActiveMs = :lastActive WHERE id = :id")
    suspend fun updateSessionStats(id: String, tokens: Long, calls: Int, pct: Float, remainingMs: Long, lastActive: Long = System.currentTimeMillis())

    @Query("UPDATE artifact_providers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM artifact_providers WHERE id = :id")
    suspend fun deleteById(id: String)
}
