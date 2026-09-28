package com.example.ui.brain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.brain.MemoryRepository
import com.example.engine.db.MemoryCategory
import com.example.engine.db.MemoryEntity
import com.example.utils.LogKeeper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mini-Phase 11.7: Brain & Memory Management Screen.
 *
 * Full-fidelity cognitive memory console:
 * - Search bar with real-time text matching.
 * - Category filter chips: All, Rules, Preferences, Decisions, Snippets.
 * - Add, Edit, Pin/Unpin, and Delete dialogs.
 * - Zero PII LogKeeper audit logging on every user modification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagementScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MemoryRepository.getInstance(context) }

    val allMemories by repo.getAllMemories().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<MemoryEntity?>(null) }
    var memoryToDelete by remember { mutableStateOf<MemoryEntity?>(null) }

    // Filtered memories
    val filteredMemories = remember(allMemories, searchQuery, selectedCategoryFilter) {
        allMemories.filter { mem ->
            val matchesCategory = when (selectedCategoryFilter) {
                "ALL" -> true
                MemoryCategory.PROJECT_RULE -> mem.category == MemoryCategory.PROJECT_RULE
                MemoryCategory.USER_PREFERENCE -> mem.category == MemoryCategory.USER_PREFERENCE
                MemoryCategory.ARCHITECTURAL_DECISION -> mem.category == MemoryCategory.ARCHITECTURAL_DECISION
                MemoryCategory.SNIPPET -> mem.category == MemoryCategory.SNIPPET
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                mem.key.lowercase().contains(q) ||
                        mem.content.lowercase().contains(q) ||
                        mem.category.lowercase().contains(q)
            }

            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Brain & Memory Store",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${allMemories.size} persistent memories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("memory_screen_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Seed defaults if empty
                    if (allMemories.isEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    seedDefaultMemories(repo)
                                    Toast.makeText(context, "Seeded default memories", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Seed Defaults")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_memory")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_memory_input"),
                placeholder = { Text("Search by key, rule or keywords...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All",
                    MemoryCategory.PROJECT_RULE to "Rules",
                    MemoryCategory.USER_PREFERENCE to "Preferences",
                    MemoryCategory.ARCHITECTURAL_DECISION to "Decisions",
                    MemoryCategory.SNIPPET to "Snippets"
                )

                items(filters) { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    val count = remember(allMemories, key) {
                        if (key == "ALL") allMemories.size
                        else allMemories.count { it.category == key }
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text("$label ($count)") },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("filter_chip_$key")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Memory Cards List
            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No memories in this category yet" else "No matching memories found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Memories persist across chats and guide the model's architectural reasoning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Memory")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredMemories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            onTogglePin = {
                                scope.launch {
                                    repo.setPinned(memory.id, !memory.isPinned)
                                    LogKeeper.log("MemoryUI", "UserPin", "User toggled pin=${!memory.isPinned} on memory id=${memory.id}")
                                }
                            },
                            onEdit = { memoryToEdit = memory },
                            onDelete = { memoryToDelete = memory }
                        )
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        MemoryEditDialog(
            title = "Add Cognitive Memory",
            initialMemory = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { key, content, category, isPinned, relevanceScore ->
                scope.launch {
                    val id = repo.saveMemory(
                        key = key,
                        content = content,
                        category = category,
                        isPinned = isPinned,
                        relevanceScore = relevanceScore
                    )
                    LogKeeper.log("MemoryUI", "UserCreate", "User manually added memory id=$id key=$key")
                    showAddDialog = false
                    Toast.makeText(context, "Memory saved", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit Memory Dialog (Mandated LogKeeper Integration)
    memoryToEdit?.let { memory ->
        MemoryEditDialog(
            title = "Edit Memory",
            initialMemory = memory,
            onDismiss = { memoryToEdit = null },
            onConfirm = { key, content, category, isPinned, relevanceScore ->
                scope.launch {
                    val updated = memory.copy(
                        key = key,
                        content = content,
                        category = category,
                        isPinned = isPinned,
                        relevanceScore = relevanceScore,
                        lastAccessedAt = System.currentTimeMillis()
                    )
                    repo.updateMemory(updated)
                    // Mandated entry: LogKeeper.log("MemoryUI", "UserEdit", "User manually modified memory id=X")
                    LogKeeper.log("MemoryUI", "UserEdit", "User manually modified memory id=${memory.id}")
                    memoryToEdit = null
                    Toast.makeText(context, "Memory updated", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete Confirmation Dialog
    memoryToDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Memory") },
            text = { Text("Are you sure you want to permanently delete \"${memory.key}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteMemory(memory.id)
                            LogKeeper.log("MemoryUI", "UserDelete", "User manually deleted memory id=${memory.id}")
                            memoryToDelete = null
                            Toast.makeText(context, "Memory deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryEntity,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val categoryColor = when (memory.category) {
        MemoryCategory.PROJECT_RULE -> MaterialTheme.colorScheme.error
        MemoryCategory.ARCHITECTURAL_DECISION -> MaterialTheme.colorScheme.tertiary
        MemoryCategory.USER_PREFERENCE -> MaterialTheme.colorScheme.primary
        MemoryCategory.SNIPPET -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    val categoryLabel = when (memory.category) {
        MemoryCategory.PROJECT_RULE -> "RULE"
        MemoryCategory.ARCHITECTURAL_DECISION -> "DECISION"
        MemoryCategory.USER_PREFERENCE -> "PREFERENCE"
        MemoryCategory.SNIPPET -> "SNIPPET"
        else -> memory.category
    }

    val formattedDate = remember(memory.lastAccessedAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(memory.lastAccessedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("memory_card_${memory.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (memory.isPinned)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Card Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = categoryColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Score Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Score: ${String.format("%.1f", memory.relevanceScore)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Pin toggle button
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (memory.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                        contentDescription = "Pin memory",
                        tint = if (memory.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Key / Title
            Text(
                text = memory.key,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            // Content Body
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Footer metadata & action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accessed $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.weight(1f))

                // Copy Content
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Memory Content", memory.content))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }

                // Edit Button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditDialog(
    title: String,
    initialMemory: MemoryEntity?,
    onDismiss: () -> Unit,
    onConfirm: (key: String, content: String, category: String, isPinned: Boolean, relevanceScore: Float) -> Unit
) {
    var keyText by remember { mutableStateOf(initialMemory?.key ?: "") }
    var contentText by remember { mutableStateOf(initialMemory?.content ?: "") }
    var selectedCategory by remember { mutableStateOf(initialMemory?.category ?: MemoryCategory.PROJECT_RULE) }
    var isPinned by remember { mutableStateOf(initialMemory?.isPinned ?: false) }
    var relevanceScore by remember { mutableStateOf(initialMemory?.relevanceScore ?: 1.0f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(14.dp))

                // Key / Title
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Memory Key / Rule Name") },
                    placeholder = { Text("e.g. Kotlin_Compose_Rules, Theme_Preference") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_memory_key_input")
                )

                Spacer(Modifier.height(12.dp))

                // Content
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("Memory Content / Instruction") },
                    placeholder = { Text("State the exact rule, constraint, architectural pattern, or code snippet...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_memory_content_input")
                )

                Spacer(Modifier.height(12.dp))

                // Category selector
                Text("Category", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        MemoryCategory.PROJECT_RULE to "Rule",
                        MemoryCategory.USER_PREFERENCE to "Pref",
                        MemoryCategory.ARCHITECTURAL_DECISION to "Decision",
                        MemoryCategory.SNIPPET to "Snippet"
                    )
                    categories.forEach { (cat, label) ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Relevance Score Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Relevance Score: ${String.format("%.1f", relevanceScore)}", style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = relevanceScore,
                    onValueChange = { relevanceScore = it },
                    valueRange = 0.5f..2.5f,
                    steps = 7,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Pin switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPinned = !isPinned }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("High Architectural Priority (Pinned)", style = MaterialTheme.typography.bodyMedium)
                        Text("Pinned memories receive +2.5 score bonus and inject first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (keyText.isNotBlank() && contentText.isNotBlank()) {
                                onConfirm(keyText.trim(), contentText.trim(), selectedCategory, isPinned, relevanceScore)
                            }
                        },
                        enabled = keyText.isNotBlank() && contentText.isNotBlank(),
                        modifier = Modifier.testTag("dialog_memory_save_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private suspend fun seedDefaultMemories(repo: MemoryRepository) {
    repo.saveMemory(
        key = "M3_Design_Guidelines",
        content = "Strictly adhere to Material Design 3 spacing and color guidelines. Ensure 48dp minimum touch targets and cohesive surface containers.",
        category = MemoryCategory.PROJECT_RULE,
        isPinned = true,
        relevanceScore = 2.0f
    )
    repo.saveMemory(
        key = "LogKeeper_Security_Standard",
        content = "Every state transition and failure must be recorded via LogKeeper. Zero credentials or PII may ever be logged.",
        category = MemoryCategory.PROJECT_RULE,
        isPinned = true,
        relevanceScore = 2.0f
    )
    repo.saveMemory(
        key = "Kotlin_Coroutines_Concurreny",
        content = "Perform all heavy I/O operations inside withContext(Dispatchers.IO) and expose reactive state via StateFlow or SharedFlow.",
        category = MemoryCategory.ARCHITECTURAL_DECISION,
        isPinned = false,
        relevanceScore = 1.5f
    )
    repo.saveMemory(
        key = "User_Preferred_Theme",
        content = "Default to Dark Mode with vibrant primary electric violet and cyan accenting.",
        category = MemoryCategory.USER_PREFERENCE,
        isPinned = false,
        relevanceScore = 1.0f
    )
}
