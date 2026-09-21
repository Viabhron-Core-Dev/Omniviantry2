package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.orchestrator.UserToolDecision

@Composable
fun ToolCallBubble(
    message: ChatMessage,
    onDecision: (UserToolDecision) -> Unit = {},
    onToggleFold: () -> Unit = {},
    onOpenArtifactFullscreen: (InChatArtifactInfo) -> Unit = {},
    onOpenArtifactInWorkspace: (InChatArtifactInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val toolName = message.toolName ?: "Tool Call"
    val status = message.toolStatus
    val durationMs = message.toolDurationMs
    val expanded = !message.isFolded

    val detectedArtifacts = remember(message) {
        val workspaceBase = com.example.engine.fs.LocalFileManager.getWorkspaceDir()
        ArtifactExtractor.extractArtifactsFromMessage(message, workspaceBase)
    }

    var showArgs by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(true) }

    val statusColor = when (status) {
        ToolExecutionStatus.PENDING_APPROVAL -> MaterialTheme.colorScheme.tertiary
        ToolExecutionStatus.EXECUTING -> MaterialTheme.colorScheme.primary
        ToolExecutionStatus.COMPLETED -> Color(0xFF2E7D32)
        ToolExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
        ToolExecutionStatus.REJECTED -> MaterialTheme.colorScheme.outline
    }

    val icon = getToolIcon(toolName)

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "tool_arrow_anim"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            statusColor.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleFold() }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (durationMs > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "${durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = when (status) {
                            ToolExecutionStatus.PENDING_APPROVAL -> "Awaiting confirmation"
                            ToolExecutionStatus.EXECUTING -> "Executing in sandbox..."
                            ToolExecutionStatus.COMPLETED -> "Executed successfully"
                            ToolExecutionStatus.FAILED -> "Execution failed"
                            ToolExecutionStatus.REJECTED -> "Execution declined"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontSize = 11.sp
                    )
                }

                if (status == ToolExecutionStatus.EXECUTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded Details
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {

                    // Arguments Section
                    if (!message.toolArgsJson.isNullOrBlank() && message.toolArgsJson != "{}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showArgs = !showArgs }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (showArgs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Arguments",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (showArgs) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = message.toolArgsJson,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Output Section
                    if (!message.toolOutput.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Standard Output / Result",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Tool Output", message.toolOutput))
                                    Toast.makeText(context, "Output copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Output",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = message.toolOutput,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(8.dp),
                                    maxLines = 20,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (status == ToolExecutionStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Approval Action Buttons
                    if (status == ToolExecutionStatus.PENDING_APPROVAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This action requires your explicit permission before executing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDecision(UserToolDecision.REJECT) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reject", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onDecision(UserToolDecision.ALLOW_ONCE) },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Allow Once", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = { onDecision(UserToolDecision.ALLOW_ALWAYS_SESSION) },
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Always (Session)", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    // In-Chat Non-Text Artifact Cards (Tier 1 Progression)
                    if (detectedArtifacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Generated Artifacts (${detectedArtifacts.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        for (artifact in detectedArtifacts) {
                            ArtifactChatCard(
                                artifact = artifact,
                                onOpenFullscreen = onOpenArtifactFullscreen,
                                onOpenInWorkspace = onOpenArtifactInWorkspace
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getToolIcon(name: String): ImageVector {
    return when (name.lowercase()) {
        "view_file", "edit_file", "multi_edit_file", "create_file" -> Icons.Default.Description
        "delete_file" -> Icons.Default.Delete
        "move_file" -> Icons.Default.DriveFileMove
        "list_dir" -> Icons.Default.FolderOpen
        "grep_search" -> Icons.Default.Search
        "js_sandbox" -> Icons.Default.Javascript
        "shell_process", "shell_exec" -> Icons.Default.Terminal
        "proot_linux" -> Icons.Default.Memory
        "run_python" -> Icons.Default.Code
        "archive" -> Icons.Default.Archive
        "document_parser" -> Icons.Default.Article
        "mcp_manage" -> Icons.Default.Hub
        "knowledge_bits" -> Icons.Default.Bookmarks
        else -> Icons.Default.Build
    }
}
