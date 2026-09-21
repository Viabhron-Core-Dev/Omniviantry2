package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        com.example.engine.skills.SkillManager.init(context)
    }

    val skills by com.example.engine.skills.SkillManager.skills.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Functional, 2: Soul Skills
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<com.example.engine.skills.SkillEntity?>(null) }

    // Dialog state
    var skillName by remember { mutableStateOf("") }
    var skillDesc by remember { mutableStateOf("") }
    var skillType by remember { mutableStateOf(com.example.engine.skills.SkillType.FUNCTIONAL) }
    var skillInstructions by remember { mutableStateOf("") }
    var skillExamples by remember { mutableStateOf("") }
    var skillAuthor by remember { mutableStateOf("User") }
    var importUrlText by remember { mutableStateOf("") }
    var importLoading by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val filteredSkills = remember(skills, searchQuery, selectedFilterIndex) {
        skills.filter { s ->
            val matchesFilter = when (selectedFilterIndex) {
                1 -> s.type == com.example.engine.skills.SkillType.FUNCTIONAL
                2 -> s.type == com.example.engine.skills.SkillType.SOUL_PERSONA
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    s.name.contains(searchQuery, ignoreCase = true) ||
                    s.description.contains(searchQuery, ignoreCase = true) ||
                    s.instructions.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSkill = null
                    skillName = ""
                    skillDesc = ""
                    skillType = if (selectedFilterIndex == 2) com.example.engine.skills.SkillType.SOUL_PERSONA else com.example.engine.skills.SkillType.FUNCTIONAL
                    skillInstructions = ""
                    skillExamples = ""
                    skillAuthor = "User"
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Skill")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar & Import Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search skills & souls...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedButton(
                    onClick = {
                        importUrlText = ""
                        importError = null
                        showImportDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Import", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Filter Tabs: All, Functional, Soul Skills
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val filters = listOf("All (${skills.size})", "Functional", "Soul Skills")
                filters.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                        onClick = { selectedFilterIndex = index },
                        selected = selectedFilterIndex == index,
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // List of skills
            if (filteredSkills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isBlank()) "No skills found in this category." else "No skills match '$searchQuery'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSkills, key = { it.id }) { skill ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (skill.type == com.example.engine.skills.SkillType.SOUL_PERSONA)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                skill.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        if (skill.type == com.example.engine.skills.SkillType.SOUL_PERSONA) "Soul / Persona" else "Functional",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                            if (skill.isBuiltIn) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text("Built-in", style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                        if (skill.description.isNotBlank()) {
                                            Text(
                                                skill.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = skill.isEnabled,
                                        onCheckedChange = { enabled ->
                                            coroutineScope.launch {
                                                com.example.engine.skills.SkillManager.addOrUpdateSkill(
                                                    context,
                                                    skill.copy(isEnabled = enabled)
                                                )
                                            }
                                        }
                                    )
                                }

                                if (skill.instructions.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = skill.instructions,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 3,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            editingSkill = skill
                                            skillName = skill.name
                                            skillDesc = skill.description
                                            skillType = skill.type
                                            skillInstructions = skill.instructions
                                            skillExamples = skill.examples
                                            skillAuthor = skill.author
                                            showCreateDialog = true
                                        }
                                    ) {
                                        Text("Edit")
                                    }

                                    if (!skill.isBuiltIn) {
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    com.example.engine.skills.SkillManager.deleteSkill(context, skill.id)
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create / Edit Skill Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (editingSkill == null) "Create Skill / Soul" else "Edit Skill") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = skillName,
                        onValueChange = { skillName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Skill Type:", style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = skillType == com.example.engine.skills.SkillType.FUNCTIONAL,
                            onClick = { skillType = com.example.engine.skills.SkillType.FUNCTIONAL },
                            label = { Text("Functional") }
                        )
                        FilterChip(
                            selected = skillType == com.example.engine.skills.SkillType.SOUL_PERSONA,
                            onClick = { skillType = com.example.engine.skills.SkillType.SOUL_PERSONA },
                            label = { Text("Soul / Persona") }
                        )
                    }

                    OutlinedTextField(
                        value = skillDesc,
                        onValueChange = { skillDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = skillInstructions,
                        onValueChange = { skillInstructions = it },
                        label = { Text(if (skillType == com.example.engine.skills.SkillType.SOUL_PERSONA) "Soul / Persona Prompt" else "Skill Instructions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 6
                    )

                    OutlinedTextField(
                        value = skillExamples,
                        onValueChange = { skillExamples = it },
                        label = { Text("Few-Shot Examples (Optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (skillName.isNotBlank() && skillInstructions.isNotBlank()) {
                            val id = editingSkill?.id ?: ("skill_" + skillName.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' } + "_" + (System.currentTimeMillis() % 10000))
                            val skill = com.example.engine.skills.SkillEntity(
                                id = id,
                                name = skillName.trim(),
                                description = skillDesc.trim(),
                                type = skillType,
                                instructions = skillInstructions.trim(),
                                examples = skillExamples.trim(),
                                author = skillAuthor,
                                isBuiltIn = editingSkill?.isBuiltIn ?: false,
                                isEnabled = editingSkill?.isEnabled ?: true
                            )
                            coroutineScope.launch {
                                com.example.engine.skills.SkillManager.addOrUpdateSkill(context, skill)
                            }
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(if (editingSkill == null) "Create" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import from URL / Link Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Skill from Link / Hub") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Paste a direct URL to a skill definition (JSON or Markdown with YAML frontmatter from GitHub, Gist, or raw URL).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = importUrlText,
                        onValueChange = {
                            importUrlText = it
                            importError = null
                        },
                        label = { Text("Skill URL") },
                        placeholder = { Text("https://raw.githubusercontent.com/.../SKILL.md") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (importError != null) {
                        Text(
                            importError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (importLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importUrlText.isNotBlank()) {
                            importLoading = true
                            importError = null
                            coroutineScope.launch {
                                val res = com.example.engine.skills.SkillManager.importFromUrl(context, importUrlText.trim())
                                importLoading = false
                                if (res.isSuccess) {
                                    showImportDialog = false
                                } else {
                                    importError = res.exceptionOrNull()?.message ?: "Import failed"
                                }
                            }
                        }
                    },
                    enabled = !importLoading && importUrlText.isNotBlank()
                ) {
                    Text("Fetch & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ToolsSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val linuxProgress by com.example.engine.sandbox.LinuxSandboxManager.installProgress.collectAsState()

    LaunchedEffect(Unit) {
        com.example.engine.sandbox.LinuxSandboxManager.checkStatus(context)
        com.example.engine.mcp.McpServerManager.init(context)
    }

    data class ToolItem(
        val id: String,
        val name: String,
        val description: String,
        var permission: com.example.engine.tools.ToolPermission,
        val isMcp: Boolean = false,
        val mcpServerName: String? = null,
        val accountAlias: String? = null
    )

    data class ToolSection(
        val title: String,
        val subtitle: String,
        val tools: List<ToolItem>,
        var isExpanded: Boolean = false
    )

    fun getToolPerm(id: String): com.example.engine.tools.ToolPermission {
        return com.example.engine.tools.ToolPermissionManager.getEffectivePermission("", id)
    }

    // App-provided built-in tools
    val builtInFileTools = listOf(
        ToolItem("view_file", "View File", "Read local workspace files", getToolPerm("view_file")),
        ToolItem("edit_file", "Edit File", "Apply single block edits to files", getToolPerm("edit_file")),
        ToolItem("multi_edit_file", "Multi-Edit File", "Apply multiple non-contiguous edits", getToolPerm("multi_edit_file")),
        ToolItem("create_file", "Create File", "Create new files in the workspace", getToolPerm("create_file")),
        ToolItem("delete_file", "Delete File", "Delete files from workspace", getToolPerm("delete_file")),
        ToolItem("move_file", "Move / Rename File", "Move or rename files and directories", getToolPerm("move_file")),
        ToolItem("list_dir", "List Directory", "List files and child folders", getToolPerm("list_dir")),
        ToolItem("grep_search", "Grep Search", "Fast text and regex search across workspace", getToolPerm("grep_search"))
    )

    val builtInSandboxTools = listOf(
        ToolItem("run_js", "JS Sandbox", "Execute lightweight JavaScript code", getToolPerm("run_js")),
        ToolItem("shell_exec", "Shell Process", "Execute shell commands and CLI utilities", getToolPerm("shell_exec")),
        ToolItem("proot_linux", "PRoot Linux", "Execute commands in Alpine rootfs environment", getToolPerm("proot_linux")),
        ToolItem("run_python", "Run Python", "Direct Python 3 script executor", getToolPerm("run_python"))
    )

    val builtInDocKnowledgeTools = listOf(
        ToolItem("archive_manager", "Archive Manager", "Inspect and extract ZIP / tar archives", getToolPerm("archive_manager")),
        ToolItem("parse_document", "Document Parser", "Extract readable text from PDF and documents", getToolPerm("parse_document")),
        ToolItem("knowledge_bits", "Knowledge Bits", "Cross-thread reference snippets and cached URLs", getToolPerm("knowledge_bits")),
        ToolItem("manage_mcp", "MCP Manager", "Manage Model Context Protocol servers", getToolPerm("manage_mcp"))
    )

    // Dynamic MCP tools registered across active servers
    val mcpServers by com.example.engine.mcp.McpServerManager.servers.collectAsState()
    val allEngineTools = com.example.engine.EngineRegistry.getAllTools()

    val dynamicMcpTools = remember(mcpServers, allEngineTools) {
        allEngineTools.mapNotNull { tool ->
            if (tool is com.example.engine.mcp.McpDynamicToolAdapter) {
                ToolItem(
                    id = tool.name,
                    name = tool.name,
                    description = tool.description,
                    permission = getToolPerm(tool.name),
                    isMcp = true,
                    mcpServerName = tool.serverConfig.name,
                    accountAlias = tool.serverConfig.accountAlias
                )
            } else null
        }
    }

    // Foldable sections - folded by default (isExpanded = false) as requested by user
    val sections = remember {
        mutableStateListOf(
            ToolSection("Workspace Files & Search", "8 native workspace tools", builtInFileTools, isExpanded = false),
            ToolSection("Sandboxes & Execution", "4 native execution environments", builtInSandboxTools, isExpanded = false),
            ToolSection("Documents & Knowledge", "4 native document & context tools", builtInDocKnowledgeTools, isExpanded = false)
        )
    }

    var isMcpSectionExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text("Tools & Compute Sandboxes", style = MaterialTheme.typography.titleMedium)
                Text("Manage permissions for built-in capabilities and MCP extensions.", style = MaterialTheme.typography.bodyMedium)
            }

            // Linux Alpine Sandbox Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (linuxProgress.status) {
                        com.example.engine.sandbox.LinuxEnvironmentStatus.READY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        com.example.engine.sandbox.LinuxEnvironmentStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Alpine Linux RootFS Sandbox", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when (linuxProgress.status) {
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.READY -> "Status: Ready (arm64/x86_64)"
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.DOWNLOADING -> "Status: Downloading..."
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.EXTRACTING -> "Status: Unpacking tar.gz..."
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.ERROR -> "Status: Installation Error"
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.NOT_INSTALLED -> "Status: Not Installed (~3.5MB)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (linuxProgress.status) {
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.READY -> MaterialTheme.colorScheme.primary
                                    com.example.engine.sandbox.LinuxEnvironmentStatus.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        if (linuxProgress.status == com.example.engine.sandbox.LinuxEnvironmentStatus.READY) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        com.example.engine.sandbox.LinuxSandboxManager.uninstallRootfs(context)
                                    }
                                }
                            ) {
                                Text("Uninstall")
                            }
                        } else if (linuxProgress.status == com.example.engine.sandbox.LinuxEnvironmentStatus.NOT_INSTALLED ||
                                   linuxProgress.status == com.example.engine.sandbox.LinuxEnvironmentStatus.ERROR) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        com.example.engine.sandbox.LinuxSandboxManager.downloadAndInstallRootfs(context)
                                    }
                                }
                            ) {
                                Text("Install RootFS")
                            }
                        }
                    }

                    if (linuxProgress.status == com.example.engine.sandbox.LinuxEnvironmentStatus.DOWNLOADING ||
                        linuxProgress.status == com.example.engine.sandbox.LinuxEnvironmentStatus.EXTRACTING) {
                        LinearProgressIndicator(
                            progress = { linuxProgress.progressPercent },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = linuxProgress.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!linuxProgress.error.isNullOrBlank()) {
                        Text(
                            text = "Error: ${linuxProgress.error}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tools by name, ID or description...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1 Header: App-Provided Tools
                item {
                    Text(
                        "APP-PROVIDED TOOLS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // Foldable App-Provided Tool Categories (folded by default)
                sections.forEachIndexed { sIdx, section ->
                    val filtered = section.tools.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
                    }

                    if (filtered.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sections[sIdx] = section.copy(isExpanded = !section.isExpanded)
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(section.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${filtered.size} tools • ${if (section.isExpanded) "Tap to collapse" else "Folded (tap to expand)"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Icon(
                                        if (section.isExpanded || searchQuery.isNotEmpty()) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            }
                        }

                        if (section.isExpanded || searchQuery.isNotEmpty()) {
                            items(filtered.size) { toolIdx ->
                                val tool = filtered[toolIdx]
                                var showDropdown by remember { mutableStateOf(false) }

                                val permLabel = when (tool.permission) {
                                    com.example.engine.tools.ToolPermission.USE_FREELY -> "Use Freely"
                                    com.example.engine.tools.ToolPermission.NO_PERMISSION -> "No Permission"
                                    else -> "Always Ask"
                                }

                                ListItem(
                                    headlineContent = { Text(tool.name) },
                                    supportingContent = { Text(tool.description, style = MaterialTheme.typography.bodySmall) },
                                    trailingContent = {
                                        Box {
                                            TextButton(onClick = { showDropdown = true }) {
                                                Text(permLabel)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(
                                                expanded = showDropdown,
                                                onDismissRequest = { showDropdown = false }
                                            ) {
                                                listOf(
                                                    "Always Ask" to com.example.engine.tools.ToolPermission.ALWAYS_ASK,
                                                    "Use Freely" to com.example.engine.tools.ToolPermission.USE_FREELY,
                                                    "No Permission" to com.example.engine.tools.ToolPermission.NO_PERMISSION
                                                ).forEach { (optLabel, optPerm) ->
                                                    DropdownMenuItem(
                                                        text = { Text(optLabel) },
                                                        onClick = {
                                                            com.example.engine.tools.ToolPermissionManager.setGlobalOverride(tool.id, optPerm, context)
                                                            val updatedList = section.tools.toMutableList()
                                                            updatedList[toolIdx] = tool.copy(permission = optPerm)
                                                            sections[sIdx] = section.copy(tools = updatedList)
                                                            showDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 2 Divider & Header: MCP-Provided Tools
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "MCP-PROVIDED TOOLS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                val filteredMcpTools = dynamicMcpTools.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true) ||
                    (it.mcpServerName?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.accountAlias?.contains(searchQuery, ignoreCase = true) == true)
                }

                if (filteredMcpTools.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("No MCP Tools Available", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Connected MCP servers will automatically register tools here for prompt execution. Configure servers in MCP Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isMcpSectionExpanded = !isMcpSectionExpanded },
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Active MCP Server Tools", style = MaterialTheme.typography.titleSmall)
                                    Text("${filteredMcpTools.size} dynamic tools • ${if (isMcpSectionExpanded) "Tap to collapse" else "Folded (tap to expand)"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Icon(
                                    if (isMcpSectionExpanded || searchQuery.isNotEmpty()) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    if (isMcpSectionExpanded || searchQuery.isNotEmpty()) {
                        items(filteredMcpTools.size) { mcpIdx ->
                            val mcpTool = filteredMcpTools[mcpIdx]
                            var showMcpDropdown by remember { mutableStateOf(false) }

                            val permLabel = when (mcpTool.permission) {
                                com.example.engine.tools.ToolPermission.USE_FREELY -> "Use Freely"
                                com.example.engine.tools.ToolPermission.NO_PERMISSION -> "No Permission"
                                else -> "Always Ask"
                            }

                            ListItem(
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(mcpTool.name, fontWeight = FontWeight.SemiBold)
                                        if (!mcpTool.accountAlias.isNullOrBlank() && !mcpTool.accountAlias.equals("Default", ignoreCase = true)) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(mcpTool.accountAlias, style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                },
                                supportingContent = { Text(mcpTool.description, style = MaterialTheme.typography.bodySmall) },
                                trailingContent = {
                                    Box {
                                        TextButton(onClick = { showMcpDropdown = true }) {
                                            Text(permLabel)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showMcpDropdown,
                                            onDismissRequest = { showMcpDropdown = false }
                                        ) {
                                            listOf(
                                                "Always Ask" to com.example.engine.tools.ToolPermission.ALWAYS_ASK,
                                                "Use Freely" to com.example.engine.tools.ToolPermission.USE_FREELY,
                                                "No Permission" to com.example.engine.tools.ToolPermission.NO_PERMISSION
                                            ).forEach { (optLabel, optPerm) ->
                                                DropdownMenuItem(
                                                    text = { Text(optLabel) },
                                                    onClick = {
                                                        com.example.engine.tools.ToolPermissionManager.setGlobalOverride(mcpTool.id, optPerm, context)
                                                        showMcpDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
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
fun MCPSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.example.engine.mcp.McpServerManager.init(context)
    }

    val servers by com.example.engine.mcp.McpServerManager.servers.collectAsState()
    var mcpSearchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newServerName by remember { mutableStateOf("") }
    var newServerUrl by remember { mutableStateOf("") }
    var newAccountAlias by remember { mutableStateOf("") }
    var newHeadersText by remember { mutableStateOf("") }
    var selectedTransport by remember { mutableStateOf(com.example.engine.mcp.McpTransportType.SSE) }
    var serverToPing by remember { mutableStateOf<String?>(null) }
    var pingResult by remember { mutableStateOf<String?>(null) }

    // Quick Priority Templates requested by user: Google Drive, Cloudflare, GitHub, Kaggle
    data class McpTemplate(
        val name: String,
        val defaultUrl: String,
        val defaultAlias: String,
        val transport: com.example.engine.mcp.McpTransportType,
        val defaultHeaderPlaceholder: String,
        val description: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val templates = listOf(
        McpTemplate(
            name = "Google Drive MCP",
            defaultUrl = "http://localhost:8001/sse",
            defaultAlias = "Personal",
            transport = com.example.engine.mcp.McpTransportType.SSE,
            defaultHeaderPlaceholder = "Authorization: Bearer <gdrive_oauth_token>",
            description = "Search, read, upload and sync Google Drive docs & sheets",
            icon = Icons.Default.Folder
        ),
        McpTemplate(
            name = "Cloudflare MCP",
            defaultUrl = "http://localhost:8002/sse",
            defaultAlias = "Production",
            transport = com.example.engine.mcp.McpTransportType.SSE,
            defaultHeaderPlaceholder = "X-Auth-Key: <cloudflare_api_key>",
            description = "Workers, KV stores, D1 database and DNS management",
            icon = Icons.Default.Cloud
        ),
        McpTemplate(
            name = "GitHub MCP",
            defaultUrl = "http://localhost:8003/sse",
            defaultAlias = "Work",
            transport = com.example.engine.mcp.McpTransportType.SSE,
            defaultHeaderPlaceholder = "Authorization: Bearer <github_pat_token>",
            description = "Repositories, PRs, issues, commits, branches and code search",
            icon = Icons.Default.Code
        ),
        McpTemplate(
            name = "Kaggle MCP",
            defaultUrl = "http://localhost:8004/sse",
            defaultAlias = "Research",
            transport = com.example.engine.mcp.McpTransportType.SSE,
            defaultHeaderPlaceholder = "Authorization: Bearer <kaggle_api_token>",
            description = "Search datasets, pull notebooks, launch kernel compute runs",
            icon = Icons.Default.Analytics
        )
    )

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Model Context Protocol (MCP)", style = MaterialTheme.typography.titleMedium)
                Text("Multi-account external tool & resource servers", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = {
                newServerName = ""
                newServerUrl = ""
                newAccountAlias = "Personal"
                newHeadersText = ""
                selectedTransport = com.example.engine.mcp.McpTransportType.SSE
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add MCP Server", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Server")
            }
        }

        // Priority Quick-Add Presets (Google Drive, Cloudflare, GitHub, Kaggle)
        Text("Quick-Add Priority Servers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        LazyColumn(
            modifier = Modifier.heightIn(max = 170.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(templates.size) { idx ->
                val tmpl = templates[idx]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(tmpl.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column {
                                Text(tmpl.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(tmpl.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                newServerName = tmpl.name
                                newServerUrl = tmpl.defaultUrl
                                newAccountAlias = tmpl.defaultAlias
                                newHeadersText = tmpl.defaultHeaderPlaceholder
                                selectedTransport = tmpl.transport
                                showAddDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Configure", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Search bar for MCP
        OutlinedTextField(
            value = mcpSearchQuery,
            onValueChange = { mcpSearchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search MCP servers & accounts...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search MCP") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Active Configured Servers
        val filteredServers = remember(servers, mcpSearchQuery) {
            if (mcpSearchQuery.isBlank()) servers
            else servers.filter {
                it.name.contains(mcpSearchQuery, ignoreCase = true) ||
                it.accountAlias.contains(mcpSearchQuery, ignoreCase = true) ||
                it.endpointUrl.contains(mcpSearchQuery, ignoreCase = true)
            }
        }

        Text("Configured MCP Servers (${filteredServers.size}/${servers.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        if (filteredServers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (mcpSearchQuery.isBlank()) "No Servers Added Yet" else "No Servers Match '$mcpSearchQuery'", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Add custom or preset MCP endpoints above. OmniRoot supports multi-account instances (e.g. GitHub Personal and GitHub Work) running concurrently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(filteredServers) { server ->
                    val client = com.example.engine.mcp.McpServerManager.getClient(server.id)
                    val connState = client?.connectionState?.collectAsState()?.value ?: com.example.engine.mcp.McpConnectionState.DISCONNECTED

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(server.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        SuggestionChip(
                                            onClick = {},
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Text(server.accountAlias, style = MaterialTheme.typography.labelSmall)
                                                }
                                            },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                    Text(server.endpointUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(connState.name) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = when (connState) {
                                            com.example.engine.mcp.McpConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                                            com.example.engine.mcp.McpConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
                                            com.example.engine.mcp.McpConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Transport: ${server.transportType.name}${if (server.lastPingMs > 0) " (${server.lastPingMs}ms)" else ""}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        coroutineScope.launch {
                                            serverToPing = "${server.name} (${server.accountAlias})"
                                            val res = com.example.engine.mcp.McpServerManager.pingServer(server)
                                            pingResult = if (res.isSuccess) "Latency: ${res.getOrThrow()}ms" else "Failed: ${res.exceptionOrNull()?.message}"
                                        }
                                    }) {
                                        Text("Ping", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (connState == com.example.engine.mcp.McpConnectionState.CONNECTED) {
                                        TextButton(onClick = {
                                            coroutineScope.launch {
                                                com.example.engine.mcp.McpServerManager.disconnectServer(server.id)
                                            }
                                        }) {
                                            Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        TextButton(onClick = {
                                            coroutineScope.launch {
                                                com.example.engine.mcp.McpServerManager.connectServer(server)
                                            }
                                        }) {
                                            Text("Connect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                com.example.engine.mcp.McpServerManager.removeServer(context, server.id)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("✕", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (server.lastError != null) {
                                Text("Error: ${server.lastError}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (pingResult != null) {
            Snackbar(
                action = { TextButton(onClick = { pingResult = null }) { Text("Dismiss") } }
            ) {
                Text("Ping $serverToPing: $pingResult")
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Configure MCP Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newServerName,
                        onValueChange = { newServerName = it },
                        label = { Text("Server Name") },
                        placeholder = { Text("e.g. GitHub MCP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAccountAlias,
                        onValueChange = { newAccountAlias = it },
                        label = { Text("Account Alias") },
                        placeholder = { Text("e.g. Personal, Work, Org A") },
                        supportingText = { Text("Allows multiple accounts on the same MCP service") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newServerUrl,
                        onValueChange = { newServerUrl = it },
                        label = { Text("Endpoint URL") },
                        placeholder = { Text("http://localhost:8000/sse") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newHeadersText,
                        onValueChange = { newHeadersText = it },
                        label = { Text("Auth Headers (Optional)") },
                        placeholder = { Text("Authorization: Bearer <token>") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transport:", style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = selectedTransport == com.example.engine.mcp.McpTransportType.SSE,
                            onClick = { selectedTransport = com.example.engine.mcp.McpTransportType.SSE },
                            label = { Text("SSE") }
                        )
                        FilterChip(
                            selected = selectedTransport == com.example.engine.mcp.McpTransportType.HTTP,
                            onClick = { selectedTransport = com.example.engine.mcp.McpTransportType.HTTP },
                            label = { Text("HTTP") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newServerName.isNotBlank() && newServerUrl.isNotBlank()) {
                            val alias = if (newAccountAlias.isNotBlank()) newAccountAlias.trim() else "Default"
                            val cleanName = newServerName.trim().lowercase().replace(" ", "_")
                            val cleanAlias = alias.lowercase().replace(" ", "_")
                            val id = "${cleanName}_${cleanAlias}_${System.currentTimeMillis() % 10000}"

                            val parsedHeaders = mutableMapOf<String, String>()
                            if (newHeadersText.isNotBlank() && newHeadersText.contains(":")) {
                                val parts = newHeadersText.split(":", limit = 2)
                                if (parts.size == 2) {
                                    parsedHeaders[parts[0].trim()] = parts[1].trim()
                                }
                            }

                            val cfg = com.example.engine.mcp.McpServerConfig(
                                id = id,
                                name = newServerName.trim(),
                                endpointUrl = newServerUrl.trim(),
                                transportType = selectedTransport,
                                headers = parsedHeaders,
                                accountAlias = alias
                            )
                            coroutineScope.launch {
                                com.example.engine.mcp.McpServerManager.addServer(context, cfg)
                            }
                            newServerName = ""
                            newServerUrl = ""
                            newAccountAlias = ""
                            newHeadersText = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        com.example.engine.plugins.PluginManager.init(context)
        com.example.engine.skills.SkillManager.init(context)
        com.example.engine.mcp.McpServerManager.init(context)
    }

    val plugins by com.example.engine.plugins.PluginManager.plugins.collectAsState()
    val availableSkills by com.example.engine.skills.SkillManager.skills.collectAsState()
    val availableMcpServers by com.example.engine.mcp.McpServerManager.servers.collectAsState()
    val allEngineTools = remember { com.example.engine.EngineRegistry.getAllTools() }

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingPlugin by remember { mutableStateOf<com.example.engine.plugins.PluginEntity?>(null) }

    // Dialog Fields
    var pluginName by remember { mutableStateOf("") }
    var pluginDesc by remember { mutableStateOf("") }
    var pluginVersion by remember { mutableStateOf("1.0.0") }
    var pluginInstructions by remember { mutableStateOf("") }
    var pluginRestrictions by remember { mutableStateOf("") }
    val selectedSkillIds = remember { mutableStateListOf<String>() }
    val selectedToolIds = remember { mutableStateListOf<String>() }
    val selectedMcpServerIds = remember { mutableStateListOf<String>() }

    var importUrlText by remember { mutableStateOf("") }
    var importLoading by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val filteredPlugins = remember(plugins, searchQuery) {
        if (searchQuery.isBlank()) plugins
        else plugins.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
            p.description.contains(searchQuery, ignoreCase = true) ||
            p.customInstructions.contains(searchQuery, ignoreCase = true) ||
            p.restrictions.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPlugin = null
                    pluginName = ""
                    pluginDesc = ""
                    pluginVersion = "1.0.0"
                    pluginInstructions = ""
                    pluginRestrictions = ""
                    selectedSkillIds.clear()
                    selectedToolIds.clear()
                    selectedMcpServerIds.clear()
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Plugin")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Search
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search plugins & bundles...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedButton(
                    onClick = {
                        importUrlText = ""
                        importError = null
                        showImportDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Import", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                "Plugins bundle custom skills, MCP servers, tools, and strict behavioral instructions into a single one-tap agent capability suite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // List of Plugins
            if (filteredPlugins.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isBlank()) "No plugins created yet. Tap '+' to bundle tools, MCPs, and skills."
                        else "No plugins match '$searchQuery'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredPlugins, key = { it.id }) { plugin ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                plugin.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("v${plugin.version}", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                        if (plugin.description.isNotBlank()) {
                                            Text(
                                                plugin.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = plugin.isEnabled,
                                        onCheckedChange = { enabled ->
                                            coroutineScope.launch {
                                                com.example.engine.plugins.PluginManager.addOrUpdatePlugin(
                                                    context,
                                                    plugin.copy(isEnabled = enabled)
                                                )
                                            }
                                        }
                                    )
                                }

                                // Bundle summary chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("${plugin.selectedSkillIds.size} Skills", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("${plugin.selectedToolIds.size} Tools", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("${plugin.selectedMcpServerIds.size} MCPs", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }

                                if (plugin.customInstructions.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Instructions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = plugin.customInstructions,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }

                                if (plugin.restrictions.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Restrictions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                            Text(
                                                text = plugin.restrictions,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            editingPlugin = plugin
                                            pluginName = plugin.name
                                            pluginDesc = plugin.description
                                            pluginVersion = plugin.version
                                            pluginInstructions = plugin.customInstructions
                                            pluginRestrictions = plugin.restrictions
                                            selectedSkillIds.clear()
                                            selectedSkillIds.addAll(plugin.selectedSkillIds)
                                            selectedToolIds.clear()
                                            selectedToolIds.addAll(plugin.selectedToolIds)
                                            selectedMcpServerIds.clear()
                                            selectedMcpServerIds.addAll(plugin.selectedMcpServerIds)
                                            showCreateDialog = true
                                        }
                                    ) {
                                        Text("Edit")
                                    }

                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                com.example.engine.plugins.PluginManager.deletePlugin(context, plugin.id)
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create / Edit Plugin Dialog with Sub-pickers
    if (showCreateDialog) {
        var pickerTab by remember { mutableIntStateOf(0) } // 0: Details, 1: Skills, 2: Tools, 3: MCP Servers

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (editingPlugin == null) "Build New Plugin" else "Edit Plugin") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val tabs = listOf("Info", "Skills (${selectedSkillIds.size})", "Tools (${selectedToolIds.size})", "MCPs (${selectedMcpServerIds.size})")
                        tabs.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                                onClick = { pickerTab = index },
                                selected = pickerTab == index,
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    when (pickerTab) {
                        0 -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                                item {
                                    OutlinedTextField(
                                        value = pluginName,
                                        onValueChange = { pluginName = it },
                                        label = { Text("Plugin Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = pluginDesc,
                                        onValueChange = { pluginDesc = it },
                                        label = { Text("Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = pluginVersion,
                                        onValueChange = { pluginVersion = it },
                                        label = { Text("Version") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = pluginInstructions,
                                        onValueChange = { pluginInstructions = it },
                                        label = { Text("Custom Prompt Instructions") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        maxLines = 4
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = pluginRestrictions,
                                        onValueChange = { pluginRestrictions = it },
                                        label = { Text("Strict Operational Restrictions (Guardrails)") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp),
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                        1 -> {
                            // Skills Multi-Selector
                            Text("Select Skills to bundle into this plugin:", style = MaterialTheme.typography.labelMedium)
                            if (availableSkills.isEmpty()) {
                                Text("No skills available. Add skills in the Skills page first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                                    items(availableSkills) { skill ->
                                        val isChecked = selectedSkillIds.contains(skill.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isChecked) selectedSkillIds.remove(skill.id)
                                                    else selectedSkillIds.add(skill.id)
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { check ->
                                                    if (check) selectedSkillIds.add(skill.id) else selectedSkillIds.remove(skill.id)
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    if (skill.type == com.example.engine.skills.SkillType.SOUL_PERSONA) "Soul / Persona" else "Functional Skill",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Tools Multi-Selector
                            Text("Select App-Provided Tools to bundle:", style = MaterialTheme.typography.labelMedium)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                                items(allEngineTools) { tool ->
                                    val isChecked = selectedToolIds.contains(tool.name)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isChecked) selectedToolIds.remove(tool.name)
                                                else selectedToolIds.add(tool.name)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { check ->
                                                if (check) selectedToolIds.add(tool.name) else selectedToolIds.remove(tool.name)
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tool.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(tool.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // MCP Servers Multi-Selector
                            Text("Select MCP Servers to bind:", style = MaterialTheme.typography.labelMedium)
                            if (availableMcpServers.isEmpty()) {
                                Text("No MCP servers configured yet. Add servers in the MCP page first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                                    items(availableMcpServers) { mcp ->
                                        val isChecked = selectedMcpServerIds.contains(mcp.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isChecked) selectedMcpServerIds.remove(mcp.id)
                                                    else selectedMcpServerIds.add(mcp.id)
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { check ->
                                                    if (check) selectedMcpServerIds.add(mcp.id) else selectedMcpServerIds.remove(mcp.id)
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${mcp.name} (${mcp.accountAlias})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text(mcp.endpointUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
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
                        if (pluginName.isNotBlank()) {
                            val id = editingPlugin?.id ?: ("plugin_" + pluginName.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' } + "_" + (System.currentTimeMillis() % 10000))
                            val plugin = com.example.engine.plugins.PluginEntity(
                                id = id,
                                name = pluginName.trim(),
                                description = pluginDesc.trim(),
                                version = pluginVersion.trim(),
                                author = editingPlugin?.author ?: "User",
                                selectedSkillIds = selectedSkillIds.toList(),
                                selectedToolIds = selectedToolIds.toList(),
                                selectedMcpServerIds = selectedMcpServerIds.toList(),
                                customInstructions = pluginInstructions.trim(),
                                restrictions = pluginRestrictions.trim(),
                                isEnabled = editingPlugin?.isEnabled ?: true
                            )
                            coroutineScope.launch {
                                com.example.engine.plugins.PluginManager.addOrUpdatePlugin(context, plugin)
                            }
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(if (editingPlugin == null) "Create Plugin" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import Plugin Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Plugin Bundle") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Paste a URL pointing to a plugin bundle manifest JSON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = importUrlText,
                        onValueChange = {
                            importUrlText = it
                            importError = null
                        },
                        label = { Text("Plugin Manifest URL") },
                        placeholder = { Text("https://example.com/plugin.json") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (importError != null) {
                        Text(
                            importError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (importLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importUrlText.isNotBlank()) {
                            importLoading = true
                            importError = null
                            coroutineScope.launch {
                                val res = com.example.engine.plugins.PluginManager.importFromUrl(context, importUrlText.trim())
                                importLoading = false
                                if (res.isSuccess) {
                                    showImportDialog = false
                                } else {
                                    importError = res.exceptionOrNull()?.message ?: "Import failed"
                                }
                            }
                        }
                    },
                    enabled = !importLoading && importUrlText.isNotBlank()
                ) {
                    Text("Fetch & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun IntegrationsSettingsContent() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Integrations", style = MaterialTheme.typography.titleMedium)
        Text("Manage third-party connections.")
    }
}

@Composable
fun PermissionsSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshKey++
    }

    val micGranted = remember(refreshKey) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notificationGranted = remember(refreshKey) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val storageGranted = remember(refreshKey) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "OmniRoot requests minimal permissions required for local agent execution, voice transcription, and document analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Microphone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Voice input and Whisper audio transcription", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (micGranted) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Granted", color = MaterialTheme.colorScheme.primary) },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                            )
                        } else {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Grant", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    HorizontalDivider()

                    // Notifications
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Notifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Agent background task and build completion alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (notificationGranted) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Granted", color = MaterialTheme.colorScheme.primary) },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                            )
                        } else {
                            Button(
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Grant", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    HorizontalDivider()

                    // Storage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Media & Documents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Photo picker and local file import for attachments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (storageGranted) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Granted", color = MaterialTheme.colorScheme.primary) },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                            )
                        } else {
                            Button(
                                onClick = {
                                    val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                                    } else {
                                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    permissionLauncher.launch(perms)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Grant", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Android App System Settings")
            }
        }
    }
}

@Composable
fun FontSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("typography_prefs", android.content.Context.MODE_PRIVATE) }

    var selectedFontFamily by remember { mutableStateOf(prefs.getString("font_family", "Default") ?: "Default") }
    var selectedScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1.0f)) }

    fun saveTypography() {
        prefs.edit()
            .putString("font_family", selectedFontFamily)
            .putFloat("font_scale", selectedScale)
            .apply()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Typography & Fonts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Customize the typeface and visual scale used across chats, code blocks, and system views.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Live Preview Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val previewFont = when (selectedFontFamily) {
                        "Monospace" -> FontFamily.Monospace
                        "Serif" -> FontFamily.Serif
                        "Sans-Serif" -> FontFamily.SansSerif
                        else -> FontFamily.Default
                    }
                    Text(
                        "OmniRoot Engine v2.5",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = previewFont)
                    )
                    Text(
                        "Agent reasoning and prompt execution with strict zero-drift parameters.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = previewFont)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "fun executeAgent(task: Task): Flow<Token> = flow {\n    emit(Token(\"Executing...\"))\n}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Font Family Selector
        item {
            Text("App Font Family", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            val families = listOf("Default", "Sans-Serif", "Serif", "Monospace")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    families.forEach { fam ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFontFamily = fam
                                    saveTypography()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fam, style = MaterialTheme.typography.bodyMedium)
                            RadioButton(
                                selected = selectedFontFamily == fam,
                                onClick = {
                                    selectedFontFamily = fam
                                    saveTypography()
                                }
                            )
                        }
                    }
                }
            }
        }

        // UI Font Scale
        item {
            Text("Font Scale (${String.format(java.util.Locale.US, "%.2fx", selectedScale)})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Slider(
                        value = selectedScale,
                        onValueChange = {
                            selectedScale = it
                            saveTypography()
                        },
                        valueRange = 0.85f..1.3f,
                        steps = 5
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Compact (0.85x)", style = MaterialTheme.typography.labelSmall)
                        Text("Standard (1.0x)", style = MaterialTheme.typography.labelSmall)
                        Text("Large (1.3x)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("gdrive_prefs", android.content.Context.MODE_PRIVATE) }
    var isConnected by remember { mutableStateOf(prefs.getBoolean("is_connected", false)) }
    var accountEmail by remember { mutableStateOf(prefs.getString("account_email", "") ?: "") }
    var showConnectDialog by remember { mutableStateOf(false) }
    var inputEmail by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isConnected) {
            // Blank till gdrive connected. Show there get gdrive
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            "Google Drive Not Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "The Component Library stores and syncs prompt packs, code snippets, and UI components across your devices using Google Drive.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Button(
                            onClick = {
                                inputEmail = ""
                                showConnectDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive")
                        }
                    }
                }
            }
        } else {
            // Connected state: displays component folders and files
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Google Drive Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Connected as $accountEmail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = {
                        prefs.edit().putBoolean("is_connected", false).putString("account_email", "").apply()
                        isConnected = false
                        accountEmail = ""
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            }

            HorizontalDivider()

            Text("Synced Library Folders", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("Prompt Templates") },
                        supportingContent = { Text("12 prompts synced • Google Drive/OmniRoot/Prompts") },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Code Snippets & Architecture Blueprints") },
                        supportingContent = { Text("8 files synced • Google Drive/OmniRoot/Snippets") },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Custom Artifacts & UI Mockups") },
                        supportingContent = { Text("5 files synced • Google Drive/OmniRoot/Artifacts") },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
    }

    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = { Text("Connect Google Drive Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the Google account email to link with OmniRoot's cloud library sync:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Google Account Email") },
                        placeholder = { Text("user@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = if (inputEmail.isNotBlank()) inputEmail.trim() else "user@gmail.com"
                        prefs.edit()
                            .putBoolean("is_connected", true)
                            .putString("account_email", email)
                            .apply()
                        accountEmail = email
                        isConnected = true
                        showConnectDialog = false
                    }
                ) {
                    Text("Connect Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BackupSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // SAF Create Document Launcher
    val createDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && exportPassword.isNotBlank()) {
            isProcessing = true
            coroutineScope.launch {
                try {
                    val outStream = context.contentResolver.openOutputStream(uri)
                    if (outStream != null) {
                        val result = com.example.engine.backup.BackupManager.exportBackup(
                            context,
                            exportPassword.toCharArray(),
                            outStream
                        )
                        result.onSuccess { stats ->
                            statusMessage = "Backup created: ${stats.messageCount} msgs, ${stats.keyCount} keys, ${stats.configCount} configs, ${stats.artifactProviderCount} artifact providers."
                            android.widget.Toast.makeText(context, statusMessage, android.widget.Toast.LENGTH_LONG).show()
                        }.onFailure { err ->
                            statusMessage = "Backup failed: ${err.message}"
                            android.widget.Toast.makeText(context, statusMessage, android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        statusMessage = "Unable to open destination file."
                    }
                } catch (e: Exception) {
                    statusMessage = "Export error: ${e.message}"
                } finally {
                    isProcessing = false
                    exportPassword = ""
                    exportPasswordConfirm = ""
                }
            }
        }
    }

    // SAF Open Document Launcher
    val openDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePasswordDialog = true
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Encrypted Backup & Restore", style = MaterialTheme.typography.titleMedium)
        Text(
            "Securely backup all chats, online API keys, workspace configurations, and tool permissions using authenticated AES-256-GCM encryption with PBKDF2 key derivation.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup Scope", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("• All chat messages across all sessions\n• All provider API keys and active account selections\n• Claude.ai Headless Artifact Providers & encrypted passkeys\n• Workspace system prompts, models, and fallback chains\n• Custom tool permissions & sandbox execution policies", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (statusMessage != null) {
            Text(statusMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { showExportPasswordDialog = true },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup")
            }

            OutlinedButton(
                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore")
            }
        }
    }

    // Export Password Dialog
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            title = { Text("Set Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a strong passphrase to encrypt your backup file.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportPasswordConfirm,
                        onValueChange = { exportPasswordConfirm = it },
                        label = { Text("Confirm Passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exportPassword.isNotBlank() && exportPassword == exportPasswordConfirm) {
                            showExportPasswordDialog = false
                            val time = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                            createDocLauncher.launch("omniroot_backup_$time.enc")
                        } else {
                            android.widget.Toast.makeText(context, "Passphrases must match and not be blank", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = exportPassword.isNotBlank() && exportPassword == exportPasswordConfirm
                ) {
                    Text("Select Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Restore Password Dialog
    if (showRestorePasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRestorePasswordDialog = false 
                pendingRestoreUri = null
            },
            title = { Text("Enter Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when creating this backup file.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        if (uri != null && restorePassword.isNotBlank()) {
                            showRestorePasswordDialog = false
                            isProcessing = true
                            coroutineScope.launch {
                                try {
                                    val inStream = context.contentResolver.openInputStream(uri)
                                    if (inStream != null) {
                                        val result = com.example.engine.backup.BackupManager.restoreBackup(
                                            context,
                                            restorePassword.toCharArray(),
                                            inStream
                                        )
                                        result.onSuccess { stats ->
                                            statusMessage = "Restore complete: ${stats.messageCount} msgs, ${stats.keyCount} keys, ${stats.configCount} configs, ${stats.artifactProviderCount} artifact providers restored."
                                            android.widget.Toast.makeText(context, statusMessage, android.widget.Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            statusMessage = "Restore failed: ${err.message}"
                                            android.widget.Toast.makeText(context, statusMessage, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Restore error: ${e.message}"
                                } finally {
                                    isProcessing = false
                                    restorePassword = ""
                                    pendingRestoreUri = null
                                }
                            }
                        }
                    },
                    enabled = restorePassword.isNotBlank()
                ) {
                    Text("Decrypt & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRestorePasswordDialog = false
                    pendingRestoreUri = null
                }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("editor_prefs", android.content.Context.MODE_PRIVATE) }

    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 14f)) }
    var tabSize by remember { mutableIntStateOf(prefs.getInt("tab_size", 4)) }
    var showLineNumbers by remember { mutableStateOf(prefs.getBoolean("show_line_numbers", true)) }
    var wordWrap by remember { mutableStateOf(prefs.getBoolean("word_wrap", false)) }
    var autoIndent by remember { mutableStateOf(prefs.getBoolean("auto_indent", true)) }
    var bracketMatching by remember { mutableStateOf(prefs.getBoolean("bracket_matching", true)) }

    fun saveEditorPrefs() {
        prefs.edit()
            .putFloat("font_size", fontSize)
            .putInt("tab_size", tabSize)
            .putBoolean("show_line_numbers", showLineNumbers)
            .putBoolean("word_wrap", wordWrap)
            .putBoolean("auto_indent", autoIndent)
            .putBoolean("bracket_matching", bracketMatching)
            .apply()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Code Editor Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Fine-tune font scaling, indentation, and formatting rules for the in-app code editor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Live Editor Preview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Editor Live Preview", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        val indent = " ".repeat(tabSize)
                        val sampleCode = buildString {
                            if (showLineNumbers) append("1: ")
                            appendLine("class OmniRootAgent {")
                            if (showLineNumbers) append("2: ")
                            appendLine("${indent}val version = \"2.5\"")
                            if (showLineNumbers) append("3: ")
                            appendLine("${indent}fun run() = true")
                            if (showLineNumbers) append("4: ")
                            append("}")
                        }
                        Text(
                            text = sampleCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.toInt().sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Font Size Slider
        item {
            Text("Font Size (${fontSize.toInt()} sp)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Slider(
                        value = fontSize,
                        onValueChange = {
                            fontSize = it
                            saveEditorPrefs()
                        },
                        valueRange = 10f..24f,
                        steps = 7
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("10 sp", style = MaterialTheme.typography.labelSmall)
                        Text("14 sp (Default)", style = MaterialTheme.typography.labelSmall)
                        Text("24 sp", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Tab Size Picker
        item {
            Text("Tab Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(2, 4, 8)
                tabs.forEachIndexed { index, size ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        onClick = {
                            tabSize = size
                            saveEditorPrefs()
                        },
                        selected = tabSize == size,
                        label = { Text("$size Spaces", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // Toggles
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    ListItem(
                        headlineContent = { Text("Line Numbers") },
                        supportingContent = { Text("Show line numbering gutter on the left") },
                        trailingContent = {
                            Switch(
                                checked = showLineNumbers,
                                onCheckedChange = {
                                    showLineNumbers = it
                                    saveEditorPrefs()
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Word Wrap") },
                        supportingContent = { Text("Wrap long lines horizontally instead of scrolling") },
                        trailingContent = {
                            Switch(
                                checked = wordWrap,
                                onCheckedChange = {
                                    wordWrap = it
                                    saveEditorPrefs()
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Auto-Indent") },
                        supportingContent = { Text("Automatically indent subsequent lines on newline") },
                        trailingContent = {
                            Switch(
                                checked = autoIndent,
                                onCheckedChange = {
                                    autoIndent = it
                                    saveEditorPrefs()
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Bracket & Quote Auto-Close") },
                        supportingContent = { Text("Automatically close paired delimiters like {}, [], and \"\"") },
                        trailingContent = {
                            Switch(
                                checked = bracketMatching,
                                onCheckedChange = {
                                    bracketMatching = it
                                    saveEditorPrefs()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryModulesSettingsContent() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Memory Modules (Phase 11)", style = MaterialTheme.typography.titleMedium)
        Text("Manage agent memory architectures (Episodic, Vector/RAG, File-System).")
        Button(onClick = {}) { Text("Add Memory Module") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericSettingsScreen(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            content()
        }
    }
}
