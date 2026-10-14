package com.example.engine.webaiprovider

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.browser.customtabs.*
import com.example.R
import com.example.utils.LogKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebAiCustomTabManager coordinates Chrome Custom Tabs connections,
 * session warmup, URL pre-rendering, and manages active Web AI browser tabs
 * with zero-overhead on-device persistence.
 */
object WebAiCustomTabManager {
    private const val TAG = "WebAiCustomTabManager"

    private const val PREFS_NAME = "web_ai_provider_prefs"
    private const val KEY_PERSISTED_TABS = "key_persisted_active_tabs"
    private const val KEY_PERSISTED_CURRENT_ID = "key_persisted_current_tab_id"

    const val ACTION_SWITCH_TAB = "com.example.ACTION_CUSTOM_TAB_SWITCH"
    const val ACTION_CLOSE_TAB = "com.example.ACTION_CUSTOM_TAB_CLOSE"
    const val ACTION_ADD_TAB = "com.example.ACTION_CUSTOM_TAB_ADD"
    const val EXTRA_TAB_ID = "extra_tab_id"
    const val EXTRA_TAB_URL = "extra_tab_url"
    const val EXTRA_SERVICE_NAME = "extra_service_name"

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null
    private var isConnectionBound = false

    private val _activeTabs = MutableStateFlow<List<WebAiActiveTab>>(emptyList())
    val activeTabs: StateFlow<List<WebAiActiveTab>> = _activeTabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()

    private var isInitialized = false

    /**
     * Rehydrates persisted open tabs from SharedPreferences without background processes.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = sp.getString(KEY_PERSISTED_TABS, null)
            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<WebAiActiveTab>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        WebAiActiveTab(
                            id = obj.getString("id"),
                            serviceId = obj.getString("serviceId"),
                            serviceName = obj.getString("serviceName"),
                            brandColorHex = obj.optString("brandColorHex", "#1A73E8"),
                            url = obj.getString("url"),
                            sessionMode = runCatching {
                                WebAiSessionMode.valueOf(obj.optString("sessionMode", "DEFAULT"))
                            }.getOrDefault(WebAiSessionMode.DEFAULT),
                            profileLabel = obj.optString("profileLabel", "Default"),
                            targetPackage = if (obj.has("targetPackage") && !obj.isNull("targetPackage")) obj.getString("targetPackage") else null,
                            isCurrent = obj.optBoolean("isCurrent", false)
                        )
                    )
                }
                val curId = sp.getString(KEY_PERSISTED_CURRENT_ID, list.firstOrNull()?.id)
                _activeTabs.value = list
                _currentTabId.value = curId
                WebAiProviderManager.updateOpenTabCount(list.size)
                LogKeeper.log("INFO", TAG, "Restored ${list.size} persisted active tabs.")
            }
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Failed to restore active tabs: ${e.message}")
        }
    }

    private fun persistTabs(context: Context, tabs: List<WebAiActiveTab>, currentId: String?) {
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
                    put("isCurrent", t.isCurrent)
                }
                array.put(obj)
            }
            sp.edit()
                .putString(KEY_PERSISTED_TABS, array.toString())
                .putString(KEY_PERSISTED_CURRENT_ID, currentId)
                .apply()
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Failed to persist active tabs: ${e.message}")
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
            // Prioritize Firefox packages supporting Custom Tabs
            for (pkg in WebAiProviderManager.FIREFOX_PACKAGES) {
                val serviceIntent = Intent("android.support.customtabs.action.CustomTabsService").setPackage(pkg)
                if (pm.queryIntentServices(serviceIntent, 0).isNotEmpty()) {
                    LogKeeper.log("INFO", TAG, "Resolved Firefox Custom Tabs engine: $pkg")
                    return pkg
                }
            }
            // Check if any Firefox package is installed at all for web viewing
            for (pkg in WebAiProviderManager.FIREFOX_PACKAGES) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    LogKeeper.log("INFO", TAG, "Found Firefox package: $pkg")
                    return pkg
                } catch (_: Exception) {}
            }
            // Fallback to system default if Firefox not installed
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
     * Helper to launch a tab within Custom Tabs if available, falling back gracefully to browser Intent.
     */
    private fun launchTabInCustomTabsOrBrowser(context: Context, tab: WebAiActiveTab) {
        // Suspend heavy on-device processes (GGUF llama.cpp inference, media players, bitmap caches)
        // to maximize free RAM headroom and prevent Android LMK process termination
        try {
            com.example.engine.lifecycle.HeavyTaskThrottler.suspendHeavyTasks(context)
        } catch (_: Exception) {}

        val targetBrowserPkg = resolveTargetBrowserPackage(context)
        val targetPackage = if (tab.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !tab.targetPackage.isNullOrBlank()) {
            tab.targetPackage
        } else {
            targetBrowserPkg
        }

        val session = customTabsSession ?: customTabsClient?.newSession(CustomTabsCallback())
        val builder = session?.let { CustomTabsIntent.Builder(it) } ?: CustomTabsIntent.Builder()
        builder.setShowTitle(true)
        builder.setUrlBarHidingEnabled(true)
        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)

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
            LogKeeper.log("INFO", TAG, "Launched Custom Tabs session ($targetPackage) for ${tab.serviceName}")
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
     * Launches a multi-tab session or single service via Custom Tabs, preserving and merging
     * with existing active tabs rather than replacing them.
     */
    fun launchSession(context: Context, servicesToLaunch: List<WebAiService>) {
        if (servicesToLaunch.isEmpty()) return

        if (!isInitialized) {
            init(context)
        }

        bindCustomTabsService(context)

        // Build active tab list for incoming services
        val incomingTabs = servicesToLaunch.mapIndexed { index, svc ->
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
                isCurrent = index == 0
            )
        }

