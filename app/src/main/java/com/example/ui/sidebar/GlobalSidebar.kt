package com.example.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.fs.LocalFileManager
import com.example.engine.lifecycle.HeavyTaskThrottler
import com.example.engine.webaiprovider.WebAiProviderManager
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
    var workspaces by remember { mutableStateOf(emptyList<File>()) }
    var showKnowledgeBitsSheet by remember { mutableStateOf(false) }
    
    LaunchedEffect(currentChatId, onClose) { // Trigger reload when sidebar opens or chat changes
        workspaces = LocalFileManager.getWorkspaces()
    }

    if (showKnowledgeBitsSheet) {
        com.example.ui.library.KnowledgeBitsBottomSheet(
            onDismiss = { showKnowledgeBitsSheet = false }
        )
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
                        HeavyTaskThrottler.suspendHeavyTasks(context)
                        onNavigateToWebAiPortal()
                        onClose()
                    }
                ) {
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
                                "Multi-browser session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (openTabCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text("$openTabCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
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
