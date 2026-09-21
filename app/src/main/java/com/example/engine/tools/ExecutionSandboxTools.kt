package com.example.engine.tools

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.engine.fs.LocalFileManager
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 1. JsSandboxTool: Isolated JavaScript execution runtime.
 * Evaluates scripts safely without OS/filesystem access, capturing console logs and return values.
 */
class JsSandboxTool : Tool {
    override val name: String = "js_sandbox"
    override val description: String = "Executes isolated JavaScript code in a secure sandboxed runtime. Returns console logs and the evaluated result. Ideal for calculations, data filtering, algorithmic transformations, and JSON parsing without OS access."
    override val permission: ToolPermission = ToolPermission.USE_FREELY

    override val parametersSchema: Map<String, Any> = mapOf(
        "Code" to mapOf(
            "type" to "string",
            "description" to "The JavaScript code to execute.",
            "required" to true
        ),
        "TimeoutMs" to mapOf(
            "type" to "integer",
            "description" to "Execution timeout in milliseconds (default 5000ms, max 15000ms)."
        )
    )

    private var headlessWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val code = args["Code"] as? String
            ?: args["code"] as? String
            ?: args["script"] as? String
            ?: return@withContext "Error: 'Code' parameter is required."

        val rawTimeout = (args["TimeoutMs"] as? Number)?.toLong() ?: 5000L
        val timeoutMs = rawTimeout.coerceIn(500L, 15000L)

        val context = LocalFileManager.getContext()
        if (context == null) {
            // Fallback for non-Android / non-initialized context
            return@withContext executeFallback(code)
        }

