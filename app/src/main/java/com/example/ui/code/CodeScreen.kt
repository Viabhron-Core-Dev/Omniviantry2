package com.example.ui.code

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import com.example.engine.server.PreviewServerManager
import com.example.engine.fs.FileNode
import com.example.engine.fs.FileHistoryEngine
import java.io.File
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspacePullRequestEntity
import java.util.UUID

data class OpenFileTab(
    val fileNode: FileNode,
    val isRawEditMode: Boolean = false,
    val activeViewerOverride: ViewerType = ViewerType.AUTO
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(
    onMenuClick: () -> Unit,
    onNavigateToChat: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isServerRunning by PreviewServerManager.isRunning.collectAsState()
    val editorState = rememberCodeEditorState()
    
    var openTabs by remember { mutableStateOf(listOf<OpenFileTab>()) }
    var activeTabIndex by remember { mutableIntStateOf(-1) }
    var isMinimized by remember { mutableStateOf(false) }
    var showCommentSheetForFile by remember { mutableStateOf<File?>(null) }

    val activeTab = openTabs.getOrNull(activeTabIndex)
    val selectedFile = activeTab?.fileNode
    var activeViewerOverride by remember(activeTab) { mutableStateOf(activeTab?.activeViewerOverride ?: ViewerType.AUTO) }
    var isRawEditMode by remember(activeTab) { mutableStateOf(activeTab?.isRawEditMode ?: false) }

    fun openOrFocusTab(fileNode: FileNode, viewerOverride: ViewerType = ViewerType.AUTO, rawMode: Boolean = false) {
        val existingIndex = openTabs.indexOfFirst { it.fileNode.file.absolutePath == fileNode.file.absolutePath }
        if (existingIndex != -1) {
            activeTabIndex = existingIndex
            openTabs = openTabs.toMutableList().also {
                if (viewerOverride != ViewerType.AUTO || rawMode) {
                    it[existingIndex] = it[existingIndex].copy(
                        activeViewerOverride = viewerOverride,
                        isRawEditMode = rawMode
                    )
                }
            }
            isMinimized = false
        } else {
            val newTab = OpenFileTab(
                fileNode = fileNode,
                activeViewerOverride = viewerOverride,
                isRawEditMode = rawMode
            )
            openTabs = openTabs + newTab
            activeTabIndex = openTabs.lastIndex
            isMinimized = false
        }
        scope.launch {
            editorState.loadFile(fileNode.file)
        }
    }

    fun closeTab(index: Int) {
        if (index in openTabs.indices) {
            val newTabs = openTabs.toMutableList().also { it.removeAt(index) }
            openTabs = newTabs
            if (newTabs.isEmpty()) {
                activeTabIndex = -1
                isMinimized = false
            } else {
                val newIdx = index.coerceAtMost(newTabs.lastIndex)
                activeTabIndex = newIdx
                scope.launch {
                    editorState.loadFile(newTabs[newIdx].fileNode.file)
                }
            }
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showSyntaxCheckDialog by remember { mutableStateOf(false) }
    var showSaveAsPRDialog by remember { mutableStateOf(false) }
    var showForkArtifactDialog by remember { mutableStateOf(false) }
    val workspaceName = remember(com.example.engine.fs.LocalFileManager.getWorkspaceDir().name) { 
        mutableStateOf(com.example.engine.fs.LocalFileManager.getWorkspaceName(com.example.engine.fs.LocalFileManager.getWorkspaceDir().name)) 
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { 
                    val relativePath = remember(selectedFile?.file?.path) {
                        if (selectedFile != null) {
                            val wsDir = com.example.engine.fs.LocalFileManager.getWorkspaceDir()
                            val path = selectedFile.file.relativeToOrNull(wsDir)?.path ?: selectedFile.name
                            path.replace(java.io.File.separatorChar, '/')
                        } else ""
                    }
                    Column {
                        if (selectedFile != null && !isMinimized) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "workspace",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = " / ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                                val parts = relativePath.split("/")
                                if (parts.size > 1) {
                                    val dirParts = parts.dropLast(1)
                                    val dirDisplay = if (dirParts.size > 2) {
                                        "... / " + dirParts.last()
                                    } else {
                                        dirParts.joinToString(" / ")
                                    }
                                    Text(
                                        text = dirDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = " / ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    text = selectedFile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (editorState.isLiveGeneration) {
                                Text("● Live Generation View", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Text(workspaceName.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Code & Workspace Explorer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Global Menu")
                    }
                },
                actions = {
                    if (selectedFile != null && !isMinimized) {
                        IconButton(onClick = { 
                            editorState.saveFile()
                            Toast.makeText(context, "Saved ${selectedFile.name}", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Code", editorState.content.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = {
                            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            val fileToDownload = selectedFile.file
                            if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                                try {
                                    fileToDownload.copyTo(java.io.File(downloadsDir, fileToDownload.name), overwrite = true)
                                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to download", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                    
                    IconButton(onClick = {
                        if (isServerRunning) {
                            PreviewServerManager.stop()
                        } else {
                            PreviewServerManager.start(File(context.cacheDir, "workspace"))
                        }
                    }) {
                        Icon(
                            if (isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isServerRunning) "Stop Server" else "Start Server"
                        )
                    }
                    IconButton(onClick = { 
                        if (openTabs.isNotEmpty()) {
                            isMinimized = !isMinimized
                        }
                    }) {
                        Icon(
                            if (isMinimized || selectedFile == null) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = "Toggle Explorer"
                        )
                    }
                            
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "More Options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    val currentWsDir = remember { com.example.engine.fs.LocalFileManager.getWorkspaceDir().name }
                                    val isArtifactWs = remember(currentWsDir) {
                                        currentWsDir.startsWith("artifact_") || com.example.engine.fs.ArtifactWorkspaceManager.getArtifactIdForWorkspace(currentWsDir) != null
                                    }

                                    if (isArtifactWs) {
                                        DropdownMenuItem(
                                            text = { Text("Save to Artifact") },
                                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showMenu = false
                                                editorState.saveFile()
                                                scope.launch {
                                                    val result = com.example.engine.fs.ArtifactWorkspaceManager.saveWorkspaceToArtifact(context, currentWsDir)
                                                    if (result.isSuccess) {
                                                        Toast.makeText(context, "Saved changes back to Artifact!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to save: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Fork / Save as New Artifact") },
                                            leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                            onClick = {
                                                showMenu = false
                                                editorState.saveFile()
                                                showForkArtifactDialog = true
                                            }
                                        )
                                        HorizontalDivider()
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Save as Artifact (mini app)") },
                                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showMenu = false
                                                editorState.saveFile()
                                                scope.launch {
                                                    val result = com.example.engine.fs.ArtifactWorkspaceManager.saveCurrentChatAsArtifact(context, currentWsDir, workspaceName.value)
                                                    if (result.isSuccess) {
                                                        Toast.makeText(context, "Saved to Artifacts (mini apps)!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Save failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                        HorizontalDivider()
                                    }

                                    DropdownMenuItem(
                                        text = { Text("Toggle Line Wrap: ${if (editorState.isLineWrapEnabled) "ON" else "OFF"}") },
                                        onClick = { 
                                            editorState.isLineWrapEnabled = !editorState.isLineWrapEnabled
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Find & Replace") },
                                        onClick = { 
                                            showFindReplaceDialog = true
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Go to Line") },
                                        onClick = { 
                                            showGoToLineDialog = true
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Check Syntax Error") },
                                        onClick = { 
                                            showSyntaxCheckDialog = true
                                            showMenu = false
                                        }
                                    )

                                }
                            }
                        }
                    )
                    
                    if (showFindReplaceDialog) {
                        FindReplaceDialog(editorState = editorState, onDismiss = { showFindReplaceDialog = false })
                    }
                    if (showGoToLineDialog) {
                        GoToLineDialog(editorState = editorState, onDismiss = { showGoToLineDialog = false })
                    }
                    if (showSyntaxCheckDialog) {
                        SyntaxCheckDialog(editorState = editorState, onDismiss = { showSyntaxCheckDialog = false })
                    }
                    
                    if (showForkArtifactDialog) {
                        var forkTitle by remember { mutableStateOf("${workspaceName.value} (Fork)") }
                        val currentWs = com.example.engine.fs.LocalFileManager.getWorkspaceDir().name
                        AlertDialog(
                            onDismissRequest = { showForkArtifactDialog = false },
                            title = { Text("Fork to New Artifact") },
                            text = {
                                OutlinedTextField(
                                    value = forkTitle,
                                    onValueChange = { forkTitle = it },
                                    label = { Text("Forked Artifact Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (forkTitle.isNotBlank()) {
                                        scope.launch {
                                            val result = com.example.engine.fs.ArtifactWorkspaceManager.forkWorkspaceToNewArtifact(context, currentWs, forkTitle.trim())
                                            if (result.isSuccess) {
                                                val (newEntity, _) = result.getOrThrow()
                                                workspaceName.value = newEntity.title
                                                Toast.makeText(context, "Forked to new artifact '${newEntity.title}'!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Fork failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                            showForkArtifactDialog = false
                                        }
                                    }
                                }) {
                                    Text("Fork")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showForkArtifactDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showSaveAsPRDialog && selectedFile != null) {
                        var prTitle by remember { mutableStateOf("") }
                        var prDesc by remember { mutableStateOf("") }
                        val db = remember { AppDatabase.getDatabase(context) }
                        
                        AlertDialog(
                            onDismissRequest = { showSaveAsPRDialog = false },
                            title = { Text("Save as Pull Request") },
                            text = {
                                Column {
                                    Text("Propose your changes to ${selectedFile?.name}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = prTitle,
                                        onValueChange = { prTitle = it },
                                        label = { Text("PR Title") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = prDesc,
                                        onValueChange = { prDesc = it },
                                        label = { Text("Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch {
                                        val pr = WorkspacePullRequestEntity(
                                            id = UUID.randomUUID().toString(),
                                            workspaceId = com.example.engine.fs.LocalFileManager.getWorkspaceDir().name,
                                            title = prTitle,
                                            description = prDesc,
                                            targetFile = selectedFile!!.file.absolutePath,
                                            diff = editorState.content.text, // Simply storing current editor state as diff for now
                                            status = "open",
                                            createdAt = System.currentTimeMillis()
                                        )
                                        db.workspacePullRequestDao().savePullRequest(pr)
                                        showSaveAsPRDialog = false
                                        Toast.makeText(context, "Pull Request Created", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text("Submit PR")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSaveAsPRDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }



                    // Artifact Workspace Control Bar with Mode Switcher, Pass to AI, and Window Controls
                    if (selectedFile != null && !isMinimized) {
                        val currentFile = selectedFile.file
                        val name = currentFile.name.lowercase()
                        val isNonCodeArtifact = !isSourceCodeFile(name)

                        Surface(
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Mode Switcher: View vs Raw Edit
                                    if (isNonCodeArtifact) {
                                        SingleChoiceSegmentedButtonRow {
                                            SegmentedButton(
                                                selected = !isRawEditMode,
                                                onClick = { 
                                                    isRawEditMode = false
                                                    if (activeTabIndex in openTabs.indices) {
                                                        openTabs = openTabs.toMutableList().also {
                                                            it[activeTabIndex] = it[activeTabIndex].copy(isRawEditMode = false)
                                                        }
                                                    }
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                            ) {
                                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("View", style = MaterialTheme.typography.labelSmall)
                                            }
                                            SegmentedButton(
                                                selected = isRawEditMode,
                                                onClick = { 
                                                    isRawEditMode = true
                                                    if (activeTabIndex in openTabs.indices) {
                                                        openTabs = openTabs.toMutableList().also {
                                                            it[activeTabIndex] = it[activeTabIndex].copy(isRawEditMode = true)
                                                        }
                                                    }
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Raw", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .border(
                                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Code,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = getFileIconTint(currentFile.name)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Source",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // "Pass to AI" Action Button (Multimodal Staging)
                                    val basketRefs by WorkspaceChatBridge.stagedReferences.collectAsState()
                                    FilledTonalButton(
                                        onClick = {
                                            val promptBridge = "@artifact/${currentFile.name} Please examine or work on this artifact."
                                            WorkspaceChatBridge.stagePrompt(promptBridge)
                                            WorkspaceChatBridge.addStagedReference(
                                                StagedReference.GenericArtifact(
                                                    file = currentFile,
                                                    summary = "Full artifact ${currentFile.name}"
                                                )
                                            )
                                            Toast.makeText(context, "Staged '${currentFile.name}' to Basket (${WorkspaceChatBridge.getBasketCount()})", Toast.LENGTH_SHORT).show()
                                            onNavigateToChat()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (basketRefs.isNotEmpty()) "Stage (${basketRefs.size})" else "Stage",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Google AI Studio Contextual Comment Button
                                    FilledTonalIconButton(
                                        onClick = { showCommentSheetForFile = currentFile },
                                        modifier = Modifier.size(28.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AddComment,
                                            contentDescription = "Comment for AI",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Mini-OS Window Management: Minimize & Close controls
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { isMinimized = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Minimize File",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    IconButton(
                                        onClick = { closeTab(activeTabIndex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close File",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Viewport: Either active file viewer or full-screen File Explorer
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (selectedFile != null && !isMinimized) {
                            val currentFile = selectedFile.file
                            val name = selectedFile.name.lowercase()

                            if (isRawEditMode) {
                                TextViewer(currentFile, editorState)
                            } else {
                                when (activeViewerOverride) {
                                    ViewerType.CODE -> TextViewer(currentFile, editorState)
                                    ViewerType.IMAGE -> ImageViewer(currentFile, onPassSelectionToChat = { onNavigateToChat() })
                                    ViewerType.VIDEO -> VideoViewer(currentFile)
                                    ViewerType.AUDIO -> AudioViewer(currentFile)
                                    ViewerType.PPT -> PptViewer(currentFile)
                                    ViewerType.DOCUMENT -> PdfViewer(currentFile)
                                    ViewerType.WEBVIEW_STAGE -> StageWebViewer(currentFile)
                                    ViewerType.AUTO -> {
                                        when {
                                            name.endsWith(".pdf") -> PdfViewer(currentFile)
                                            name.endsWith(".ppt") || name.endsWith(".pptx") || name.endsWith(".slides.md") -> PptViewer(currentFile)
                                            name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> {
                                                ImageViewer(currentFile, onPassSelectionToChat = { onNavigateToChat() })
                                            }
                                            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".mov") -> {
                                                VideoViewer(currentFile)
                                            }
                                            name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg") || name.endsWith(".flac") -> {
                                                AudioViewer(currentFile)
                                            }
                                            name.endsWith(".gltf") || name.endsWith(".glb") || name.endsWith(".stl") || name.endsWith(".obj") ||
                                            name.endsWith(".geojson") || name.endsWith(".kml") -> {
                                                StageWebViewer(currentFile)
                                            }
                                            else -> TextViewer(currentFile, editorState)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Full-screen file explorer (no cramped side panel)
                            FileExplorer(
                                modifier = Modifier.fillMaxSize(),
                                onFileClick = { fileNode ->
                                    openOrFocusTab(fileNode)
                                },
                                onFileOpenWith = { fileNode, viewer ->
                                    openOrFocusTab(fileNode, viewerOverride = viewer, rawMode = (viewer == ViewerType.CODE))
                                },
                                onCloseClick = { isMinimized = false },
                                onNavigateToChat = onNavigateToChat
                            )
                        }
                    }

                    // Bottom File Tabs Strip (Google AI Studio IDE tabs style)
                    if (openTabs.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 1.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(openTabs) { idx, tab ->
                                    val isActive = (idx == activeTabIndex && !isMinimized)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (idx == activeTabIndex) {
                                                    isMinimized = !isMinimized
                                                } else {
                                                    activeTabIndex = idx
                                                    isMinimized = false
                                                    scope.launch {
                                                        editorState.loadFile(tab.fileNode.file)
                                                    }
                                                }
                                            }
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = getFileIcon(tab.fileNode.name),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = getFileIconTint(tab.fileNode.name)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = tab.fileNode.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    modifier = Modifier.widthIn(max = 120.dp)
                                                )
                                                if (idx == activeTabIndex && isMinimized) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "• min",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                // Close tab button
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .clickable { closeTab(idx) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Close tab",
                                                        modifier = Modifier.size(12.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }

                                // '+' button to return to file explorer
                                item {
                                    IconButton(
                                        onClick = { isMinimized = true },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Browse Files",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        if (showCommentSheetForFile != null) {
            ArtifactCommentBottomSheet(
                reference = StagedReference.GenericArtifact(
                    file = showCommentSheetForFile!!,
                    summary = "Artifact ${showCommentSheetForFile!!.name}"
                ),
                onDismiss = { showCommentSheetForFile = null },
                onNavigateToChat = onNavigateToChat
            )
        }
    }
}

private fun getFileIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".sh") -> Icons.Default.Code
        lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml") || lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".yaml") || lower.endsWith(".yml") -> Icons.Default.DataObject
        lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") -> Icons.Default.Image
        lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".md") -> Icons.Default.Description
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".mov") -> Icons.Default.VideoFile
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".flac") -> Icons.Default.AudioFile
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileIconTint(name: String): Color {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".kt") || lower.endsWith(".java") -> Color(0xFF9C27B0) // Kotlin purple
        lower.endsWith(".py") -> Color(0xFF1976D2) // Python blue
        lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx") -> Color(0xFFFBC02D) // JS/TS yellow
        lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml") || lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".yaml") || lower.endsWith(".yml") -> Color(0xFFE65100) // Config orange
        lower.endsWith(".md") || lower.endsWith(".txt") -> Color(0xFF00897B) // Docs teal
        lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") -> Color(0xFFE91E63) // Image pink
        lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mp3") || lower.endsWith(".wav") -> Color(0xFF00ACC1) // Media cyan
        lower.endsWith(".pdf") -> Color(0xFFD32F2F) // PDF red
        else -> Color(0xFF78909C) // Default slate
    }
}

@Composable
fun FileRevertDialog(file: File, onDismiss: () -> Unit, onRevert: (File) -> Unit) {
    val revisions = remember(file) { FileHistoryEngine.getRevisions(file) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File History & Revert") },
        text = {
            if (revisions.isEmpty()) {
                Text("No history available for this file.")
            } else {
                LazyColumn {
                    items(revisions) { revision ->
                        ListItem(
                            headlineContent = { Text(revision.name) },
                            supportingContent = { Text("Size: ${revision.length()} bytes") },
                            trailingContent = {
                                TextButton(onClick = { onRevert(revision) }) {
                                    Text("Revert")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun isSourceCodeFile(name: String): Boolean {
    return name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".js") ||
            name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".jsx") ||
            name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".json") ||
            name.endsWith(".xml") || name.endsWith(".py") || name.endsWith(".sh") ||
            name.endsWith(".sql") || name.endsWith(".gradle") || name.endsWith(".kts") ||
            name.endsWith(".toml") || name.endsWith(".yaml") || name.endsWith(".yml") ||
            name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h")
}

