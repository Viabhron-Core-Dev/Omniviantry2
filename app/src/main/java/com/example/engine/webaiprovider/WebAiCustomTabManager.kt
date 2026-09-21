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

/**
 * WebAiCustomTabManager coordinates Chrome Custom Tabs connections,
 * session warmup, URL pre-rendering, and builds custom RemoteViews
 * bottom toolbars with interactive tab-switching bubbles, close targets,
 * and a quick add [+] button.
 */
object WebAiCustomTabManager {
    private const val TAG = "WebAiCustomTabManager"

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
     * Initializes and binds the CustomTabsService for immediate fast-launch.
     */
    fun bindCustomTabsService(context: Context) {
        if (isConnectionBound) return
        try {
            val packageName = CustomTabsClient.getPackageName(context, null)
            if (packageName != null) {
                CustomTabsClient.bindCustomTabsService(context, packageName, connection)
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
     * Launches a multi-tab session or single service via Custom Tabs with bottom strip.
     */
    fun launchSession(context: Context, servicesToLaunch: List<WebAiService>) {
        if (servicesToLaunch.isEmpty()) return

        bindCustomTabsService(context)

        // Build active tab list
        val newTabs = servicesToLaunch.mapIndexed { index, svc ->
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

        _activeTabs.value = newTabs
        val firstTab = newTabs.first()
        _currentTabId.value = firstTab.id

        // Pre-warm the rest
        if (newTabs.size > 1) {
            for (t in newTabs.drop(1)) {
                warmupAndMayLaunch(t.url)
            }
        }

        // Build Custom Tabs Intent
        val session = customTabsSession ?: customTabsClient?.newSession(CustomTabsCallback())
        val builder = session?.let { CustomTabsIntent.Builder(it) } ?: CustomTabsIntent.Builder()

        builder.setShowTitle(true)
        builder.setUrlBarHidingEnabled(true)
        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)

        // RemoteViews Bottom Toolbar Strip
        val remoteViews = buildBottomStripRemoteViews(context, newTabs, firstTab.id)
        val clickableIds = getClickableIdsForTabs(newTabs.size)
        val pendingIntent = createBroadcastPendingIntent(context)

        builder.setSecondaryToolbarViews(remoteViews, clickableIds, pendingIntent)

        // Ephemeral / Incognito check for first tab
        val intent = builder.build()
        if (firstTab.sessionMode == WebAiSessionMode.EPHEMERAL) {
            intent.intent.putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
            intent.intent.putExtra("org.chromium.chrome.browser.customtabs.EXTRA_ENABLE_EPHEMERAL_BROWSING", true)
        } else if (firstTab.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !firstTab.targetPackage.isNullOrBlank()) {
            intent.intent.setPackage(firstTab.targetPackage)
        }

        intent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        try {
            intent.launchUrl(context, Uri.parse(firstTab.url))
            WebAiProviderManager.recordActiveLaunch(firstTab.serviceName, firstTab.url)
            WebAiProviderManager.updateOpenTabCount(newTabs.size)
            LogKeeper.log("INFO", TAG, "Launched Custom Tabs session with ${newTabs.size} tabs. Active: ${firstTab.serviceName}")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Custom Tabs launch failed, falling back to Intent.ACTION_VIEW: ${e.message}")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(firstTab.url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (firstTab.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !firstTab.targetPackage.isNullOrBlank()) {
                    setPackage(firstTab.targetPackage)
                }
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                fallbackIntent.setPackage(null)
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Appends a new tab to an active or backgrounded session and immediately switches to it.
     */
    fun appendAndSwitchTab(context: Context, newTab: WebAiActiveTab) {
        val currentTabs = _activeTabs.value.toMutableList()
        // Check if tab with same ID or URL already exists
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

        updateActiveTabs(updatedTabs, activeId)
        refreshCustomTabsToolbar(context)

        // Switch to the newly appended tab in the Custom Tab or Browser
        val targetTab = updatedTabs.first { it.id == activeId }
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetTab.url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (targetTab.sessionMode == WebAiSessionMode.EPHEMERAL) {
                putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
                putExtra("org.chromium.chrome.browser.customtabs.EXTRA_ENABLE_EPHEMERAL_BROWSING", true)
            } else if (targetTab.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !targetTab.targetPackage.isNullOrBlank()) {
                setPackage(targetTab.targetPackage)
            }
        }
        try {
            context.startActivity(navIntent)
        } catch (_: Exception) {
            navIntent.setPackage(null)
            context.startActivity(navIntent)
        }
        WebAiProviderManager.recordActiveLaunch(targetTab.serviceName, targetTab.url)
        LogKeeper.log("INFO", TAG, "Appended and switched to tab: ${targetTab.serviceName} (${targetTab.profileLabel})")
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

    fun updateActiveTabs(newTabs: List<WebAiActiveTab>, currentId: String?) {
        _activeTabs.value = newTabs
        _currentTabId.value = currentId
        WebAiProviderManager.updateOpenTabCount(newTabs.size)
    }

    /**
     * Refreshes the secondary toolbar on the running Custom Tab session.
     */
    fun refreshCustomTabsToolbar(context: Context) {
        val session = customTabsSession ?: return
        val currentTabs = _activeTabs.value
        val currentId = _currentTabId.value
        val remoteViews = buildBottomStripRemoteViews(context, currentTabs, currentId)
        val clickableIds = getClickableIdsForTabs(currentTabs.size)
        val pendingIntent = createBroadcastPendingIntent(context)

        try {
            session.setSecondaryToolbarViews(remoteViews, clickableIds, pendingIntent)
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "setSecondaryToolbarViews failed: ${e.message}")
        }
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
