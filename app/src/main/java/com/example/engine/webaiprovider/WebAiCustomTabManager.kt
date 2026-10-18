package com.example.engine.webaiprovider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.*
import com.example.engine.lifecycle.HeavyTaskThrottler
import com.example.utils.LogKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject


/**
 * WebAiCustomTabManager coordinates Chrome/Firefox Custom Tabs connections,
 * single-instance bookmark launching with multi-browser routing, and zero-overhead
 * on-device persistence. It has NO secondary toolbars or bottom strips inside Custom Tabs.
 */
object WebAiCustomTabManager {
    private const val TAG = "WebAiCustomTabManager"

    private const val PREFS_NAME = "web_ai_provider_prefs"
    private const val KEY_PERSISTED_BOOKMARKS = "key_persisted_active_tabs"
    private const val KEY_PERSISTED_CURRENT_ID = "key_persisted_current_tab_id"

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null
    private var isConnectionBound = false

    private val _activeTabs = MutableStateFlow<List<WebAiActiveTab>>(emptyList())
    val activeTabs: StateFlow<List<WebAiActiveTab>> = _activeTabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes bookmarks and restores last active bookmark state from SharedPreferences.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        WebAiProviderManager.init(context)

        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = sp.getString(KEY_PERSISTED_BOOKMARKS, null)
            val curId = sp.getString(KEY_PERSISTED_CURRENT_ID, null)
            _currentTabId.value = curId

            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<WebAiActiveTab>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    list.add(
                        WebAiActiveTab(
                            id = id,
                            serviceId = obj.getString("serviceId"),
                            serviceName = obj.getString("serviceName"),
                            brandColorHex = obj.optString("brandColorHex", "#1A73E8"),
                            url = obj.getString("url"),
                            sessionMode = runCatching {
                                WebAiSessionMode.valueOf(obj.optString("sessionMode", "DEFAULT"))
                            }.getOrDefault(WebAiSessionMode.DEFAULT),
                            profileLabel = obj.optString("profileLabel", "Default"),
                            targetPackage = if (obj.has("targetPackage") && !obj.isNull("targetPackage")) obj.getString("targetPackage") else null,
                            isCurrent = id == curId
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _activeTabs.value = list
                    WebAiProviderManager.updateOpenTabCount(list.size)
                    LogKeeper.log("INFO", TAG, "Restored ${list.size} bookmarks from storage.")
                    return
                }
            }

