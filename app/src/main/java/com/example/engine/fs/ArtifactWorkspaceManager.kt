package com.example.engine.fs

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.engine.db.ArtifactEntity
import com.example.engine.omniroot.artifact.ArtifactKeyStore
import com.example.engine.settings.ThreadSecretsStore
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Enterprise 2-Part Artifact Workspace Manager.
 *
 * Implements the 2-Part Artifact Architecture (TWO_PART_ARTIFACT_SECRETS_PLAN):
 * - Part 1 (The Repo): Stored in /artifacts/<artifactId>/repo/ and copied into workspaces for editing.
 *   Exclusively client-side HTML/JS/CSS code and assets.
 * - Part 2 (The Encrypted Secrets Bundle): Stored strictly in /artifacts/<artifactId>/secrets_bundle.enc,
 *   encrypted via AES-256-GCM through ArtifactKeyStore.
 * - Blind Fork & Edit: When loaded into a workspace, Part 1 files are copied into /workspaces/<ws_id>/,
 *   while Part 2 secrets are decrypted in memory and saved to private SharedPreferences (ThreadSecretsStore).
 *   Part 2 files are STRICTLY WITHHELD from /workspaces/, ensuring AI agent tools have zero access to credentials.
 */
object ArtifactWorkspaceManager {

    private fun getArtifactDir(context: Context, artifactId: String): File {
        return File(context.filesDir, "artifacts/$artifactId")
    }

    fun getArtifactRepoDir(context: Context, artifactId: String): File {
        return File(getArtifactDir(context, artifactId), "repo")
    }

    fun getArtifactSecretsFile(context: Context, artifactId: String): File {
        return File(getArtifactDir(context, artifactId), "secrets_bundle.enc")
    }

