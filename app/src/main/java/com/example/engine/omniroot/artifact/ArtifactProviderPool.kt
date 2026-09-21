package com.example.engine.omniroot.artifact

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.utils.LogKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton pool managing all registered Claude.ai Omnivian Artifact Providers.
 * Responsibilities:
 * 1. Maintains provider entities in Room.
 * 2. Manages active headless WebViews with an LRU concurrency cap to prevent OOM on 3GB devices.
 * 3. Enforces that the artifact is the source of truth for tokens/window: polls getState() immediately on startup and every 30s.
 * 4. Exposes getBestAvailable() for routing.
 */
class ArtifactProviderPool private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ArtifactProviderPool? = null

        fun getInstance(context: Context): ArtifactProviderPool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtifactProviderPool(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val activeWebViews = ConcurrentHashMap<String, ArtifactWebView>()
    private val providerEntities = ConcurrentHashMap<String, ArtifactProviderEntity>()

    private val _providersFlow = MutableStateFlow<List<ArtifactProviderEntity>>(emptyList())
    val providersFlow: StateFlow<List<ArtifactProviderEntity>> = _providersFlow.asStateFlow()

    private val _activeStatuses = MutableStateFlow<Map<String, ArtifactProviderStatus>>(emptyMap())
    val activeStatuses: StateFlow<Map<String, ArtifactProviderStatus>> = _activeStatuses.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadProvidersFromDb()
        startPollingLoop()
    }

    private fun getMaxConcurrency(): Int {
        val prefs = context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("max_concurrent_webviews", 2).coerceIn(1, 4)
    }

    private fun loadProvidersFromDb() {
        scope.launch {
            val list = db.artifactProviderDao().getAll()
            providerEntities.clear()
            list.forEach { providerEntities[it.id] = it }
            _providersFlow.value = list

            // Immediately spin up top N providers and query getState() to resync stats
            val cap = getMaxConcurrency()
            val toSpin = list.filter { it.enabled }.take(cap)
            toSpin.forEach { provider ->
                getOrCreateWebView(provider)
            }

            // Immediately resync stats from live artifacts (artifact is the source of truth)
            resyncAllStates()

            // Ensure all enabled artifact providers are present in ai_models table
            try {
                val modelEntities = list.filter { it.enabled }.map { provider ->
                    com.example.engine.db.AiModelEntity(
                        providerId = "artifact",
                        modelId = provider.name.replace(" ", "_"),
                        inputType = "TEXT",
                        outputType = "TEXT",
                        description = "Claude.ai Omnivian Artifact Provider: ${provider.name}"
                    )
                }
                if (modelEntities.isNotEmpty()) {
                    db.aiModelDao().insertModels(modelEntities)
                }
            } catch (_: Exception) {}
        }
    }

    fun resyncAllStates() {
        mainScope.launch {
            LogKeeper.log("ArtifactProviderPool", "ResyncStates", "Querying getState() on all active WebViews to resync session tokens from artifact source of truth")
            activeWebViews.values.forEach { webView ->
                webView.queryState { stateJson ->
                    if (stateJson != null) {
                        val tokens = stateJson.optLong("tokensUsed", stateJson.optLong("sessionTokens", 0L))
                        val calls = stateJson.optInt("sessionCalls", 0)
                        val pct = stateJson.optDouble("tokenPct", 0.0).toFloat()
                        val remainingMs = stateJson.optLong("windowRemainingMs", 0L)
                        updateProviderStats(webView.provider.id, tokens, calls, pct, remainingMs)
                    }
                }
            }
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(30_000L) // 30s status polling interval
                resyncAllStates()
            }
        }
    }

    @Synchronized
    fun getOrCreateWebView(provider: ArtifactProviderEntity): ArtifactWebView {
        val existing = activeWebViews[provider.id]
        if (existing != null) return existing

        // Enforce concurrency cap: if at or above cap, park least recently used webview
        val cap = getMaxConcurrency()
        if (activeWebViews.size >= cap) {
            val oldest = activeWebViews.entries
                .filter { it.value.status != ArtifactProviderStatus.BUSY }
                .minByOrNull { providerEntities[it.key]?.lastActiveMs ?: 0L }

            if (oldest != null) {
                LogKeeper.log("ArtifactProviderPool", "LRUCap", "Parking idle WebView for ${oldest.key} to preserve RAM")
                oldest.value.destroy()
                activeWebViews.remove(oldest.key)
                updateStatus(oldest.key, ArtifactProviderStatus.SLEEPING)
            }
        }

        val newWv = ArtifactWebView(
            context = context,
            provider = provider,
            onStatsUpdated = { tokens, calls, pct, remainingMs ->
                updateProviderStats(provider.id, tokens, calls, pct, remainingMs)
            },
            onStatusChanged = { status ->
                updateStatus(provider.id, status)
            }
        )

        activeWebViews[provider.id] = newWv
        updateStatus(provider.id, ArtifactProviderStatus.REFRESHING)
        return newWv
    }

    private fun updateStatus(id: String, status: ArtifactProviderStatus) {
        val updated = _activeStatuses.value.toMutableMap()
        updated[id] = status
        _activeStatuses.value = updated
    }

    private fun updateProviderStats(id: String, tokens: Long, calls: Int, pct: Float, remainingMs: Long) {
        scope.launch {
            val current = providerEntities[id] ?: return@launch
            val updated = current.copy(
                sessionTokens = tokens,
                sessionCalls = if (calls > 0) current.sessionCalls + calls else current.sessionCalls,
                tokenPct = pct,
                windowRemainingMs = remainingMs,
                lastActiveMs = System.currentTimeMillis()
            )
            providerEntities[id] = updated
            db.artifactProviderDao().update(updated)
            _providersFlow.value = providerEntities.values.sortedBy { it.priority }
        }
    }

    /**
     * Finds the best available provider:
     * 1. Must be enabled.
     * 2. Highest priority (lowest priority int).
     * 3. Lowest sessionTokens cached usage.
     * 4. Spins up WebView if parked.
     */
    suspend fun getBestAvailable(): ArtifactWebView? {
        val candidates = providerEntities.values
            .filter { it.enabled }
            .sortedWith(compareBy({ it.priority }, { it.sessionTokens }))

        for (candidate in candidates) {
            val existing = activeWebViews[candidate.id]
            if (existing != null && existing.status == ArtifactProviderStatus.READY) {
                return existing
            }
            if (existing == null) {
                // Spin up on demand
                val spun = getOrCreateWebView(candidate)
                return spun
            }
        }

        // Return any available active webview even if busy (caller will queue)
        return candidates.firstOrNull()?.let { getOrCreateWebView(it) }
    }

    suspend fun addProvider(
        name: String,
        url: String,
        rawPasskey: String?,
        priority: Int = 2
    ): ArtifactProviderEntity {
        val hashedPasskey = if (!rawPasskey.isNullOrBlank()) {
            ArtifactKeyStore.hashPasskey(rawPasskey)
        } else {
            null
        }

        val entity = ArtifactProviderEntity(
            name = name,
            url = url,
            owner = "Claude.ai",
            hashedPasskey = hashedPasskey,
            priority = priority,
            enabled = true,
            sessionTokens = 0L,
            lastActiveMs = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )

        // Save original passkey in KeyStore
        if (!rawPasskey.isNullOrBlank()) {
            ArtifactKeyStore.savePasskey(context, entity.id, rawPasskey)
        }

        db.artifactProviderDao().insert(entity)
        providerEntities[entity.id] = entity
        _providersFlow.value = providerEntities.values.sortedBy { it.priority }

        // Also register in ai_models table so it appears in the chat model picker
        try {
            db.aiModelDao().insertModels(
                listOf(
                    com.example.engine.db.AiModelEntity(
                        providerId = "artifact",
                        modelId = entity.name.replace(" ", "_"),
                        inputType = "TEXT",
                        outputType = "TEXT",
                        description = "Claude.ai Omnivian Artifact Provider: ${entity.name}"
                    )
                )
            )
        } catch (_: Exception) {}

        // Spin up WebView
        getOrCreateWebView(entity)
        return entity
    }

    suspend fun removeProvider(id: String) {
        val wv = activeWebViews.remove(id)
        wv?.destroy()
        ArtifactKeyStore.removePasskey(context, id)
        db.artifactProviderDao().deleteById(id)
        providerEntities.remove(id)
        _providersFlow.value = providerEntities.values.sortedBy { it.priority }
        val updated = _activeStatuses.value.toMutableMap()
        updated.remove(id)
        _activeStatuses.value = updated
    }

    suspend fun toggleEnabled(id: String, enabled: Boolean) {
        val entity = providerEntities[id] ?: return
        val updated = entity.copy(enabled = enabled)
        providerEntities[id] = updated
        db.artifactProviderDao().update(updated)
        _providersFlow.value = providerEntities.values.sortedBy { it.priority }

        if (!enabled) {
            val wv = activeWebViews.remove(id)
            wv?.destroy()
            updateStatus(id, ArtifactProviderStatus.SLEEPING)
        }
    }

    fun reloadProvider(id: String) {
        activeWebViews[id]?.reload()
    }

    fun pauseAll() {
        activeWebViews.values.forEach { it.pause() }
    }

    fun resumeAll() {
        activeWebViews.values.forEach { it.resume() }
    }

    fun destroyAll() {
        pollingJob?.cancel()
        activeWebViews.values.forEach { it.destroy() }
        activeWebViews.clear()
    }
}
