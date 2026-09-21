package com.example.engine.router

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.engine.omniroot.pipeline.TranslationEngine
import com.example.ui.chat.OmniChoice
import com.example.ui.chat.OmniMessage
import com.example.ui.chat.OmniRequest
import com.example.ui.chat.OmniResponse
import com.example.ui.chat.OmniTool
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CandidateNode(
    val providerId: String,
    val modelId: String,
    val preferredKeyRef: String? = null
) {
    fun toDisplayString(): String =
        if (preferredKeyRef.isNullOrBlank()) "$providerId/$modelId" else "$providerId/$modelId@$preferredKeyRef"
}

data class FallbackExecutionResult(
    val response: OmniResponse? = null,
    val text: String? = null,
    val routedViaFallback: Boolean = false,
    val actualModelName: String = "",
    val actualProviderId: String = "",
    val actualAccountAlias: String? = null,
    val fallbackReason: String? = null,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val httpCode: Int? = null
)

/**
 * Intelligent Provider & Multi-Account Failover Router for OmniRoot.
 * Supports:
 * 1. Model-to-Account Binding: Bind specific models to specific accounts/keys (e.g. Work vs Personal) via '@alias'.
 * 2. Account Quota Pooling (Option C): Automatically catches HTTP 429 / RESOURCE_EXHAUSTED and fails over across all keys
 *    under the same provider before triggering model downgrades.
 * 3. Model Fallback Chains: Seamlessly failover to secondary and tertiary candidate models upon provider outage.
 */
object FallbackChainRouter {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun dispatchWithFallback(
        context: Context,
        messages: List<OmniMessage>,
        targetModel: String,
        tools: List<OmniTool>? = null,
        temperature: Float? = null,
        topP: Float? = null,
        maxTokens: Int? = null,
        onChunk: ((String) -> Unit)? = null
    ): FallbackExecutionResult = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val candidateChain = resolveCandidateChain(db, targetModel)
        
        LogKeeper.log(
            "FallbackRouter",
            "ChainResolved",
            "Target: $targetModel, Resolved Candidates (${candidateChain.size}): ${candidateChain.joinToString(" -> ") { it.toDisplayString() }}"
        )

        var lastError: String = "No available provider responded successfully."
        var lastHttpCode: Int? = null
        var previousFailureReason: String? = null

        for (i in candidateChain.indices) {
            val candidate = candidateChain[i]
            val providerId = candidate.providerId
            val modelId = candidate.modelId
            val isFallbackRoute = (i > 0)

            LogKeeper.log(
                "FallbackRouter",
                if (isFallbackRoute) "AttemptingFallback" else "AttemptingPrimary",
                "Dispatching to provider='$providerId', model='$modelId', account='${candidate.preferredKeyRef ?: "auto"}' (Attempt ${i + 1}/${candidateChain.size})"
            )

            val attemptResult = executeProviderCallWithAccountPool(
                db = db,
                candidate = candidate,
                messages = messages,
                tools = tools,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                context = context,
                onChunk = onChunk
            )

            if (attemptResult.isSuccess && attemptResult.response != null) {
                if (isFallbackRoute) {
                    LogKeeper.log(
                        "FallbackRouter",
                        "FailoverSuccess",
                        "Successfully fulfilled turn via fallback model: $modelId on $providerId [Account: ${attemptResult.actualAccountAlias ?: "default"}] (Reason: $previousFailureReason)"
                    )
                }

                val primaryChoice = attemptResult.response.choices?.firstOrNull()
                val responseText = primaryChoice?.message?.content

                return@withContext FallbackExecutionResult(
                    response = attemptResult.response,
                    text = responseText,
                    routedViaFallback = isFallbackRoute,
                    actualModelName = modelId,
                    actualProviderId = providerId,
                    actualAccountAlias = attemptResult.actualAccountAlias,
                    fallbackReason = if (isFallbackRoute) previousFailureReason else null,
                    isSuccess = true,
                    httpCode = attemptResult.httpCode
                )
            } else {
                lastError = attemptResult.errorMessage ?: "Unknown error"
                lastHttpCode = attemptResult.httpCode
                previousFailureReason = when {
                    lastHttpCode == 429 -> "Rate Limit / Quota Exceeded across all accounts (HTTP 429)"
                    lastHttpCode in 500..599 -> "Server Unavailable (HTTP $lastHttpCode)"
                    lastError.contains("context", ignoreCase = true) || lastError.contains("token", ignoreCase = true) -> "Context Length Overflow"
                    lastError.contains("key", ignoreCase = true) -> "API Key Missing / Invalid"
                    else -> "Connection / Gateway Timeout"
                }

                LogKeeper.log(
                    "FallbackRouter",
                    "FailoverTriggered",
                    "Candidate failed ($providerId/$modelId): $previousFailureReason. Error: $lastError"
                )
            }
        }

