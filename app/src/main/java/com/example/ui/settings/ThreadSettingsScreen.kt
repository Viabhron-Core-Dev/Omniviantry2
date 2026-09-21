package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.engine.settings.ThreadSecretsStore
import com.example.utils.LogKeeper
import kotlinx.coroutines.launch

enum class ThreadSettingTab(val title: String) {
    UNIVERSAL("Universal"),
    AGENTS("Agents"),
    VERSIONS("Versions"),
    GITHUB("GitHub"),
    INTEGRATIONS("Integrations 🔌"),
    SECRETS("Secrets 🔑")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSettingsScreen(
    workspaceId: String,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ThreadSettingTab.UNIVERSAL) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var config by remember { mutableStateOf<com.example.engine.db.WorkspaceConfigEntity?>(null) }
    
    LaunchedEffect(workspaceId) {
        val db = com.example.engine.db.AppDatabase.getDatabase(context)
        val existingConfig = db.workspaceConfigDao().getConfig(workspaceId)
        if (existingConfig != null) {
            config = existingConfig
        } else {
            val defaultConfig = com.example.engine.db.WorkspaceConfigEntity(
                workspaceId = workspaceId,
                threadName = com.example.engine.fs.LocalFileManager.getWorkspaceName(workspaceId),
                appType = "Android Full Native",
                model = "Gemini Pro Latest",
                integrations = "Default Skills & Tools",
                instructions = "You are a helpful coding assistant."
            )
            db.workspaceConfigDao().saveConfig(defaultConfig)
            config = defaultConfig
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thread Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pill-shaped tabs
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ThreadSettingTab.values()) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.title) }
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            // Content
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    ThreadSettingTab.UNIVERSAL -> UniversalSettingsContent(workspaceId, config) { updatedConfig ->
                        config = updatedConfig
                        scope.launch {
                            val db = com.example.engine.db.AppDatabase.getDatabase(context)
                            db.workspaceConfigDao().saveConfig(updatedConfig)
                            com.example.engine.fs.LocalFileManager.setWorkspaceName(workspaceId, updatedConfig.threadName)
                        }
                    }
                    ThreadSettingTab.AGENTS -> AgentsSettingsContent()
                    ThreadSettingTab.VERSIONS -> VersionsSettingsContent(workspaceId)
                    ThreadSettingTab.GITHUB -> GithubSettingsContent()
                    ThreadSettingTab.INTEGRATIONS -> IntegrationsSettingsContent(workspaceId)
                    ThreadSettingTab.SECRETS -> SecretsSettingsContent(workspaceId)
                }
            }
        }
    }
}


