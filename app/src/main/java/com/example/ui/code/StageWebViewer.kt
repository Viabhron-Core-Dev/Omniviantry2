package com.example.ui.code

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.utils.LogKeeper
import java.io.File

/**
 * StageWebViewer (Mini-Phase 3 Deliverable 3).
 * A lightweight <10KB sandbox shell that renders:
 * - 3D models (.stl, .gltf, .glb, .obj) via Google <model-viewer>
 * - Geo spatial maps (.geojson, .kml) via Leaflet.js
 *
 * Implements strict zero-leak cleanup: destroys WebGL/WebView context on exit
 * to immediately reclaim VRAM.
 */
@Composable
fun StageWebViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val ext = remember(file) { file.extension.lowercase() }

    val htmlPayload = remember(file) {
        when (ext) {
            "geojson", "kml" -> {
                val jsonContent = try { file.readText() } catch (e: Exception) { "{}" }
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                    <style>
                        body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #121212; }
                    </style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        try {
                            const map = L.map('map').setView([20, 0], 2);
                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                attribution: '© OpenStreetMap'
                            }).addTo(map);
                            const data = $jsonContent;
                            const layer = L.geoJSON(data).addTo(map);
                            map.fitBounds(layer.getBounds());
                        } catch(e) {
                            console.error("Map parse error: " + e.message);
                        }
                    </script>
                </body>
                </html>
                """.trimIndent()
            }
            "gltf", "glb", "stl", "obj" -> {
                val fileUri = "file://${file.absolutePath}"
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                    <script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/3.4.0/model-viewer.min.js"></script>
                    <style>
                        body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: #121212; overflow: hidden; }
                        model-viewer { width: 100%; height: 100%; background-color: #121212; }
                    </style>
                </head>
                <body>
                    <model-viewer 
                        src="$fileUri" 
                        alt="3D Model" 
                        auto-rotate 
                        camera-controls 
                        touch-action="pan-y" 
                        ar>
                    </model-viewer>
                </body>
                </html>
                """.trimIndent()
            }
            else -> {
                """
                <!DOCTYPE html>
                <html><body style="background:#121212;color:white;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;">
                <h3>Preview not available for .$ext</h3>
                </body></html>
                """.trimIndent()
            }
        }
    }

    DisposableEffect(file) {
        onDispose {
            LogKeeper.log("StageWebViewer", "Teardown", "Destroying WebGL context for ${file.name}")
            try {
                webViewRef?.apply {
                    loadUrl("about:blank")
                    clearHistory()
                    removeAllViews()
                    destroy()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                            LogKeeper.log("StageWebViewer", "Console", "${cm?.message()} [${cm?.sourceId()}:${cm?.lineNumber()}]")
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }

                    loadDataWithBaseURL("file://${file.parentFile?.absolutePath}/", htmlPayload, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Loading 3D / Stage environment...", color = Color.White, fontSize = 12.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ext == "geojson" || ext == "kml") Icons.Default.Map else Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(file.name, color = Color.White, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
            }
        }
    }
}
