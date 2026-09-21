package com.example.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.omniroot.artifact.ArtifactProviderPool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsContent() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("general_prefs", Context.MODE_PRIVATE) }

    var maxWebViews by remember { mutableIntStateOf(prefs.getInt("max_concurrent_webviews", 2)) }
    var timeoutSeconds by remember { mutableIntStateOf(prefs.getInt("artifact_timeout_seconds", 90)) }
    var autoReload by remember { mutableStateOf(prefs.getBoolean("auto_reload_on_crash", true)) }
    var autoSyncTokens by remember { mutableStateOf(prefs.getBoolean("auto_sync_tokens", true)) }
    var showPurgeConfirmation by remember { mutableStateOf(false) }
    var purgeFeedback by remember { mutableStateOf<String?>(null) }

    fun savePrefs() {
        prefs.edit()
            .putInt("max_concurrent_webviews", maxWebViews)
            .putInt("artifact_timeout_seconds", timeoutSeconds)
            .putBoolean("auto_reload_on_crash", autoReload)
            .putBoolean("auto_sync_tokens", autoSyncTokens)
            .apply()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("General App & Runtime Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Configure background WebView concurrency limits, AI generation timeouts, and memory optimization.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Section: Omnivian WebView Concurrency
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Max Concurrent WebViews", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Limits active Chromium instances in memory ($maxWebViews active)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val counts = listOf(1, 2, 3, 4)
                        counts.forEachIndexed { index, count ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = counts.size),
                                onClick = {
                                    maxWebViews = count
                                    savePrefs()
                                },
                                selected = maxWebViews == count,
                                label = { Text("$count") }
                            )
                        }
                    }

                    if (maxWebViews > 2) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    "Caution: Running >2 WebViews simultaneously consumes 200MB+ heap and may trigger low-memory kills on devices with <=3GB RAM.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else {
                        Text(
                            "Recommended: 2 WebViews allows smooth switching while keeping RAM usage safe on Android Go.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Section: Generation Timeouts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Artifact Generation Timeout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Maximum wait time before timeout (${timeoutSeconds}s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Slider(
                        value = timeoutSeconds.toFloat(),
                        onValueChange = {
                            timeoutSeconds = it.toInt()
                            savePrefs()
                        },
                        valueRange = 30f..180f,
                        steps = 4
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("30s (Fast)", style = MaterialTheme.typography.labelSmall)
                        Text("90s (Default)", style = MaterialTheme.typography.labelSmall)
                        Text("180s (Deep)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Section: Runtime Switches
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Auto-Reload on Render Crash") },
                        supportingContent = { Text("Automatically recovers WebViews using exponential backoff") },
                        trailingContent = {
                            Switch(
                                checked = autoReload,
                                onCheckedChange = {
                                    autoReload = it
                                    savePrefs()
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Auto-Sync Token States") },
                        supportingContent = { Text("Polls getState() every 30s to keep UI token bars updated") },
                        trailingContent = {
                            Switch(
                                checked = autoSyncTokens,
                                onCheckedChange = {
                                    autoSyncTokens = it
                                    savePrefs()
                                }
                            )
                        }
                    )
                }
            }
        }

        // Section: Memory & Cache Maintenance
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Memory & Cache Maintenance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Purge all cached WebView DOM storage and reset active Omnivian Chromium renderers if experiencing desync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Button(
                        onClick = { showPurgeConfirmation = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Purge WebViews & Clear Cache")
                    }

                    if (purgeFeedback != null) {
                        Text(
                            text = purgeFeedback!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = { Text("Purge WebViews & Cache?") },
            text = {
                Text("This will immediately destroy all running background WebViews, clear temporary WebView storage, and resync active artifact providers from scratch.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        ArtifactProviderPool.getInstance(context).destroyAll()
                        val webView = android.webkit.WebView(context)
                        webView.clearCache(true)
                        webView.destroy()
                        ArtifactProviderPool.getInstance(context).resyncAllStates()
                        showPurgeConfirmation = false
                        purgeFeedback = "WebViews purged and reset successfully."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