        return@withContext FallbackExecutionResult(
            isSuccess = false,
            errorMessage = "All fallback models and account pools exhausted. Last error: $lastError",
            httpCode = lastHttpCode,
            actualModelName = targetModel,
            actualProviderId = "unknown"
        )
    }

    private suspend fun resolveCandidateChain(db: AppDatabase, targetModel: String): List<CandidateNode> {
        val candidates = mutableListOf<CandidateNode>()

        if (targetModel.startsWith("fallback/")) {
            val chainId = targetModel.removePrefix("fallback/")
            val chains = db.fallbackChainDao().getAllChains().first()
            val chain = chains.find { it.id == chainId }
            if (chain != null) {
                try {
                    val arr = JSONArray(chain.chainDataJson)
                    for (i in 0 until arr.length()) {
                        val raw = arr.getString(i).trim()
                        if (raw.isNotEmpty()) {
                            candidates.add(parseCandidateString(raw))
                        }
                    }
                } catch (e: Exception) {
                    LogKeeper.log("FallbackRouter", "ParseChainError", "Error parsing fallback chain: ${e.message}")
                }
            }
        }

        if (candidates.isEmpty()) {
            val primary = if (targetModel.isNotBlank() && targetModel != "Select Model") targetModel else "google_ai_studio/gemini-2.5-flash"
            candidates.add(parseCandidateString(primary))
        }

        return candidates.distinct()
    }

    private fun parseCandidateString(raw: String): CandidateNode {
        if (raw.trim().startsWith("{")) {
            try {
                val obj = JSONObject(raw)
                val pId = obj.optString("providerId", "google_ai_studio")
                val mId = obj.optString("modelId", "gemini-2.5-flash")
                val kRef = obj.optString("keyRef").takeIf { it.isNotBlank() }
                    ?: obj.optString("account").takeIf { it.isNotBlank() }
                return CandidateNode(pId, mId, kRef)
            } catch (_: Exception) {}
        }

        var providerId = "google_ai_studio"
        var modelId = raw
        var keyRef: String? = null

        val atIdx = raw.indexOf('@')
        val stringWithoutKey = if (atIdx != -1) {
            keyRef = raw.substring(atIdx + 1).trim()
            raw.substring(0, atIdx).trim()
        } else {
            raw.trim()
        }

        val slashIdx = stringWithoutKey.indexOf('/')
        if (slashIdx != -1) {
            providerId = stringWithoutKey.substring(0, slashIdx).trim()
            modelId = stringWithoutKey.substring(slashIdx + 1).trim()
        } else {
            modelId = stringWithoutKey
        }

        return CandidateNode(providerId, modelId, keyRef)
    }

    private suspend fun executeProviderCallWithAccountPool(
        db: AppDatabase,
        candidate: CandidateNode,
        messages: List<OmniMessage>,
        tools: List<OmniTool>?,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        context: Context,
        onChunk: ((String) -> Unit)? = null
    ): FallbackExecutionResult {
        val providerId = candidate.providerId
        val modelId = candidate.modelId
        val preferredKeyRef = candidate.preferredKeyRef

        if (providerId.equals("artifact", ignoreCase = true) || providerId.equals("omnivian", ignoreCase = true)) {
            return com.example.engine.omniroot.artifact.ArtifactRouter.route(
                context = context,
                messages = messages,
                systemPrompt = null,
                onChunk = onChunk
            )
        }

        val allKeys = db.apiKeyDao().getKeysForProviderList(providerId)
        if (allKeys.isEmpty()) {
            return FallbackExecutionResult(
                isSuccess = false,
                errorMessage = "No API key configured for provider '$providerId'",
                httpCode = 401
            )
        }

        // Build candidate key list prioritizing preferred account, then active key, then others
        val candidateKeys = mutableListOf<com.example.engine.db.ApiKeyEntity>()
        if (!preferredKeyRef.isNullOrBlank()) {
            val preferred = allKeys.find { 
                it.id.equals(preferredKeyRef, ignoreCase = true) || 
                it.alias.equals(preferredKeyRef, ignoreCase = true) 
            }
            if (preferred != null) {
                candidateKeys.add(preferred)
            }
        }
        allKeys.filter { it.isActive && !candidateKeys.contains(it) }.forEach { candidateKeys.add(it) }
        allKeys.filter { !candidateKeys.contains(it) }.forEach { candidateKeys.add(it) }

        var lastResult: FallbackExecutionResult? = null

        for (kIndex in candidateKeys.indices) {
            val key = candidateKeys[kIndex]
            if (kIndex > 0) {
                LogKeeper.log(
                    "FallbackRouter",
                    "AccountPoolFailover",
                    "Auto-failing over to account '${key.alias}' on $providerId for model '$modelId' (${kIndex + 1}/${candidateKeys.size})"
                )
            }

            val result = executeSingleKeyCall(
                providerId = providerId,
                modelId = modelId,
                apiKey = key.keyValue,
                accountAlias = key.alias,
                messages = messages,
                tools = tools,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                context = context
            )

            if (result.isSuccess) {
                return result
            }

            lastResult = result

            val isQuotaExhausted = result.httpCode == 429 ||
                    result.errorMessage?.contains("quota", ignoreCase = true) == true ||
                    result.errorMessage?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
                    result.errorMessage?.contains("rate_limit", ignoreCase = true) == true

            if (isQuotaExhausted && kIndex < candidateKeys.size - 1) {
                LogKeeper.log(
                    "FallbackRouter",
                    "AccountQuotaExhausted",
                    "Account '${key.alias}' quota exhausted (429) on $providerId/$modelId. Trying next available account in pool..."
                )
                continue
            } else {
                break
            }
        }

        return lastResult ?: FallbackExecutionResult(
            isSuccess = false,
            errorMessage = "All accounts exhausted for provider '$providerId'",
            httpCode = 500
        )
    }

    private suspend fun executeSingleKeyCall(
        providerId: String,
        modelId: String,
        apiKey: String,
        accountAlias: String,
        messages: List<OmniMessage>,
        tools: List<OmniTool>?,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        context: Context
    ): FallbackExecutionResult {
        return try {
            val (targetFormat, baseUrl) = when (providerId) {
                "google_ai_studio" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
                )
                "openai" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.openai.com/v1/chat/completions"
                )
                "anthropic" -> Pair(
                    TranslationEngine.ProviderFormat.ANTHROPIC,
                    "https://api.anthropic.com/v1/messages"
                )
                "openrouter" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://openrouter.ai/api/v1/chat/completions"
                )
                "groq" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.groq.com/openai/v1/chat/completions"
                )
                "together_ai" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.together.xyz/v1/chat/completions"
                )
                "github_models" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://models.inference.ai.azure.com/chat/completions"
                )
                "cloudflare_ai" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.cloudflare.com/client/v4/accounts"
                )
                "cerebras" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.cerebras.ai/v1/chat/completions"
                )
                "mistral" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.mistral.ai/v1/chat/completions"
                )
                "sambanova" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.sambanova.ai/v1/chat/completions"
                )
                "huggingface" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api-inference.huggingface.co/v1/chat/completions"
                )
                "cohere" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://api.cohere.ai/v1/chat"
                )
                "glhf" -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://glhf.chat/api/openai/v1/chat/completions"
                )
                else -> Pair(
                    TranslationEngine.ProviderFormat.OPENAI,
                    "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
                )
            }

            if (apiKey.isBlank()) {
                return FallbackExecutionResult(
                    isSuccess = false,
                    errorMessage = "API key is blank for account '$accountAlias'",
                    httpCode = 401
                )
            }

            var effectiveBaseUrl = baseUrl
            var effectiveAuthHeader = "Bearer $apiKey"

            if (providerId == "cloudflare_ai") {
                val cfAccountId = if (apiKey.contains(":")) apiKey.substringBefore(":").trim()
                                  else if (apiKey.contains("/")) apiKey.substringBefore("/").trim()
                                  else ""
                val cfToken = if (apiKey.contains(":")) apiKey.substringAfter(":").trim()
                              else if (apiKey.contains("/")) apiKey.substringAfter("/").trim()
                              else apiKey.trim()
                if (cfAccountId.isBlank()) {
                    return FallbackExecutionResult(
                        isSuccess = false,
                        errorMessage = "Cloudflare Workers AI key must be formatted as 'account_id:api_token'",
                        httpCode = 400
                    )
                }
                effectiveBaseUrl = "https://api.cloudflare.com/client/v4/accounts/$cfAccountId/ai/v1/chat/completions"
                effectiveAuthHeader = "Bearer $cfToken"
            }

            // Mini-Phase 11.1: Context Budget Optimization
            val (optimizedMessages, budgetReport) = com.example.engine.brain.ContextBudgetManager.prepareContext(
                messages = messages,
                modelName = modelId
            )

            val omniRequest = OmniRequest(
                model = modelId,
                messages = optimizedMessages,
                tools = tools,
                temperature = temperature,
                top_p = topP,
                max_tokens = maxTokens
            )

            val payload = TranslationEngine.translateRequest(omniRequest, targetFormat, context)
            val requestBuilder = Request.Builder().url(effectiveBaseUrl)

            when (providerId) {
                "anthropic" -> {
                    requestBuilder.addHeader("x-api-key", apiKey)
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                }
                "openrouter" -> {
                    requestBuilder.addHeader("Authorization", effectiveAuthHeader)
                    requestBuilder.addHeader("HTTP-Referer", "https://omniroot.ai")
                    requestBuilder.addHeader("X-Title", "OmniRoot IDE")
                }
                else -> {
                    requestBuilder.addHeader("Authorization", effectiveAuthHeader)
                }
            }

            requestBuilder.post(payload.toRequestBody(jsonMediaType))
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return FallbackExecutionResult(
                    isSuccess = false,
                    httpCode = response.code,
                    errorMessage = "HTTP ${response.code}: $bodyString",
                    actualAccountAlias = accountAlias
                )
            }

            val omniResponse = TranslationEngine.translateResponse(bodyString, targetFormat)
            FallbackExecutionResult(
                response = omniResponse,
                isSuccess = true,
                httpCode = response.code,
                actualAccountAlias = accountAlias
            )
        } catch (e: Exception) {
            FallbackExecutionResult(
                isSuccess = false,
                errorMessage = e.message ?: e.javaClass.simpleName,
                httpCode = 500,
                actualAccountAlias = accountAlias
            )
        }
    }
}
