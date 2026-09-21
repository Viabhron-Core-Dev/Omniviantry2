package com.example.ui.chat

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PWAPreviewBottomSheet(
    url: String,
    title: String = "Artifact Preview",
    workspaceId: String? = null,
    onDismiss: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeWorkspaceId = remember(workspaceId) {
        workspaceId ?: com.example.engine.fs.LocalFileManager.getWorkspaceDir().name
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header (Title, Save, Refresh, Close)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                scope.launch {
                                    val result = com.example.engine.fs.ArtifactWorkspaceManager.saveCurrentChatAsArtifact(context, activeWorkspaceId, title)
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Saved to Artifacts (mini apps)!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Save failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Save, contentDescription = "Save as Artifact", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { webView?.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload")
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                HorizontalDivider()

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false

                                val secretsJson = com.example.engine.settings.ThreadSecretsStore.getSecretsJson(ctx, activeWorkspaceId)
                                val secretsCount = com.example.engine.settings.ThreadSecretsStore.getSecrets(ctx, activeWorkspaceId).size

                                val injectionScript = """
                                    (function() {
                                        try {
                                            window.__SECRETS__ = Object.freeze($secretsJson);
                                            window.dispatchEvent(new CustomEvent('secretsready', { detail: { count: $secretsCount } }));
                                        } catch(e) {
                                            console.error('Omnivian secrets injection error:', e);
                                        }
                                    })();
                                """.trimIndent()

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        view?.evaluateJavascript(injectionScript, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(injectionScript, null)
                                        com.example.utils.LogKeeper.log("INFO", "PWAPreview", "BlindInjectionSuccess: Injected $secretsCount runtime secrets into WebView")
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        com.example.utils.LogKeeper.log("WARNING", "PWA_Preview", "Error ($errorCode): $description at $failingUrl")
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                        if (consoleMessage?.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                            com.example.utils.LogKeeper.log("ERROR", "PWA_Preview", "Console: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }
                                loadUrl(url)
                                webView = this
                            }
                        },
                        update = { _ -> },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
