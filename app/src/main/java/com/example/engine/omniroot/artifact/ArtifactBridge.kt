package com.example.engine.omniroot.artifact

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.example.utils.LogKeeper
import java.lang.ref.WeakReference

/**
 * Android JavaScriptInterface bridge for Claude.ai Omnivian Artifacts.
 * Marshals all calls from the Chromium JavaBridge background thread onto the Android Main Looper.
 */
class ArtifactBridge(
    val providerId: String,
    listener: ArtifactBridgeListener,
    private val passkeyProvider: () -> String?
) {

    interface ArtifactBridgeListener {
        fun onArtifactReady(providerId: String)
        fun onArtifactLocked(providerId: String)
        fun onArtifactChunk(providerId: String, chunk: String)
        fun onArtifactResponse(providerId: String, text: String, tokens: Long, timeMs: Long)
        fun onArtifactError(providerId: String, code: String, message: String)
        fun onArtifactStatusChange(providerId: String, status: String, tokens: Long)
        fun onArtifactTokenWarning(providerId: String, used: Long, limit: Long)
        fun onArtifactWindowRefresh(providerId: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listenerRef = WeakReference(listener)

    @JavascriptInterface
    fun onReady() {
        onReady(providerId)
    }

    @JavascriptInterface
    fun onReady(id: String?) {
        val targetId = id ?: providerId
        LogKeeper.log("ArtifactBridge", "onReady", "Artifact ready for provider: $targetId")
        mainHandler.post {
            listenerRef.get()?.onArtifactReady(targetId)
        }
    }

    @JavascriptInterface
    fun onLocked() {
        onLocked(providerId)
    }

    @JavascriptInterface
    fun onLocked(id: String?) {
        val targetId = id ?: providerId
        LogKeeper.log("ArtifactBridge", "onLocked", "Artifact locked for provider: $targetId")
        mainHandler.post {
            listenerRef.get()?.onArtifactLocked(targetId)
        }
    }

    @JavascriptInterface
    fun onChunk(text: String) {
        onChunk(providerId, text)
    }

    @JavascriptInterface
    fun onChunk(id: String?, text: String) {
        val targetId = id ?: providerId
        mainHandler.post {
            listenerRef.get()?.onArtifactChunk(targetId, text)
        }
    }

    @JavascriptInterface
    fun onResponse(text: String, tokens: Long) {
        onResponse(providerId, text, tokens, 0L)
    }

    @JavascriptInterface
    fun onResponse(text: String, tokens: Long, timeMs: Long) {
        onResponse(providerId, text, tokens, timeMs)
    }

    @JavascriptInterface
    fun onResponse(id: String?, text: String, tokens: Long, timeMs: Long) {
        val targetId = id ?: providerId
        LogKeeper.log("ArtifactBridge", "onResponse", "Received response from $targetId with $tokens tokens in ${timeMs}ms")
        mainHandler.post {
            listenerRef.get()?.onArtifactResponse(targetId, text, tokens, timeMs)
        }
    }

    @JavascriptInterface
    fun onError(code: String, message: String) {
        onError(providerId, code, message)
    }

    @JavascriptInterface
    fun onError(id: String?, code: String, message: String) {
        val targetId = id ?: providerId
        LogKeeper.log("ArtifactBridge", "onError", "Artifact error on $targetId: [$code] $message")
        mainHandler.post {
            listenerRef.get()?.onArtifactError(targetId, code, message)
        }
    }

    @JavascriptInterface
    fun onStatusChange(status: String, tokens: Long) {
        onStatusChange(providerId, status, tokens)
    }

    @JavascriptInterface
    fun onStatusChange(id: String?, status: String, tokens: Long) {
        val targetId = id ?: providerId
        mainHandler.post {
            listenerRef.get()?.onArtifactStatusChange(targetId, status, tokens)
        }
    }

    @JavascriptInterface
    fun onTokenWarning(used: Long, limit: Long) {
        onTokenWarning(providerId, used, limit)
    }

    @JavascriptInterface
    fun onTokenWarning(id: String?, used: Long, limit: Long) {
        val targetId = id ?: providerId
        LogKeeper.log("ArtifactBridge", "TokenWarning", "Artifact $targetId token warning: $used / $limit")
        mainHandler.post {
            listenerRef.get()?.onArtifactTokenWarning(targetId, used, limit)
        }
    }

    @JavascriptInterface
    fun onWindowRefresh() {
        onWindowRefresh(providerId)
    }

    @JavascriptInterface
    fun onWindowRefresh(id: String?) {
        val targetId = id ?: providerId
        LogKeeper.log("ArtifactBridge", "WindowRefresh", "Artifact $targetId window refreshed")
        mainHandler.post {
            listenerRef.get()?.onArtifactWindowRefresh(targetId)
        }
    }

    /**
     * Called synchronously by the Artifact during initialization to auto-unlock if locked.
     * Fetches the original plaintext passkey securely decrypted from KeyStore.
     */
    @JavascriptInterface
    fun requestPasskey(): String {
        return passkeyProvider.invoke().orEmpty()
    }
}
