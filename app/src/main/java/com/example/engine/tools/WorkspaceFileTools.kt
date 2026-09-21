package com.example.engine.tools

import com.example.engine.fs.LocalFileManager
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Path and security helper for in-memory workspace tools.
 * Resolves paths safely within the active workspace sandbox and defends against directory traversal.
 */
object WorkspacePathResolver {

    fun resolve(path: String): Result<File> {
        return try {
            val workspaceRoot = LocalFileManager.getWorkspaceDir().canonicalFile
            val cleanedPath = path.trim()
                .removePrefix("file://")
                .trimStart('/')

            val targetFile = if (cleanedPath.isEmpty()) {
                workspaceRoot
            } else {
                File(workspaceRoot, cleanedPath).canonicalFile
            }

            if (!targetFile.path.startsWith(workspaceRoot.path)) {
                Result.failure(SecurityException("Access denied: Path '$path' attempts to escape the active workspace boundary."))
            } else {
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getRelativePath(file: File): String {
        return try {
            val root = LocalFileManager.getWorkspaceDir().canonicalFile
            val target = file.canonicalFile
            if (target.path == root.path) {
                "/"
            } else if (target.path.startsWith(root.path)) {
                "/" + target.path.removePrefix(root.path).trimStart(File.separatorChar).replace(File.separatorChar, '/')
            } else {
                target.path
            }
        } catch (e: Exception) {
            file.name
        }
    }
}

/**
 * 1. view_file: Token-optimized 1-indexed line slicer.
 */
class ViewFileTool : Tool {
    override val name: String = "view_file"
    override val description: String = "View the contents of a file in the workspace. Supports 1-indexed line slicing (StartLine and EndLine) to read specific ranges."

    override val parametersSchema: Map<String, Any> = mapOf(
        "AbsolutePath" to mapOf(
            "type" to "string",
            "description" to "The path to the file to view relative to workspace root.",
            "required" to true
        ),
        "StartLine" to mapOf(
            "type" to "integer",
            "description" to "Optional 1-indexed start line, inclusive."
        ),
        "EndLine" to mapOf(
            "type" to "integer",
            "description" to "Optional 1-indexed end line, inclusive."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val path = args["AbsolutePath"] as? String
            ?: args["path"] as? String
            ?: args["TargetFile"] as? String
            ?: return@withContext "Error: 'AbsolutePath' parameter is required."

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            return@withContext "Error: ${targetFileRes.exceptionOrNull()?.message}"
        }
        val file = targetFileRes.getOrThrow()

        if (!file.exists()) {
            return@withContext "Error: File '$path' does not exist."
        }
        if (file.isDirectory) {
            return@withContext "Error: '$path' is a directory, not a file. Use 'list_dir' instead."
        }

        try {
            val allLines = file.readLines()
            val totalLines = allLines.size
            val totalBytes = file.length()

            val rawStart = (args["StartLine"] as? Number)?.toInt()
            val rawEnd = (args["EndLine"] as? Number)?.toInt()

            val maxSlice = 1600
            val startLine = (rawStart ?: 1).coerceAtLeast(1)
            val endLine = (rawEnd ?: (startLine + maxSlice - 1)).coerceAtLeast(startLine)

            val clampedStart = startLine.coerceIn(1, if (totalLines == 0) 1 else totalLines)
            val clampedEnd = endLine.coerceIn(clampedStart, if (totalLines == 0) 1 else totalLines)

            val relPath = WorkspacePathResolver.getRelativePath(file)

            buildString {
                appendLine("File Path: `$relPath`")
                appendLine("Total Lines: $totalLines")
                appendLine("Total Bytes: $totalBytes")
                if (totalLines > 0) {
                    appendLine("Showing lines $clampedStart to $clampedEnd")
                    for (i in clampedStart..clampedEnd) {
                        val lineIndex = i - 1
                        val content = if (lineIndex in allLines.indices) allLines[lineIndex] else ""
                        appendLine("$i: $content")
                    }
                    if (clampedEnd < totalLines) {
                        appendLine("... [${totalLines - clampedEnd} more lines remaining in file]")
                    }
                } else {
                    appendLine("[Empty File]")
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("ViewFileTool", "Error", "Error reading $path: ${e.message}")
            "Error reading file '$path': ${e.message}"
        }
    }
}

/**
 * 2. edit_file: Exact single-occurrence substring replacer.
 */
class EditFileTool : Tool {
    override val name: String = "edit_file"
    override val description: String = "Perform a single contiguous surgical edit to a file. TargetContent must be an exact, unique substring in the target file."

    override val parametersSchema: Map<String, Any> = mapOf(
        "TargetFile" to mapOf(
            "type" to "string",
            "description" to "The target file to modify.",
            "required" to true
        ),
        "TargetContent" to mapOf(
            "type" to "string",
            "description" to "The exact string to be replaced. Must match exactly one occurrence in the file.",
            "required" to true
        ),
        "ReplacementContent" to mapOf(
            "type" to "string",
            "description" to "The content to replace the target content with.",
            "required" to true
        ),
        "Instruction" to mapOf(
            "type" to "string",
            "description" to "A short description of the changes being made."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val path = args["TargetFile"] as? String
            ?: args["path"] as? String
            ?: return@withContext "Error: 'TargetFile' parameter is required."

        val targetContent = args["TargetContent"] as? String
            ?: return@withContext "Error: 'TargetContent' parameter is required."

        val replacementContent = args["ReplacementContent"] as? String
            ?: return@withContext "Error: 'ReplacementContent' parameter is required."

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            return@withContext "Error: ${targetFileRes.exceptionOrNull()?.message}"
        }
        val file = targetFileRes.getOrThrow()

        if (!file.exists()) {
            return@withContext "Error: File '$path' does not exist."
        }
        if (file.isDirectory) {
            return@withContext "Error: '$path' is a directory, not a file."
        }

        try {
            val originalText = file.readText()
            val occurrences = countOccurrences(originalText, targetContent)

            if (occurrences == 0) {
                return@withContext "Error: Target content not found in file '${WorkspacePathResolver.getRelativePath(file)}'. Please use 'view_file' to check the current file contents and whitespace."
            }
            if (occurrences > 1) {
                return@withContext "Error: Target content matched $occurrences occurrences in file '${WorkspacePathResolver.getRelativePath(file)}'. It must match exactly once. Please include more surrounding lines to create a unique match."
            }

            val newText = originalText.replaceFirst(targetContent, replacementContent)
            file.writeText(newText)
            LocalFileManager.refreshFileTree()

            val relPath = WorkspacePathResolver.getRelativePath(file)
            LogKeeper.log("EditFileTool", "Success", "Edited $relPath")
            "Successfully edited file `$relPath` (1 occurrence replaced)."
        } catch (e: Exception) {
            LogKeeper.log("EditFileTool", "Error", "Error editing $path: ${e.message}")
            "Error editing file '$path': ${e.message}"
        }
    }

    private fun countOccurrences(text: String, target: String): Int {
        if (target.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(target, index)
            if (index != -1) {
                count++
                index += target.length
            } else {
                break
            }
        }
        return count
    }
}

/**
 * 3. multi_edit_file: Batch non-contiguous edits with atomic validation.
 */
class MultiEditFileTool : Tool {
    override val name: String = "multi_edit_file"
    override val description: String = "Apply multiple non-contiguous surgical edits to a file in a single atomic step."

    override val parametersSchema: Map<String, Any> = mapOf(
        "TargetFile" to mapOf(
            "type" to "string",
            "description" to "The target file to modify.",
            "required" to true
        ),
        "ReplacementChunks" to mapOf(
            "type" to "array",
            "description" to "A list of replacement chunks with TargetContent and ReplacementContent.",
            "required" to true
        ),
        "Instruction" to mapOf(
            "type" to "string",
            "description" to "A short description of the changes being made."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val path = args["TargetFile"] as? String
            ?: args["path"] as? String
            ?: return@withContext "Error: 'TargetFile' parameter is required."

        val rawChunks = args["ReplacementChunks"]
        val chunks = parseReplacementChunks(rawChunks)

        if (chunks.isEmpty()) {
            return@withContext "Error: 'ReplacementChunks' must contain at least one valid chunk."
        }

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            return@withContext "Error: ${targetFileRes.exceptionOrNull()?.message}"
        }
        val file = targetFileRes.getOrThrow()

        if (!file.exists()) {
            return@withContext "Error: File '$path' does not exist."
        }

        try {
            var currentText = file.readText()
            val relPath = WorkspacePathResolver.getRelativePath(file)

            // Step 1: Pre-validation of all chunks
            for ((index, chunk) in chunks.withIndex()) {
                val target = chunk.first
                if (target.isEmpty()) {
                    return@withContext "Error in Chunk #${index + 1}: TargetContent cannot be empty."
                }
                val count = currentText.split(target).size - 1
                if (count == 0) {
                    return@withContext "Error in Chunk #${index + 1}: TargetContent not found in `$relPath`."
                }
                if (count > 1) {
                    return@withContext "Error in Chunk #${index + 1}: TargetContent matched $count occurrences in `$relPath`. Must be unique."
                }
                // Simulate replacement to check cascading chunks
                currentText = currentText.replaceFirst(target, chunk.second)
            }

            // Step 2: Write fully verified changes
            file.writeText(currentText)
            LocalFileManager.refreshFileTree()

            LogKeeper.log("MultiEditFileTool", "Success", "Applied ${chunks.size} chunks to $relPath")
            "Successfully applied ${chunks.size} replacement chunks to `$relPath`."
        } catch (e: Exception) {
            LogKeeper.log("MultiEditFileTool", "Error", "Error in multi_edit_file: ${e.message}")
            "Error executing multi_edit_file: ${e.message}"
        }
    }

    private fun parseReplacementChunks(raw: Any?): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        when (raw) {
            is List<*> -> {
                for (item in raw) {
                    if (item is Map<*, *>) {
                        val target = item["TargetContent"]?.toString() ?: ""
                        val replacement = item["ReplacementContent"]?.toString() ?: ""
                        if (target.isNotEmpty()) {
                            result.add(target to replacement)
                        }
                    } else if (item is JSONObject) {
                        val target = item.optString("TargetContent", "")
                        val replacement = item.optString("ReplacementContent", "")
                        if (target.isNotEmpty()) {
                            result.add(target to replacement)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val obj = raw.optJSONObject(i)
                    if (obj != null) {
                        val target = obj.optString("TargetContent", "")
                        val replacement = obj.optString("ReplacementContent", "")
                        if (target.isNotEmpty()) {
                            result.add(target to replacement)
                        }
                    }
                }
            }
        }
        return result
    }
}

/**
 * 4. create_file: Safe UTF-8 file creation with overwrite protection.
 */
class CreateFileTool : Tool {
    override val name: String = "create_file"
    override val description: String = "Create a new file in the workspace with specified content."

    override val parametersSchema: Map<String, Any> = mapOf(
        "TargetFile" to mapOf(
            "type" to "string",
            "description" to "The target file path to create.",
            "required" to true
        ),
        "Content" to mapOf(
            "type" to "string",
            "description" to "The content to write to the file."
        ),
        "Overwrite" to mapOf(
            "type" to "boolean",
            "description" to "Set to true to overwrite an existing file. Defaults to false."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val path = args["TargetFile"] as? String
            ?: args["path"] as? String
            ?: return@withContext "Error: 'TargetFile' parameter is required."

        val content = args["Content"] as? String ?: ""
        val overwrite = (args["Overwrite"] as? Boolean) ?: false

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            return@withContext "Error: ${targetFileRes.exceptionOrNull()?.message}"
        }
        val file = targetFileRes.getOrThrow()

        if (file.exists() && !overwrite) {
            return@withContext "Error: File '${WorkspacePathResolver.getRelativePath(file)}' already exists. To overwrite it, set 'Overwrite: true'."
        }

        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            LocalFileManager.refreshFileTree()

            val relPath = WorkspacePathResolver.getRelativePath(file)
            LogKeeper.log("CreateFileTool", "Success", "Created $relPath (${content.length} chars)")
            "Successfully created file `$relPath` (${content.length} characters)."
        } catch (e: Exception) {
            LogKeeper.log("CreateFileTool", "Error", "Error creating $path: ${e.message}")
            "Error creating file '$path': ${e.message}"
        }
    }
}

/**
 * 5. delete_file: Protected file/directory deletion within workspace sandbox.
 */
class DeleteFileTool : Tool {
    override val name: String = "delete_file"
    override val description: String = "Delete an existing file or directory inside the workspace."
    override val permission: ToolPermission = ToolPermission.ALWAYS_ASK

    override val parametersSchema: Map<String, Any> = mapOf(
        "TargetFile" to mapOf(
            "type" to "string",
            "description" to "The target file or directory path to delete.",
            "required" to true
        ),
        "Force" to mapOf(
            "type" to "boolean",
            "description" to "If true, recursively deletes directories and contents. Defaults to false."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val path = args["TargetFile"] as? String
            ?: args["DirectoryPath"] as? String
            ?: args["path"] as? String
            ?: return@withContext "Error: 'TargetFile' parameter is required."

        val force = (args["Force"] as? Boolean) ?: false

        val targetFileRes = WorkspacePathResolver.resolve(path)
        if (targetFileRes.isFailure) {
            return@withContext "Error: ${targetFileRes.exceptionOrNull()?.message}"
        }
        val file = targetFileRes.getOrThrow()

        if (!file.exists()) {
            return@withContext "Error: Path '$path' does not exist."
        }

        val relPath = WorkspacePathResolver.getRelativePath(file)
        if (relPath == "/") {
            return@withContext "Error: Cannot delete the entire workspace root."
        }

        try {
            if (file.isDirectory && !force && (file.listFiles()?.isNotEmpty() == true)) {
                return@withContext "Error: Directory `$relPath` is not empty. Specify 'Force: true' to delete recursively."
            }

            val deleted = file.deleteRecursively()
            if (deleted) {
                LocalFileManager.refreshFileTree()
                LogKeeper.log("DeleteFileTool", "Success", "Deleted $relPath")
                "Successfully deleted `$relPath`."
            } else {
                "Failed to delete `$relPath`."
            }
        } catch (e: Exception) {
            LogKeeper.log("DeleteFileTool", "Error", "Error deleting $path: ${e.message}")
            "Error deleting '$path': ${e.message}"
        }
    }
}

/**
 * 6. move_file: Safe move and rename within workspace.
 */
class MoveFileTool : Tool {
    override val name: String = "move_file"
    override val description: String = "Move or rename a file or directory within the workspace."

    override val parametersSchema: Map<String, Any> = mapOf(
        "SourcePath" to mapOf(
            "type" to "string",
            "description" to "The source file or directory path.",
            "required" to true
        ),
        "DestinationPath" to mapOf(
            "type" to "string",
            "description" to "The destination path.",
            "required" to true
        ),
        "Overwrite" to mapOf(
            "type" to "boolean",
            "description" to "Set to true to overwrite destination if it exists."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val src = args["SourcePath"] as? String
            ?: args["from"] as? String
            ?: return@withContext "Error: 'SourcePath' parameter is required."

        val dest = args["DestinationPath"] as? String
            ?: args["to"] as? String
            ?: return@withContext "Error: 'DestinationPath' parameter is required."

        val overwrite = (args["Overwrite"] as? Boolean) ?: false

        val srcRes = WorkspacePathResolver.resolve(src)
        val destRes = WorkspacePathResolver.resolve(dest)

        if (srcRes.isFailure) return@withContext "Error: ${srcRes.exceptionOrNull()?.message}"
        if (destRes.isFailure) return@withContext "Error: ${destRes.exceptionOrNull()?.message}"

        val srcFile = srcRes.getOrThrow()
        val destFile = destRes.getOrThrow()

        if (!srcFile.exists()) {
            return@withContext "Error: Source path '$src' does not exist."
        }
        if (destFile.exists() && !overwrite) {
            return@withContext "Error: Destination path '$dest' already exists. Set 'Overwrite: true' to replace."
        }

        try {
            destFile.parentFile?.mkdirs()
            if (destFile.exists() && overwrite) {
                destFile.deleteRecursively()
            }

            val success = srcFile.renameTo(destFile)
            if (success) {
                LocalFileManager.refreshFileTree()
                val srcRel = WorkspacePathResolver.getRelativePath(srcFile)
                val destRel = WorkspacePathResolver.getRelativePath(destFile)
                LogKeeper.log("MoveFileTool", "Success", "Moved $srcRel -> $destRel")
                "Successfully moved `$srcRel` to `$destRel`."
            } else {
                "Failed to move '$src' to '$dest'."
            }
        } catch (e: Exception) {
            LogKeeper.log("MoveFileTool", "Error", "Error moving $src to $dest: ${e.message}")
            "Error moving file: ${e.message}"
        }
    }
}

/**
 * 7. list_dir: Formatted directory listing with size & recursive depth control.
 */
class ListDirTool : Tool {
    override val name: String = "list_dir"
    override val description: String = "List the contents of a directory in the workspace, including file sizes and subdirectories."

    override val parametersSchema: Map<String, Any> = mapOf(
        "DirectoryPath" to mapOf(
            "type" to "string",
            "description" to "The directory to list. Defaults to workspace root.",
            "required" to false
        ),
        "Recursive" to mapOf(
            "type" to "boolean",
            "description" to "If true, lists subdirectories recursively."
        ),
        "MaxDepth" to mapOf(
            "type" to "integer",
            "description" to "Maximum depth for recursive listing. Defaults to 2."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val path = args["DirectoryPath"] as? String ?: args["path"] as? String ?: ""
        val recursive = (args["Recursive"] as? Boolean) ?: false
        val maxDepth = ((args["MaxDepth"] as? Number)?.toInt()) ?: (if (recursive) 2 else 1)

        val targetRes = WorkspacePathResolver.resolve(path)
        if (targetRes.isFailure) {
            return@withContext "Error: ${targetRes.exceptionOrNull()?.message}"
        }
        val targetDir = targetRes.getOrThrow()

        if (!targetDir.exists()) {
            return@withContext "Error: Directory '$path' does not exist."
        }
        if (!targetDir.isDirectory) {
            return@withContext "Error: '$path' is a file, not a directory."
        }

        try {
            val relPath = WorkspacePathResolver.getRelativePath(targetDir)
            val entries = mutableListOf<String>()
            walkDir(targetDir, 0, maxDepth, entries)

            buildString {
                appendLine("Directory: `$relPath` (${entries.size} items)")
                if (entries.isEmpty()) {
                    appendLine("  (empty directory)")
                } else {
                    entries.forEach { appendLine(it) }
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("ListDirTool", "Error", "Error listing $path: ${e.message}")
            "Error listing directory '$path': ${e.message}"
        }
    }

    private fun walkDir(dir: File, currentDepth: Int, maxDepth: Int, outList: MutableList<String>) {
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
        val indent = "  ".repeat(currentDepth + 1)
        for (file in files) {
            val sizeStr = if (file.isDirectory) "[DIR]" else formatSize(file.length())
            outList.add("$indent• ${file.name} ($sizeStr)")
            if (file.isDirectory && currentDepth + 1 < maxDepth) {
                walkDir(file, currentDepth + 1, maxDepth, outList)
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/**
 * 8. grep_search: In-memory recursive text & regex search across workspace source files.
 */
class GrepSearchTool : Tool {
    override val name: String = "grep_search"
    override val description: String = "Perform a fast, in-memory recursive text or regex search across workspace files with line numbers."

    override val parametersSchema: Map<String, Any> = mapOf(
        "Query" to mapOf(
            "type" to "string",
            "description" to "The search text or regular expression to find.",
            "required" to true
        ),
        "DirectoryPath" to mapOf(
            "type" to "string",
            "description" to "The subdirectory to search in. Defaults to workspace root."
        ),
        "CaseSensitive" to mapOf(
            "type" to "boolean",
            "description" to "Whether the search should be case sensitive. Defaults to true."
        ),
        "FilePattern" to mapOf(
            "type" to "string",
            "description" to "Optional file extension or pattern filter (e.g. '.kt', '.json', '.html')."
        ),
        "MaxMatches" to mapOf(
            "type" to "integer",
            "description" to "Maximum number of matching lines to return. Defaults to 100."
        )
    )

    private val ignoredDirectories = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".build-outputs")
    private val binaryExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "zip", "tar", "gz", "apk", "jar", "class", "dex", "so", "bin", "db", "sqlite")

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val query = args["Query"] as? String
            ?: args["query"] as? String
            ?: return@withContext "Error: 'Query' parameter is required."

        val dirPath = args["DirectoryPath"] as? String ?: args["path"] as? String ?: ""
        val caseSensitive = (args["CaseSensitive"] as? Boolean) ?: true
        val filePattern = (args["FilePattern"] as? String)?.lowercase()?.removePrefix("*")
        val maxMatches = ((args["MaxMatches"] as? Number)?.toInt()) ?: 100

        val targetRes = WorkspacePathResolver.resolve(dirPath)
        if (targetRes.isFailure) {
            return@withContext "Error: ${targetRes.exceptionOrNull()?.message}"
        }
        val searchRoot = targetRes.getOrThrow()

        if (!searchRoot.exists()) {
            return@withContext "Error: Directory '$dirPath' does not exist."
        }

        try {
            val matches = mutableListOf<String>()
            var scannedFiles = 0
            val matchedFiles = mutableSetOf<String>()

            val regex = try {
                if (caseSensitive) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                // Fall back to literal match if invalid regex
                null
            }

            searchRoot.walkTopDown()
                .onEnter { dir -> !ignoredDirectories.contains(dir.name) }
                .filter { it.isFile && !binaryExtensions.contains(it.extension.lowercase()) }
                .filter { filePattern == null || it.name.lowercase().endsWith(filePattern) }
                .forEach { file ->
                    if (matches.size >= maxMatches) return@forEach
                    scannedFiles++
                    try {
                        val lines = file.readLines()
                        val relPath = WorkspacePathResolver.getRelativePath(file)
                        for ((index, line) in lines.withIndex()) {
                            val isMatch = if (regex != null) {
                                regex.containsMatchIn(line)
                            } else {
                                line.contains(query, ignoreCase = !caseSensitive)
                            }

                            if (isMatch) {
                                matches.add("$relPath:${index + 1}: ${line.trimEnd()}")
                                matchedFiles.add(relPath)
                                if (matches.size >= maxMatches) break
                            }
                        }
                    } catch (_: Exception) {
                        // Skip unreadable files
                    }
                }

            buildString {
                appendLine("Grep Results for `$query`:")
                appendLine("Found ${matches.size} match(es) across ${matchedFiles.size} file(s) (scanned $scannedFiles files):")
                if (matches.isEmpty()) {
                    appendLine("  (no matches found)")
                } else {
                    matches.forEach { appendLine(it) }
                    if (matches.size >= maxMatches) {
                        appendLine("... [Capped at $maxMatches matches]")
                    }
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("GrepSearchTool", "Error", "Error during grep for '$query': ${e.message}")
            "Error running grep search: ${e.message}"
        }
    }
}
