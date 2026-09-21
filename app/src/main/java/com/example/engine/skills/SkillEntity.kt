package com.example.engine.skills

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SkillType {
    FUNCTIONAL,
    SOUL_PERSONA
}

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey
    val id: String,
    override val name: String,
    override val description: String,
    val type: SkillType = SkillType.FUNCTIONAL,
    override val instructions: String,
    val examples: String = "",
    val author: String = "User",
    val sourceUrl: String? = null,
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) : Skill {
    fun toSkill(): Skill = this
}
