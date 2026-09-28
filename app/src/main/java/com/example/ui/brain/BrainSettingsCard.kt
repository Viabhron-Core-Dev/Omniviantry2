package com.example.ui.brain

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.brain.BrainSettingsStore
import com.example.engine.brain.ContextBudgetManager
import kotlin.math.roundToInt

/**
 * Mini-Phase 11.7: Brain Settings Card.
 *
 * Implements:
 * - "Show Thinking Process" toggle in chat controls.
 * - Memory recall threshold slider with real-time semantic strictness feedback.
 * - Active token budget & context pressure preview bar with color-coded headroom warnings.
 * - Quick-action navigation to the full MemoryManagementScreen.
 */
@Composable
fun BrainSettingsCard(
    modelName: String = "gemini-2.5-pro",
    estimatedTokens: Int = 0,
    onOpenMemoryManagement: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        BrainSettingsStore.init(context)
    }

    val showThinking by BrainSettingsStore.showThinkingFlow.collectAsState()
    val recallThreshold by BrainSettingsStore.recallThresholdFlow.collectAsState()

    // Calculate token pressure metrics
    val profile = remember(modelName) {
        ContextBudgetManager.getProfileForModel(modelName)
    }
    val safeBudget = (profile.maxContextTokens - profile.targetHeadroomTokens).coerceAtLeast(1024)
    val contextPressure = (estimatedTokens.toFloat() / safeBudget.toFloat()).coerceIn(0.0f, 1.0f)
    val pressurePercent = (contextPressure * 100f).roundToInt()

    val progressColor by animateColorAsState(
        targetValue = when {
            contextPressure >= 0.85f -> MaterialTheme.colorScheme.error
            contextPressure >= 0.70f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "pressureColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("brain_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = "Brain Core",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Brain & Cognitive Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Reasoning streams, semantic memory & token budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // 1. "Show Thinking Process" toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Thinking Process",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (showThinking)
                            "Display live Chain-of-Thought bubbles and planning tags"
                        else
                            "Collapse and hide raw reasoning tokens in message timeline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = showThinking,
                    onCheckedChange = { BrainSettingsStore.setShowThinkingProcess(context, it) },
                    modifier = Modifier.testTag("toggle_show_thinking")
                )
            }

            Spacer(Modifier.height(14.dp))

            // 2. Memory Recall Threshold Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Memory Recall Threshold",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = String.format("%.2f", recallThreshold),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = when {
                        recallThreshold < 0.8f -> "Broad Recall: Surfaces loose associative memories and rules."
                        recallThreshold <= 1.4f -> "Balanced Recall: High semantic keyword and category correlation."
                        else -> "Strict Recall: Surfaces exact title matches and pinned rules only."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )

                Slider(
                    value = recallThreshold,
                    onValueChange = { BrainSettingsStore.setRecallThreshold(context, it) },
                    valueRange = 0.2f..2.5f,
                    steps = 22,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_memory_recall_threshold")
                )
            }

            Spacer(Modifier.height(10.dp))

            // 3. Active Token Budget & Context Pressure Preview Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Context Window Budget",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$pressurePercent% used",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }

                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { contextPressure },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("context_pressure_progress_bar"),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${String.format("%,d", estimatedTokens)} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Limit: ${String.format("%,d", profile.maxContextTokens)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Model: ${profile.modelId} (Headroom: ${String.format("%,d", profile.targetHeadroomTokens)} tokens)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 4. Quick Action: Manage Memories
            if (onOpenMemoryManagement != null) {
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = onOpenMemoryManagement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_manage_memories"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Memory & Knowledge Store")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
