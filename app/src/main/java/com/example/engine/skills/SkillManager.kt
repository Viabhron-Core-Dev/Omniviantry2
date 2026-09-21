package com.example.engine.skills

import android.content.Context
import com.example.engine.EngineRegistry
import com.example.engine.db.AppDatabase
import com.example.utils.LogKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SkillManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _skills = MutableStateFlow<List<SkillEntity>>(emptyList())
    val skills: StateFlow<List<SkillEntity>> = _skills.asStateFlow()

    private var initialized = false
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.skillDao()

            // Prepopulate default built-in skills if empty
            ensureDefaultSkills(dao)

            dao.getAllSkills().collectLatest { skillList ->
                _skills.value = skillList
                // Sync with EngineRegistry
                skillList.filter { it.isEnabled }.forEach { skillEntity ->
                    EngineRegistry.registerSkill(skillEntity)
                }
            }
        }
    }

    private suspend fun ensureDefaultSkills(dao: SkillDao) {
        val defaults = listOf(
            SkillEntity(
                id = "skill_code_architect",
                name = "Clean Code Architect",
                description = "Enforces SOLID principles, modular architecture, and zero unnecessary bloat.",
                type = SkillType.FUNCTIONAL,
                instructions = "Analyze design patterns, modularize large components (<500 lines), and ensure strict single-responsibility principles in all code changes.",
                examples = "Input: Add complex feature -> Output: Break into repository, domain model, and composable UI.",
                author = "OmniRoot Built-in",
                isBuiltIn = true
            ),
            SkillEntity(
                id = "skill_security_auditor",
                name = "Security & Credential Sentinel",
                description = "Scans code for exposed credentials, insecure network flows, and permission bypasses.",
                type = SkillType.FUNCTIONAL,
                instructions = "Perform deep credential scans. Strictly flag committed API keys, .keystore files, hardcoded passwords, and cleartext HTTP traffic.",
                examples = "Input: Hardcoded apiKey in code -> Output: Move to BuildConfig / local environment immediately.",
                author = "OmniRoot Built-in",
                isBuiltIn = true
            ),
            SkillEntity(
                id = "soul_honest_partner",
                name = "Soul: Brutally Honest Senior Tech Lead",
                description = "Soul/Persona: Push back against bad architecture, detail real-world technical risks, and stress-test assumptions.",
                type = SkillType.SOUL_PERSONA,
                instructions = "You are a seasoned, brutally honest Senior Technical Lead. Always stress-test architectures, push back against bloat, prevent OOMs, and never give sycophantic praise.",
                examples = "User: Let's put everything in one file. -> AI: Strongly push back: Detail race conditions and OOM failure modes.",
                author = "OmniRoot Built-in",
                isBuiltIn = true
            ),
            SkillEntity(
                id = "soul_socratic_mentor",
                name = "Soul: Socratic Systems Mentor",
                description = "Soul/Persona: Guides user to deeper systems understanding through targeted inquiries and conceptual breakdowns.",
                type = SkillType.SOUL_PERSONA,
                instructions = "Adopt the persona of a calm, methodical mentor. Break complex topics into intuitive first principles, providing concise analogies without unnecessary jargon.",
                examples = "User: How does coroutines cancellation work? -> AI: Explains cooperative cancellation and Job lifecycle with clear visual analogies.",
                author = "OmniRoot Built-in",
                isBuiltIn = true
            )
        )

        defaults.forEach { defaultSkill ->
            val existing = dao.getSkillById(defaultSkill.id)
            if (existing == null) {
                dao.insertSkill(defaultSkill)
            }
        }
    }

    suspend fun addOrUpdateSkill(context: Context, skill: SkillEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            db.skillDao().insertSkill(skill)
            if (skill.isEnabled) {
                EngineRegistry.registerSkill(skill)
            }
            LogKeeper.log("SkillManager", "SaveSkill", "Saved skill: ${skill.name} (${skill.type})")
            Result.success(Unit)
        } catch (e: Exception) {
            LogKeeper.log("SkillManager", "Error", "Failed saving skill: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteSkill(context: Context, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            db.skillDao().deleteSkillById(id)
            LogKeeper.log("SkillManager", "DeleteSkill", "Deleted skill ID: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromUrl(context: Context, url: String): Result<SkillEntity> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))

                val skillEntity = parseSkillContent(body, url)
                val db = AppDatabase.getDatabase(context)
                db.skillDao().insertSkill(skillEntity)
                LogKeeper.log("SkillManager", "ImportSuccess", "Imported skill '${skillEntity.name}' from $url")
                Result.success(skillEntity)
            }
        } catch (e: Exception) {
            LogKeeper.log("SkillManager", "ImportError", "Failed importing skill from $url: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseSkillContent(content: String, sourceUrl: String): SkillEntity {
        // Check if JSON
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val name = json.optString("name", "Imported Skill")
            val desc = json.optString("description", "")
            val typeStr = json.optString("type", "FUNCTIONAL")
            val type = if (typeStr.equals("SOUL_PERSONA", ignoreCase = true) || typeStr.equals("SOUL", ignoreCase = true)) {
                SkillType.SOUL_PERSONA
            } else {
                SkillType.FUNCTIONAL
            }
            val instructions = json.optString("instructions", json.optString("prompt", ""))
            val examples = json.optString("examples", "")
            val author = json.optString("author", "Online Import")
            val id = "imported_" + (name.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }) + "_" + (System.currentTimeMillis() % 10000)

            return SkillEntity(
                id = id,
                name = name,
                description = desc,
                type = type,
                instructions = instructions,
                examples = examples,
                author = author,
                sourceUrl = sourceUrl,
                isBuiltIn = false
            )
        }

        // Parse Markdown (e.g. SKILL.md with frontmatter)
        var name = "Imported Skill"
        var desc = "Imported from Markdown"
        var type = SkillType.FUNCTIONAL
        var instructions = trimmed

        if (trimmed.startsWith("---")) {
            val endFrontmatter = trimmed.indexOf("---", 3)
            if (endFrontmatter != -1) {
                val frontmatter = trimmed.substring(3, endFrontmatter)
                instructions = trimmed.substring(endFrontmatter + 3).trim()
                frontmatter.lines().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim().lowercase()
                        val value = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                        when (key) {
                            "name" -> name = value
                            "description" -> desc = value
                            "type" -> {
                                if (value.contains("soul", ignoreCase = true) || value.contains("persona", ignoreCase = true)) {
                                    type = SkillType.SOUL_PERSONA
                                }
                            }
                        }
                    }
                }
            }
        }

        val id = "imported_" + (name.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }) + "_" + (System.currentTimeMillis() % 10000)
        return SkillEntity(
            id = id,
            name = name,
            description = desc,
            type = type,
            instructions = instructions,
            author = "Online Markdown",
            sourceUrl = sourceUrl,
            isBuiltIn = false
        )
    }
}
