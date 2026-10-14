package com.example.engine.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSettingsDao {
    @Query("SELECT * FROM chat_settings")
    suspend fun getAllSettings(): List<ChatSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllSettings(settings: List<ChatSettingsEntity>)

    @Query("SELECT * FROM chat_settings WHERE workspaceId = :workspaceId")
    fun getSettingsFlow(workspaceId: String): Flow<ChatSettingsEntity?>

    @Query("SELECT * FROM chat_settings WHERE workspaceId = :workspaceId")
    suspend fun getSettings(workspaceId: String): ChatSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ChatSettingsEntity)

    @Query("UPDATE chat_settings SET rollingSummary = :summary, summaryUpToMessageId = :messageId WHERE workspaceId = :workspaceId")
    suspend fun updateRollingSummary(workspaceId: String, summary: String?, messageId: String?)

    @Query("UPDATE chat_settings SET rollingSummary = NULL, summaryUpToMessageId = NULL WHERE workspaceId = :workspaceId")
    suspend fun clearRollingSummary(workspaceId: String)
}
