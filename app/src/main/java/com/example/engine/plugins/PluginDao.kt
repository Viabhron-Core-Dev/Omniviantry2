package com.example.engine.plugins

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY name ASC")
    fun getAllPlugins(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE id = :id")
    suspend fun getPluginById(id: String): PluginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: PluginEntity)

    @Update
    suspend fun updatePlugin(plugin: PluginEntity)

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun deletePluginById(id: String)
}