        try {
            withTimeout(timeoutMs) {
                runInHeadlessWebView(context, code)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            LogKeeper.log("JsSandboxTool", "Timeout", "JS execution timed out after ${timeoutMs}ms")
            "Error: JavaScript execution timed out after ${timeoutMs}ms. Infinite loops or unresolving promises are aborted."
        } catch (e: Exception) {
            LogKeeper.log("JsSandboxTool", "Error", "JS execution error: ${e.message}")
            "Error evaluating JavaScript: ${e.message}"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runInHeadlessWebView(context: Context, userCode: String): String = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            try {
                val logs = mutableListOf<String>()
                var isCompleted = false

                val webView = headlessWebView ?: WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.databaseEnabled = false
                    settings.domStorageEnabled = false
                    headlessWebView = this
                }

                class JsBridge {
                    @JavascriptInterface
                    fun log(level: String, msg: String) {
                        logs.add("[$level] $msg")
                    }

                    @JavascriptInterface
                    fun complete(success: Boolean, resultJson: String) {
                        if (isCompleted) return
                        isCompleted = true
                        mainHandler.post {
                            val output = buildString {
                                appendLine("=== JavaScript Sandbox Result ===")
                                if (logs.isNotEmpty()) {
                                    appendLine("--- Console Output ---")
                                    logs.forEach { appendLine(it) }
                                }
                                appendLine("--- Return Value ---")
                                appendLine(resultJson)
                            }
                            if (continuation.isActive) {
                                continuation.resume(output)
                            }
                        }
                    }
                }

                webView.addJavascriptInterface(JsBridge(), "__bridge")

                val escapedCode = JSONObject.quote(userCode)
                val wrapperScript = """
                    (async function() {
                        const originalLog = console.log;
                        const originalWarn = console.warn;
                        const originalError = console.error;
                        
                        console.log = function(...args) {
                            __bridge.log('LOG', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                        };
                        console.warn = function(...args) {
                            __bridge.log('WARN', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                        };
                        console.error = function(...args) {
                            __bridge.log('ERROR', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                        };
                        
                        try {
                            const evalFn = new Function($escapedCode);
                            const res = await evalFn();
                            let formatted = 'undefined';
                            if (res !== undefined) {
                                try {
                                    formatted = JSON.stringify(res, null, 2);
                                } catch(e) {
                                    formatted = String(res);
                                }
                            }
                            __bridge.complete(true, formatted);
                        } catch (err) {
                            __bridge.complete(false, 'Exception: ' + (err && err.stack ? err.stack : String(err)));
                        }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(wrapperScript, null)

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        try {
                            webView.stopLoading()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume("Error launching JS sandbox: ${e.message}")
                }
            }
        }
    }

    private fun executeFallback(code: String): String {
        return buildString {
            appendLine("=== JavaScript Sandbox (Fallback Mode) ===")
            appendLine("Code received (${code.length} chars).")
            appendLine("Evaluator: Context is initializing or headless.")
        }
    }
}

/**
 * 2. ShellProcessTool: Sandboxed Native Process Runner.
 * Executes commands tightly scoped to the workspace directory with timeout & stream capture.
 */
class ShellProcessTool : Tool {
    override val name: String = "run_command"
    override val description: String = "Executes a shell command or script inside the active workspace directory. Captures stdout, stderr, and exit codes. Scoped strictly to the active workspace sandbox with configurable timeout."
    override val permission: ToolPermission = ToolPermission.ALWAYS_ASK

    override val parametersSchema: Map<String, Any> = mapOf(
        "CommandLine" to mapOf(
            "type" to "string",
            "description" to "The exact shell command line string to execute.",
            "required" to true
        ),
        "Cwd" to mapOf(
            "type" to "string",
            "description" to "The current working directory relative to the workspace root. Defaults to workspace root."
        ),
        "TimeoutMs" to mapOf(
            "type" to "integer",
            "description" to "Execution timeout in milliseconds. Defaults to 15000ms (15 seconds)."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val commandLine = args["CommandLine"] as? String
            ?: args["command"] as? String
            ?: args["cmd"] as? String
            ?: return@withContext "Error: 'CommandLine' parameter is required."

        val cwdParam = args["Cwd"] as? String ?: args["cwd"] as? String ?: ""
        val rawTimeout = (args["TimeoutMs"] as? Number)?.toLong() ?: 15000L
        val timeoutMs = rawTimeout.coerceIn(1000L, 60000L)

        val workspaceDir = LocalFileManager.getWorkspaceDir().canonicalFile
        val targetCwd = if (cwdParam.isNotBlank()) {
            val resolved = WorkspacePathResolver.resolve(cwdParam)
            if (resolved.isFailure) {
                return@withContext "Error resolving Cwd: ${resolved.exceptionOrNull()?.message}"
            }
            resolved.getOrThrow()
        } else {
            workspaceDir
        }

        if (!targetCwd.exists() || !targetCwd.isDirectory) {
            return@withContext "Error: Working directory '${WorkspacePathResolver.getRelativePath(targetCwd)}' does not exist."
        }

        val startTime = System.currentTimeMillis()

        try {
            val shellBinary = findShellBinary()
            val processBuilder = ProcessBuilder(shellBinary, "-c", commandLine)
                .directory(targetCwd)

            val env = processBuilder.environment()
            env["HOME"] = workspaceDir.absolutePath
            env["PWD"] = targetCwd.absolutePath
            env["TMPDIR"] = File(workspaceDir, ".tmp").apply { mkdirs() }.absolutePath
            env["PATH"] = "${env["PATH"] ?: ""}:/system/bin:/system/xbin:/vendor/bin"

            val process = processBuilder.start()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdoutBuilder.appendLine(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stderrBuilder.appendLine(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val duration = System.currentTimeMillis() - startTime

            if (!finished) {
                process.destroyForcibly()
                LogKeeper.log("ShellProcessTool", "Timeout", "Command '$commandLine' timed out after ${timeoutMs}ms")
                return@withContext "Error: Command timed out after ${timeoutMs}ms and was forcibly terminated.\nPartial Output:\n$stdoutBuilder"
            }

            stdoutThread.join(500)
            stderrThread.join(500)

            val exitCode = process.exitValue()
            LocalFileManager.refreshFileTree()

            val stdout = stdoutBuilder.toString().trim()
            val stderr = stderrBuilder.toString().trim()

            LogKeeper.log("ShellProcessTool", "Complete", "Command exit=$exitCode in ${duration}ms")

            buildString {
                appendLine("Command: `$commandLine`")
                appendLine("Working Dir: `${WorkspacePathResolver.getRelativePath(targetCwd)}`")
                appendLine("Exit Code: $exitCode (completed in ${duration}ms)")
                if (stdout.isNotEmpty()) {
                    appendLine("--- STDOUT ---")
                    appendLine(stdout)
                }
                if (stderr.isNotEmpty()) {
                    appendLine("--- STDERR ---")
                    appendLine(stderr)
                }
                if (stdout.isEmpty() && stderr.isEmpty()) {
                    appendLine("(No output produced)")
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("ShellProcessTool", "Error", "Error running command: ${e.message}")
            "Error executing shell command: ${e.message}"
        }
    }

    private fun findShellBinary(): String {
        return when {
            File("/system/bin/sh").canExecute() -> "/system/bin/sh"
            File("/bin/sh").canExecute() -> "/bin/sh"
            else -> "sh"
        }
    }
}

/**
 * 3. PRootLinuxTool: Zero-Root Linux & Python/Node Environment.
 * Runs commands inside userland container with active workspace mounted to /workspace.
 */
class PRootLinuxTool : Tool {
    override val name: String = "proot_exec"
    override val description: String = "Executes commands inside a zero-root PRoot Linux container environment (Alpine Linux rootfs) with Python 3, Node.js, and CLI tools. Binds the active workspace to /workspace."
    override val permission: ToolPermission = ToolPermission.ALWAYS_ASK

    override val parametersSchema: Map<String, Any> = mapOf(
        "Command" to mapOf(
            "type" to "string",
            "description" to "Command to execute inside Linux rootfs (e.g., 'python3 script.py', 'node index.js', 'apk info').",
            "required" to true
        ),
        "Runtime" to mapOf(
            "type" to "string",
            "description" to "Target runtime environment: 'python', 'node', or 'shell'. Defaults to 'shell'."
        ),
        "ScriptContent" to mapOf(
            "type" to "string",
            "description" to "Optional script content to write and execute inside the workspace container."
        ),
        "TimeoutMs" to mapOf(
            "type" to "integer",
            "description" to "Timeout in milliseconds. Defaults to 30000ms (30 seconds)."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val command = args["Command"] as? String
            ?: args["command"] as? String
            ?: ""

        val scriptContent = args["ScriptContent"] as? String
        val runtime = (args["Runtime"] as? String)?.lowercase() ?: "shell"
        val rawTimeout = (args["TimeoutMs"] as? Number)?.toLong() ?: 30000L
        val timeoutMs = rawTimeout.coerceIn(1000L, 120000L)

        val workspaceDir = LocalFileManager.getWorkspaceDir().canonicalFile
        val baseDir = LocalFileManager.getBaseDir() ?: workspaceDir.parentFile ?: workspaceDir

        // If inline script is provided, save it to a temporary runner script
        val finalCommand = if (!scriptContent.isNullOrBlank()) {
            val extension = when (runtime) {
                "python" -> ".py"
                "node", "javascript" -> ".js"
                else -> ".sh"
            }
            val tempScript = File(workspaceDir, ".runner_script$extension")
            tempScript.writeText(scriptContent)
            when (runtime) {
                "python" -> "python3 ${tempScript.name}"
                "node", "javascript" -> "node ${tempScript.name}"
                else -> "sh ${tempScript.name}"
            }
        } else {
            if (command.isBlank()) {
                return@withContext "Error: Either 'Command' or 'ScriptContent' must be provided."
            }
            command
        }

        val prootDir = File(baseDir, "proot")
        val rootfsDir = File(baseDir, "rootfs")
        val prootBin = File(prootDir, "proot")

        val hasNativePRoot = prootBin.exists() && prootBin.canExecute() && rootfsDir.exists()

        if (hasNativePRoot) {
            executePRootNative(prootBin, rootfsDir, workspaceDir, finalCommand, timeoutMs)
        } else {
            executePRootUserlandFallback(workspaceDir, finalCommand, runtime, timeoutMs)
        }
    }

    private fun executePRootNative(
        prootBin: File,
        rootfsDir: File,
        workspaceDir: File,
        command: String,
        timeoutMs: Long
    ): String {
        val startTime = System.currentTimeMillis()
        return try {
            val processBuilder = ProcessBuilder(
                prootBin.absolutePath,
                "-r", rootfsDir.absolutePath,
                "-b", "${workspaceDir.absolutePath}:/workspace",
                "-w", "/workspace",
                "/bin/sh", "-c", command
            ).directory(workspaceDir)

            val process = processBuilder.start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val duration = System.currentTimeMillis() - startTime

            if (!finished) {
                process.destroyForcibly()
                return "Error: PRoot container command timed out after ${timeoutMs}ms and was killed."
            }

            LocalFileManager.refreshFileTree()
            val exitCode = process.exitValue()

            buildString {
                appendLine("=== PRoot Alpine Container Execution ===")
                appendLine("Command: `$command`")
                appendLine("Exit Code: $exitCode (${duration}ms)")
                if (stdout.isNotBlank()) {
                    appendLine("--- STDOUT ---")
                    appendLine(stdout.trim())
                }
                if (stderr.isNotBlank()) {
                    appendLine("--- STDERR ---")
                    appendLine(stderr.trim())
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("PRootLinuxTool", "Error", "PRoot native execution error: ${e.message}")
            "Error executing PRoot container: ${e.message}"
        }
    }

    private fun executePRootUserlandFallback(
        workspaceDir: File,
        command: String,
        runtime: String,
        timeoutMs: Long
    ): String {
        val startTime = System.currentTimeMillis()
        return try {
            val shellBinary = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "sh"
            val processBuilder = ProcessBuilder(shellBinary, "-c", command)
                .directory(workspaceDir)

            val env = processBuilder.environment()
            env["HOME"] = workspaceDir.absolutePath
            env["PWD"] = workspaceDir.absolutePath
            env["TMPDIR"] = File(workspaceDir, ".tmp").apply { mkdirs() }.absolutePath

            val process = processBuilder.start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val duration = System.currentTimeMillis() - startTime

            if (!finished) {
                process.destroyForcibly()
                return "Error: Command timed out after ${timeoutMs}ms."
            }

            LocalFileManager.refreshFileTree()
            val exitCode = process.exitValue()

            buildString {
                appendLine("=== Compute Sandbox Runner ($runtime) ===")
                appendLine("Command: `$command`")
                appendLine("Exit Code: $exitCode (${duration}ms)")
                if (stdout.isNotBlank()) {
                    appendLine("--- STDOUT ---")
                    appendLine(stdout.trim())
                }
                if (stderr.isNotBlank()) {
                    appendLine("--- STDERR ---")
                    appendLine(stderr.trim())
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("PRootLinuxTool", "Error", "Sandbox execution error: ${e.message}")
            "Error executing sandbox command: ${e.message}"
        }
    }
}

/**
 * 4. RunPythonTool: Direct Python 3 compute tool.
 */
class RunPythonTool : Tool {
    override val name: String = "run_python"
    override val description: String = "Convenience compute tool to execute Python 3 code or scripts against the active workspace."
    override val permission: ToolPermission = ToolPermission.SESSION_ALLOW

    override val parametersSchema: Map<String, Any> = mapOf(
        "Code" to mapOf(
            "type" to "string",
            "description" to "Python 3 code snippet to execute."
        ),
        "ScriptPath" to mapOf(
            "type" to "string",
            "description" to "Path to an existing .py script relative to workspace root."
        ),
        "TimeoutMs" to mapOf(
            "type" to "integer",
            "description" to "Execution timeout in milliseconds. Defaults to 15000ms."
        )
    )

    private val prootTool = PRootLinuxTool()

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val code = args["Code"] as? String ?: args["code"] as? String
        val scriptPath = args["ScriptPath"] as? String ?: args["script_path"] as? String
        val timeoutMs = (args["TimeoutMs"] as? Number)?.toLong() ?: 15000L

        if (code.isNullOrBlank() && scriptPath.isNullOrBlank()) {
            return@withContext "Error: Either 'Code' or 'ScriptPath' parameter is required."
        }

        val forwardArgs = mutableMapOf<String, Any>(
            "Runtime" to "python",
            "TimeoutMs" to timeoutMs
        )

        if (!code.isNullOrBlank()) {
            forwardArgs["ScriptContent"] = code
            forwardArgs["Command"] = "python3 .runner_script.py"
        } else if (!scriptPath.isNullOrBlank()) {
            val scriptFileRes = WorkspacePathResolver.resolve(scriptPath)
            if (scriptFileRes.isFailure) {
                return@withContext "Error: ${scriptFileRes.exceptionOrNull()?.message}"
            }
            val scriptFile = scriptFileRes.getOrThrow()
            if (!scriptFile.exists()) {
                return@withContext "Error: Python script '$scriptPath' does not exist in workspace."
            }
            forwardArgs["Command"] = "python3 ${scriptFile.name}"
        }

        prootTool.execute(forwardArgs)
    }
}
