package com.example.engine.backup

import android.content.Context
import com.example.engine.db.*
import com.example.engine.tools.ToolPermissionManager
import com.example.ui.chat.MessageRole
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupStats(
    val messageCount: Int,
    val keyCount: Int,
    val configCount: Int,
    val settingsCount: Int,
    val permissionCount: Int,
    val artifactProviderCount: Int = 0,
    val artifactCount: Int = 0
)

data class RestoreStats(
    val messageCount: Int,
    val keyCount: Int,
    val configCount: Int,
    val settingsCount: Int,
    val permissionCount: Int,
    val artifactProviderCount: Int = 0,
    val artifactCount: Int = 0
)

/**
 * Enterprise-grade local encrypted backup & restore service.
 * Protects chats, online API keys, workspace configurations, and tool permissions
 * using PBKDF2 key derivation and AES-256-GCM authenticated encryption.
 */
object BackupManager {
    private const val MAGIC_HEADER = "OMNI_BAK_V1"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256

    suspend fun exportBackup(
        context: Context,
        password: CharArray,
        outputStream: OutputStream
    ): Result<BackupStats> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)

            // 1. Gather all data
            val messages = db.chatMessageDao().getAllMessages()
            val keys = db.apiKeyDao().getAllKeysList()
            val configs = db.workspaceConfigDao().getAllConfigs()
            val settings = db.chatSettingsDao().getAllSettings()
            val overrides = ToolPermissionManager.getAllOverrides()
            val artifactProviders = db.artifactProviderDao().getAll()
            val artifacts = db.artifactDao().getAllArtifacts()

            // 2. Build JSON payload
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())

            // Messages
            val msgArray = JSONArray()
            messages.forEach { m ->
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("sessionId", m.sessionId)
                    put("text", m.text)
                    put("role", m.role.name)
                    put("modelName", m.modelName ?: "")
                    put("providerId", m.providerId ?: "")
                    put("editedFilesJson", m.editedFilesJson)
                    put("appActionsJson", m.appActionsJson)
                    put("isFolded", m.isFolded)
                    put("timestamp", m.timestamp)
                    put("toolCallId", m.toolCallId ?: "")
                    put("toolName", m.toolName ?: "")
                    put("toolArgsJson", m.toolArgsJson ?: "")
                    put("toolOutput", m.toolOutput ?: "")
                    put("toolDurationMs", m.toolDurationMs)
                    put("toolStatus", m.toolStatus ?: "")
                    put("routedViaFallback", m.routedViaFallback)
                    put("fallbackReason", m.fallbackReason ?: "")
                    put("thoughtContent", m.thoughtContent ?: "")
                    put("planContent", m.planContent ?: "")
                    put("thinkingDurationMs", m.thinkingDurationMs)
                }
                msgArray.put(obj)
            }
            root.put("messages", msgArray)

            // Keys
            val keyArray = JSONArray()
            keys.forEach { k ->
                val obj = JSONObject().apply {
                    put("id", k.id)
                    put("providerId", k.providerId)
                    put("alias", k.alias)
                    put("keyMasked", k.keyMasked)
                    put("keyValue", k.keyValue)
                    put("isActive", k.isActive)
                    put("createdAt", k.createdAt)
                }
                keyArray.put(obj)
            }
            root.put("keys", keyArray)

            // Workspace Configs
            val cfgArray = JSONArray()
            configs.forEach { c ->
                val obj = JSONObject().apply {
                    put("workspaceId", c.workspaceId)
                    put("threadName", c.threadName)
                    put("appType", c.appType)
                    put("model", c.model)
                    put("integrations", c.integrations)
                    put("instructions", c.instructions)
                }
                cfgArray.put(obj)
            }
            root.put("configs", cfgArray)

            // Chat Settings
            val setArray = JSONArray()
            settings.forEach { s ->
                val obj = JSONObject().apply {
                    put("workspaceId", s.workspaceId)
                    put("temperature", s.temperature.toDouble())
                    put("minP", s.minP.toDouble())
                    put("topP", s.topP.toDouble())
                    put("maxTokens", s.maxTokens)
                    put("systemPrompt", s.systemPrompt)
                    put("contextSize", s.contextSize)
                    put("numThreads", s.numThreads)
                    put("useMmap", s.useMmap)
                    put("useMlock", s.useMlock)
                    put("unfoldOnScreen", s.unfoldOnScreen)
                }
                setArray.put(obj)
            }
            root.put("settings", setArray)

            // Tool Permissions
            val permObj = JSONObject()
            overrides.forEach { (tool, perm) ->
                permObj.put(tool, perm.name)
            }
            root.put("tool_permissions", permObj)

            // Artifact Providers (Claude headless instances & decrypted passkeys)
            val apArray = JSONArray()
            artifactProviders.forEach { ap ->
                val obj = JSONObject().apply {
                    put("id", ap.id)
                    put("name", ap.name)
                    put("url", ap.url)
                    put("owner", ap.owner)
                    put("hashedPasskey", ap.hashedPasskey ?: "")
                    put("priority", ap.priority)
                    put("enabled", ap.enabled)
                    put("sessionTokens", ap.sessionTokens)
                    put("sessionCalls", ap.sessionCalls)
                    put("tokenPct", ap.tokenPct.toDouble())
                    put("windowRemainingMs", ap.windowRemainingMs)
                    put("windowStartMs", ap.windowStartMs)
                    put("lastActiveMs", ap.lastActiveMs)
                    put("createdAt", ap.createdAt)
                    // Retrieve raw passkey from hardware keystore to preserve in encrypted backup file
                    val rawPasskey = com.example.engine.omniroot.artifact.ArtifactKeyStore.getPasskey(context, ap.id)
                    put("rawPasskey", rawPasskey ?: "")
                }
                apArray.put(obj)
            }
            root.put("artifact_providers", apArray)

            // Saved Artifacts (HTML, Notes, PWAs, etc.)
            val artArray = JSONArray()
            artifacts.forEach { art ->
                val obj = JSONObject().apply {
                    put("id", art.id)
                    put("title", art.title)
                    put("type", art.type)
                    put("content", art.content)
                    put("isPinned", art.isPinned)
                    put("updatedAt", art.updatedAt)
                    put("workspaceId", art.workspaceId ?: "")
                    put("iconUri", art.iconUri ?: "")
                    put("isLightweight", art.isLightweight)
                    put("manifestJson", art.manifestJson ?: "")
                    put("settingsJson", art.settingsJson ?: "")
                    put("version", art.version)
                }
                artArray.put(obj)
            }
            root.put("artifacts", artArray)

            // 3. Encrypt payload
            val plaintextBytes = root.toString().toByteArray(StandardCharsets.UTF_8)
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE)
            random.nextBytes(salt)
            val iv = ByteArray(IV_SIZE)
            random.nextBytes(iv)

            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val ciphertext = cipher.doFinal(plaintextBytes)

            // 4. Write stream: Magic Header (11 bytes) + salt (16 bytes) + iv (12 bytes) + ciphertext
            outputStream.use { out ->
                out.write(MAGIC_HEADER.toByteArray(StandardCharsets.UTF_8))
                out.write(salt)
                out.write(iv)
                out.write(ciphertext)
                out.flush()
            }

            LogKeeper.log("BackupManager", "ExportSuccess", "Backup exported: ${messages.size} msgs, ${keys.size} keys, ${configs.size} configs, ${artifactProviders.size} artifact providers, ${artifacts.size} artifacts")
            Result.success(
                BackupStats(
                    messageCount = messages.size,
                    keyCount = keys.size,
                    configCount = configs.size,
                    settingsCount = settings.size,
                    permissionCount = overrides.size,
                    artifactProviderCount = artifactProviders.size,
                    artifactCount = artifacts.size
                )
            )
        } catch (e: Exception) {
            LogKeeper.log("BackupManager", "ExportError", "Failed to export backup: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(
        context: Context,
        password: CharArray,
        inputStream: InputStream
    ): Result<RestoreStats> = withContext(Dispatchers.IO) {
        try {
            val allBytes = inputStream.use { it.readBytes() }
            val headerBytes = MAGIC_HEADER.toByteArray(StandardCharsets.UTF_8)
            if (allBytes.size < headerBytes.size + SALT_SIZE + IV_SIZE) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or corrupted backup file."))
            }

            // Verify header
            val readHeader = String(allBytes.copyOfRange(0, headerBytes.size), StandardCharsets.UTF_8)
            if (readHeader != MAGIC_HEADER) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported backup format or corrupt header."))
            }

            var offset = headerBytes.size
            val salt = allBytes.copyOfRange(offset, offset + SALT_SIZE)
            offset += SALT_SIZE
            val iv = allBytes.copyOfRange(offset, offset + IV_SIZE)
            offset += IV_SIZE
            val ciphertext = allBytes.copyOfRange(offset, allBytes.size)

            // Decrypt
            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val plaintextBytes = cipher.doFinal(ciphertext)

            val root = JSONObject(String(plaintextBytes, StandardCharsets.UTF_8))
            val db = AppDatabase.getDatabase(context)

            // 1. Restore Messages
            val msgArray = root.optJSONArray("messages") ?: JSONArray()
            val restoredMessages = mutableListOf<ChatMessageEntity>()
            for (i in 0 until msgArray.length()) {
                val obj = msgArray.getJSONObject(i)
                val roleStr = obj.optString("role", "USER")
                val role = try {
                    MessageRole.valueOf(roleStr)
                } catch (_: Exception) {
                    MessageRole.USER
                }
                restoredMessages.add(
                    ChatMessageEntity(
                        id = obj.getString("id"),
                        sessionId = obj.getString("sessionId"),
                        text = obj.optString("text", ""),
                        role = role,
                        modelName = obj.optString("modelName").takeIf { it.isNotBlank() },
                        providerId = obj.optString("providerId").takeIf { it.isNotBlank() },
                        editedFilesJson = obj.optString("editedFilesJson", "[]"),
                        appActionsJson = obj.optString("appActionsJson", "[]"),
                        isFolded = obj.optBoolean("isFolded", true),
                        timestamp = obj.getLong("timestamp"),
                        toolCallId = obj.optString("toolCallId").takeIf { it.isNotBlank() },
                        toolName = obj.optString("toolName").takeIf { it.isNotBlank() },
                        toolArgsJson = obj.optString("toolArgsJson").takeIf { it.isNotBlank() },
                        toolOutput = obj.optString("toolOutput").takeIf { it.isNotBlank() },
                        toolDurationMs = obj.optLong("toolDurationMs", 0L),
                        toolStatus = obj.optString("toolStatus").takeIf { it.isNotBlank() },
                        routedViaFallback = obj.optBoolean("routedViaFallback", false),
                        fallbackReason = obj.optString("fallbackReason").takeIf { it.isNotBlank() },
                        thoughtContent = obj.optString("thoughtContent").takeIf { it.isNotBlank() },
                        planContent = obj.optString("planContent").takeIf { it.isNotBlank() },
                        thinkingDurationMs = obj.optLong("thinkingDurationMs", 0L)
                    )
                )
            }
            if (restoredMessages.isNotEmpty()) {
                db.chatMessageDao().insertMessages(restoredMessages)
            }

            // 2. Restore Keys
            val keyArray = root.optJSONArray("keys") ?: JSONArray()
            val restoredKeys = mutableListOf<ApiKeyEntity>()
            for (i in 0 until keyArray.length()) {
                val obj = keyArray.getJSONObject(i)
                restoredKeys.add(
                    ApiKeyEntity(
                        id = obj.getString("id"),
                        providerId = obj.getString("providerId"),
                        alias = obj.getString("alias"),
                        keyMasked = obj.getString("keyMasked"),
                        keyValue = obj.getString("keyValue"),
                        isActive = obj.optBoolean("isActive", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (restoredKeys.isNotEmpty()) {
                db.apiKeyDao().insertKeys(restoredKeys)
            }

            // 3. Restore Configs
            val cfgArray = root.optJSONArray("configs") ?: JSONArray()
            val restoredConfigs = mutableListOf<WorkspaceConfigEntity>()
            for (i in 0 until cfgArray.length()) {
                val obj = cfgArray.getJSONObject(i)
                restoredConfigs.add(
                    WorkspaceConfigEntity(
                        workspaceId = obj.getString("workspaceId"),
                        threadName = obj.optString("threadName", "Main Thread"),
                        appType = obj.optString("appType", "GENERAL"),
                        model = obj.optString("model", "gemini-2.5-flash"),
                        integrations = obj.optString("integrations", ""),
                        instructions = obj.optString("instructions", "")
                    )
                )
            }
            if (restoredConfigs.isNotEmpty()) {
                db.workspaceConfigDao().saveConfigs(restoredConfigs)
            }

            // 4. Restore Chat Settings
            val setArray = root.optJSONArray("settings") ?: JSONArray()
            val restoredSettings = mutableListOf<ChatSettingsEntity>()
            for (i in 0 until setArray.length()) {
                val obj = setArray.getJSONObject(i)
                restoredSettings.add(
                    ChatSettingsEntity(
                        workspaceId = obj.getString("workspaceId"),
                        temperature = obj.optDouble("temperature", 0.7).toFloat(),
                        minP = obj.optDouble("minP", 0.05).toFloat(),
                        topP = obj.optDouble("topP", 0.95).toFloat(),
                        maxTokens = obj.optInt("maxTokens", 2048),
                        systemPrompt = obj.optString("systemPrompt", ""),
                        contextSize = obj.optInt("contextSize", 2048),
                        numThreads = obj.optInt("numThreads", 4),
                        useMmap = obj.optBoolean("useMmap", true),
                        useMlock = obj.optBoolean("useMlock", false),
                        unfoldOnScreen = obj.optBoolean("unfoldOnScreen", false)
                    )
                )
            }
            if (restoredSettings.isNotEmpty()) {
                db.chatSettingsDao().saveAllSettings(restoredSettings)
            }

            // 5. Restore Tool Permissions
            val permObj = root.optJSONObject("tool_permissions")
            val permMap = mutableMapOf<String, String>()
            if (permObj != null) {
                val keysIter = permObj.keys()
                while (keysIter.hasNext()) {
                    val k = keysIter.next()
                    permMap[k] = permObj.getString(k)
                }
                ToolPermissionManager.restoreOverrides(context, permMap)
            }

            // 6. Restore Artifact Providers & Passkeys
            val apArray = root.optJSONArray("artifact_providers") ?: JSONArray()
            val restoredArtifactProviders = mutableListOf<com.example.engine.omniroot.artifact.ArtifactProviderEntity>()
            for (i in 0 until apArray.length()) {
                val obj = apArray.getJSONObject(i)
                val apId = obj.getString("id")
                val apName = obj.getString("name")
                val apUrl = obj.getString("url")
                val rawPasskey = obj.optString("rawPasskey", "").takeIf { it.isNotBlank() }
                val hashedPasskey = obj.optString("hashedPasskey").takeIf { it.isNotBlank() }
                    ?: rawPasskey?.let { com.example.engine.omniroot.artifact.ArtifactKeyStore.hashPasskey(it) }

                val entity = com.example.engine.omniroot.artifact.ArtifactProviderEntity(
                    id = apId,
                    name = apName,
                    url = apUrl,
                    owner = obj.optString("owner", "Claude.ai"),
                    hashedPasskey = hashedPasskey,
                    priority = obj.optInt("priority", 2),
                    enabled = obj.optBoolean("enabled", true),
                    sessionTokens = obj.optLong("sessionTokens", 0L),
                    sessionCalls = obj.optInt("sessionCalls", 0),
                    tokenPct = obj.optDouble("tokenPct", 0.0).toFloat(),
                    windowRemainingMs = obj.optLong("windowRemainingMs", 0L),
                    windowStartMs = obj.optLong("windowStartMs", 0L),
                    lastActiveMs = obj.optLong("lastActiveMs", System.currentTimeMillis()),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                restoredArtifactProviders.add(entity)

                // Restore passkey into hardware/encrypted keystore
                if (!rawPasskey.isNullOrBlank()) {
                    com.example.engine.omniroot.artifact.ArtifactKeyStore.savePasskey(context, apId, rawPasskey)
                }
            }

            if (restoredArtifactProviders.isNotEmpty()) {
                restoredArtifactProviders.forEach { db.artifactProviderDao().insert(it) }

                // Register into ai_models table
                try {
                    val aiModels = restoredArtifactProviders.map { ap ->
                        com.example.engine.db.AiModelEntity(
                            providerId = "artifact",
                            modelId = ap.name.replace(" ", "_"),
                            inputType = "TEXT",
                            outputType = "TEXT",
                            description = "Claude.ai Omnivian Artifact Provider: ${ap.name}"
                        )
                    }
                    db.aiModelDao().insertModels(aiModels)
                } catch (_: Exception) {}

                // Notify active pool to load restored providers
                try {
                    com.example.engine.omniroot.artifact.ArtifactProviderPool.getInstance(context).resyncAllStates()
                } catch (_: Exception) {}
            }

            // 7. Restore Saved Artifacts
            val artArray = root.optJSONArray("artifacts") ?: JSONArray()
            val restoredArtifacts = mutableListOf<ArtifactEntity>()
            for (i in 0 until artArray.length()) {
                val obj = artArray.getJSONObject(i)
                restoredArtifacts.add(
                    ArtifactEntity(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        type = obj.getString("type"),
                        content = obj.getString("content"),
                        isPinned = obj.optBoolean("isPinned", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        workspaceId = obj.optString("workspaceId").takeIf { it.isNotBlank() },
                        iconUri = obj.optString("iconUri").takeIf { it.isNotBlank() },
                        isLightweight = obj.optBoolean("isLightweight", false),
                        manifestJson = obj.optString("manifestJson").takeIf { it.isNotBlank() },
                        settingsJson = obj.optString("settingsJson").takeIf { it.isNotBlank() },
                        version = obj.optLong("version", 1L)
                    )
                )
            }
            if (restoredArtifacts.isNotEmpty()) {
                db.artifactDao().insertArtifacts(restoredArtifacts)
            }

            LogKeeper.log("BackupManager", "RestoreSuccess", "Restored: ${restoredMessages.size} msgs, ${restoredKeys.size} keys, ${restoredConfigs.size} configs, ${restoredArtifactProviders.size} artifact providers, ${restoredArtifacts.size} artifacts")
            Result.success(
                RestoreStats(
                    messageCount = restoredMessages.size,
                    keyCount = restoredKeys.size,
                    configCount = restoredConfigs.size,
                    settingsCount = restoredSettings.size,
                    permissionCount = permMap.size,
                    artifactProviderCount = restoredArtifactProviders.size,
                    artifactCount = restoredArtifacts.size
                )
            )
        } catch (e: Exception) {
            LogKeeper.log("BackupManager", "RestoreError", "Restore failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
