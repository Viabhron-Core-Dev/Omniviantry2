package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val type: String, // e.g., "ERROR", "CRASH", "FAILURE", "INFO", "STARTUP"
    val component: String,
    val message: String,
    val stackTrace: String? = null
)

/**
 * Headless logging engine for OmniRoot.
 *
 * Architecture:
 * - Headless Catcher: Asynchronously sanitizes and streams logs directly to disk (2MB rolling limit).
 * - Zero In-Memory Bloat: Does NOT store logs in state flows or RAM lists.
 * - Emergency Parachute Buffer: Retains only a tiny ring buffer (50 items) in memory for crashes.
 * - On-Demand Reader: Reads and parses logs from disk only when requested by the UI.
 */
object LogKeeper {
    private const val TAG = "LogKeeper"
    private const val PREFS_NAME = "omniroot_log_prefs"
    private const val KEY_ENABLED = "log_keeper_enabled"
    private const val LOG_FILE_NAME = "omniroot_active_logs.jsonl"
    private const val LOG_OLD_FILE_NAME = "omniroot_active_logs.old.jsonl"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB rolling limit
    private const val PARACHUTE_BUFFER_CAPACITY = 50

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // Signal emitted when new logs are written, allowing active reader UI to reload on-demand
    private val _logUpdateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logUpdateSignal: SharedFlow<Unit> = _logUpdateSignal.asSharedFlow()

    // Bounded in-memory parachute buffer for emergency crash dumps only
    private val parachuteRingBuffer = ArrayDeque<LogEntry>(PARACHUTE_BUFFER_CAPACITY)
    private val bufferLock = Any()

    private var appContext: Context? = null
    private var logFile: File? = null
    private var oldLogFile: File? = null
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isExceptionHandlerInstalled = false

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs?.getBoolean(KEY_ENABLED, true) ?: true

        logFile = File(app.filesDir, LOG_FILE_NAME)
        oldLogFile = File(app.filesDir, LOG_OLD_FILE_NAME)

        installCrashParachute(app)

