package com.example.engine.plugins

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "User",
    val selectedSkillIds: List<String> = emptyList(),
    val selectedToolIds: List<String> = emptyList(),
    val selectedMcpServerIds: List<String> = emptyList(),
    val customInstructions: String = "",
    val restrictions: String = "",
    val sourceUrl: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
