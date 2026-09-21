package com.example.engine.omniroot.artifact

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.utils.LogKeeper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference

enum class ArtifactProviderStatus {
    READY,
    BUSY,
    LOCKED,
    ERROR,
    REFRESHING,
    SLEEPING
}

/**
 * Headless WebView wrapper for a Claude.ai Omnivian Artifact provider.
 */
@SuppressLint("SetJavaScriptEnabled")
class ArtifactWebView(
    private val context: Context,
    val provider: ArtifactProviderEntity,
    private val onStatsUpdated: (tokens: Long, calls: Int, pct: Float, remainingMs: Long) -> Unit,
    private val onStatusChanged: (ArtifactProviderStatus) -> Unit
) : ArtifactBridge.ArtifactBridgeListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    var status: ArtifactProviderStatus = ArtifactProviderStatus.REFRESHING
        private set(value) {
            field = value
            onStatusChanged(value)
        }

    private var reloadAttempts = 0
    private val maxReloadAttempts = 3
    private var isDestroyed = false

    // Active generation state
    private var activeResponseDeferred: CompletableDeferred<String>? = null
    private val activeChunkFlow = MutableSharedFlow<String>(
        replay = 10,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        mainHandler.post {
            initWebView()
        }
    }

    private fun initWebView() {
        if (isDestroyed) return
        try {
            val wv = WebView(context.applicationContext)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
            }

            val bridge = ArtifactBridge(
                providerId = provider.id,
                listener = this,
                passkeyProvider = { ArtifactKeyStore.getPasskey(context, provider.id) }
            )
            wv.addJavascriptInterface(bridge, "Android")

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    LogKeeper.log("ArtifactWebView", "PageLoaded", "Artifact page loaded for ${provider.id}: $url")
                    injectBridgeBootstrap(view)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        LogKeeper.log("ArtifactWebView", "LoadError", "Main frame load error for ${provider.id}: ${error?.description}")
                        handlePageCrash()
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    LogKeeper.log("ArtifactWebView", "RenderCrash", "Renderer gone for ${provider.id}. Did crash: ${detail?.didCrash()}")
                    handlePageCrash()
                    return true
                }
            }

            wv.webChromeClient = object : WebChromeClient() {}

            wv.loadUrl(provider.url)
            this.webView = wv
            this.status = ArtifactProviderStatus.REFRESHING
        } catch (e: Exception) {
            LogKeeper.log("ArtifactWebView", "InitError", "Failed to init WebView for ${provider.id}: ${e.message}")
            this.status = ArtifactProviderStatus.ERROR
        }
    }

    private fun injectBridgeBootstrap(view: WebView?) {
        val bootstrapScript = """
            (function() {
                if (window.__omnivian_bootstrapped) return;
                window.__omnivian_bootstrapped = true;

                // Listen for cross-origin or child iframe postMessages
                window.addEventListener('message', function(event) {
                    if (!event.data) return;
                    if (event.data.type === 'OMNIVIAN_READY' && window.Android) {
                        window.Android.onReady();
                    } else if (event.data.type === 'OMNIVIAN_CHUNK' && window.Android) {
                        window.Android.onChunk(event.data.text || '');
                    } else if (event.data.type === 'OMNIVIAN_RESPONSE' && window.Android) {
                        window.Android.onResponse(event.data.text || '', event.data.tokens || 0, event.data.timeMs || 0);
                    } else if (event.data.type === 'OMNIVIAN_ERROR' && window.Android) {
                        window.Android.onError(event.data.code || 'UNKNOWN', event.data.message || '');
                    } else if (event.data.type === 'OMNIVIAN_STATUS' && window.Android) {
                        window.Android.onStatusChange(event.data.status || 'READY', event.data.tokens || 0);
                    }
                });

                // Check if the page has an ArtifactAPI exposed directly
                if (window.ArtifactAPI) {
                    if (window.ArtifactAPI.isLocked) {
                        if (window.Android) window.Android.onLocked();
                    } else {
                        if (window.Android) window.Android.onReady();
                    }
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(bootstrapScript, null)
    }

    private fun handlePageCrash() {
        if (isDestroyed) return
        status = ArtifactProviderStatus.ERROR
        activeResponseDeferred?.completeExceptionally(RuntimeException("WebView renderer process crashed"))
        activeResponseDeferred = null

        if (reloadAttempts < maxReloadAttempts) {
            reloadAttempts++
            val backoffMs = reloadAttempts * 2000L
            LogKeeper.log("ArtifactWebView", "ReloadBackoff", "Scheduling reload $reloadAttempts/$maxReloadAttempts in ${backoffMs}ms for ${provider.id}")
            mainHandler.postDelayed({
                if (!isDestroyed) {
                    webView?.destroy()
                    webView = null
                    initWebView()
                }
            }, backoffMs)
        }
    }

    fun reload() {
        mainHandler.post {
            reloadAttempts = 0
            webView?.reload() ?: initWebView()
        }
    }

    fun unlock(passkey: String) {
        mainHandler.post {
            val escapedPass = JSONObject.quote(passkey)
            val script = """
                if (window.ArtifactAPI && typeof window.ArtifactAPI.unlock === 'function') {
                    window.ArtifactAPI.unlock($escapedPass);
                } else {
                    document.querySelectorAll('iframe').forEach(function(f) {
                        try {
                            f.contentWindow.postMessage({ type: 'OMNIVIAN_UNLOCK', passkey: $escapedPass }, '*');
                        } catch(e){}
                    });
                }
            """.trimIndent()
            webView?.evaluateJavascript(script, null)
        }
    }

    suspend fun send(message: String, systemPrompt: String): Flow<String> = withContext(Dispatchers.Main) {
        status = ArtifactProviderStatus.BUSY
        val deferred = CompletableDeferred<String>()
        activeResponseDeferred = deferred

        val escapedMsg = JSONObject.quote(message)
        val escapedSys = JSONObject.quote(systemPrompt)

        val script = """
            (function() {
                if (window.ArtifactAPI && typeof window.ArtifactAPI.sendPrompt === 'function') {
                    window.ArtifactAPI.sendPrompt($escapedMsg, $escapedSys);
                } else {
                    var sent = false;
                    document.querySelectorAll('iframe').forEach(function(f) {
                        try {
                            f.contentWindow.postMessage({ type: 'OMNIVIAN_PROMPT', prompt: $escapedMsg, systemPrompt: $escapedSys }, '*');
                            sent = true;
                        } catch(e){}
                    });
                    if (!sent && window.Android) {
                        window.Android.onError('NO_ARTIFACT_API', 'Neither window.ArtifactAPI nor child iframe responded to prompt delivery');
                    }
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
        return@withContext activeChunkFlow
    }

    suspend fun awaitResponse(): String {
        return activeResponseDeferred?.await() ?: throw IllegalStateException("No active request deferred")
    }

    fun queryState(onResult: (JSONObject?) -> Unit) {
        mainHandler.post {
            val script = """
                (function() {
                    try {
                        if (window.ArtifactAPI && typeof window.ArtifactAPI.getState === 'function') {
                            return JSON.stringify(window.ArtifactAPI.getState());
                        }
                    } catch(e){}
                    return "{}";
                })();
            """.trimIndent()
            webView?.evaluateJavascript(script) { resultJson ->
                try {
                    val clean = if (resultJson != null && resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                        org.json.JSONTokener(resultJson).nextValue().toString()
                    } else {
                        resultJson ?: "{}"
                    }
                    onResult(JSONObject(clean))
                } catch (e: Exception) {
                    onResult(null)
                }
            }
        }
    }

    fun pause() {
        mainHandler.post {
            webView?.onPause()
            webView?.pauseTimers()
            status = ArtifactProviderStatus.SLEEPING
        }
    }

    fun resume() {
        mainHandler.post {
            webView?.onResume()
            webView?.resumeTimers()
            if (status == ArtifactProviderStatus.SLEEPING) {
                status = ArtifactProviderStatus.READY
            }
        }
    }

    fun destroy() {
        isDestroyed = true
        mainHandler.post {
            try {
                webView?.stopLoading()
                webView?.clearHistory()
                webView?.destroy()
                webView = null
            } catch (_: Exception) {}
            status = ArtifactProviderStatus.SLEEPING
        }
    }

    // --- ArtifactBridgeListener Callbacks ---

    override fun onArtifactReady(providerId: String) {
        status = ArtifactProviderStatus.READY
        reloadAttempts = 0
    }

    override fun onArtifactLocked(providerId: String) {
        status = ArtifactProviderStatus.LOCKED
        val passkey = ArtifactKeyStore.getPasskey(context, providerId)
        if (!passkey.isNullOrBlank()) {
            LogKeeper.log("ArtifactWebView", "AutoUnlock", "Auto-unlocking $providerId with stored passkey")
            unlock(passkey)
        }
    }

    override fun onArtifactChunk(providerId: String, chunk: String) {
        CoroutineScope(Dispatchers.Main).launch {
            activeChunkFlow.emit(chunk)
        }
    }

    override fun onArtifactResponse(providerId: String, text: String, tokens: Long, timeMs: Long) {
        status = ArtifactProviderStatus.READY
        activeResponseDeferred?.complete(text)
        activeResponseDeferred = null
        onStatsUpdated(tokens, 1, 0f, 0L)
    }

    override fun onArtifactError(providerId: String, code: String, message: String) {
        status = ArtifactProviderStatus.ERROR
        activeResponseDeferred?.completeExceptionally(RuntimeException("[$code] $message"))
        activeResponseDeferred = null
    }

    override fun onArtifactStatusChange(providerId: String, status: String, tokens: Long) {
        when (status.uppercase()) {
            "READY" -> this.status = ArtifactProviderStatus.READY
            "GENERATING", "BUSY" -> this.status = ArtifactProviderStatus.BUSY
            "LOCKED" -> this.status = ArtifactProviderStatus.LOCKED
            "ERROR" -> this.status = ArtifactProviderStatus.ERROR
        }
    }

    override fun onArtifactTokenWarning(providerId: String, used: Long, limit: Long) {
        val pct = if (limit > 0) (used.toFloat() / limit.toFloat()) * 100f else 0f
        onStatsUpdated(used, 0, pct, 0L)
    }

    override fun onArtifactWindowRefresh(providerId: String) {
        status = ArtifactProviderStatus.READY
        onStatsUpdated(0L, 0, 0f, 0L)
    }
}
