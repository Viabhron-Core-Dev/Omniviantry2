package com.example.engine.omniroot.artifact

import android.content.Context
import com.example.engine.router.FallbackExecutionResult
import com.example.ui.chat.OmniChoice
import com.example.ui.chat.OmniMessage
import com.example.ui.chat.OmniResponse
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OmniRoot Artifact Router.
 * Routes chat requests to available Omnivian Claude.ai Artifact WebViews in the pool.
 */
object ArtifactRouter {

    suspend fun route(
        context: Context,
        messages: List<OmniMessage>,
        systemPrompt: String? = null,
        onChunk: ((String) -> Unit)? = null
    ): FallbackExecutionResult = withContext(Dispatchers.IO) {
        val pool = ArtifactProviderPool.getInstance(context)

        // Read timeout from general settings (default 90s)
        val prefs = context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE)
        val timeoutSeconds = prefs.getInt("artifact_timeout_seconds", 90).coerceIn(30, 180)
        val timeoutMs = timeoutSeconds * 1000L

        // Format prompt from messages. Since the headless artifact is stateless (no history),
        // we synthesize the conversation thread turns into the prompt payload if there are multiple turns,
        // so the model has the complete conversational context.
        val nonSystemMessages = messages.filter { it.role != "system" }
        val userPrompt = if (nonSystemMessages.size <= 1) {
            nonSystemMessages.lastOrNull()?.content ?: ""
        } else {
            nonSystemMessages.joinToString("\n\n") { msg ->
                val roleTag = when (msg.role.lowercase()) {
                    "user" -> "User"
                    "assistant" -> "Assistant"
                    "tool" -> "Tool (${msg.name ?: "Output"})"
                    else -> msg.role.replaceFirstChar { it.uppercase() }
                }
                "$roleTag: ${msg.content ?: ""}"
            }
        }
        val fullSys = systemPrompt ?: messages.filter { it.role == "system" }.mapNotNull { it.content }.joinToString("\n")

        LogKeeper.log("ArtifactRouter", "Dispatch", "Routing request to Omnivian Artifact pool with timeout ${timeoutSeconds}s")

        val result = withTimeoutOrNull(timeoutMs) {
            var webView: ArtifactWebView? = null
            val startTime = System.currentTimeMillis()

            // Wait/poll for available provider
            while (webView == null) {
                val candidate = pool.getBestAvailable()
                if (candidate != null && (candidate.status == ArtifactProviderStatus.READY || candidate.status == ArtifactProviderStatus.SLEEPING)) {
                    webView = candidate
                    break
                }
                if (System.currentTimeMillis() - startTime > timeoutMs - 5000L) {
                    break
                }
                delay(1000L) // Wait for a provider to free up
            }

            if (webView == null) {
                return@withTimeoutOrNull FallbackExecutionResult(
                    isSuccess = false,
                    errorMessage = "No available Omnivian Artifact providers in pool or all providers busy",
                    httpCode = 503
                )
            }

            try {
                val chunkFlow = webView.send(userPrompt, fullSys)

                // Collect chunks in background coroutine if callback provided
                val chunkJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    chunkFlow.collect { chunk ->
                        onChunk?.invoke(chunk)
                    }
                }

                val responseText = webView.awaitResponse()
                chunkJob.cancel()

                val omniResponse = OmniResponse(
                    choices = listOf(
                        OmniChoice(
                            message = OmniMessage(
                                role = "assistant",
                                content = responseText
                            )
                        )
                    )
                )

                FallbackExecutionResult(
                    response = omniResponse,
                    text = responseText,
                    routedViaFallback = false,
                    actualModelName = webView.provider.name,
                    actualProviderId = "artifact",
                    actualAccountAlias = webView.provider.owner,
                    isSuccess = true
                )
            } catch (e: Exception) {
                LogKeeper.log("ArtifactRouter", "ExecutionFailed", "Artifact execution failed: ${e.message}")
                FallbackExecutionResult(
                    isSuccess = false,
                    errorMessage = "Omnivian Artifact error: ${e.message}",
                    httpCode = 500
                )
            }
        }

        return@withContext result ?: FallbackExecutionResult(
            isSuccess = false,
            errorMessage = "Omnivian Artifact request timed out after ${timeoutSeconds}s",
            httpCode = 504
        )
    }

    /**
     * Exposes streaming Flow of tokens.
     */
    fun routeStream(
        context: Context,
        messages: List<OmniMessage>,
        systemPrompt: String? = null
    ): Flow<String> = flow {
        val result = route(context, messages, systemPrompt) { chunk ->
            // Progressive chunk
        }
        if (result.isSuccess && result.text != null) {
            emit(result.text)
        } else {
            throw RuntimeException(result.errorMessage ?: "Artifact streaming failed")
        }
    }
}
