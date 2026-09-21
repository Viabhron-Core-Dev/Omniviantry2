package com.example.engine.plugins

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PluginManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _plugins = MutableStateFlow<List<PluginEntity>>(emptyList())
    val plugins: StateFlow<List<PluginEntity>> = _plugins.asStateFlow()

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
            val dao = db.pluginDao()

            // Prepopulate sample plugin if empty
            ensureDefaultPlugins(dao)

            dao.getAllPlugins().collectLatest { pluginList ->
                _plugins.value = pluginList
            }
        }
    }

    private suspend fun ensureDefaultPlugins(dao: PluginDao) {
        val defaultPlugin = PluginEntity(
            id = "plugin_fullstack_auditor",
            name = "Full-Stack Security & Architecture Suite",
            description = "Bundles Clean Code Architect, Security Sentinel, File Tools, and Code Execution with zero-leak restrictions.",
            version = "1.0.0",
            author = "OmniRoot Built-in",
            selectedSkillIds = listOf("skill_code_architect", "skill_security_auditor", "soul_honest_partner"),
            selectedToolIds = listOf("view_file", "edit_file", "grep_search", "document_parser"),
            selectedMcpServerIds = emptyList(),
            customInstructions = "Always maintain atomic commits, verify linting standards, and review changed files before user confirmation.",
            restrictions = "Forbidden from printing or logging secret keys, tokens, or plaintext credentials.",
            isEnabled = true
        )

        val existing = dao.getPluginById(defaultPlugin.id)
        if (existing == null) {
            dao.insertPlugin(defaultPlugin)
        }
    }

    suspend fun addOrUpdatePlugin(context: Context, plugin: PluginEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            db.pluginDao().insertPlugin(plugin)
            LogKeeper.log("PluginManager", "SavePlugin", "Saved plugin: ${plugin.name} (v${plugin.version})")
            Result.success(Unit)
        } catch (e: Exception) {
            LogKeeper.log("PluginManager", "Error", "Failed saving plugin: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deletePlugin(context: Context, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            db.pluginDao().deletePluginById(id)
            LogKeeper.log("PluginManager", "DeletePlugin", "Deleted plugin ID: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromUrl(context: Context, url: String): Result<PluginEntity> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))

                val json = JSONObject(body.trim())
                val name = json.optString("name", "Imported Plugin")
                val desc = json.optString("description", "")
                val version = json.optString("version", "1.0.0")
                val author = json.optString("author", "Online Import")
                val instructions = json.optString("customInstructions", json.optString("instructions", ""))
                val restrictions = json.optString("restrictions", "")

                val skillsArray = json.optJSONArray("selectedSkillIds") ?: json.optJSONArray("skills")
                val skillIds = mutableListOf<String>()
                if (skillsArray != null) {
                    for (i in 0 until skillsArray.length()) {
                        skillIds.add(skillsArray.getString(i))
                    }
                }

                val toolsArray = json.optJSONArray("selectedToolIds") ?: json.optJSONArray("tools")
                val toolIds = mutableListOf<String>()
                if (toolsArray != null) {
                    for (i in 0 until toolsArray.length()) {
                        toolIds.add(toolsArray.getString(i))
                    }
                }

                val mcpArray = json.optJSONArray("selectedMcpServerIds") ?: json.optJSONArray("mcps")
                val mcpIds = mutableListOf<String>()
                if (mcpArray != null) {
                    for (i in 0 until mcpArray.length()) {
                        mcpIds.add(mcpArray.getString(i))
                    }
                }

                val cleanId = "plugin_" + (name.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }) + "_" + (System.currentTimeMillis() % 10000)
                val plugin = PluginEntity(
                    id = cleanId,
                    name = name,
                    description = desc,
                    version = version,
                    author = author,
                    selectedSkillIds = skillIds,
                    selectedToolIds = toolIds,
                    selectedMcpServerIds = mcpIds,
                    customInstructions = instructions,
                    restrictions = restrictions,
                    sourceUrl = url,
                    isEnabled = true
                )

                val db = AppDatabase.getDatabase(context)
                db.pluginDao().insertPlugin(plugin)
                LogKeeper.log("PluginManager", "ImportSuccess", "Imported plugin '${plugin.name}' from $url")
                Result.success(plugin)
            }
        } catch (e: Exception) {
            LogKeeper.log("PluginManager", "ImportError", "Failed importing plugin from $url: ${e.message}")
            Result.failure(e)
        }
    }
}
