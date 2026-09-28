package com.example.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.EngineRegistry
import com.example.engine.tools.ToolPermission
import com.example.engine.tools.ToolPermissionManager

data class ToolGroupDefinition(
    val categoryId: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tools: List<ToolItemDefinition>
)

data class ToolItemDefinition(
    val name: String,
    val displayName: String,
    val description: String,
    val isDangerous: Boolean = false
)

/**
 * Foldable Accordion Tools Directory (Mini-Phase 4 Deliverable 1).
 *
 * Material 3 collapsible accordion cards organizing tools into clear functional groups:
 * - 📂 Global Core Utilities (`view_file`, `edit_file`, `list_dir`, `create_file`, `delete_file`, `move_file`, `grep_search`)
 * - 💻 Workspace Scoped Tools (`proot_linux`, `run_python`, `archive_tool`, `document_parser`)
 * - 🎬 Media & Silicon Tools (`js_sandbox`, `shell_process`, `media_pool`, `skia_markup`)
 * - 🔌 External MCP Servers (`mcp_manage`, `github_sync`, `cloudflare_worker`, `custom_webhook`)
 *
 * Features:
 * - Active count indicator chips (`X/Y Active`)
 * - Animated chevron expansion/collapse
 * - Master enable/disable toggles per category and per tool
 * - Direct integration with ToolPermissionManager
 */
@Composable
fun ToolAccordionDirectory(
    sessionId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionRevision by remember { mutableIntStateOf(0) }

    val toolGroups = remember {
        listOf(
            ToolGroupDefinition(
                categoryId = "core_utils",
                title = "Global Core Utilities",
                description = "Fundamental filesystem, inspection, and manipulation tools.",
                icon = Icons.Default.Folder,
                tools = listOf(
                    ToolItemDefinition("view_file", "View File", "Inspects file contents with start/end line slice reading."),
                    ToolItemDefinition("edit_file", "Edit File", "Performs surgical contiguous text replacements."),
                    ToolItemDefinition("multi_edit_file", "Multi-Edit File", "Applies atomic non-contiguous batch replacements."),
                    ToolItemDefinition("create_file", "Create File", "Creates new files or overwrites existing targets."),
                    ToolItemDefinition("delete_file", "Delete File", "Permanently removes a workspace file.", isDangerous = true),
                    ToolItemDefinition("move_file", "Move / Rename File", "Relocates or renames a file inside workspace."),
                    ToolItemDefinition("list_dir", "List Directory", "Enumerates files and directories recursively or shallow."),
                    ToolItemDefinition("grep_search", "Grep Search", "High-performance regex/plain text codebase search.")
                )
            ),
            ToolGroupDefinition(
                categoryId = "workspace_scoped",
                title = "Workspace Scoped Tools",
                description = "Code execution engines, parsers, and workspace archives.",
                icon = Icons.Default.Computer,
                tools = listOf(
                    ToolItemDefinition("proot_linux", "PRoot Linux Sandbox", "Runs isolated Linux ELF binaries & apt packages in user-space.", isDangerous = true),
                    ToolItemDefinition("run_python", "Python Execution", "Executes Python scripts within the local interpreter environment."),
                    ToolItemDefinition("archive_tool", "Archive & Zip Manager", "Compresses, uncompresses, and packages workspace bundles."),
                    ToolItemDefinition("document_parser", "Document Parser", "Extracts structured text from PDFs, PPTs, and Office docs.")
                )
            ),
            ToolGroupDefinition(
                categoryId = "media_silicon",
                title = "Media & Silicon Tools",
                description = "Hardware acceleration, audio/video pipelines, and JS sandbox.",
                icon = Icons.Default.Movie,
                tools = listOf(
                    ToolItemDefinition("js_sandbox", "QuickJS Sandbox", "Lightweight deterministic ES2020 JavaScript engine."),
                    ToolItemDefinition("shell_process", "Background Process Runner", "Manages async background daemon commands & pipelines.", isDangerous = true),
                    ToolItemDefinition("media_pool", "ExoPlayer Silicon Engine", "Coordinates hardware video decoding and audio playback."),
                    ToolItemDefinition("skia_markup", "Skia GPU Markup", "Hardware-accelerated region selection and bounding boxes.")
                )
            ),
            ToolGroupDefinition(
                categoryId = "external_mcp",
                title = "External MCP Servers",
                description = "Model Context Protocol providers, GitHub, and Cloudflare integrations.",
                icon = Icons.Default.Extension,
                tools = listOf(
                    ToolItemDefinition("mcp_manage", "MCP Server Manager", "Lists, starts, and interrogates dynamic MCP tools."),
                    ToolItemDefinition("github_sync", "GitHub Octokit Gateway", "Syncs commits, pull requests, and remote branch tracking."),
                    ToolItemDefinition("cloudflare_worker", "Cloudflare Edge Gateway", "Triggers worker deployments and fetches KV records."),
                    ToolItemDefinition("custom_webhook", "REST Automation Webhook", "Sends authenticated JSON payloads to remote endpoints.")
                )
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        toolGroups.forEach { group ->
            key(group.categoryId, permissionRevision) {
                ToolAccordionCard(
                    group = group,
                    context = context,
                    sessionId = sessionId,
                    onPermissionChanged = { permissionRevision++ }
                )
            }
        }
    }
}

@Composable
fun ToolAccordionCard(
    group: ToolGroupDefinition,
    context: Context,
    sessionId: String,
    onPermissionChanged: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Count how many tools in this group are not completely disabled
    val activeCount = remember(group, sessionId) {
        group.tools.count { tool ->
            !ToolPermissionManager.isForbidden(tool.name)
        }
    }
    val allActive = activeCount == group.tools.size

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "tool_arrow_anim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(group.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Active Count Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (activeCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$activeCount / ${group.tools.size} Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (activeCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Master Toggle Row for this Category
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Enable All in Category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = allActive,
                            onCheckedChange = { enableAll ->
                                group.tools.forEach { tool ->
                                    val newPerm = if (enableAll) {
                                        if (tool.isDangerous) ToolPermission.ALWAYS_ASK else ToolPermission.USE_FREELY
                                    } else {
                                        ToolPermission.NO_PERMISSION
                                    }
                                    ToolPermissionManager.setGlobalOverride(tool.name, newPerm, context)
                                }
                                onPermissionChanged()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Individual Tools in Category
                    group.tools.forEach { tool ->
                        ToolItemRow(
                            tool = tool,
                            context = context,
                            sessionId = sessionId,
                            onPermissionChanged = onPermissionChanged
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolItemRow(
    tool: ToolItemDefinition,
    context: Context,
    sessionId: String,
    onPermissionChanged: () -> Unit
) {
    val currentPerm = remember(tool.name) {
        ToolPermissionManager.getGlobalPermission(tool.name) ?: if (tool.isDangerous) ToolPermission.ALWAYS_ASK else ToolPermission.USE_FREELY
    }
    val isForbidden = currentPerm == ToolPermission.NO_PERMISSION

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (tool.isDangerous) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "APPROVAL REQUIRED",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tool Switch: Active vs Disabled
            Switch(
                checked = !isForbidden,
                onCheckedChange = { isEnabled ->
                    val nextPerm = if (isEnabled) {
                        if (tool.isDangerous) ToolPermission.ALWAYS_ASK else ToolPermission.USE_FREELY
                    } else {
                        ToolPermission.NO_PERMISSION
                    }
                    ToolPermissionManager.setGlobalOverride(tool.name, nextPerm, context)
                    onPermissionChanged()
                }
            )
        }
    }
}
