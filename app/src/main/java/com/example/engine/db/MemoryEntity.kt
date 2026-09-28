package com.example.engine.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object MemoryCategory {
    const val USER_PREFERENCE = "USER_PREFERENCE"
    const val PROJECT_RULE = "PROJECT_RULE"
    const val ARCHITECTURAL_DECISION = "ARCHITECTURAL_DECISION"
    const val SNIPPET = "SNIPPET"

    val ALL = listOf(USER_PREFERENCE, PROJECT_RULE, ARCHITECTURAL_DECISION, SNIPPET)
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val content: String,
    val category: String = MemoryCategory.USER_PREFERENCE,
    val relevanceScore: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val accessCount: Int = 0
)