    /**
     * Packages a workspace into the 2-Part Artifact structure:
     * - Part 1: /files/artifacts/<artifactId>/repo/ (web code files)
     * - Part 2: /files/artifacts/<artifactId>/secrets_bundle.enc (AES-256-GCM encrypted secrets)
     */
    fun package2PartArtifact(context: Context, artifactId: String, workspaceDir: File, workspaceId: String) {
        try {
            val artifactDir = getArtifactDir(context, artifactId)
            val repoDir = getArtifactRepoDir(context, artifactId)
            if (!repoDir.exists()) {
                repoDir.mkdirs()
            }

            // Copy Part 1 files only (exclude any metadata, .enc, or secrets)
            val files = workspaceDir.listFiles() ?: emptyArray()
            var fileCount = 0
            for (file in files) {
                val name = file.name
                if (name.startsWith(".") || name.endsWith(".enc") || name.startsWith(".env")) {
                    continue
                }
                file.copyTo(File(repoDir, name), overwrite = true)
                fileCount++
            }

            // Package Part 2: Encrypted Secrets Bundle
            val secretsJson = ThreadSecretsStore.getSecretsJson(context, workspaceId)
            val encryptedBundle = ArtifactKeyStore.encryptString(secretsJson)
            val secretsFile = getArtifactSecretsFile(context, artifactId)
            secretsFile.writeText(encryptedBundle)

            LogKeeper.log("INFO", "ArtifactManager", "Artifact2PartPackaged: Artifact $artifactId created: Part 1 ($fileCount files), Part 2 encrypted")
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "ArtifactManager", "Failed to package 2-part artifact $artifactId: ${e.message}")
        }
    }

    /**
     * Initializes or prepares a workspace folder for an artifact.
     * Extracts Part 1 content into the workspace while strictly withholding Part 2 from the workspace folder.
     * Injects Part 2 secrets directly into private SharedPreferences for runtime preview injection.
     */
    suspend fun openArtifactInWorkspace(context: Context, artifact: ArtifactEntity): String = withContext(Dispatchers.IO) {
        val workspaceId = artifact.workspaceId?.ifBlank { null } ?: "artifact_${artifact.id}"
        val baseDir = context.filesDir
        val workspaceFolder = File(baseDir, "workspaces/$workspaceId")
        if (!workspaceFolder.exists()) {
            workspaceFolder.mkdirs()
        }

        // Write workspace metadata name
        val nameFile = File(workspaceFolder, ".workspace_name")
        nameFile.writeText(artifact.title)

        // Store artifact reference ID
        val metaFile = File(workspaceFolder, ".artifact_id")
        metaFile.writeText(artifact.id)

        // 1. Extract Part 1 (The Repo)
        val repoDir = getArtifactRepoDir(context, artifact.id)
        if (repoDir.exists() && (repoDir.listFiles()?.isNotEmpty() == true)) {
            // Copy code files from Part 1 repo storage
            repoDir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(".enc") && !file.name.startsWith(".env")) {
                    file.copyTo(File(workspaceFolder, file.name), overwrite = true)
                }
            }
        } else {
            // Fallback: extract from artifact entity content
            if (artifact.type == "COLOR_NOTES") {
                val notesFile = File(workspaceFolder, "notes.json")
                notesFile.writeText(artifact.content)

                val mdFile = File(workspaceFolder, "README.md")
                mdFile.writeText("# ${artifact.title}\n\nVoice and Color Notes collection.\n")
            } else {
                // HTML / PWA / Web app
                val indexFile = File(workspaceFolder, "index.html")
                indexFile.writeText(artifact.content)

                val manifestFile = File(workspaceFolder, "manifest.json")
                if (artifact.manifestJson != null) {
                    manifestFile.writeText(artifact.manifestJson)
                } else {
                    val defaultManifest = JSONObject().apply {
                        put("name", artifact.title)
                        put("short_name", artifact.title.take(12))
                        put("start_url", "./index.html")
                        put("display", if (artifact.isLightweight) "minimal-ui" else "standalone")
                        put("theme_color", "#1E1E2E")
                        put("background_color", "#11111B")
                    }
                    manifestFile.writeText(defaultManifest.toString(2))
                }

                val settingsFile = File(workspaceFolder, "settings.json")
                if (artifact.settingsJson != null) {
                    settingsFile.writeText(artifact.settingsJson)
                } else {
                    settingsFile.writeText(JSONObject().put("theme", "auto").put("isLightweight", artifact.isLightweight).toString(2))
                }

                val readme = File(workspaceFolder, "README.md")
                readme.writeText("# ${artifact.title}\n\nInteractive web artifact.\n")
            }
        }

        // 2. Extract Part 2: Blind Ingestion (Withhold from workspace filesystem)
        val secretsFile = getArtifactSecretsFile(context, artifact.id)
        if (secretsFile.exists()) {
            val encrypted = secretsFile.readText()
            val decryptedJson = ArtifactKeyStore.decryptString(encrypted)
            if (!decryptedJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(decryptedJson)
                    val map = mutableMapOf<String, String>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = json.optString(k, "")
                    }
                    ThreadSecretsStore.saveAllSecrets(context, workspaceId, map)
                } catch (e: Exception) {
                    LogKeeper.log("WARNING", "ArtifactManager", "Error parsing decrypted secrets for ${artifact.id}: ${e.message}")
                }
            }
        }

        LogKeeper.log("INFO", "ArtifactManager", "ArtifactForkedToWorkspace: Artifact ${artifact.id} opened in workspace $workspaceId (Part 1 only; Part 2 withheld from workspace)")

        // Link artifact to this workspaceId if not already linked
        if (artifact.workspaceId != workspaceId) {
            val db = AppDatabase.getDatabase(context)
            db.artifactDao().updateArtifact(artifact.copy(workspaceId = workspaceId, updatedAt = System.currentTimeMillis()))
        }

        LocalFileManager.switchWorkspace(workspaceId)
        workspaceId
    }

    /**
     * Finds the artifact ID associated with the current workspace.
     */
    fun getArtifactIdForWorkspace(workspaceId: String): String? {
        val workspaceDir = LocalFileManager.getWorkspaceDir()
        val metaFile = File(workspaceDir, ".artifact_id")
        if (metaFile.exists()) {
            return metaFile.readText().trim().ifBlank { null }
        }
        if (workspaceId.startsWith("artifact_")) {
            return workspaceId.removePrefix("artifact_")
        }
        return null
    }

    /**
     * Saves changes from the workspace back to the original artifact entity and packages 2-Part store.
     */
    suspend fun saveWorkspaceToArtifact(context: Context, workspaceId: String): Result<ArtifactEntity> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val artifactId = getArtifactIdForWorkspace(workspaceId)
                ?: return@withContext Result.failure(IllegalStateException("No linked artifact for workspace $workspaceId"))

            val existing = db.artifactDao().getArtifactById(artifactId)
                ?: return@withContext Result.failure(IllegalStateException("Artifact $artifactId not found in database"))

            val workspaceDir = LocalFileManager.getWorkspaceDir()
            val updatedContent = if (existing.type == "COLOR_NOTES") {
                val notesFile = File(workspaceDir, "notes.json")
                if (notesFile.exists()) notesFile.readText() else existing.content
            } else {
                val indexFile = File(workspaceDir, "index.html")
                if (indexFile.exists()) indexFile.readText() else existing.content
            }

            val manifestFile = File(workspaceDir, "manifest.json")
            val manifestJson = if (manifestFile.exists()) manifestFile.readText() else existing.manifestJson

            val settingsFile = File(workspaceDir, "settings.json")
            val settingsJson = if (settingsFile.exists()) settingsFile.readText() else existing.settingsJson

            val isLightweight = try {
                if (settingsJson != null) JSONObject(settingsJson).optBoolean("isLightweight", existing.isLightweight) else existing.isLightweight
            } catch (_: Exception) { existing.isLightweight }

            val updatedName = LocalFileManager.getWorkspaceName(workspaceId)
            val updatedEntity = existing.copy(
                title = updatedName.ifBlank { existing.title },
                content = updatedContent,
                manifestJson = manifestJson,
                settingsJson = settingsJson,
                isLightweight = isLightweight,
                version = existing.version + 1L,
                updatedAt = System.currentTimeMillis()
            )

            db.artifactDao().updateArtifact(updatedEntity)

            // Package into 2-Part storage
            package2PartArtifact(context, artifactId, workspaceDir, workspaceId)

            Result.success(updatedEntity)
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "ArtifactManager", "saveWorkspaceToArtifact failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Forks the current workspace into a new artifact and new workspace repo (Blind Fork).
     * Part 1 is copied; Part 2 is copied to new private SharedPreferences and packaged into new vault.
     */
    suspend fun forkWorkspaceToNewArtifact(context: Context, currentWorkspaceId: String, newTitle: String): Result<Pair<ArtifactEntity, String>> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val newArtifactId = UUID.randomUUID().toString()
            val newWorkspaceId = "artifact_$newArtifactId"

            val sourceDir = LocalFileManager.getWorkspaceDir()
            val targetDir = File(context.filesDir, "workspaces/$newWorkspaceId")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Copy Part 1 files ONLY (omitting any .enc or .env files)
            sourceDir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(".enc") && !file.name.startsWith(".env")) {
                    file.copyTo(File(targetDir, file.name), overwrite = true)
                }
            }

            // Update metadata files in new workspace
            File(targetDir, ".workspace_name").writeText(newTitle)
            File(targetDir, ".artifact_id").writeText(newArtifactId)

            // Determine content
            val indexFile = File(targetDir, "index.html")
            val notesFile = File(targetDir, "notes.json")
            val (type, content) = when {
                indexFile.exists() -> "HTML" to indexFile.readText()
                notesFile.exists() -> "COLOR_NOTES" to notesFile.readText()
                else -> "HTML" to "<html><body><h2>${newTitle}</h2></body></html>"
            }

            val manifestFile = File(targetDir, "manifest.json")
            val manifestJson = if (manifestFile.exists()) manifestFile.readText() else null

            val settingsFile = File(targetDir, "settings.json")
            val settingsJson = if (settingsFile.exists()) settingsFile.readText() else null

            val newArtifact = ArtifactEntity(
                id = newArtifactId,
                title = newTitle,
                type = type,
                content = content,
                isPinned = false,
                manifestJson = manifestJson,
                settingsJson = settingsJson,
                version = 1L,
                updatedAt = System.currentTimeMillis(),
                workspaceId = newWorkspaceId
            )

            db.artifactDao().insertArtifact(newArtifact)

            // Copy Part 2 secrets directly between SharedPreferences
            val secrets = ThreadSecretsStore.getSecrets(context, currentWorkspaceId)
            ThreadSecretsStore.saveAllSecrets(context, newWorkspaceId, secrets)

            // Package 2-part artifact
            package2PartArtifact(context, newArtifactId, targetDir, newWorkspaceId)

            LocalFileManager.switchWorkspace(newWorkspaceId)
            LogKeeper.log("INFO", "ArtifactManager", "ArtifactForkedToWorkspace: Artifact $newArtifactId forked to workspace $newWorkspaceId (Part 1 only; Part 2 withheld from workspace)")

            Result.success(Pair(newArtifact, newWorkspaceId))
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "ArtifactManager", "forkWorkspaceToNewArtifact failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Saves normal chat workspace as a new Artifact (mini app) in 2-Part form.
     */
    suspend fun saveCurrentChatAsArtifact(context: Context, workspaceId: String, title: String): Result<ArtifactEntity> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val base = LocalFileManager.getBaseDir() ?: context.filesDir
            val targetWsDir = File(base, "workspaces/$workspaceId")
            val workspaceDir = if (targetWsDir.exists() && targetWsDir.isDirectory) targetWsDir else LocalFileManager.getWorkspaceDir()
            val indexFile = File(workspaceDir, "index.html")
            val notesFile = File(workspaceDir, "notes.json")

            val (type, content) = when {
                indexFile.exists() -> "HTML" to indexFile.readText()
                notesFile.exists() -> "COLOR_NOTES" to notesFile.readText()
                else -> {
                    val htmlFile = workspaceDir.listFiles()?.firstOrNull { it.extension.lowercase() in listOf("html", "htm", "js") }
                    if (htmlFile != null) {
                        "HTML" to htmlFile.readText()
                    } else {
                        "HTML" to "<html><body style='font-family:sans-serif;padding:24px;'><h2>$title</h2><p>Saved from chat session $workspaceId.</p></body></html>"
                    }
                }
            }

            val manifestFile = File(workspaceDir, "manifest.json")
            val manifestJson = if (manifestFile.exists()) manifestFile.readText() else null

            val settingsFile = File(workspaceDir, "settings.json")
            val settingsJson = if (settingsFile.exists()) settingsFile.readText() else null

            val artifactId = UUID.randomUUID().toString()
            val artifact = ArtifactEntity(
                id = artifactId,
                title = title.ifBlank { "Mini App ($workspaceId)" },
                type = type,
                content = content,
                isPinned = false,
                manifestJson = manifestJson,
                settingsJson = settingsJson,
                version = 1L,
                updatedAt = System.currentTimeMillis(),
                workspaceId = workspaceId
            )

            File(workspaceDir, ".artifact_id").writeText(artifactId)
            db.artifactDao().insertArtifact(artifact)

            // Package 2-Part vault
            package2PartArtifact(context, artifactId, workspaceDir, workspaceId)

            Result.success(artifact)
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "ArtifactManager", "saveCurrentChatAsArtifact failed: ${e.message}")
            Result.failure(e)
        }
    }
}
