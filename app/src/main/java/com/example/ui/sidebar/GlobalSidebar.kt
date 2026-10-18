package com.example.ui.sidebar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.fs.LocalFileManager
import com.example.engine.lifecycle.HeavyTaskThrottler
import com.example.engine.webaiprovider.WebAiCustomTabManager
import com.example.engine.webaiprovider.WebAiProviderManager
import com.example.engine.webaiprovider.WebAiSessionMode
import java.io.File
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GlobalSidebar(
    onClose: () -> Unit, 
    onNavigateToSettings: () -> Unit = {}, 
    onNavigateToArtifacts: () -> Unit = {},
    onNavigateToWebAiPortal: () -> Unit = {},
    onNewChat: () -> Unit = {},
    currentChatId: String,
    onChatSelected: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val openTabCount by WebAiProviderManager.openTabCount.collectAsState()
    val activeTabs by WebAiCustomTabManager.activeTabs.collectAsState()
    val currentTabId by WebAiCustomTabManager.currentTabId.collectAsState()
    var isWebAiFolded by rememberSaveable { mutableStateOf(true) }
    var workspaces by remember { mutableStateOf(emptyList<File>()) }
    var showKnowledgeBitsSheet by remember { mutableStateOf(false) }
    var showMemoryScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        WebAiProviderManager.init(context)
        WebAiCustomTabManager.init(context)
    }
    
    LaunchedEffect(currentChatId, onClose) { // Trigger reload when sidebar opens or chat changes
        workspaces = LocalFileManager.getWorkspaces()
    }

    if (showKnowledgeBitsSheet) {
        com.example.ui.library.KnowledgeBitsBottomSheet(
            onDismiss = { showKnowledgeBitsSheet = false }
        )
    }

    if (showMemoryScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMemoryScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.example.ui.brain.MemoryManagementScreen(
                    onNavigateBack = { showMemoryScreen = false }
                )
            }
        }
    }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("New Chat") },
            selected = false,
            onClick = { onNewChat(); onClose() },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Code, contentDescription = null) },
            label = { Text("Artifacts (mini apps)") },
            selected = false,
            onClick = { 
                onNavigateToArtifacts()
                onClose()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            label = { Text("Knowledge Bits (Cache)") },
            selected = false,
            onClick = { 
                showKnowledgeBitsSheet = true
                onClose()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
            label = { Text("Brain & Memory Store") },
            selected = false,
            onClick = { 
                showMemoryScreen = true
                onClose()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.DesignServices, contentDescription = null) },
            label = { Text("Design") },
            selected = false,
            onClick = { 
                android.widget.Toast.makeText(context, "Design Studio coming soon", android.widget.Toast.LENGTH_SHORT).show()
                onClose()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
            label = { Text("Library") },
            selected = false,
            onClick = { 
                android.widget.Toast.makeText(context, "Component Library coming soon", android.widget.Toast.LENGTH_SHORT).show()
                onClose()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text("List of Chats (Repos)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "pinned_web_ai_hub") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    onClick = {
                        if (activeTabs.isEmpty()) {
                            HeavyTaskThrottler.suspendHeavyTasks(context)
                            onNavigateToWebAiPortal()
                            onClose()
                        } else {
                            isWebAiFolded = !isWebAiFolded
                        }
                    }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Main Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Public,
                                    contentDescription = "Web AI Hub",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Web AI Hub",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "PINNED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    if (activeTabs.isEmpty()) {
                                        "Manage AI bookmarks"
                                    } else if (isWebAiFolded) {
                                        val activeTab = activeTabs.firstOrNull { it.id == currentTabId }
                                        if (activeTab != null) {
                                            "Active: ${activeTab.serviceName} • Tap to view"
                                        } else {
                                            "${activeTabs.size} AI bookmark${if (activeTabs.size > 1) "s" else ""}"
                                        }
                                    } else {
                                        "Tap to launch • Single-tab mode"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            if (activeTabs.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("${activeTabs.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isWebAiFolded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = if (isWebAiFolded) "Expand bookmarks" else "Collapse bookmarks",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Unfolded List of AI Bookmarks
                        AnimatedVisibility(
                            visible = !isWebAiFolded && activeTabs.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    thickness = 0.8.dp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                // Action header row: "BOOKMARKS (N)" + [+] add + [✕] clear active
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "AI BOOKMARKS (${activeTabs.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // [+] Launch / Add more from portal
                                        IconButton(
                                            onClick = {
                                                HeavyTaskThrottler.suspendHeavyTasks(context)
                                                onNavigateToWebAiPortal()
                                                onClose()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Manage bookmarks",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        if (currentTabId != null) {
                                            Spacer(Modifier.width(4.dp))
                                            // [✕] Clear active session
                                            IconButton(
                                                onClick = {
                                                    WebAiCustomTabManager.closeActiveSession(context)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear active session",
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                // Individual Tab Rows
                                activeTabs.forEach { tab ->
                                    val isCurrent = tab.id == currentTabId
                                    val tabColor = remember(tab.brandColorHex) {
                                        try {
                                            Color(android.graphics.Color.parseColor(tab.brandColorHex))
                                        } catch (_: Exception) {
                                            Color(0xFF1A73E8)
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                        },
                                        tonalElevation = if (isCurrent) 2.dp else 0.dp,
                                        onClick = {
                                            WebAiCustomTabManager.switchToTab(context, tab.id)
                                            onClose()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Brand color pill / circle with initial
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(tabColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    tab.serviceName.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        tab.serviceName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    if (isCurrent) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(MaterialTheme.colorScheme.primary)
                                                        )
                                                    }
                                                }
                                                val browserLabel = remember(tab.targetPackage, tab.sessionMode) {
                                                    if (tab.sessionMode == WebAiSessionMode.EPHEMERAL) {
                                                        "Incognito"
                                                    } else if (!tab.targetPackage.isNullOrBlank()) {
                                                        if (tab.targetPackage.contains("firefox", ignoreCase = true)) "Firefox"
                                                        else if (tab.targetPackage.contains("chrome", ignoreCase = true)) "Chrome"
                                                        else "Custom Browser"
                                                    } else null
                                                }
                                                val subtitleText = when {
                                                    tab.profileLabel.isNotBlank() && tab.profileLabel != "Default" && browserLabel != null -> "${tab.profileLabel} • $browserLabel"
                                                    tab.profileLabel.isNotBlank() && tab.profileLabel != "Default" -> tab.profileLabel
                                                    browserLabel != null -> browserLabel
                                                    else -> null
                                                }
                                                if (subtitleText != null) {
                                                    Text(
                                                        subtitleText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // Cross beside name to close this specific tab
                                            IconButton(
                                                onClick = {
                                                    WebAiCustomTabManager.closeTab(context, tab.id)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Close ${tab.serviceName} tab",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            items(workspaces) { workspace ->
                ChatSidebarItem(
                    workspace = workspace,
                    isSelected = workspace.name == currentChatId,
                    onClick = { 
                        onChatSelected(workspace.name)
                        onClose()
                    },
                    onDelete = {
                        val workspaceToDelete = workspace.name
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val db = com.example.engine.db.AppDatabase.getDatabase(context)
                            db.chatMessageDao().clearSession(workspaceToDelete)
                            db.workspaceConfigDao().deleteConfig(workspaceToDelete)
                        }
                        LocalFileManager.deleteWorkspace(workspace.name)
                        val remaining = LocalFileManager.getWorkspaces()
                        workspaces = remaining
                        if (workspace.name == currentChatId) {
                            if (remaining.isNotEmpty()) {
                                onChatSelected(remaining.first().name)
                            } else {
                                onNewChat()
                            }
                        }
                    }
                )
            }
        }
        
        HorizontalDivider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Global Settings") },
            selected = false,
            onClick = onNavigateToSettings,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ChatSidebarItem(
    workspace: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var chatName by remember { mutableStateOf(LocalFileManager.getWorkspaceName(workspace.name)) }
    val timestamp = remember(workspace.lastModified()) {
        java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(workspace.lastModified()))
    }

    NavigationDrawerItem(
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        label = { 
            Column {
                Text(chatName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        badge = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; showRename = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive (GDrive)") },
                        onClick = { 
                            showMenu = false
                            android.widget.Toast.makeText(context, "Archive requires Google Drive integration", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    )

    if (showRename) {
        var newName by remember { mutableStateOf(chatName) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename Chat") },
            text = { 
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Chat Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    chatName = newName
                    LocalFileManager.setWorkspaceName(workspace.name, newName)
                    showRename = false 
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
