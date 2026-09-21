package com.example.ui.artifacts

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.db.AppDatabase
import com.example.engine.db.ArtifactEntity
import com.example.engine.fs.ArtifactWorkspaceManager
import com.example.engine.fs.LocalFileManager
import com.example.engine.pwa.PwaZipImporter
import com.example.utils.LogKeeper
import com.example.utils.LogKeeperCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class VoiceNoteCard(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var body: String,
    var colorHex: String = "#FFF9C4", // Soft Yellow
    var isPinned: Boolean = false,
    var checklist: List<ChecklistItem> = emptyList(),
    var audioTimestamp: String? = null
)

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var isDone: Boolean = false
)

val NOTE_COLORS = listOf(
    "#FFF9C4" to "Sun Yellow",
    "#C8E6C9" to "Mint Green",
    "#E1BEE7" to "Lavender",
    "#FFCCBC" to "Coral Peach",
    "#BBDEFB" to "Sky Blue",
    "#CFD8DC" to "Cool Slate"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactsScreen(
    onNavigateBack: () -> Unit,
    onEditInChatCode: ((ArtifactEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember(context) { AppDatabase.getDatabase(context) }
    val artifactDao = db.artifactDao()

    val artifactsList by artifactDao.getAllArtifactsFlow().collectAsState(initial = emptyList())
    var selectedArtifact by remember { mutableStateOf<ArtifactEntity?>(null) }
    var settingsArtifact by remember { mutableStateOf<ArtifactEntity?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val isMultiSelectMode = selectedIds.isNotEmpty()
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }

    var showCreationOptionsSheet by remember { mutableStateOf(false) }
    var showThroughChatDialog by remember { mutableStateOf(false) }
    var showNewNotesDialog by remember { mutableStateOf(false) }
    var isImportingZip by remember { mutableStateOf(false) }
    var importProgressText by remember { mutableStateOf("Processing archive...") }

    // Launcher for selecting dist.zip / web app archives
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImportingZip = true
                importProgressText = "Extracting & validating PWA distribution archive..."
                val result = PwaZipImporter.importZip(context, uri)
                isImportingZip = false
                if (result.isSuccess) {
                    val entity = result.getOrThrow()
                    Toast.makeText(context, "Successfully imported PWA: ${entity.title}!", Toast.LENGTH_LONG).show()
                    selectedArtifact = entity
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Import failed"
                    Toast.makeText(context, "ZIP Import Error: $err", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Seed default persistent voice notes if empty
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val existing = artifactDao.getAllArtifacts()
            if (existing.none { it.id == "system_default_notes" }) {
                val initialNotes = listOf(
                    VoiceNoteCard(
                        id = "note_1",
                        title = "🎙️ Quick Voice Memos",
                        body = "Tap the mic shortcut or widget button anytime to record voice thoughts. Transcriptions will automatically cluster here.",
                        colorHex = "#FFF9C4",
                        isPinned = true
                    ),
                    VoiceNoteCard(
                        id = "note_2",
                        title = "✨ Sprint Action Items",
                        body = "Key milestones and workspace items to finish:",
                        colorHex = "#C8E6C9",
                        isPinned = false,
                        checklist = listOf(
                            ChecklistItem("item_1", "Add OpenAI & Anthropic custom API keys", true),
                            ChecklistItem("item_2", "Sync offline chat threads", true),
                            ChecklistItem("item_3", "Try home screen Quick Chat widget", false)
                        )
                    ),
                    VoiceNoteCard(
                        id = "note_3",
                        title = "💡 Idea: OmniRoot Autonomous Mode",
                        body = "Explore self-correcting tool loops with terminal sandbox in background sync.",
                        colorHex = "#E1BEE7",
                        isPinned = false
                    )
                )
                val jsonNotes = serializeNotes(initialNotes)
                val defaultEntity = ArtifactEntity(
                    id = "system_default_notes",
                    title = "My Voice & Color Notes",
                    type = "COLOR_NOTES",
                    content = jsonNotes,
                    isPinned = true,
                    updatedAt = System.currentTimeMillis()
                )
                artifactDao.insertArtifact(defaultEntity)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedIds.size == artifactsList.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(artifactsList.map { it.id })
                            }
                        }) {
                            Icon(
                                if (selectedIds.size == artifactsList.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = "Select All / Deselect All"
                            )
                        }
                        IconButton(onClick = { showBatchDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Artifacts (mini apps)", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = { showCreationOptionsSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New / Import", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                ExtendedFloatingActionButton(
                    onClick = { showCreationOptionsSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New / Import") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (artifactsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artifactsList, key = { it.id }) { artifact ->
                        val isSelected = selectedIds.contains(artifact.id)
                        ArtifactListItemCard(
                            artifact = artifact,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedIds.remove(artifact.id)
                                } else {
                                    selectedIds.add(artifact.id)
                                }
                            },
                            onClick = {
                                if (isMultiSelectMode) {
                                    if (isSelected) selectedIds.remove(artifact.id) else selectedIds.add(artifact.id)
                                } else {
                                    selectedArtifact = artifact
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    selectedIds.add(artifact.id)
                                }
                            },
                            onOpenSettings = {
                                settingsArtifact = artifact
                            },
                            onEditInChatCode = {
                                if (onEditInChatCode != null) {
                                    onEditInChatCode(artifact)
                                } else {
                                    scope.launch {
                                        com.example.engine.fs.ArtifactWorkspaceManager.openArtifactInWorkspace(context, artifact)
                                        Toast.makeText(context, "Loaded into workspace. Switch to Chat/Code tabs to edit with AI.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    artifactDao.deleteArtifact(artifact)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Full Screen Dedicated Settings Page for this Artifact
        settingsArtifact?.let { targetArtifact ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { settingsArtifact = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                ArtifactSettingsScreen(
                    artifact = targetArtifact,
                    onBack = { settingsArtifact = null },
                    onArtifactUpdated = { updated ->
                        settingsArtifact = updated
                        if (selectedArtifact?.id == updated.id) {
                            selectedArtifact = updated
                        }
                    },
                    onArtifactDeleted = {
                        settingsArtifact = null
                        if (selectedArtifact?.id == targetArtifact.id) {
                            selectedArtifact = null
                        }
                    }
                )
            }
        }

        // Batch Delete Confirmation Dialog
        if (showBatchDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirmDialog = false },
                icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete ${selectedIds.size} Artifacts?") },
                text = {
                    Text("This will permanently delete the selected artifacts, their workspace repositories, and associated cache.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val toDelete = selectedIds.toList()
                            showBatchDeleteConfirmDialog = false
                            selectedIds.clear()
                            scope.launch(Dispatchers.IO) {
                                artifactDao.deleteByIds(toDelete)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Deleted ${toDelete.size} artifacts", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Full Screen Opened Artifact Viewer with Top Bar Drag-Down/Close Header (No Swipe Interference)
        selectedArtifact?.let { current ->
            OpenedArtifactViewerDialog(
                artifact = current,
                onDismiss = { selectedArtifact = null },
                onOpenSettings = {
                    settingsArtifact = current
                },
                onEditInChatCode = {
                    val target = selectedArtifact ?: current
                    selectedArtifact = null
                    if (onEditInChatCode != null) {
                        onEditInChatCode(target)
                    } else {
                        scope.launch {
                            com.example.engine.fs.ArtifactWorkspaceManager.openArtifactInWorkspace(context, target)
                            Toast.makeText(context, "Loaded into workspace. Switch to Chat/Code tabs to edit with AI.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSave = { updatedEntity ->
                    scope.launch(Dispatchers.IO) {
                        artifactDao.updateArtifact(updatedEntity)
                    }
                    selectedArtifact = updatedEntity
                }
            )
        }

        // ZIP Import In-Progress Dialog
        if (isImportingZip) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Importing PWA Archive...") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = importProgressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {}
            )
        }

        // New / Import Options Bottom Sheet
        if (showCreationOptionsSheet) {
            ArtifactCreationOptionsSheet(
                onDismiss = { showCreationOptionsSheet = false },
                onChooseChat = {
                    showThroughChatDialog = true
                },
                onChooseZip = {
                    zipPickerLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onChooseNotes = {
                    showNewNotesDialog = true
                }
            )
        }

        // Through Chat Workspace Packaging Dialog
        if (showThroughChatDialog) {
            val workspaces = remember {
                val list = LocalFileManager.getWorkspaces().map { it.name }
                if (list.isEmpty()) listOf("default") else list
            }
            val currentWs = remember {
                LocalFileManager.getWorkspaceDir().name
            }

            PackageChatAsArtifactDialog(
                workspaces = workspaces,
                currentWorkspace = currentWs,
                onDismiss = { showThroughChatDialog = false },
                onPackage = { selectedWs, title ->
                    scope.launch {
                        showThroughChatDialog = false
                        val res = ArtifactWorkspaceManager.saveCurrentChatAsArtifact(context, selectedWs, title)
                        if (res.isSuccess) {
                            val entity = res.getOrThrow()
                            Toast.makeText(context, "Packaged '${entity.title}' as Artifact!", Toast.LENGTH_LONG).show()
                            selectedArtifact = entity
                        } else {
                            Toast.makeText(context, "Packaging failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Create New Voice & Color Notes Dialog
        if (showNewNotesDialog) {
            var notesTitle by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showNewNotesDialog = false },
                title = { Text("New Voice & Color Notes") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Create a scratchpad for voice recordings, checklists, and color-coded ideas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = notesTitle,
                            onValueChange = { notesTitle = it },
                            label = { Text("Notes Title") },
                            placeholder = { Text("e.g. Project Sprint Memos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (notesTitle.isNotBlank()) {
                            val newId = UUID.randomUUID().toString()
                            val initialContent = serializeNotes(listOf(VoiceNoteCard(title = "First Note", body = "")))
                            val newEntity = ArtifactEntity(
                                id = newId,
                                title = notesTitle.trim(),
                                type = "COLOR_NOTES",
                                content = initialContent,
                                updatedAt = System.currentTimeMillis()
                            )
                            scope.launch(Dispatchers.IO) {
                                artifactDao.insertArtifact(newEntity)
                            }
                            showNewNotesDialog = false
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewNotesDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtifactListItemCard(
    artifact: ArtifactEntity,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditInChatCode: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dateStr = remember(artifact.updatedAt) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(artifact.updatedAt))
    }

    val iconBitmap = remember(artifact.iconUri) {
        if (!artifact.iconUri.isNullOrBlank()) {
            val f = File(artifact.iconUri)
            if (f.exists() && f.isFile) {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            } else null
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        when (artifact.type) {
                            "COLOR_NOTES" -> Color(0xFFFEF3C7)
                            "PWA" -> Color(0xFFEDE9FE)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = artifact.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = when (artifact.type) {
                            "COLOR_NOTES" -> Icons.Default.Mic
                            "PWA" -> Icons.Default.Web
                            else -> Icons.Default.Code
                        },
                        contentDescription = null,
                        tint = when (artifact.type) {
                            "COLOR_NOTES" -> Color(0xFFD97706)
                            "PWA" -> Color(0xFF7C3AED)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (artifact.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (artifact.type) {
                            "PWA" -> Color(0xFF10B981).copy(alpha = 0.15f)
                            "COLOR_NOTES" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = if (artifact.type == "COLOR_NOTES") "NOTES" else artifact.type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (artifact.type) {
                                "PWA" -> Color(0xFF047857)
                                "COLOR_NOTES" -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "· $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isMultiSelectMode) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEditInChatCode) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Edit with AI in Chat & Code",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Top Bar Drag-Down & Close Header Artifact Viewer.
 * Replaces swipe-down modal sheets so that internal note/html scrolling is never cut off or accidentally dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenedArtifactViewerDialog(
    artifact: ArtifactEntity,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onEditInChatCode: () -> Unit,
    onSave: (ArtifactEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notesList by remember(artifact.id, artifact.content) {
        mutableStateOf(if (artifact.type == "COLOR_NOTES") parseNotes(artifact.content) else emptyList())
    }
    var htmlContent by remember(artifact.id, artifact.content) {
        mutableStateOf(artifact.content)
    }
    var engineMode by remember(artifact.id) {
        mutableStateOf(if (artifact.type == "PWA") "BACKUP" else "PRIMARY")
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Area (Title, Edit with AI, Refresh, Close)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = artifact.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (artifact.type != "COLOR_NOTES") {
                                FilterChip(
                                    selected = engineMode == "BACKUP",
                                    onClick = {
                                        engineMode = if (engineMode == "PRIMARY") "BACKUP" else "PRIMARY"
                                        LogKeeper.log("INFO", "ArtifactViewer", "User toggled engine mode to $engineMode for '${artifact.title}'")
                                    },
                                    label = {
                                        Text(
                                            if (engineMode == "PRIMARY") "Primary" else "Backup",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (engineMode == "PRIMARY") Icons.Default.Language else Icons.Default.OfflinePin,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }

                            Button(
                                onClick = onEditInChatCode,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit with AI", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val currentWs = artifact.workspaceId ?: "artifact_${artifact.id}"
                                        val res = com.example.engine.fs.ArtifactWorkspaceManager.forkWorkspaceToNewArtifact(
                                            context,
                                            currentWs,
                                            "${artifact.title} (Fork)"
                                        )
                                        if (res.isSuccess) {
                                            val (newEntity, newWsId) = res.getOrThrow()
                                            Toast.makeText(context, "Blind-forked to $newWsId! Part 2 secrets preserved.", Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Fork failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fork", fontSize = 12.sp)
                            }

                            if (artifact.type == "COLOR_NOTES") {
                                Button(
                                    onClick = {
                                        val newCard = VoiceNoteCard(title = "New Voice Note", body = "")
                                        val updated = notesList + newCard
                                        notesList = updated
                                        onSave(artifact.copy(content = serializeNotes(updated), updatedAt = System.currentTimeMillis()))
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Note")
                                }
                            }

                            if (artifact.type != "COLOR_NOTES") {
                                IconButton(onClick = {
                                    val targetFile = java.io.File(context.filesDir, "artifacts/${artifact.id}/repo/index.html")
                                    val filePath = if (targetFile.exists()) targetFile.absolutePath else ""
                                    StandaloneArtifactActivity.launch(
                                        context = context,
                                        title = artifact.title,
                                        filePath = filePath,
                                        artifactId = artifact.id,
                                        workspaceId = artifact.workspaceId ?: "artifact_${artifact.id}"
                                    )
                                }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Pop Out into Standalone Task", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "App Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(onClick = {
                                if (artifact.type == "COLOR_NOTES") {
                                    notesList = parseNotes(artifact.content)
                                } else {
                                    htmlContent = artifact.content
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                // Main Content View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (artifact.type == "COLOR_NOTES") {
                        ColorNotesContentView(
                            notes = notesList,
                            onUpdateNotes = { updated ->
                                notesList = updated
                                onSave(artifact.copy(content = serializeNotes(updated), updatedAt = System.currentTimeMillis()))
                            }
                        )
                    } else {
                        if (engineMode == "BACKUP") {
                            PwaBackupPlayerView(
                                artifact = artifact,
                                onSwitchToPrimary = { engineMode = "PRIMARY" },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            HtmlArtifactContentView(
                                html = htmlContent,
                                artifactId = artifact.id,
                                title = artifact.title,
                                workspaceId = artifact.workspaceId ?: "artifact_${artifact.id}",
                                onHtmlChange = { newHtml ->
                                    htmlContent = newHtml
                                    onSave(artifact.copy(content = newHtml, updatedAt = System.currentTimeMillis()))
                                },
                                onFallbackToBackup = {
                                    engineMode = "BACKUP"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorNotesContentView(
    notes: List<VoiceNoteCard>,
    onUpdateNotes: (List<VoiceNoteCard>) -> Unit
) {
    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes yet. Tap '+ Add Note' to create one.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                VoiceNoteCardItem(
                    note = note,
                    onUpdate = { updatedNote ->
                        val updatedList = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                        onUpdateNotes(updatedList)
                    },
                    onDelete = {
                        val updatedList = notes.filter { it.id != note.id }
                        onUpdateNotes(updatedList)
                    }
                )
            }
        }
    }
}

@Composable
fun VoiceNoteCardItem(
    note: VoiceNoteCard,
    onUpdate: (VoiceNoteCard) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(note.title) { mutableStateOf(note.title) }
    var body by remember(note.body) { mutableStateOf(note.body) }
    val cardColor = remember(note.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex))
        } catch (e: Exception) {
            Color(0xFFFFF9C4)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Note Title & Palette Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicNoteTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onUpdate(note.copy(title = it))
                    },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Color picker dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NOTE_COLORS.take(4).forEach { (hex, _) ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .clickable { onUpdate(note.copy(colorHex = hex)) }
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            BasicNoteTextField(
                value = body,
                onValueChange = {
                    body = it
                    onUpdate(note.copy(body = it))
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth()
            )

            // Checklist Items
            if (note.checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(6.dp))
                note.checklist.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isDone,
                            onCheckedChange = { checked ->
                                val updatedChecklist = note.checklist.toMutableList()
                                updatedChecklist[index] = item.copy(isDone = checked)
                                onUpdate(note.copy(checklist = updatedChecklist))
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Black,
                                uncheckedColor = Color.Black.copy(alpha = 0.6f),
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (item.isDone) Color.Black.copy(alpha = 0.4f) else Color.Black
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BasicNoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        modifier = modifier
    )
}

@Composable
fun HtmlArtifactContentView(
    html: String,
    artifactId: String? = null,
    title: String = "Artifact",
    workspaceId: String? = null,
    onHtmlChange: (String) -> Unit,
    onFallbackToBackup: (() -> Unit)? = null
) {
    var loadError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidViewWebView(
            html = html,
            artifactId = artifactId,
            title = title,
            workspaceId = workspaceId,
            onError = { err ->
                loadError = err
            },
            modifier = Modifier.fillMaxSize()
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = loadError != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Loader issue encountered. Try Backup Engine?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            loadError = null
                            onFallbackToBackup?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Switch to Backup", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AndroidViewWebView(
    html: String,
    artifactId: String? = null,
    title: String = "Artifact",
    workspaceId: String? = null,
    onError: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = true

                // Connect to LogKeeper via LogKeeperCatcher
                LogKeeperCatcher.attachToWebView(this, "Artifact_$title")

                val activeWs = workspaceId ?: com.example.engine.fs.LocalFileManager.getWorkspaceDir().name
                val secretsJson = com.example.engine.settings.ThreadSecretsStore.getSecretsJson(ctx, activeWs)
                val secretsCount = com.example.engine.settings.ThreadSecretsStore.getSecrets(ctx, activeWs).size

                val injectScript = """
                    (function() {
                        try {
                            window.__SECRETS__ = Object.freeze($secretsJson);
                            window.dispatchEvent(new CustomEvent('secretsready', { detail: { count: $secretsCount } }));
                        } catch(e) {
                            console.error('Omnivian secrets injection error:', e);
                        }
                    })();
                """.trimIndent()

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.evaluateJavascript(injectScript, null)
                    }

                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(injectScript, null)
                        LogKeeper.log("INFO", "ArtifactViewer", "Loaded '$title' with $secretsCount injected secrets")
                    }

                    override fun onReceivedError(view: android.webkit.WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        val err = "Primary loader error ($errorCode): $description at $failingUrl"
                        LogKeeper.log("ERROR", "ArtifactViewer", err)
                        onError?.invoke(err)
                    }
                }
            }
        },
        update = { webView ->
            val repoDir = if (artifactId != null) File(webView.context.filesDir, "artifacts/$artifactId/repo") else null
            val baseUrl = if (repoDir != null && repoDir.exists()) "file://${repoDir.absolutePath}/" else null
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactCreationOptionsSheet(
    onDismiss: () -> Unit,
    onChooseChat: () -> Unit,
    onChooseZip: () -> Unit,
    onChooseNotes: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Create or Import Artifact",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Package your chat workspaces into standalone mini apps or import compiled PWA distribution archives.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Option 1: Through Chat
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onChooseChat()
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Through Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Package current chat workspace files, UI, and encrypted secrets into a persistent mini app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Option 2: Import ZIP (dist.zip)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onChooseZip()
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Import ZIP (dist.zip)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    "PWA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Import a production PWA or web build zip archive with manifest auto-detection & offline loader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Option 3: Voice & Color Notes
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onChooseNotes()
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice & Color Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Create quick voice memos, checklists, and color-coded idea cards",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PackageChatAsArtifactDialog(
    workspaces: List<String>,
    currentWorkspace: String,
    onDismiss: () -> Unit,
    onPackage: (workspaceId: String, title: String) -> Unit
) {
    var selectedWs by remember { mutableStateOf(currentWorkspace) }
    var title by remember { mutableStateOf("Mini App (${selectedWs.replace("workspace_", "")})") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Package Chat as Artifact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Turn any active chat workspace into an isolated 2-part artifact with blind credential separation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Artifact Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Select Chat Workspace Source:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    workspaces.forEach { ws ->
                        val isSelected = ws == selectedWs
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedWs = ws
                                    title = "Mini App (${ws.replace("workspace_", "")})"
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ws,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onPackage(selectedWs, title.trim())
                    }
                }
            ) {
                Text("Package & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helpers to serialize and deserialize voice/color notes to JSON
fun serializeNotes(notes: List<VoiceNoteCard>): String {
    val arr = JSONArray()
    for (n in notes) {
        val obj = JSONObject().apply {
            put("id", n.id)
            put("title", n.title)
            put("body", n.body)
            put("colorHex", n.colorHex)
            put("isPinned", n.isPinned)
            val checkArr = JSONArray()
            for (c in n.checklist) {
                checkArr.put(JSONObject().apply {
                    put("id", c.id)
                    put("text", c.text)
                    put("isDone", c.isDone)
                })
            }
            put("checklist", checkArr)
        }
        arr.put(obj)
    }
    return arr.toString()
}

fun parseNotes(jsonStr: String): List<VoiceNoteCard> {
    val list = mutableListOf<VoiceNoteCard>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val checkList = mutableListOf<ChecklistItem>()
            val checkArr = obj.optJSONArray("checklist")
            if (checkArr != null) {
                for (j in 0 until checkArr.length()) {
                    val cObj = checkArr.getJSONObject(j)
                    checkList.add(
                        ChecklistItem(
                            id = cObj.optString("id", UUID.randomUUID().toString()),
                            text = cObj.optString("text", ""),
                            isDone = cObj.optBoolean("isDone", false)
                        )
                    )
                }
            }
            list.add(
                VoiceNoteCard(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "Voice Note"),
                    body = obj.optString("body", ""),
                    colorHex = obj.optString("colorHex", "#FFF9C4"),
                    isPinned = obj.optBoolean("isPinned", false),
                    checklist = checkList
                )
            )
        }
    } catch (e: Exception) {
        // Return default single note if parsing fails
        list.add(VoiceNoteCard(title = "Voice Memo", body = jsonStr))
    }
    return list
}