        log("INFO", "LogKeeper", "Headless LogKeeper initialized. Rolling disk logging active (2MB limit).")
    }

    private fun installCrashParachute(context: Context) {
        if (isExceptionHandlerInstalled) return
        isExceptionHandlerInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTraceString = sw.toString()

                log("CRASH", "UncaughtException", "Fatal crash on thread ${thread.name}: ${throwable.message}", stackTraceString)
                dropCrashParachute(context, thread, throwable, stackTraceString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to drop crash parachute", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun dropCrashParachute(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        stackTraceString: String
    ) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "OmniRoot_CRASH_PARACHUTE_$timeStamp.txt"

        val targetDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir
        )

        val recentLogs: List<LogEntry> = synchronized(bufferLock) {
            parachuteRingBuffer.toList()
        }

        for (dir in targetDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val crashFile = File(dir, fileName)
                crashFile.printWriter().use { out ->
                    out.println("=======================================================")
                    out.println("          OMNIROOT EMERGENCY CRASH PARACHUTE           ")
                    out.println("=======================================================")
                    out.println("Timestamp:   ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                    out.println("Thread:      ${thread.name} (id: ${thread.id})")
                    out.println("Exception:   ${throwable.javaClass.name}")
                    out.println("Message:     ${throwable.message}")
                    out.println("Device:      ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                    out.println("Package:     ${context.packageName}")
                    out.println("\n----------------- FATAL STACK TRACE -----------------")
                    out.println(stackTraceString)
                    out.println("\n----------------- PRE-CRASH RECENT LOGS -------------")
                    recentLogs.forEach { entry ->
                        val entryTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
                        out.println("[$entryTime] [${entry.type}] ${entry.component}: ${entry.message}")
                        if (!entry.stackTrace.isNullOrBlank()) {
                            out.println("   Trace: ${entry.stackTrace.lines().take(3).joinToString(" ")}")
                        }
                    }
                    out.println("=======================================================")
                }
                Log.e(TAG, "Emergency Crash Parachute dropped successfully to: ${crashFile.absolutePath}")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error writing crash parachute to ${dir.absolutePath}", e)
            }
        }
    }

    fun toggle(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    /**
     * Headless log catcher. Asynchronously records the log entry to a rolling disk file.
     */
    fun log(type: String, component: String, message: String, stackTrace: String? = null) {
        if (!_isEnabled.value) return

        val sanitizedMessage = sanitize(message)
        val sanitizedStackTrace = stackTrace?.let { sanitize(it) }

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = type,
            component = component,
            message = sanitizedMessage,
            stackTrace = sanitizedStackTrace
        )

        // Keep small parachute buffer updated
        synchronized(bufferLock) {
            if (parachuteRingBuffer.size >= PARACHUTE_BUFFER_CAPACITY) {
                parachuteRingBuffer.removeFirst()
            }
            parachuteRingBuffer.addLast(entry)
        }

        Log.d(TAG, "[$type] $component: $sanitizedMessage")

        // Persist to rolling disk log file
        scope.launch {
            try {
                val file = logFile ?: return@launch

                // Rolling log check: if file exceeds 2MB, rotate to .old
                if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                    val old = oldLogFile ?: File(file.parentFile, LOG_OLD_FILE_NAME)
                    if (old.exists()) old.delete()
                    file.renameTo(old)
                }

                val obj = JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("type", entry.type)
                    put("component", entry.component)
                    put("message", entry.message)
                    if (entry.stackTrace != null) {
                        put("stackTrace", entry.stackTrace)
                    }
                }
                file.appendText(obj.toString() + "\n")
                _logUpdateSignal.tryEmit(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append log entry to disk", e)
            }
        }
    }

    /**
     * On-Demand Reader UI function.
     * Reads logs from the rolling disk files without storing them permanently in memory.
     */
    suspend fun readLogsFromDisk(limit: Int = 1000): List<LogEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LogEntry>()

        fun parseFile(f: File?) {
            if (f == null || !f.exists()) return
            try {
                f.forEachLine { line ->
                    if (line.isNotBlank()) {
                        try {
                            val obj = JSONObject(line)
                            result.add(
                                LogEntry(
                                    timestamp = obj.optLong("timestamp", 0L),
                                    type = obj.optString("type", "INFO"),
                                    component = obj.optString("component", "Unknown"),
                                    message = obj.optString("message", ""),
                                    stackTrace = if (obj.has("stackTrace") && !obj.isNull("stackTrace")) obj.getString("stackTrace") else null
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading log file: ${f.name}", e)
            }
        }

        // Read active and old files
        parseFile(oldLogFile)
        parseFile(logFile)

        // Sort descending and return up to limit
        result.sortedByDescending { it.timestamp }.take(limit)
    }

    suspend fun exportAndClear(context: Context): Boolean = withContext(Dispatchers.IO) {
        val logsToExport = readLogsFromDisk(limit = 10000)
        if (logsToExport.isEmpty()) return@withContext false

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) {
            downloadsDir
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(targetDir, "OmniRoot_Log_$timestamp.txt")

        try {
            file.printWriter().use { out ->
                out.println("--- OmniRoot Log Export ---")
                out.println("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                out.println("Total entries: ${logsToExport.size}")
                out.println("----------------------------------------")
                logsToExport.forEach { entry ->
                    val timeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
                    out.println("[$timeString] [${entry.type}] ${entry.component}")
                    out.println("Message: ${entry.message}")
                    if (entry.stackTrace != null) {
                        out.println("StackTrace:\n${entry.stackTrace}")
                    }
                    out.println("----------------------------------------")
                }
            }

            // Clear disk files and memory buffer
            logFile?.delete()
            oldLogFile?.delete()
            synchronized(bufferLock) {
                parachuteRingBuffer.clear()
            }
            _logUpdateSignal.tryEmit(Unit)
            Log.d(TAG, "Logs exported and cleared to: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs", e)
            false
        }
    }

    private fun sanitize(input: String): String {
        return input.replace(Regex("(?i)(password|secret|key|token|credential)[\\s=:]+[^\\s,;]+"), "$1=***SANITIZED***")
    }
}
