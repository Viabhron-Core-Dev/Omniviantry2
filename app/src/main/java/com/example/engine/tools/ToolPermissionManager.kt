package com.example.engine.tools

import android.content.Context
import android.content.SharedPreferences
import com.example.engine.EngineRegistry
import com.example.utils.LogKeeper
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages runtime execution permissions and manual user-approval gates
 * for tools and sandboxes in the OmniRoot agentic engine.
 */
object ToolPermissionManager {

    private const val PREFS_NAME = "omniroot_tool_permissions"
    private var prefs: SharedPreferences? = null

    private val sessionApprovedTools = ConcurrentHashMap<String, MutableSet<String>>()
    private val globalToolOverrides = ConcurrentHashMap<String, ToolPermission>()

    fun init(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.all?.forEach { (key, value) ->
            if (value is String) {
                try {
                    globalToolOverrides[key.lowercase().trim()] = ToolPermission.valueOf(value)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Determines whether a tool is completely disabled / forbidden by policy.
     */
    fun isForbidden(toolName: String): Boolean {
        val normalizedName = toolName.lowercase().trim()
        val override = globalToolOverrides[normalizedName]
        if (override != null) {
            return override == ToolPermission.NO_PERMISSION
        }
        val tool = EngineRegistry.getTool(normalizedName)
        val defaultPerm = tool?.permission ?: getDefaultPermissionForTool(normalizedName)
        return defaultPerm == ToolPermission.NO_PERMISSION
    }

    /**
     * Determines whether a tool requires manual user approval before execution.
     */
    fun requiresApproval(sessionId: String, toolName: String): Boolean {
        val normalizedName = toolName.lowercase().trim()

        // If explicitly forbidden, it doesn't wait for approval (it is rejected immediately)
        if (isForbidden(normalizedName)) {
            return false
        }

        // 1. Check session-level pre-approvals
        val sessionSet = sessionApprovedTools[sessionId]
        if (sessionSet != null && sessionSet.contains(normalizedName)) {
            return false
        }

        // 2. Check global override
        val globalOverride = globalToolOverrides[normalizedName]
        if (globalOverride != null) {
            return when (globalOverride) {
                ToolPermission.USE_FREELY -> false
                ToolPermission.ALWAYS_ASK -> true
                ToolPermission.SESSION_ALLOW -> false
                ToolPermission.NO_PERMISSION -> false
            }
        }

        // 3. Fallback to Tool default permission
        val tool = EngineRegistry.getTool(normalizedName)
        val defaultPerm = tool?.permission ?: getDefaultPermissionForTool(normalizedName)
        return when (defaultPerm) {
            ToolPermission.ALWAYS_ASK -> true
            ToolPermission.NO_PERMISSION -> false
            ToolPermission.USE_FREELY -> false
            ToolPermission.SESSION_ALLOW -> false
        }
    }

    fun getEffectivePermission(sessionId: String, toolName: String): ToolPermission {
        val normalizedName = toolName.lowercase().trim()
        val globalOverride = globalToolOverrides[normalizedName]
        if (globalOverride != null) {
            return globalOverride
        }
        val sessionSet = sessionApprovedTools[sessionId]
        if (sessionSet != null && sessionSet.contains(normalizedName)) {
            return ToolPermission.SESSION_ALLOW
        }
        val tool = EngineRegistry.getTool(normalizedName)
        return tool?.permission ?: getDefaultPermissionForTool(normalizedName)
    }

    fun getGlobalPermission(toolName: String): ToolPermission? {
        return globalToolOverrides[toolName.lowercase().trim()]
    }

    fun getAllOverrides(): Map<String, ToolPermission> {
        return globalToolOverrides.toMap()
    }

    private fun getDefaultPermissionForTool(toolName: String): ToolPermission {
        return when (toolName.lowercase()) {
            "delete_file", "shell_exec", "proot_linux", "run_python", "shell_process" -> ToolPermission.ALWAYS_ASK
            else -> ToolPermission.USE_FREELY
        }
    }

    /**
     * Whitelist a tool for the active session (Allow Always in this thread).
     */
    fun approveForSession(sessionId: String, toolName: String) {
        val normalizedName = toolName.lowercase().trim()
        sessionApprovedTools.computeIfAbsent(sessionId) { ConcurrentHashMap.newKeySet() }.add(normalizedName)
        LogKeeper.log("ToolPermissionManager", "SessionApproved", "Tool '$normalizedName' approved for session '$sessionId'")
    }

    /**
     * Revoke session whitelist for a tool.
     */
    fun revokeSessionApproval(sessionId: String, toolName: String) {
        val normalizedName = toolName.lowercase().trim()
        sessionApprovedTools[sessionId]?.remove(normalizedName)
        LogKeeper.log("ToolPermissionManager", "SessionRevoked", "Tool '$normalizedName' approval revoked for session '$sessionId'")
    }

    /**
     * Set a global permission override for a tool with immediate disk persistence.
     */
    fun setGlobalOverride(toolName: String, permission: ToolPermission, context: Context? = null) {
        val normalizedName = toolName.lowercase().trim()
        globalToolOverrides[normalizedName] = permission

        val sp = prefs ?: context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.also { prefs = it }
        sp?.edit()?.putString(normalizedName, permission.name)?.apply()
        LogKeeper.log("ToolPermissionManager", "GlobalOverride", "Tool '$normalizedName' set to $permission")
    }

    fun restoreOverrides(context: Context, overrides: Map<String, String>) {
        val sp = prefs ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        val editor = sp.edit()
        overrides.forEach { (toolName, permString) ->
            try {
                val perm = ToolPermission.valueOf(permString)
                val normalized = toolName.lowercase().trim()
                globalToolOverrides[normalized] = perm
                editor.putString(normalized, perm.name)
            } catch (_: Exception) {}
        }
        editor.apply()
        LogKeeper.log("ToolPermissionManager", "RestoredOverrides", "Restored ${overrides.size} tool permission overrides")
    }

    /**
     * Clear all session approval caches when a session is closed or reset.
     */
    fun clearSession(sessionId: String) {
        sessionApprovedTools.remove(sessionId)
    }
}