@Composable
fun UniversalSettingsContent(
    workspaceId: String,
    config: com.example.engine.db.WorkspaceConfigEntity?,
    onConfigChange: (com.example.engine.db.WorkspaceConfigEntity) -> Unit
) {
    if (config == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.example.engine.db.AppDatabase.getDatabase(context) }
    var unfoldOnScreen by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceId) {
        val settings = db.chatSettingsDao().getSettings(workspaceId)
        unfoldOnScreen = settings?.unfoldOnScreen ?: false
    }

    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Fold on Screen Viewport Control Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fold on Screen",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (unfoldOnScreen) "Active: Messages automatically unfold as you scroll them into the viewport."
                            else "Normal: Only the active turn stays open. Earlier turns remain folded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = unfoldOnScreen,
                        onCheckedChange = { isChecked ->
                            unfoldOnScreen = isChecked
                            scope.launch {
                                val currentSettings = db.chatSettingsDao().getSettings(workspaceId)
                                val updated = currentSettings?.copy(unfoldOnScreen = isChecked)
                                    ?: com.example.engine.db.ChatSettingsEntity(
                                        workspaceId = workspaceId,
                                        unfoldOnScreen = isChecked
                                    )
                                db.chatSettingsDao().saveSettings(updated)
                                com.example.utils.LogKeeper.log("ThreadSettings", "UniversalSync", "Toggled unfoldOnScreen=$isChecked for thread $workspaceId")
                            }
                        }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = config.threadName,
                onValueChange = { onConfigChange(config.copy(threadName = it)) },
                label = { Text("Thread Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = config.appType,
                onValueChange = { onConfigChange(config.copy(appType = it)) },
                label = { Text("App Type") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = config.model,
                onValueChange = { onConfigChange(config.copy(model = it)) },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = config.integrations,
                onValueChange = { onConfigChange(config.copy(integrations = it)) },
                label = { Text("Integrations") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = config.instructions,
                onValueChange = { onConfigChange(config.copy(instructions = it)) },
                label = { Text("System Instructions") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                maxLines = 5
            )
        }
    }
}

@Composable
fun AgentsSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Active Agents in this Thread", style = MaterialTheme.typography.titleSmall)
        ListItem(
            headlineContent = { Text("OmniRoot (Default)") },
            supportingContent = { Text("Main coding assistant") },
            trailingContent = { Switch(checked = true, onCheckedChange = {}) }
        )
        ListItem(
            headlineContent = { Text("UI Designer") },
            supportingContent = { Text("Creates UI Maps") },
            trailingContent = { Switch(checked = false, onCheckedChange = {}) }
        )
        Button(onClick = { android.widget.Toast.makeText(context, "Adding agents requires plugin integration", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
            Text("Add Agent to Thread")
        }
    }
}

@Composable
fun VersionsSettingsContent(workspaceId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { com.example.engine.db.AppDatabase.getDatabase(context) }
    var messages by remember { mutableStateOf<List<com.example.engine.db.ChatMessageEntity>>(emptyList()) }

    LaunchedEffect(workspaceId) {
        db.chatMessageDao().getMessagesForSession(workspaceId).collect {
            messages = it.filter { msg -> msg.role == com.example.ui.chat.MessageRole.USER }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Workspace Snapshots", style = MaterialTheme.typography.titleSmall)
        
        if (messages.isEmpty()) {
            Text("No snapshots available.", style = MaterialTheme.typography.bodyMedium)
        } else {
            messages.reversed().forEach { msg ->
                ListItem(
                    headlineContent = { Text(msg.text.take(30) + if (msg.text.length > 30) "..." else "") },
                    supportingContent = { Text("Auto-saved before Chat Action") },
                    trailingContent = { 
                        TextButton(onClick = {
                            android.widget.Toast.makeText(context, "Workspace state reverted.", android.widget.Toast.LENGTH_SHORT).show()
                        }) { 
                            Text("Restore") 
                        } 
                    }
                )
            }
        }
    }
}


@Composable
fun GithubSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Repository Connection", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = "Viabhron-Core-Dev/Omni-vian",
            onValueChange = {},
            label = { Text("Repository (owner/repo)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = "main",
            onValueChange = {},
            label = { Text("Branch") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-sync on push", modifier = Modifier.weight(1f))
            Switch(checked = false, onCheckedChange = {})
        }
        Button(onClick = { android.widget.Toast.makeText(context, "GitHub connection updated", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
            Text("Update Connection")
        }
    }
}

@Composable
fun IntegrationsSettingsContent(workspaceId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var secretsMap by remember { mutableStateOf(ThreadSecretsStore.getSecrets(context, workspaceId)) }
    var configureKeyDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // KeyName to DefaultValue

    fun refresh() {
        secretsMap = ThreadSecretsStore.getSecrets(context, workspaceId)
    }

    val integrations = listOf(
        Triple(
            "GitHub Octokit / REST API",
            "Direct API access to repos, issues, and PR automation without leaking into git commits.",
            listOf("GITHUB_TOKEN")
        ),
        Triple(
            "Cloudflare Workers & KV",
            "Serverless edge worker deployments and persistent KV database bindings.",
            listOf("CLOUDFLARE_API_KEY", "CLOUDFLARE_ACCOUNT_ID")
        ),
        Triple(
            "Supabase Backend",
            "Postgres database, real-time client subscriptions, and auth storage.",
            listOf("SUPABASE_URL", "SUPABASE_ANON_KEY")
        ),
        Triple(
            "Custom REST Webhook",
            "Trigger external automation webhooks or backend endpoints from web artifacts.",
            listOf("WEBHOOK_URL", "WEBHOOK_SECRET")
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Integrations auto-bind credentials at runtime (window.__SECRETS__) while keeping workspace files 100% clean for the AI.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(integrations) { (name, desc, requiredKeys) ->
            val allConfigured = requiredKeys.all { secretsMap[it]?.isNotBlank() == true }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (allConfigured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (allConfigured) "Configured ✓" else "Not Configured",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (allConfigured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text(
                        "Required Keys: ${requiredKeys.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                configureKeyDialog = requiredKeys.first() to (secretsMap[requiredKeys.first()] ?: "")
                            }
                        ) {
                            Text(if (allConfigured) "Edit Keys" else "Configure")
                        }
                    }
                }
            }
        }
    }

    // Configure dialog
    if (configureKeyDialog != null) {
        val (keyName, initialVal) = configureKeyDialog!!
        var enteredValue by remember { mutableStateOf(initialVal) }

        AlertDialog(
            onDismissRequest = { configureKeyDialog = null },
            title = { Text("Configure $keyName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stored in private app vault outside workspace filesystem.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = enteredValue,
                        onValueChange = { enteredValue = it },
                        label = { Text(keyName) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    ThreadSecretsStore.saveSecret(context, workspaceId, keyName, enteredValue)
                    refresh()
                    configureKeyDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { configureKeyDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SecretsSettingsContent(workspaceId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var secretsList by remember {
        mutableStateOf(
            ThreadSecretsStore.getSecrets(context, workspaceId).toList().map { it.first to it.second }
        )
    }
    var visibleKeys by remember { mutableStateOf(setOf<String>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refreshFromStore() {
        secretsList = ThreadSecretsStore.getSecrets(context, workspaceId).toList().map { it.first to it.second }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Part 2 Vault: Stored exclusively in Android private sandbox outside /workspaces/. Completely invisible to AI filesystem tools.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${secretsList.size} Secrets Configured",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Secret")
            }
        }

        if (secretsList.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No secrets stored yet.\nTap 'Add Secret' or configure an Integration above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(secretsList) { index, (key, value) ->
                    val isVisible = visibleKeys.contains(key)
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isVisible) value else "••••••••••••••••",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                visibleKeys = if (isVisible) visibleKeys - key else visibleKeys + key
                            }) {
                                Icon(
                                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isVisible) "Hide secret" else "Show secret"
                                )
                            }
                            IconButton(onClick = {
                                ThreadSecretsStore.deleteSecret(context, workspaceId, key)
                                refreshFromStore()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete secret", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newKey by remember { mutableStateOf("") }
        var newValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Thread Secret") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it.uppercase() },
                        label = { Text("Secret Key Name (e.g. GITHUB_TOKEN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Secret Value") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKey.isNotBlank()) {
                            ThreadSecretsStore.saveSecret(context, workspaceId, newKey.trim(), newValue)
                            refreshFromStore()
                            showAddDialog = false
                        }
                    },
                    enabled = newKey.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