            // If no persisted bookmarks exist, populate default bookmarks from WebAiProviderManager
            syncBookmarksFromProviderManager(context)
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Failed to restore bookmarks: ${e.message}")
            syncBookmarksFromProviderManager(context)
        }
    }

    /**
     * Syncs default enabled services as bookmarks in the sidebar.
     */
    fun syncBookmarksFromProviderManager(context: Context) {
        val services = WebAiProviderManager.services.value.filter { it.enabled }
        val curId = _currentTabId.value
        val list = services.map { svc ->
            val activeProf = svc.profiles.firstOrNull { it.id == svc.activeProfileId }
                ?: svc.profiles.firstOrNull()
            val url = activeProf?.launchUrl?.ifBlank { svc.baseUrl } ?: svc.baseUrl
            WebAiActiveTab(
                id = svc.id,
                serviceId = svc.id,
                serviceName = svc.name,
                brandColorHex = svc.brandColorHex,
                url = url,
                sessionMode = activeProf?.sessionMode ?: WebAiSessionMode.DEFAULT,
                profileLabel = activeProf?.label ?: "Default",
                targetPackage = activeProf?.targetPackage,
                isCurrent = svc.id == curId
            )
        }
        _activeTabs.value = list
        WebAiProviderManager.updateOpenTabCount(list.size)
        persistBookmarks(context, list, curId)
    }

    private fun persistBookmarks(context: Context, tabs: List<WebAiActiveTab>, currentId: String?) {
        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (t in tabs) {
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("serviceId", t.serviceId)
                    put("serviceName", t.serviceName)
                    put("brandColorHex", t.brandColorHex)
                    put("url", t.url)
                    put("sessionMode", t.sessionMode.name)
                    put("profileLabel", t.profileLabel)
                    if (t.targetPackage != null) put("targetPackage", t.targetPackage)
                    put("isCurrent", t.id == currentId)
                }
                array.put(obj)
            }
            sp.edit()
                .putString(KEY_PERSISTED_BOOKMARKS, array.toString())
                .putString(KEY_PERSISTED_CURRENT_ID, currentId)
                .apply()
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Failed to persist bookmarks: ${e.message}")
        }
    }

    private val connection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            LogKeeper.log("INFO", TAG, "CustomTabsService connected: ${name.packageName}")
            customTabsClient = client
            client.warmup(0L)
            customTabsSession = client.newSession(CustomTabsCallback())
            isConnectionBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            LogKeeper.log("INFO", TAG, "CustomTabsService disconnected: ${name.packageName}")
            customTabsClient = null
            customTabsSession = null
            isConnectionBound = false
        }
    }

    /**
     * Resolves the target browser package honoring user preferences, defaulting to Firefox.
     */
    fun resolveTargetBrowserPackage(context: Context): String? {
        val enginePref = WebAiProviderManager.getPreferredBrowserEngine()
        val pm = context.packageManager

        if (enginePref == WebAiProviderManager.ENGINE_FIREFOX) {
            for (pkg in WebAiProviderManager.FIREFOX_PACKAGES) {
                val serviceIntent = Intent("android.support.customtabs.action.CustomTabsService").setPackage(pkg)
                if (pm.queryIntentServices(serviceIntent, 0).isNotEmpty()) {
                    LogKeeper.log("INFO", TAG, "Resolved Firefox Custom Tabs engine: $pkg")
                    return pkg
                }
            }
            for (pkg in WebAiProviderManager.FIREFOX_PACKAGES) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    LogKeeper.log("INFO", TAG, "Found Firefox package: $pkg")
                    return pkg
                } catch (_: Exception) {}
            }
            val defaultPkg = CustomTabsClient.getPackageName(context, null)
            LogKeeper.log("INFO", TAG, "Firefox preferred but not detected on device; fallback to: $defaultPkg")
            return defaultPkg
        } else if (enginePref == WebAiProviderManager.ENGINE_CHROME) {
            for (pkg in WebAiProviderManager.CHROME_PACKAGES) {
                val serviceIntent = Intent("android.support.customtabs.action.CustomTabsService").setPackage(pkg)
                if (pm.queryIntentServices(serviceIntent, 0).isNotEmpty()) {
                    return pkg
                }
            }
            return CustomTabsClient.getPackageName(context, null)
        } else {
            return CustomTabsClient.getPackageName(context, null)
        }
    }

    /**
     * Initializes and binds the CustomTabsService for immediate fast-launch.
     */
    fun bindCustomTabsService(context: Context) {
        if (isConnectionBound) return
        try {
            val packageName = resolveTargetBrowserPackage(context)
            if (packageName != null) {
                CustomTabsClient.bindCustomTabsService(context, packageName, connection)
                LogKeeper.log("INFO", TAG, "CustomTabsService bound to package: $packageName")
            } else {
                LogKeeper.log("WARNING", TAG, "No Custom Tabs compatible browser package found.")
            }
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Failed to bind CustomTabsService: ${e.message}")
        }
    }

    /**
     * Warm up browser engine and pre-render the target URL.
     */
    fun warmupAndMayLaunch(url: String) {
        try {
            customTabsClient?.warmup(0L)
            customTabsSession?.mayLaunchUrl(Uri.parse(url), null, null)
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "mayLaunchUrl failed: ${e.message}")
        }
    }

    /**
     * Launches a single AI bookmark in Custom Tabs with zero bottom bars or secondary toolbars.
     * Supports multi-browser account isolation via targetPackage.
     */
    fun launchBookmark(context: Context, service: WebAiService, profileId: String? = null) {
        if (!isInitialized) {
            init(context)
        }
        bindCustomTabsService(context)

        val targetProfile = if (!profileId.isNullOrBlank()) {
            service.profiles.firstOrNull { it.id == profileId }
        } else {
            service.profiles.firstOrNull { it.id == service.activeProfileId } ?: service.profiles.firstOrNull()
        }

        val launchUrl = targetProfile?.launchUrl?.ifBlank { service.baseUrl } ?: service.baseUrl
        val tab = WebAiActiveTab(
            id = service.id,
            serviceId = service.id,
            serviceName = service.name,
            brandColorHex = service.brandColorHex,
            url = launchUrl,
            sessionMode = targetProfile?.sessionMode ?: WebAiSessionMode.DEFAULT,
            profileLabel = targetProfile?.label ?: "Default",
            targetPackage = targetProfile?.targetPackage,
            isCurrent = true
        )

        // Update active tab tracking
        val currentTabs = _activeTabs.value.toMutableList()
        val existingIndex = currentTabs.indexOfFirst { it.id == tab.id }
        val updatedTabs = if (existingIndex >= 0) {
            currentTabs.map { it.copy(isCurrent = it.id == tab.id) }
        } else {
            (currentTabs.map { it.copy(isCurrent = false) } + tab)
        }

        _activeTabs.value = updatedTabs
        _currentTabId.value = tab.id
        persistBookmarks(context, updatedTabs, tab.id)

        launchTabInCleanCustomTabs(context, tab)
    }

    /**
     * Launches a single WebAiActiveTab directly in clean Custom Tabs.
     */
    fun switchToTab(context: Context, tabId: String) {
        if (!isInitialized) {
            init(context)
        }
        val currentTabs = _activeTabs.value
        val targetTab = currentTabs.firstOrNull { it.id == tabId } ?: return

        val updatedTabs = currentTabs.map { it.copy(isCurrent = it.id == tabId) }
        _activeTabs.value = updatedTabs
        _currentTabId.value = tabId
        persistBookmarks(context, updatedTabs, tabId)

        launchTabInCleanCustomTabs(context, targetTab)
        LogKeeper.log("INFO", TAG, "Switched to bookmark: ${targetTab.serviceName} (${targetTab.profileLabel})")
    }

    /**
     * Backward-compatible session launcher for single or multiple services (launches the first/active one).
     */
    fun launchSession(context: Context, servicesToLaunch: List<WebAiService>) {
        if (servicesToLaunch.isEmpty()) return
        val target = servicesToLaunch.first()
        launchBookmark(context, target)
    }

    /**
     * Backward-compatible append method: switches to single tab.
     */
    fun appendAndSwitchTab(context: Context, newTab: WebAiActiveTab) {
        if (!isInitialized) {
            init(context)
        }
        val currentTabs = _activeTabs.value.toMutableList()
        val existingIndex = currentTabs.indexOfFirst { it.id == newTab.id }
        val updatedTabs = if (existingIndex >= 0) {
            currentTabs.map { it.copy(isCurrent = it.id == newTab.id) }
        } else {
            currentTabs.map { it.copy(isCurrent = false) } + newTab.copy(isCurrent = true)
        }
        _activeTabs.value = updatedTabs
        _currentTabId.value = newTab.id
        persistBookmarks(context, updatedTabs, newTab.id)

        launchTabInCleanCustomTabs(context, newTab)
    }

    /**
     * Core launcher: opens clean, full-screen Custom Tab with NO bottom strip.
     * Uses targetPackage for separate browser account isolation if configured.
     */
    private fun launchTabInCleanCustomTabs(context: Context, tab: WebAiActiveTab) {
        // Suspend heavy on-device processes (GGUF llama.cpp inference, media players, bitmap caches)
        // to maximize free RAM headroom and prevent Android LMK process termination
        try {
            HeavyTaskThrottler.suspendHeavyTasks(context)
        } catch (_: Exception) {}

        val targetBrowserPkg = resolveTargetBrowserPackage(context)
        val targetPackage = if (tab.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !tab.targetPackage.isNullOrBlank()) {
            tab.targetPackage
        } else if (!tab.targetPackage.isNullOrBlank()) {
            tab.targetPackage
        } else {
            targetBrowserPkg
        }

        val session = customTabsSession ?: customTabsClient?.newSession(CustomTabsCallback())
        val builder = session?.let { CustomTabsIntent.Builder(it) } ?: CustomTabsIntent.Builder()
        builder.setShowTitle(true)
        builder.setUrlBarHidingEnabled(true)
        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)

        // NO setSecondaryToolbarViews or RemoteViews - 100% clean browser window

        val intent = builder.build()
        if (!targetPackage.isNullOrBlank()) {
            intent.intent.setPackage(targetPackage)
        }

        if (tab.sessionMode == WebAiSessionMode.EPHEMERAL) {
            intent.intent.putExtra("org.mozilla.firefox.PRIVATE_BROWSING", true)
            intent.intent.putExtra("private_browsing_mode", true)
            intent.intent.putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
            intent.intent.putExtra("org.chromium.chrome.browser.customtabs.EXTRA_ENABLE_EPHEMERAL_BROWSING", true)
        }

        intent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        try {
            intent.launchUrl(context, Uri.parse(tab.url))
            LogKeeper.log("INFO", TAG, "Launched clean Custom Tab ($targetPackage) for ${tab.serviceName}")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Custom Tabs launch failed, falling back to Intent.ACTION_VIEW: ${e.message}")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (!targetPackage.isNullOrBlank()) {
                    setPackage(targetPackage)
                }
                if (tab.sessionMode == WebAiSessionMode.EPHEMERAL) {
                    putExtra("org.mozilla.firefox.PRIVATE_BROWSING", true)
                    putExtra("private_browsing_mode", true)
                    putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
                    putExtra("org.chromium.chrome.browser.customtabs.EXTRA_ENABLE_EPHEMERAL_BROWSING", true)
                }
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                fallbackIntent.setPackage(null)
                context.startActivity(fallbackIntent)
            }
        }
        WebAiProviderManager.recordActiveLaunch(tab.serviceName, tab.url)
    }

    /**
     * Clears the active session indicator.
     */
    fun closeActiveSession(context: Context) {
        if (!isInitialized) init(context)
        val updated = _activeTabs.value.map { it.copy(isCurrent = false) }
        _activeTabs.value = updated
        _currentTabId.value = null
        persistBookmarks(context, updated, null)
        LogKeeper.log("INFO", TAG, "Cleared active session.")
    }

    /**
     * Removes an individual bookmark from the sidebar.
     */
    fun closeTab(context: Context, tabId: String) {
        if (!isInitialized) init(context)
        val currentTabs = _activeTabs.value
        val remaining = currentTabs.filter { it.id != tabId }
        val currentId = _currentTabId.value
        val nextId = if (currentId == tabId) null else currentId
        val updated = remaining.map { it.copy(isCurrent = it.id == nextId) }
        _activeTabs.value = updated
        _currentTabId.value = nextId
        WebAiProviderManager.updateOpenTabCount(remaining.size)
        persistBookmarks(context, updated, nextId)
        LogKeeper.log("INFO", TAG, "Removed bookmark: $tabId. Remaining: ${remaining.size}")
    }

    /**
     * Closes all bookmarks / resets to empty.
     */
    fun closeAllTabs(context: Context) {
        if (!isInitialized) init(context)
        _activeTabs.value = emptyList()
        _currentTabId.value = null
        WebAiProviderManager.updateOpenTabCount(0)
        persistBookmarks(context, emptyList(), null)
        LogKeeper.log("INFO", TAG, "Cleared all bookmarks.")
    }
}
