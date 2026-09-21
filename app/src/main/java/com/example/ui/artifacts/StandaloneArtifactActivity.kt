package com.example.ui.artifacts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.settings.ThreadSecretsStore
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.LogKeeper
import java.io.File

/**
 * Standalone Android Activity for mini-apps/artifacts.
 * Declared with documentLaunchMode="always" and FLAG_ACTIVITY_NEW_TASK so that each
 * opened web mini-app gets its own independent Recent Apps task card on Android.
 */
class StandaloneArtifactActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ARTIFACT_ID = "extra_artifact_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_WORKSPACE_ID = "extra_workspace_id"

        fun launch(
            context: Context,
            title: String,
            filePath: String,
            artifactId: String? = null,
            workspaceId: String? = null
        ) {
            val intent = Intent(context, StandaloneArtifactActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_ARTIFACT_ID, artifactId)
                putExtra(EXTRA_WORKSPACE_ID, workspaceId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            context.startActivity(intent)
            LogKeeper.log("StandaloneArtifactActivity", "Launch", "Launched standalone task for: $title ($filePath)")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Mini App"
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        val workspaceId = intent.getStringExtra(EXTRA_WORKSPACE_ID)

        setContent {
            MyApplicationTheme {
                var webViewInstance by remember { mutableStateOf<WebView?>(null) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { webViewInstance?.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                }
                                IconButton(onClick = { finishAndRemoveTask() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Task")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (filePath.isBlank() && intent.getStringExtra(EXTRA_ARTIFACT_ID) == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No artifact target file provided.")
                            }
                        } else {
                            val resolvedUrl = remember(filePath) {
                                val file = File(filePath)
                                if (file.exists()) "file://${file.absolutePath}" else "about:blank"
                            }

                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = true
                                        settings.allowContentAccess = true
                                        settings.mediaPlaybackRequiresUserGesture = false

                                        // Attach global LogKeeper web catcher
                                        com.example.utils.LogKeeperCatcher.attachToWebView(this, "Standalone_$title")

                                        val activeWs = workspaceId ?: "default"
                                        val secretsJson = ThreadSecretsStore.getSecretsJson(ctx, activeWs)
                                        val secretsCount = ThreadSecretsStore.getSecrets(ctx, activeWs).size

                                        val injectionScript = """
                                            (function() {
                                                try {
                                                    window.__SECRETS__ = Object.freeze($secretsJson);
                                                    window.dispatchEvent(new CustomEvent('secretsready', { detail: { count: $secretsCount } }));
                                                } catch(e) {
                                                    console.error('StandaloneArtifact injection error:', e);
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
                                                LogKeeper.log("INFO", "StandaloneWeb", "Page finished: $url (injected $secretsCount secrets)")
                                            }

                                            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                                super.onReceivedError(view, errorCode, description, failingUrl)
                                                LogKeeper.log("ERROR", "StandaloneWeb", "Error ($errorCode): $description at $failingUrl")
                                            }
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                                if (cm?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                                    LogKeeper.log("ERROR", "StandaloneWeb", "${cm.message()} (${cm.sourceId()}:${cm.lineNumber()})")
                                                }
                                                return super.onConsoleMessage(cm)
                                            }
                                        }

                                        loadUrl(resolvedUrl)
                                        webViewInstance = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