        // Merge incoming tabs with existing active tabs to preserve user sessions
        val currentTabs = _activeTabs.value
        val mergedTabs = mutableListOf<WebAiActiveTab>()
        for (existing in currentTabs) {
            val isBeingReplaced = incomingTabs.any { it.id == existing.id || (it.serviceId == existing.serviceId && it.profileLabel == existing.profileLabel) }
            if (!isBeingReplaced) {
                mergedTabs.add(existing.copy(isCurrent = false))
            }
        }
        mergedTabs.addAll(incomingTabs)

        val firstTab = incomingTabs.first()
        val targetTabId = firstTab.id

        // Pre-warm the rest
        if (mergedTabs.size > 1) {
            for (t in mergedTabs.filter { it.id != targetTabId }) {
                warmupAndMayLaunch(t.url)
            }
        }

        updateActiveTabs(mergedTabs, targetTabId, context)
        launchTabInCustomTabsOrBrowser(context, firstTab)
    }

    /**
     * Appends a new tab to an active or backgrounded session and immediately switches to it.
     */
    fun appendAndSwitchTab(context: Context, newTab: WebAiActiveTab) {
        if (!isInitialized) {
            init(context)
        }
        val currentTabs = _activeTabs.value.toMutableList()
        val existingIndex = currentTabs.indexOfFirst { it.id == newTab.id || (it.serviceId == newTab.serviceId && it.profileLabel == newTab.profileLabel) }
        val updatedTabs: List<WebAiActiveTab>
        val activeId: String

        if (existingIndex >= 0) {
            val existing = currentTabs[existingIndex]
            activeId = existing.id
            updatedTabs = currentTabs.map { it.copy(isCurrent = it.id == activeId) }
        } else {
            activeId = newTab.id
            val deact = currentTabs.map { it.copy(isCurrent = false) }
            updatedTabs = deact + newTab.copy(isCurrent = true)
        }

        updateActiveTabs(updatedTabs, activeId, context)

        val targetTab = updatedTabs.first { it.id == activeId }
        launchTabInCustomTabsOrBrowser(context, targetTab)
        LogKeeper.log("INFO", TAG, "Appended and switched to tab: ${targetTab.serviceName} (${targetTab.profileLabel})")
    }

    /**
     * Switches to an existing tab from the folded list in GlobalSidebar using Custom Tabs.
     */
    fun switchToTab(context: Context, tabId: String) {
        if (!isInitialized) {
            init(context)
        }
        val currentTabs = _activeTabs.value
        val targetTab = currentTabs.firstOrNull { it.id == tabId } ?: return
        val updatedTabs = currentTabs.map { it.copy(isCurrent = it.id == tabId) }
        updateActiveTabs(updatedTabs, tabId, context)

        launchTabInCustomTabsOrBrowser(context, targetTab)
        LogKeeper.log("INFO", TAG, "Switched active tab to: ${targetTab.serviceName} (${targetTab.profileLabel})")
    }

    /**
     * Closes an individual tab from the folded list in GlobalSidebar.
     */
    fun closeTab(context: Context, tabId: String) {
        if (!isInitialized) {
            init(context)
        }
        val currentTabs = _activeTabs.value
        val remaining = currentTabs.filter { it.id != tabId }
        val currentId = _currentTabId.value
        val nextId = if (currentId == tabId) remaining.firstOrNull()?.id else currentId
        val updated = remaining.map { it.copy(isCurrent = it.id == nextId) }
        updateActiveTabs(updated, nextId, context)
        LogKeeper.log("INFO", TAG, "Closed tab: $tabId. Remaining: ${remaining.size}")
    }

    /**
     * Closes all active tabs from the top close button in GlobalSidebar.
     */
    fun closeAllTabs(context: Context) {
        if (!isInitialized) {
            init(context)
        }
        updateActiveTabs(emptyList(), null, context)
        LogKeeper.log("INFO", TAG, "Closed all tabs.")
    }

    /**
     * Constructs RemoteViews with up to 6 active tab slots plus [+] button.
     */
    fun buildBottomStripRemoteViews(
        context: Context,
        tabs: List<WebAiActiveTab>,
        currentId: String?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.custom_tab_bottom_strip)

        val slotGroupIds = listOf(
            Triple(R.id.tab_slot_0, R.id.tab_slot_0_bg, Pair(R.id.tab_slot_0_icon, R.id.tab_slot_0_close)),
            Triple(R.id.tab_slot_1, R.id.tab_slot_1_bg, Pair(R.id.tab_slot_1_icon, R.id.tab_slot_1_close)),
            Triple(R.id.tab_slot_2, R.id.tab_slot_2_bg, Pair(R.id.tab_slot_2_icon, R.id.tab_slot_2_close)),
            Triple(R.id.tab_slot_3, R.id.tab_slot_3_bg, Pair(R.id.tab_slot_3_icon, R.id.tab_slot_3_close)),
            Triple(R.id.tab_slot_4, R.id.tab_slot_4_bg, Pair(R.id.tab_slot_4_icon, R.id.tab_slot_4_close)),
            Triple(R.id.tab_slot_5, R.id.tab_slot_5_bg, Pair(R.id.tab_slot_5_icon, R.id.tab_slot_5_close))
        )

        // Configure slots
        for (i in slotGroupIds.indices) {
            val (slotRoot, slotBg, iconClose) = slotGroupIds[i]
            val (slotIcon, slotClose) = iconClose

            if (i < tabs.size) {
                val tab = tabs[i]
                val isSelected = tab.id == currentId

                views.setViewVisibility(slotRoot, android.view.View.VISIBLE)
                views.setInt(
                    slotBg,
                    "setBackgroundResource",
                    if (isSelected) R.drawable.bg_tab_bubble_active else R.drawable.bg_tab_bubble_normal
                )

                // Generate branded icon bitmap with service abbreviation
                val iconBmp = createServiceIconBitmap(tab.serviceName, tab.brandColorHex)
                views.setImageViewBitmap(slotIcon, iconBmp)

                // Tab selection PendingIntent
                val switchIntent = Intent(context, WebAiTabBroadcastReceiver::class.java).apply {
                    action = ACTION_SWITCH_TAB
                    putExtra(EXTRA_TAB_ID, tab.id)
                    putExtra(EXTRA_TAB_URL, tab.url)
                    putExtra(EXTRA_SERVICE_NAME, tab.serviceName)
                }
                val switchPi = PendingIntent.getBroadcast(
                    context,
                    100 + i,
                    switchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(slotRoot, switchPi)

                // Close button PendingIntent
                val closeIntent = Intent(context, WebAiTabBroadcastReceiver::class.java).apply {
                    action = ACTION_CLOSE_TAB
                    putExtra(EXTRA_TAB_ID, tab.id)
                }
                val closePi = PendingIntent.getBroadcast(
                    context,
                    200 + i,
                    closeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(slotClose, closePi)
            } else {
                views.setViewVisibility(slotRoot, android.view.View.GONE)
            }
        }

        // [+] Add Button PendingIntent
        val addIntent = Intent(context, WebAiTabBroadcastReceiver::class.java).apply {
            action = ACTION_ADD_TAB
        }
        val addPi = PendingIntent.getBroadcast(
            context,
            300,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tab_btn_add, addPi)

        return views
    }

    fun updateActiveTabs(newTabs: List<WebAiActiveTab>, currentId: String?, context: Context? = null) {
        _activeTabs.value = newTabs
        _currentTabId.value = currentId
        WebAiProviderManager.updateOpenTabCount(newTabs.size)
        if (context != null) {
            persistTabs(context, newTabs, currentId)
        }
    }

    /**
     * Secondary toolbar on running Custom Tab session is removed in favor of
     * the native folded tab list in the Global Sidebar.
     */
    fun refreshCustomTabsToolbar(context: Context) {
        // No-op: secondary toolbar strip replaced by native sidebar folded tab manager
    }

    private fun getClickableIdsForTabs(tabCount: Int): IntArray {
        val baseList = mutableListOf(R.id.tab_btn_add)
        val slotClickables = listOf(
            listOf(R.id.tab_slot_0, R.id.tab_slot_0_close),
            listOf(R.id.tab_slot_1, R.id.tab_slot_1_close),
            listOf(R.id.tab_slot_2, R.id.tab_slot_2_close),
            listOf(R.id.tab_slot_3, R.id.tab_slot_3_close),
            listOf(R.id.tab_slot_4, R.id.tab_slot_4_close),
            listOf(R.id.tab_slot_5, R.id.tab_slot_5_close)
        )
        for (i in 0 until tabCount.coerceAtMost(slotClickables.size)) {
            baseList.addAll(slotClickables[i])
        }
        return baseList.toIntArray()
    }

    private fun createBroadcastPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WebAiTabBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createServiceIconBitmap(serviceName: String, colorHex: String): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var colorInt = 0xFF1A73E8.toInt()
        try {
            colorInt = android.graphics.Color.parseColor(colorHex)
        } catch (_: Exception) {}

        // Draw pill/circle
        paint.color = colorInt
        val rect = RectF(4f, 4f, size - 4f, size - 4f)
        canvas.drawRoundRect(rect, 16f, 16f, paint)

        // Draw initial letter text
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        val text = serviceName.take(2).uppercase()
        val textY = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, canvas.width / 2f, textY, paint)

        return bitmap
    }
}
