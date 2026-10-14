package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.brain.ContextBudgetManager
import com.example.engine.brain.RollingSummaryManager
import com.example.engine.db.AppDatabase
import com.example.engine.db.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTokenPanelBottomSheet(
    onDismiss: () -> Unit,
    sessionId: String? = null,
    activeModel: String = "gemini-1.5-pro",
    viewModel: TokenPanelViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val rpm by viewModel.currentRpm.collectAsState()
    val tpm by viewModel.currentTpm.collectAsState()
    val totalCost by viewModel.totalCost.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var rollingSummaryText by remember { mutableStateOf<String?>(null) }
    var pinnedMemories by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var configuredContextSize by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(sessionId) {
        if (!sessionId.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(context)
                val settings = db.chatSettingsDao().getSettings(sessionId)
                rollingSummaryText = settings?.rollingSummary
                configuredContextSize = settings?.contextSize
                pinnedMemories = db.memoryDao().getPinnedMemoriesSync()
            }
        }
    }

    val modelProfile = remember(activeModel, configuredContextSize) {
        ContextBudgetManager.getProfileForModel(activeModel, configuredContextSize)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI Context & Token Health",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Context & Memory") },
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Quotas & Costs") },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Tab 0: Context Window & Memory Drift Protection
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Dynamic Context Window Profiler",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Max Context: ${modelProfile.maxContextTokens} tokens", style = MaterialTheme.typography.bodySmall)
                                    Text("Headroom Reserved: ${modelProfile.targetHeadroomTokens} tokens", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { 0.25f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Allocations: 20% System & Pinned Rules | 20% Rolling Summary | 40% Recent Dialogue | 20% Output Headroom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Rolling Summary Section
                    item {
                        Card {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rolling Conversation Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    if (rollingSummaryText != null && sessionId != null) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    RollingSummaryManager.clearRollingSummary(context, sessionId)
                                                    rollingSummaryText = null
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Clear Summary", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (!rollingSummaryText.isNullOrBlank()) {
                                    Text(
                                        text = rollingSummaryText!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "No compacted turns yet. As turns exceed the active window budget, they will automatically be summarized here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // Pinned Architectural Memories
                    item {
                        Text("Pinned Architectural Invariants (${pinnedMemories.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    if (pinnedMemories.isEmpty()) {
                        item {
                            Text(
                                "No pinned memories. Critical architecture and rules detected in chat will automatically be pinned here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        items(pinnedMemories) { mem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mem.key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(mem.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val db = AppDatabase.getInstance(context)
                                                db.memoryDao().setPinned(mem.id, false)
                                                pinnedMemories = db.memoryDao().getPinnedMemoriesSync()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.PushPin, contentDescription = "Unpin", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Quotas & Costs
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val currentTpmValue = tpm ?: 0
                    val maxTpm = 60000
                    val tpmProgress = (currentTpmValue.toFloat() / maxTpm.toFloat()).coerceIn(0f, 1f)
                    val isTpmWarning = tpmProgress > 0.8f

                    val currentRpmValue = rpm
                    val maxRpm = 60
                    val rpmProgress = (currentRpmValue.toFloat() / maxRpm.toFloat()).coerceIn(0f, 1f)
                    val isRpmWarning = rpmProgress > 0.8f

                    Text("Tokens Per Minute (TPM)", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { tpmProgress },
                            modifier = Modifier.weight(1f).height(8.dp),
                            color = if (isTpmWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$currentTpmValue / $maxTpm")
                    }

                    Text("Requests Per Minute (RPM)", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { rpmProgress },
                            modifier = Modifier.weight(1f).height(8.dp),
                            color = if (isRpmWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$currentRpmValue / $maxRpm")
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Estimated Total Cost") },
                            supportingContent = { Text("Across all logged API requests") },
                            leadingContent = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                            trailingContent = { Text(String.format("$%.4f", totalCost ?: 0.0), style = MaterialTheme.typography.titleMedium) }
                        )
                    }
                }
            }
        }
    }
}
