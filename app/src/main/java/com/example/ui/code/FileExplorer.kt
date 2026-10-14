package com.example.ui.code

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.engine.fs.FileNode
import com.example.engine.fs.LocalFileManager
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspaceIssueEntity
import com.example.engine.db.WorkspacePullRequestEntity
import java.util.UUID


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

@Composable
fun FileExplorer(
    onFileClick: (FileNode) -> Unit,
    onFileOpenWith: (FileNode, ViewerType) -> Unit = { node, _ -> onFileClick(node) },
    onCloseClick: (() -> Unit)? = null,
    onNavigateToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fileTree by LocalFileManager.fileTreeState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var commentingReference by remember { mutableStateOf<StagedReference?>(null) }
    val workspaceName = remember(LocalFileManager.getWorkspaceDir().name) { 
        mutableStateOf(LocalFileManager.getWorkspaceName(LocalFileManager.getWorkspaceDir().name)) 
    }

    val workspaceId = LocalFileManager.getWorkspaceDir().name
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Files", "Issues", "PRs")
    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onCloseClick != null) {
                IconButton(onClick = onCloseClick) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workspaceName.value,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Workspace Explorer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search files & folders...") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )
        
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        
        if (selectedTabIndex == 0) {
            fileTree?.let { rootNode ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (searchQuery.isNotEmpty()) {
                    val searchResults = searchFileTree(rootNode, searchQuery)
                    if (searchResults.isEmpty()) {
                        item {
                            Text("No files found", modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(searchResults) { node ->
                            SearchResultItem(
                                node = node,
                                onFileClick = onFileClick,
                                onCommentClick = { n ->
                                    commentingReference = StagedReference.GenericArtifact(
                                        file = n.file,
                                        summary = "Artifact ${n.name}"
                                    )
                                }
                            )
                        }
                    }
                } else {
                    item {
                        FileTreeNodeView(
                            node = rootNode,
                            level = 0,
                            onFileClick = onFileClick,
                            onFileOpenWith = onFileOpenWith,
                            onCommentClick = { n ->
                                commentingReference = StagedReference.GenericArtifact(
                                    file = n.file,
                                    summary = "Artifact ${n.name}"
                                )
                            }
                        )
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workspace found")
            }
        }
        } else if (selectedTabIndex == 1) {
            IssuesTab(workspaceId = workspaceId)
        } else if (selectedTabIndex == 2) {
            PullRequestsTab(workspaceId = workspaceId)
        }

        if (commentingReference != null) {
            ArtifactCommentBottomSheet(
                reference = commentingReference!!,
                onDismiss = { commentingReference = null },
                onNavigateToChat = onNavigateToChat
            )
        }
    }
}

@Composable
fun IssuesTab(workspaceId: String) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val issues by db.workspaceIssueDao().getIssuesForWorkspace(workspaceId).collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (issues.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No local issues found.")
                }
            }
        }
        items(issues) { issue ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = issue.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Status: ${issue.status}", style = MaterialTheme.typography.bodySmall)
                    if (issue.targetFile != null) {
                        Text(text = "Target: ${issue.targetFile}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun PullRequestsTab(workspaceId: String) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val prs by db.workspacePullRequestDao().getPullRequestsForWorkspace(workspaceId).collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (prs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No local pull requests found.")
                }
            }
        }
        items(prs) { pr ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = pr.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Status: ${pr.status}", style = MaterialTheme.typography.bodySmall)
                    if (pr.targetFile != null) {
                        Text(text = "Target: ${pr.targetFile}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

fun searchFileTree(node: FileNode, query: String): List<FileNode> {
    val results = mutableListOf<FileNode>()
    if (node.name.contains(query, ignoreCase = true)) {
        results.add(node)
    }
    if (node.isDirectory) {
        node.children.forEach { child ->
            results.addAll(searchFileTree(child, query))
        }
    }
    return results
}

@Composable
fun SearchResultItem(
    node: FileNode,
    onFileClick: (FileNode) -> Unit,
    onCommentClick: (FileNode) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!node.isDirectory) {
                    onFileClick(node)
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getFileIcon(node, isExpanded = false),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = getFileTint(node)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (!node.isDirectory) {
            IconButton(
                onClick = { onCommentClick(node) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.AddComment,
                    contentDescription = "Comment for AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FileTreeNodeView(
    node: FileNode,
    level: Int,
    onFileClick: (FileNode) -> Unit,
    onFileOpenWith: (FileNode, ViewerType) -> Unit = { n, _ -> onFileClick(n) },
    onCommentClick: (FileNode) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showContextMenu by remember { mutableStateOf(false) }
    var showOpenWithMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showCreateIssueDialog by remember { mutableStateOf(false) }
    var showCreatePRDialog by remember { mutableStateOf(false) }

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "file_swipe_offset"
    )
    
    val context = LocalContext.current
    
    val uploadFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    var fileName = "uploaded_file_${System.currentTimeMillis()}"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                    }
                    val targetFile = java.io.File(node.file, fileName)
                    LocalFileManager.copyUriToFile(context, uri, targetFile)
                }
            }
        }
    }
    
    val uploadZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempZip = java.io.File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.zip")
                val result = LocalFileManager.copyUriToFile(context, uri, tempZip)
                if (result.isSuccess) {
                    LocalFileManager.unzipFile(tempZip, node.file)
                    tempZip.delete()
                    LocalFileManager.refreshFileTree()
                }
            }
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!node.isDirectory) {
                        Modifier.pointerInput(node.file.path) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipeOffsetX > 70f) {
                                        onCommentClick(node)
                                    }
                                    swipeOffsetX = 0f
                                },
                                onDragCancel = {
                                    swipeOffsetX = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val newOffset = swipeOffsetX + dragAmount
                                    swipeOffsetX = newOffset.coerceIn(0f, 120f)
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            // Revealed swipe-to-comment indicator
            if (animatedOffsetX > 10f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = "Comment",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Comment for AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(animatedOffsetX.toInt(), 0) }
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (node.isDirectory) {
                            isExpanded = !isExpanded
                        } else {
                            onFileClick(node)
                        }
                    }
                    .padding(start = (level * 16 + 12).dp, top = 7.dp, bottom = 7.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.isDirectory) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = getFileIcon(node, isExpanded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = getFileTint(node)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (!node.isDirectory) {
                    IconButton(
                        onClick = { onCommentClick(node) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = "Comment for AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(16.dp))
                }
                
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    if (!node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Comment for AI") },
                            leadingIcon = { Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showContextMenu = false
                                onCommentClick(node)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open With...") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { 
                                showContextMenu = false
                                showOpenWithMenu = true 
                            }
                        )
                    }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { showContextMenu = false; showRenameDialog = true }
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = { showContextMenu = false; showCopyDialog = true }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showContextMenu = false
                        scope.launch { LocalFileManager.deleteFile(node.file) }
                    }
                )
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("New File") },
                        onClick = { showContextMenu = false; showNewFileDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("New Folder") },
                        onClick = { showContextMenu = false; showNewFolderDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Compress to Zip") },
                        onClick = { 
                            showContextMenu = false
                            scope.launch {
                                val targetZip = java.io.File(node.file.parentFile, "${node.file.name}.zip")
                                LocalFileManager.zipDirectory(node.file, targetZip)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Upload Files") },
                        onClick = { 
                            showContextMenu = false
                            uploadFilesLauncher.launch("*/*")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Upload and Extract Zip") },
                        onClick = { 
                            showContextMenu = false
                            uploadZipLauncher.launch("application/zip")
                        }
                    )
                } else if (node.name.endsWith(".zip")) {
                     DropdownMenuItem(
                        text = { Text("Extract Here") },
                        onClick = { 
                            showContextMenu = false
                            scope.launch {
                                val targetDir = java.io.File(node.file.parentFile, node.file.nameWithoutExtension)
                                LocalFileManager.unzipFile(node.file, targetDir)
                            }
                        }
                    )
                }
            }
        }
    }


        if (showRenameDialog) {
            var newName by remember { mutableStateOf(node.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { LocalFileManager.renameFile(node.file, newName) }
                        showRenameDialog = false
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
            )
        }
        if (showCopyDialog) {
            var newName by remember { mutableStateOf(node.name + "_copy") }
            AlertDialog(
                onDismissRequest = { showCopyDialog = false },
                title = { Text("Copy") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { 
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                node.file.copyTo(java.io.File(node.file.parentFile, newName), overwrite = true)
                            }
                            LocalFileManager.refreshFileTree()
                        }
                        showCopyDialog = false
                    }) { Text("Copy") }
                },
                dismissButton = { TextButton(onClick = { showCopyDialog = false }) { Text("Cancel") } }
            )
        }
        if (showNewFileDialog) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewFileDialog = false },
                title = { Text("New File") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch { LocalFileManager.createFile(node.file, newName, false) }
                        }
                        showNewFileDialog = false
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") } }
            )
        }
        if (showNewFolderDialog) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("New Folder") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch { LocalFileManager.createFile(node.file, newName, true) }
                        }
                        showNewFolderDialog = false
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") } }
            )
        }

        if (showOpenWithMenu) {
            AlertDialog(
                onDismissRequest = { showOpenWithMenu = false },
                title = { Text("Open '${node.name}' With...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ViewerType.values().forEach { viewer ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOpenWithMenu = false
                                        onFileOpenWith(node, viewer)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (viewer) {
                                            ViewerType.CODE -> Icons.Default.Code
                                            ViewerType.IMAGE -> Icons.Default.Image
                                            ViewerType.VIDEO -> Icons.Default.Movie
                                            ViewerType.AUDIO -> Icons.Default.Audiotrack
                                            ViewerType.PPT -> Icons.Default.Slideshow
                                            ViewerType.DOCUMENT -> Icons.Default.PictureAsPdf
                                            ViewerType.WEBVIEW_STAGE -> Icons.Default.ViewInAr
                                            ViewerType.AUTO -> Icons.Default.AutoAwesome
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(viewer.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showOpenWithMenu = false }) { Text("Cancel") }
                }
            )
        }

        if (isExpanded && node.isDirectory) {
            node.children.forEach { child ->
                FileTreeNodeView(
                    node = child,
                    level = level + 1,
                    onFileClick = onFileClick,
                    onFileOpenWith = onFileOpenWith,
                    onCommentClick = onCommentClick
                )
            }
        }
    }
}

