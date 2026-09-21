package com.example.ui.artifacts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.db.ArtifactEntity
import com.example.engine.settings.ThreadSecretsStore
import com.example.utils.LogEntry
import com.example.utils.LogKeeper
import com.example.utils.LogKeeperCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

/**
 * Robust Backup PWA Player Engine.
 * Features:
 * 1. Offline Local Resource Interceptor: Serves all HTML/JS/CSS/WASM assets directly from sandbox with proper MIME types.
 * 2. Keep Screen On wake-lock.
 * 3. Orientation Lock (Sensor / Portrait / Landscape).
 * 4. In-Player Real-Time LogKeeper Console Inspector.
 * 5. Instant Reload & Cache Invalidation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PwaBackupPlayerView(
    artifact: ArtifactEntity,
    onSwitchToPrimary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pwaSettings = remember(artifact.settingsJson) {
        com.example.engine.pwa.PwaAppSettings.fromJson(artifact.settingsJson)
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var keepScreenOn by remember(pwaSettings) { mutableStateOf(pwaSettings.keepScreenOn) }
    var orientationMode by remember(pwaSettings) {
        val mode = when (pwaSettings.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        mutableIntStateOf(mode)
    }
    var showLogsSheet by remember { mutableStateOf(false) }
    var logsList by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // Resolve base repo directory
    val repoDir = remember(artifact.id) {
        val f = File(context.filesDir, "artifacts/${artifact.id}/repo")
        if (f.exists() && f.isDirectory) f else null
    }

    // Keep screen on management
    DisposableEffect(keepScreenOn) {
        val activity = context as? Activity
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Orientation management
    DisposableEffect(orientationMode) {
        val activity = context as? Activity
        activity?.requestedOrientation = orientationMode
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Load recent logs for sheet
    fun refreshLogs() {
        scope.launch {
            val all = LogKeeper.readLogsFromDisk(limit = 200)
            val filtered = all.filter {
                it.component.contains(artifact.title, ignoreCase = true) ||
                it.component.contains(artifact.id, ignoreCase = true) ||
                it.component.startsWith("PWA_")
            }
            logsList = if (filtered.isNotEmpty()) filtered else all.take(50)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main WebView with local interception
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                    }

                    // Attach LogKeeper Catcher
                    LogKeeperCatcher.attachToWebView(this, artifact.title) { err ->
                        lastError = err
                    }

                    // Backup Local Interceptor Client
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url ?: return null

                            // Check OFFLINE mode: if offline and not requesting local app sandbox asset, block external calls
                            val isExternal = url.scheme == "http" || url.scheme == "https"
                            val isLocalAppScheme = url.host == "app.local" || url.toString().startsWith("file://")

                            if (pwaSettings.networkThrottle == com.example.engine.pwa.PwaAppSettings.THROTTLE_OFFLINE && isExternal && !isLocalAppScheme) {
                                LogKeeper.log("WARNING", "BackupPlayer", "Blocked external request in Offline Mode: $url")
                                return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    503,
                                    "Service Unavailable (Offline Mode)",
                                    emptyMap(),
                                    java.io.ByteArrayInputStream("Offline Mode Active".toByteArray())
                                )
                            }

                            // If simulated latency is configured, inject artificial delay
                            if (pwaSettings.latencyMs > 0) {
                                try {
                                    Thread.sleep(pwaSettings.latencyMs)
                                } catch (_: InterruptedException) {}
                            }

                            if (repoDir == null || !repoDir.exists()) return null

                            val path = url.path ?: return null
                            val cleanPath = path.removePrefix("/")
                            val requestedFile = if (cleanPath.isBlank() || cleanPath == "index.html") {
                                File(repoDir, "index.html")
                            } else {
                                File(repoDir, cleanPath)
                            }

                            if (requestedFile.exists() && requestedFile.isFile) {
                                try {
                                    val mimeType = getMimeType(requestedFile)
                                    val encoding = if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json")) "UTF-8" else null
                                    return WebResourceResponse(
                                        mimeType,
                                        encoding,
                                        FileInputStream(requestedFile)
                                    )
                                } catch (e: Exception) {
                                    LogKeeper.log("WARNING", "BackupPlayer", "Error reading local asset ${requestedFile.name}: ${e.message}")
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            LogKeeper.log("INFO", "BackupPlayer", "Backup loader finished rendering: $url")
                            // Inject secrets
                            val activeWs = artifact.workspaceId ?: "artifact_${artifact.id}"
                            val secretsJson = ThreadSecretsStore.getSecretsJson(ctx, activeWs)
                            val injectScript = """
                                (function() {
                                    try {
                                        window.__SECRETS__ = Object.freeze($secretsJson);
                                        window.dispatchEvent(new CustomEvent('secretsready', { detail: { count: ${ThreadSecretsStore.getSecrets(ctx, activeWs).size} } }));
                                    } catch(e) {}
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(injectScript, null)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                lastError = "Load error: ${error?.description}"
                            }
                        }
                    }

                    // Load URL
                    if (repoDir != null && File(repoDir, "index.html").exists()) {
                        loadUrl("file://${File(repoDir, "index.html").absolutePath}")
                    } else {
                        loadDataWithBaseURL("https://app.local/", artifact.content, "text/html", "UTF-8", null)
                    }

                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Error banner if any
        AnimatedVisibility(
            visible = lastError != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = lastError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { lastError = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Floating Backup Engine Control Dock (Bottom Overlay)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Engine Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Backup Engine",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Switch back to Primary
                TextButton(
                    onClick = onSwitchToPrimary,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Primary", fontSize = 12.sp)
                }

                // Keep Screen On Toggle
                IconButton(
                    onClick = {
                        keepScreenOn = !keepScreenOn
                        Toast.makeText(context, if (keepScreenOn) "Screen wake lock: ON" else "Screen wake lock: OFF", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (keepScreenOn) Icons.Default.BrightnessHigh else Icons.Default.BrightnessAuto,
                        contentDescription = "Keep Screen On",
                        tint = if (keepScreenOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Orientation Toggle
                IconButton(
                    onClick = {
                        orientationMode = when (orientationMode) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                        val name = when (orientationMode) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "Portrait"
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "Landscape"
                            else -> "Auto"
                        }
                        Toast.makeText(context, "Orientation: $name", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = when (orientationMode) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Icons.Default.StayCurrentPortrait
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> Icons.Default.StayCurrentLandscape
                            else -> Icons.Default.ScreenRotation
                        },
                        contentDescription = "Orientation",
                        tint = if (orientationMode != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Live Logs Inspector
                IconButton(
                    onClick = {
                        refreshLogs()
                        showLogsSheet = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = "LogKeeper Live Inspector", tint = MaterialTheme.colorScheme.primary)
                }

                // Reload
                IconButton(
                    onClick = {
                        webViewRef?.reload()
                        Toast.makeText(context, "Reloading app...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Live LogKeeper Inspector Bottom Sheet
        if (showLogsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLogsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LogKeeper Console: ${artifact.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { refreshLogs() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh logs")
                            }
                            IconButton(onClick = { showLogsSheet = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (logsList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No console logs captured yet.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(logsList) { log ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (log.type) {
                                        "ERROR", "CRASH" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                        "WARNING" -> Color(0xFFFEF3C7)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "[${log.type}] ${log.component}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (log.type == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = log.message,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getMimeType(file: File): String {
    val extension = file.extension.lowercase()
    return when (extension) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "ico" -> "image/x-icon"
        "wasm" -> "application/wasm"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "txt" -> "text/plain"
        "xml" -> "application/xml"
        else -> URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
    }
}
