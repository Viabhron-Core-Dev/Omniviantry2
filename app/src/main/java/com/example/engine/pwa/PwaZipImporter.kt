package com.example.engine.pwa

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.engine.db.AppDatabase
import com.example.engine.db.ArtifactEntity
import com.example.engine.fs.ZipUtils
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PwaZipImporter {

    /**
     * Imports a PWA from a content URI (e.g. from file picker / dist.zip).
     */
    suspend fun importZip(context: Context, uri: Uri): Result<ArtifactEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryFileName(context, uri) ?: "web_app.zip"
            LogKeeper.log("INFO", "PwaZipImporter", "Starting PWA import from URI: $uri (detected name: $fileName)")

            val tempDir = File(context.cacheDir, "pwa_zip_staging_${System.currentTimeMillis()}").apply { mkdirs() }
            val tempZipFile = File(tempDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open input stream for $uri"))

            val result = importZipFile(context, tempZipFile, fileName)

            // Cleanup staging zip
            try {
                tempZipFile.delete()
                tempDir.deleteRecursively()
            } catch (_: Exception) {}

            result
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "PwaZipImporter", "Import failed from URI $uri: ${e.message}", e.stackTraceToString())
            Result.failure(e)
        }
    }

    /**
     * Imports a PWA from a local ZIP file on disk.
     */
    suspend fun importZipFile(
        context: Context,
        zipFile: File,
        originalFilename: String? = null
    ): Result<ArtifactEntity> = withContext(Dispatchers.IO) {
        try {
            if (!zipFile.exists() || zipFile.length() == 0L) {
                val err = "Zip file is empty or does not exist: ${zipFile.absolutePath}"
                LogKeeper.log("ERROR", "PwaZipImporter", err)
                return@withContext Result.failure(Exception(err))
            }

            val artifactId = UUID.randomUUID().toString()
            val finalRepoDir = File(context.filesDir, "artifacts/$artifactId/repo").apply { mkdirs() }
            val stagingDir = File(context.cacheDir, "pwa_extract_${artifactId}").apply { mkdirs() }

            LogKeeper.log("INFO", "PwaZipImporter", "Extracting ${zipFile.name} (${zipFile.length()} bytes) to staging...")
            val unzipResult = ZipUtils.unzip(zipFile, stagingDir)
            if (unzipResult.isFailure) {
                val err = unzipResult.exceptionOrNull() ?: Exception("Unknown unzip error")
                LogKeeper.log("ERROR", "PwaZipImporter", "Unzip extraction failed: ${err.message}")
                stagingDir.deleteRecursively()
                return@withContext Result.failure(err)
            }

            // Detect app root directory: handles flat zips, nested dist/build/public folders, or repo subtrees
            val appRootDir = resolveAppRootDirectory(stagingDir)
            LogKeeper.log("INFO", "PwaZipImporter", "Resolved web app root at: ${appRootDir.absolutePath}")

            // Find manifest or synthesize one
            val manifestCandidates = listOf(
                File(appRootDir, "manifest.json"),
                File(appRootDir, "app.webmanifest"),
                File(appRootDir, "manifest.webmanifest"),
                File(appRootDir, "site.webmanifest")
            )
            val manifestFile = manifestCandidates.firstOrNull { it.exists() && it.isFile }

            val fallbackTitle = deriveTitle(originalFilename ?: zipFile.name, appRootDir)
            val manifestData = if (manifestFile != null) {
                PwaManifestParser.parse(manifestFile.readText(), fallbackTitle)
            } else {
                LogKeeper.log("INFO", "PwaZipImporter", "No manifest found in zip. Synthesizing manifest for: '$fallbackTitle'")
                PwaManifestParser.synthesizeManifestData(fallbackTitle)
            }

            // Copy all files from appRootDir to finalRepoDir
            copyDirectory(appRootDir, finalRepoDir)

            // Ensure manifest.json exists in the final directory
            val targetManifest = File(finalRepoDir, "manifest.json")
            if (!targetManifest.exists()) {
                targetManifest.writeText(manifestData.rawJson)
            }

            // Resolve and extract persistent icon
            var persistentIconPath: String? = null
            val iconFile = PwaManifestParser.resolveBestIconFile(finalRepoDir, manifestData.icons)
            if (iconFile != null && iconFile.exists()) {
                val iconsDir = File(context.filesDir, "pwa_icons").apply { mkdirs() }
                val destIcon = File(iconsDir, "${artifactId}_${iconFile.name}")
                try {
                    iconFile.copyTo(destIcon, overwrite = true)
                    persistentIconPath = destIcon.absolutePath
                    LogKeeper.log("INFO", "PwaZipImporter", "Saved PWA launcher icon: ${destIcon.absolutePath}")
                } catch (e: Exception) {
                    LogKeeper.log("WARNING", "PwaZipImporter", "Could not copy icon: ${e.message}")
                }
            }

            // Find primary entry HTML file or synthesize
            val indexCandidates = listOf("index.html", "index.htm", "Index.html", "INDEX.HTML", "main.html", "app.html", "home.html")
            val indexFile = indexCandidates.map { File(finalRepoDir, it) }.firstOrNull { it.exists() && it.isFile }
                ?: finalRepoDir.walkTopDown().filter { it.isFile && it.extension.equals("html", ignoreCase = true) && !it.name.startsWith("__MACOSX") }.firstOrNull()

            val htmlContent = if (indexFile != null && indexFile.exists()) {
                // Ensure a standard index.html exists in finalRepoDir
                val standardIndex = File(finalRepoDir, "index.html")
                if (!standardIndex.exists()) {
                    try {
                        indexFile.copyTo(standardIndex, overwrite = true)
                    } catch (_: Exception) {}
                }
                indexFile.readText()
            } else {
                val fallbackHtml = "<!DOCTYPE html><html><head><title>${manifestData.name}</title></head><body><h2>${manifestData.name}</h2><p>PWA loaded from distribution archive.</p></body></html>"
                File(finalRepoDir, "index.html").writeText(fallbackHtml)
                fallbackHtml
            }

            val settingsJson = JSONObject().apply {
                put("display", manifestData.display)
                put("orientation", manifestData.orientation)
                put("themeColor", manifestData.themeColor)
                put("backgroundColor", manifestData.backgroundColor)
                put("startUrl", manifestData.startUrl)
            }.toString()

            val workspaceId = "artifact_$artifactId"
            val artifactEntity = ArtifactEntity(
                id = artifactId,
                title = manifestData.name,
                type = "PWA",
                content = htmlContent,
                isPinned = false,
                updatedAt = System.currentTimeMillis(),
                workspaceId = workspaceId,
                iconUri = persistentIconPath,
                manifestJson = manifestData.rawJson,
                settingsJson = settingsJson,
                version = 1L
            )

            // Insert into Room DB
            val db = AppDatabase.getDatabase(context)
            db.artifactDao().insertArtifact(artifactEntity)

            // Cleanup staging
            stagingDir.deleteRecursively()

            LogKeeper.log("INFO", "PwaZipImporter", "Successfully registered PWA: '${artifactEntity.title}' (ID: $artifactId, workspace: $workspaceId)")
            Result.success(artifactEntity)
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "PwaZipImporter", "importZipFile error: ${e.message}", e.stackTraceToString())
            Result.failure(e)
        }
    }

    /**
     * Resolves the actual root directory inside the extracted zip:
     * Recursively inspects the staging directory up to 5 levels deep to find the best
     * web application root folder containing an index.html or primary web assets.
     */
    private fun resolveAppRootDirectory(extractDir: File): File {
        val candidateDirs = mutableListOf<File>()

        fun searchDir(current: File, depth: Int) {
            if (depth > 5) return
            val name = current.name
            if (name.startsWith("__MACOSX") || name == ".git" || name == ".github" || name == "node_modules") return

            val files = current.listFiles() ?: return
            val hasHtml = files.any { f ->
                f.isFile && (f.name.equals("index.html", ignoreCase = true) || f.name.equals("index.htm", ignoreCase = true))
            }
            if (hasHtml) {
                candidateDirs.add(current)
            }

            for (f in files) {
                if (f.isDirectory) {
                    searchDir(f, depth + 1)
                }
            }
        }

        searchDir(extractDir, 0)

        if (candidateDirs.isNotEmpty()) {
            val bestCandidate = candidateDirs.maxByOrNull { dir ->
                var score = 0
                val dirName = dir.name.lowercase()
                if (dirName == "dist" || dirName == "build" || dirName == "out" || dirName == "public" || dirName == "www") score += 50
                if (File(dir, "manifest.json").exists() || File(dir, "app.webmanifest").exists() || File(dir, "site.webmanifest").exists()) score += 40
                if (File(dir, "assets").exists() || File(dir, "js").exists() || File(dir, "css").exists() || File(dir, "static").exists()) score += 30
                if (File(dir, "favicon.ico").exists() || File(dir, "icon.png").exists() || File(dir, "logo.png").exists()) score += 20
                if (dir == extractDir) score += 10
                score
            }
            if (bestCandidate != null) {
                LogKeeper.log("INFO", "PwaZipImporter", "Resolved best app root: ${bestCandidate.absolutePath} (out of ${candidateDirs.size} candidates)")
                return bestCandidate
            }
        }

        // If no index.html found at all, search for ANY .html file
        val allHtmlFiles = extractDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("html", ignoreCase = true) && !it.absolutePath.contains("__MACOSX") }
            .toList()
        if (allHtmlFiles.isNotEmpty()) {
            val primaryHtml = allHtmlFiles.first()
            val parent = primaryHtml.parentFile ?: extractDir
            LogKeeper.log("INFO", "PwaZipImporter", "No index.html found, using directory with '${primaryHtml.name}': ${parent.absolutePath}")
            return parent
        }

        return extractDir
    }

    private fun deriveTitle(filename: String, appRootDir: File): String {
        // 1. Try reading <title> from index.html or any html
        val indexHtml = listOf(File(appRootDir, "index.html"), File(appRootDir, "index.htm")).firstOrNull { it.exists() }
            ?: appRootDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("html", ignoreCase = true) }

        if (indexHtml != null) {
            val parsedTitle = PwaManifestParser.extractTitleFromHtml(indexHtml)
            if (!parsedTitle.isNullOrBlank()) {
                return parsedTitle
            }
        }

        // 2. Clean filename
        val base = filename.removeSuffix(".zip").removeSuffix(".ZIP")
        if (base.equals("dist", ignoreCase = true) || base.equals("build", ignoreCase = true) || base.equals("out", ignoreCase = true)) {
            return "Imported PWA"
        }

        return base.replace(Regex("[_-]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("__MACOSX") || name == ".DS_Store" || name == "Thumbs.db" || name == ".git") return@forEach
            val dest = File(target, name)
            if (file.isDirectory) {
                copyDirectory(file, dest)
            } else {
                file.copyTo(dest, overwrite = true)
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }
}
