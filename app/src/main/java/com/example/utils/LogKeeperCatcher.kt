package com.example.utils

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Global LogKeeper Catcher for WebViews, PWAs, and background operations.
 * Captures browser console events, network errors, script failures, and lifecycle metrics,
 * feeding them into the unified LogKeeper audit engine.
 */
object LogKeeperCatcher {

    fun attachToWebView(
        webView: WebView,
        tag: String = "PwaApp",
        onConsoleError: ((String) -> Unit)? = null
    ) {
        val originalChromeClient = webView.webChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                if (cm != null) {
                    val level = when (cm.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                        ConsoleMessage.MessageLevel.WARNING -> "WARNING"
                        ConsoleMessage.MessageLevel.LOG -> "INFO"
                        ConsoleMessage.MessageLevel.TIP -> "INFO"
                        ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                        null -> "INFO"
                    }
                    val msg = "${cm.message()} (${cm.sourceId()}:${cm.lineNumber()})"
                    LogKeeper.log(level, "PWA_CONSOLE[$tag]", msg)
                    if (level == "ERROR") {
                        onConsoleError?.invoke(msg)
                    }
                }
                return originalChromeClient?.onConsoleMessage(cm) ?: super.onConsoleMessage(cm)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    LogKeeper.log("INFO", "PWA_TITLE[$tag]", "Page title: $title")
                }
            }
        }
    }

    fun createLoggingWebViewClient(
        tag: String = "PwaApp",
        onPageLoaded: ((String?) -> Unit)? = null,
        onErrorOccurred: ((String) -> Unit)? = null
    ): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                LogKeeper.log("INFO", "PWA_NAV[$tag]", "Started loading: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                LogKeeper.log("INFO", "PWA_NAV[$tag]", "Finished loading: $url")
                onPageLoaded?.invoke(url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val errorMsg = "ErrorCode: ${error?.errorCode}, Description: ${error?.description} on ${request?.url}"
                LogKeeper.log("ERROR", "PWA_NET[$tag]", errorMsg)
                if (request?.isForMainFrame == true) {
                    onErrorOccurred?.invoke(errorMsg)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val status = errorResponse?.statusCode ?: 0
                val reason = errorResponse?.reasonPhrase ?: "Unknown"
                val url = request?.url?.toString() ?: "unknown"
                LogKeeper.log("WARNING", "PWA_HTTP[$tag]", "HTTP $status ($reason) for $url")
            }
        }
    }
}
