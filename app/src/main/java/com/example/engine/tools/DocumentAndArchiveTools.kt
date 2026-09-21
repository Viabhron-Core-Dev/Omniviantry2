package com.example.engine.tools

import com.example.engine.fs.DocumentParserEngine
import com.example.engine.fs.LocalFileManager
import com.example.engine.fs.ZipUtils
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Archive management tool with strict Zip-Slip protection, format inspection, and streaming compression.
 */
class ArchiveTool : Tool {
    override val name: String = "archive_manage"
    override val description: String = "Manage archives (.zip) in the workspace. Supports 'unzip' with Zip-Slip defense, 'zip' directories/files, and 'list' archive contents without extraction."
    override val permission: ToolPermission = ToolPermission.USE_FREELY

    override val parametersSchema: Map<String, Any> = mapOf(
        "Action" to mapOf(
            "type" to "string",
            "description" to "Operation to perform: 'unzip', 'zip', or 'list'.",
            "required" to true
        ),
        "ArchivePath" to mapOf(
            "type" to "string",
            "description" to "Path to the archive file relative to workspace root.",
            "required" to true
        ),
        "DestinationPath" to mapOf(
            "type" to "string",
            "description" to "Destination directory path for extraction or source folder to zip."
        ),
        "IncludePaths" to mapOf(
            "type" to "array",
            "description" to "Optional list of specific relative file paths to include when creating an archive."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val action = (args["Action"] as? String ?: args["action"] as? String ?: "unzip").lowercase()
        val archivePath = args["ArchivePath"] as? String ?: args["archive"] as? String ?: args["path"] as? String
            ?: return@withContext "Error: 'ArchivePath' parameter is required."

        val archiveFileRes = WorkspacePathResolver.resolve(archivePath)
        if (archiveFileRes.isFailure) {
            return@withContext "Error: ${archiveFileRes.exceptionOrNull()?.message}"
        }
        val archiveFile = archiveFileRes.getOrThrow()

        when (action) {
            "unzip", "extract" -> {
                if (!archiveFile.exists() || !archiveFile.isFile) {
                    return@withContext "Error: Archive file '$archivePath' does not exist."
                }
                val destParam = args["DestinationPath"] as? String ?: args["targetDir"] as? String ?: ""
                val destRes = WorkspacePathResolver.resolve(destParam)
                if (destRes.isFailure) return@withContext "Error: ${destRes.exceptionOrNull()?.message}"
                val destDir = destRes.getOrThrow()
                destDir.mkdirs()

                val result = ZipUtils.unzip(archiveFile, destDir)
                if (result.isSuccess) {
                    LocalFileManager.refreshFileTree()
                    val relDest = WorkspacePathResolver.getRelativePath(destDir)
                    LogKeeper.log("ArchiveTool", "Success", "Extracted ${archiveFile.name} to $relDest")
                    "Successfully extracted archive `${archiveFile.name}` into directory `$relDest`."
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Extraction failed"
                    LogKeeper.log("ArchiveTool", "Error", "Failed to extract: $err")
                    "Error extracting archive: $err"
                }
            }

            "zip", "compress" -> {
                val sourceParam = args["DestinationPath"] as? String ?: args["sourceDir"] as? String ?: ""
                val sourceRes = WorkspacePathResolver.resolve(sourceParam)
                if (sourceRes.isFailure) return@withContext "Error: ${sourceRes.exceptionOrNull()?.message}"
                val sourceDir = sourceRes.getOrThrow()

                if (!sourceDir.exists()) {
                    return@withContext "Error: Source directory '$sourceParam' does not exist."
                }

                archiveFile.parentFile?.mkdirs()
                val result = ZipUtils.zipDirectory(sourceDir, archiveFile)
                if (result.isSuccess) {
                    LocalFileManager.refreshFileTree()
                    val relZip = WorkspacePathResolver.getRelativePath(archiveFile)
                    val relSource = WorkspacePathResolver.getRelativePath(sourceDir)
                    LogKeeper.log("ArchiveTool", "Success", "Zipped $relSource -> $relZip")
                    "Successfully created archive `$relZip` from `$relSource` (${formatBytes(archiveFile.length())})."
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Compression failed"
                    "Error creating zip archive: $err"
                }
            }

            "list", "toc", "inspect" -> {
                if (!archiveFile.exists() || !archiveFile.isFile) {
                    return@withContext "Error: Archive file '$archivePath' does not exist."
                }
                listArchiveEntries(archiveFile)
            }

            else -> "Error: Unknown action '$action'. Valid actions: 'unzip', 'zip', 'list'."
        }
    }

    private fun listArchiveEntries(file: File): String {
        return try {
            val entries = mutableListOf<String>()
            var totalCompressedSize = 0L
            var totalUncompressedSize = 0L

            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val isDir = entry.isDirectory
                    val sizeStr = if (isDir) "[DIR]" else formatBytes(entry.size.coerceAtLeast(0L))
                    entries.add("• ${entry.name} ($sizeStr)")
                    if (!isDir && entry.size > 0) {
                        totalUncompressedSize += entry.size
                    }
                    totalCompressedSize += entry.compressedSize.coerceAtLeast(0L)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val relPath = WorkspacePathResolver.getRelativePath(file)
            buildString {
                appendLine("=== Archive Contents: `$relPath` ===")
                appendLine("Total Entries: ${entries.size} | File Size: ${formatBytes(file.length())}")
                if (totalUncompressedSize > 0) {
                    appendLine("Uncompressed Size: ${formatBytes(totalUncompressedSize)}")
                }
                appendLine()
                if (entries.isEmpty()) {
                    appendLine("  (empty archive)")
                } else {
                    entries.take(200).forEach { appendLine(it) }
                    if (entries.size > 200) {
                        appendLine("... [${entries.size - 200} more items omitted]")
                    }
                }
            }
        } catch (e: Exception) {
            "Error inspecting archive '${file.name}': ${e.message}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/**
 * Universal document parser tool for PDF, DOCX, PPTX, XLSX, EPUB, and source files.
 */
class DocumentParserTool : Tool {
    override val name: String = "parse_document"
    override val description: String = "Extracts structured text or Markdown tables from PDF, Word (.docx), PowerPoint (.pptx), Excel (.xlsx), and EPUB files without external heavy dependencies."
    override val permission: ToolPermission = ToolPermission.USE_FREELY

    override val parametersSchema: Map<String, Any> = mapOf(
        "FilePath" to mapOf(
            "type" to "string",
            "description" to "Path to the document file inside workspace.",
            "required" to true
        ),
        "MaxPages" to mapOf(
            "type" to "integer",
            "description" to "Maximum number of pages, slides, or sheets to parse (default 20, max 100)."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val filePath = args["FilePath"] as? String
            ?: args["path"] as? String
            ?: args["TargetFile"] as? String
            ?: return@withContext "Error: 'FilePath' parameter is required."

        val maxPages = ((args["MaxPages"] as? Number)?.toInt() ?: 20).coerceIn(1, 100)

        val targetRes = WorkspacePathResolver.resolve(filePath)
        if (targetRes.isFailure) {
            return@withContext "Error: ${targetRes.exceptionOrNull()?.message}"
        }
        val file = targetRes.getOrThrow()

        if (!file.exists() || !file.isFile) {
            return@withContext "Error: Document file '$filePath' does not exist."
        }

        val result = DocumentParserEngine.parseDocument(file, maxPages = maxPages)
        if (result.isSuccess) {
            val content = result.getOrThrow()
            LogKeeper.log("DocumentParserTool", "Success", "Parsed ${file.name} (${content.length} chars)")
            content
        } else {
            val err = result.exceptionOrNull()?.message ?: "Parsing error"
            LogKeeper.log("DocumentParserTool", "Error", "Error parsing ${file.name}: $err")
            "Error parsing document '$filePath': $err"
        }
    }
}