private fun getFileIcon(node: FileNode, isExpanded: Boolean): ImageVector {
    val lower = node.name.lowercase()
    return when {
        node.isDirectory && isExpanded -> Icons.Default.FolderOpen
        node.isDirectory -> Icons.Default.Folder
        lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") -> Icons.Default.Code
        lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml") || lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".yaml") || lower.endsWith(".yml") -> Icons.Default.DataObject
        lower.endsWith(".md") || lower.endsWith(".txt") -> Icons.Default.Description
        lower.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") -> Icons.Default.FolderZip
        lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") -> Icons.Default.Image
        lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") -> Icons.Default.VideoFile
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".flac") -> Icons.Default.AudioFile
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileTint(node: FileNode): Color {
    if (node.isDirectory) return Color(0xFFFFB300) // Folder warm amber
    val lower = node.name.lowercase()
    return when {
        lower.endsWith(".kt") || lower.endsWith(".java") -> Color(0xFF9C27B0) // Kotlin purple
        lower.endsWith(".py") -> Color(0xFF1976D2) // Python blue
        lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx") -> Color(0xFFFBC02D) // JS/TS yellow
        lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml") || lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".yaml") || lower.endsWith(".yml") -> Color(0xFFE65100) // Config orange
        lower.endsWith(".md") || lower.endsWith(".txt") -> Color(0xFF00897B) // Docs teal
        lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") -> Color(0xFFE91E63) // Media pink
        lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mp3") || lower.endsWith(".wav") -> Color(0xFF00ACC1) // AV cyan
        lower.endsWith(".pdf") -> Color(0xFFD32F2F) // PDF red
        lower.endsWith(".zip") -> Color(0xFF795548) // Zip brown
        else -> Color(0xFF78909C) // Default slate
    }
}
