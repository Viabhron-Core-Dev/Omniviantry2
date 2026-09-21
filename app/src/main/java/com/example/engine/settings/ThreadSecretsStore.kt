package com.example.engine.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.utils.LogKeeper
import org.json.JSONObject

/**
 * Manages thread/workspace-scoped secrets completely isolated from the workspace filesystem.
 *
 * Core Mandate (TWO_PART_ARTIFACT_SECRETS_PLAN):
 * - Part 2 secrets are stored in private SharedPreferences under the app's internal sandbox.
 * - Filesystem tools (view_file, list_dir, grep_search) inspecting /workspaces/<workspace_id>/
 *   will NEVER see these secrets on disk, ensuring 100% AI blindness.
 * - Injected into in-memory WebView runtimes via Object.freeze() at preview time.
 */
object ThreadSecretsStore {

    private const val PREFS_PREFIX = "prefs_thread_secrets_"

    private fun getPrefs(context: Context, workspaceId: String): SharedPreferences {
        val safeWsId = workspaceId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return context.getSharedPreferences("$PREFS_PREFIX$safeWsId", Context.MODE_PRIVATE)
    }

    /**
     * Returns all secrets configured for the given workspace.
     */
    fun getSecrets(context: Context, workspaceId: String): Map<String, String> {
        val prefs = getPrefs(context, workspaceId)
        val allEntries = prefs.all
        val result = mutableMapOf<String, String>()
        for ((key, value) in allEntries) {
            if (value is String && key.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    /**
     * Returns a JSON string of all secrets (for in-memory injection).
     */
    fun getSecretsJson(context: Context, workspaceId: String): String {
        val secrets = getSecrets(context, workspaceId)
        val json = JSONObject()
        for ((k, v) in secrets) {
            json.put(k, v)
        }
        return json.toString()
    }

    /**
     * Saves or updates a single secret.
     */
    fun saveSecret(context: Context, workspaceId: String, key: String, value: String) {
        if (key.isBlank()) return
        val prefs = getPrefs(context, workspaceId)
        prefs.edit().putString(key.trim(), value).apply()
        val count = prefs.all.size
        LogKeeper.log("INFO", "ThreadSecretsStore", "Secret saved for workspace $workspaceId (total keys: $count)")
    }

    /**
     * Deletes a secret by key.
     */
    fun deleteSecret(context: Context, workspaceId: String, key: String) {
        val prefs = getPrefs(context, workspaceId)
        prefs.edit().remove(key.trim()).apply()
        val count = prefs.all.size
        LogKeeper.log("INFO", "ThreadSecretsStore", "Secret removed for workspace $workspaceId (total keys: $count)")
    }

    /**
     * Overwrites all secrets for a workspace with the given map.
     */
    fun saveAllSecrets(context: Context, workspaceId: String, secrets: Map<String, String>) {
        val prefs = getPrefs(context, workspaceId)
        val editor = prefs.edit()
        editor.clear()
        for ((k, v) in secrets) {
            if (k.isNotBlank()) {
                editor.putString(k.trim(), v)
            }
        }
        editor.apply()
        LogKeeper.log("INFO", "ThreadSecretsStore", "Saved all secrets for workspace $workspaceId (count: ${secrets.size})")
    }

    /**
     * Clears all secrets for a workspace.
     */
    fun clearSecrets(context: Context, workspaceId: String) {
        val prefs = getPrefs(context, workspaceId)
        prefs.edit().clear().apply()
        LogKeeper.log("INFO", "ThreadSecretsStore", "Cleared all secrets for workspace $workspaceId")
    }
}
