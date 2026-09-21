package com.example.engine.webaiprovider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.utils.LogKeeper

/**
 * WebAiTabBroadcastReceiver receives interactions from the RemoteViews
 * bottom toolbar in Chrome Custom Tabs (tab switching, tab closing, tab adding).
 */
class WebAiTabBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WebAiTabBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        LogKeeper.log("INFO", TAG, "Received Custom Tab bottom action: $action")

        when (action) {
            WebAiCustomTabManager.ACTION_SWITCH_TAB -> {
                val tabId = intent.getStringExtra(WebAiCustomTabManager.EXTRA_TAB_ID) ?: return
                val tabUrl = intent.getStringExtra(WebAiCustomTabManager.EXTRA_TAB_URL) ?: return
                val serviceName = intent.getStringExtra(WebAiCustomTabManager.EXTRA_SERVICE_NAME) ?: "AI Service"

                val currentTabs = WebAiCustomTabManager.activeTabs.value
                val targetTab = currentTabs.firstOrNull { it.id == tabId }
                val updatedTabs = currentTabs.map {
                    it.copy(isCurrent = it.id == tabId)
                }
                WebAiCustomTabManager.updateActiveTabs(updatedTabs, tabId)
                WebAiCustomTabManager.refreshCustomTabsToolbar(context)

                // Launch/switch URL in the Custom Tab with session mode flags
                val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse(tabUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (targetTab?.sessionMode == WebAiSessionMode.EPHEMERAL) {
                        putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
                        putExtra("org.chromium.chrome.browser.customtabs.EXTRA_ENABLE_EPHEMERAL_BROWSING", true)
                    } else if (targetTab?.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !targetTab.targetPackage.isNullOrBlank()) {
                        setPackage(targetTab.targetPackage)
                    }
                }
                try {
                    context.startActivity(navIntent)
                } catch (_: Exception) {
                    navIntent.setPackage(null)
                    context.startActivity(navIntent)
                }
                WebAiProviderManager.recordActiveLaunch(serviceName, tabUrl)
                LogKeeper.log("INFO", TAG, "Switched active tab to $serviceName ($tabUrl) [Mode: ${targetTab?.sessionMode}]")
            }

            WebAiCustomTabManager.ACTION_CLOSE_TAB -> {
                val tabId = intent.getStringExtra(WebAiCustomTabManager.EXTRA_TAB_ID) ?: return
                val currentTabs = WebAiCustomTabManager.activeTabs.value
                val remainingTabs = currentTabs.filter { it.id != tabId }

                if (remainingTabs.isEmpty()) {
                    WebAiCustomTabManager.updateActiveTabs(emptyList(), null)
                    LogKeeper.log("INFO", TAG, "All tabs closed from strip.")
                    Toast.makeText(context, "All Web AI tabs closed", Toast.LENGTH_SHORT).show()
                } else {
                    val currentId = WebAiCustomTabManager.currentTabId.value
                    val nextId = if (currentId == tabId) remainingTabs.first().id else currentId
                    val updatedTabs = remainingTabs.map { it.copy(isCurrent = it.id == nextId) }

                    WebAiCustomTabManager.updateActiveTabs(updatedTabs, nextId)
                    WebAiCustomTabManager.refreshCustomTabsToolbar(context)

                    if (currentId == tabId) {
                        val nextTab = remainingTabs.first()
                        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse(nextTab.url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            if (nextTab.sessionMode == WebAiSessionMode.EPHEMERAL) {
                                putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
                                putExtra("org.chromium.chrome.browser.customtabs.EXTRA_ENABLE_EPHEMERAL_BROWSING", true)
                            } else if (nextTab.sessionMode == WebAiSessionMode.CUSTOM_BROWSER && !nextTab.targetPackage.isNullOrBlank()) {
                                setPackage(nextTab.targetPackage)
                            }
                        }
                        try {
                            context.startActivity(navIntent)
                        } catch (_: Exception) {
                            navIntent.setPackage(null)
                            context.startActivity(navIntent)
                        }
                        WebAiProviderManager.recordActiveLaunch(nextTab.serviceName, nextTab.url)
                    }
                }
            }

            WebAiCustomTabManager.ACTION_ADD_TAB -> {
                // Open lightweight quick-adder dialog overlay over Custom Tabs
                val overlayIntent = Intent(context, com.example.ui.webaiprovider.WebAiQuickAddActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(overlayIntent)
            }
        }
    }
}
