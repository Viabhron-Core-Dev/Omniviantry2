package com.example.engine.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>): List<Long>

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAll()

    @Query("SELECT * FROM memories ORDER BY isPinned DESC, lastAccessedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY isPinned DESC, lastAccessedAt DESC")
    suspend fun getAllMemoriesSync(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY isPinned DESC, lastAccessedAt DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY isPinned DESC, lastAccessedAt DESC")
    suspend fun getMemoriesByCategorySync(category: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE isPinned = 1 ORDER BY lastAccessedAt DESC")
    fun getPinnedMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isPinned = 1 ORDER BY lastAccessedAt DESC")
    suspend fun getPinnedMemoriesSync(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY isPinned DESC, relevanceScore DESC")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY isPinned DESC, relevanceScore DESC")
    suspend fun searchMemoriesSync(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: Long): MemoryEntity?

    @Query("UPDATE memories SET lastAccessedAt = :timestamp, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun updateAccess(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getCount(): Int
}
