package com.example.ui.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.webaiprovider.WebAiAccountProfile
import com.example.engine.webaiprovider.WebAiProviderManager
import com.example.engine.webaiprovider.WebAiService
import com.example.engine.webaiprovider.WebAiSessionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAiProviderSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WebAiProviderManager.init(context)
    }

    val services by WebAiProviderManager.services.collectAsState()

    var showAddServiceDialog by remember { mutableStateOf(false) }
    var serviceForProfileDialog by remember { mutableStateOf<WebAiService?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Web AI Providers")
                        Text(
                            "Configure platforms, URLs & multi-account profiles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddServiceDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Custom Service")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "These services run natively in Chrome Custom Tabs with full OAuth passkey login and zero 403 blocks. Add profiles for personal or work accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(services, key = { it.id }) { service ->
                WebAiServiceCard(
                    service = service,
                    onToggleEnabled = { enabled ->
                        WebAiProviderManager.toggleServiceEnabled(service.id, enabled)
                    },
                    onAddProfile = {
                        serviceForProfileDialog = service
                    },
                    onDeleteProfile = { profileId ->
                        WebAiProviderManager.removeAccountProfile(service.id, profileId)
                    },
                    onDeleteService = {
                        WebAiProviderManager.deleteService(service.id)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { WebAiProviderManager.resetToDefaults() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Built-in Providers to Defaults")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddServiceDialog) {
        AddCustomServiceDialog(
            onDismiss = { showAddServiceDialog = false },
            onAdd = { name, url, colorHex ->
                WebAiProviderManager.addCustomService(name, url, colorHex)
                showAddServiceDialog = false
            }
        )
    }

    serviceForProfileDialog?.let { service ->
        AddAccountProfileDialog(
            service = service,
            onDismiss = { serviceForProfileDialog = null },
            onAdd = { label, url, mode, targetPkg ->
                WebAiProviderManager.addAccountProfile(service.id, label, url, mode, targetPkg)
                serviceForProfileDialog = null
            }
        )
    }
}

@Composable
fun WebAiServiceCard(
    service: WebAiService,
    onToggleEnabled: (Boolean) -> Unit,
    onAddProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteService: () -> Unit
) {
    val brandColor = remember(service.brandColorHex) {
        try {
            Color(AndroidColor.parseColor(service.brandColorHex))
        } catch (e: Exception) {
            Color(0xFF1A73E8)
        }
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (service.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = service.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = brandColor,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = service.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (service.profiles.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${service.profiles.size} accounts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = service.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = service.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profiles summary / accordion toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${service.profiles.size} Account Profile(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                service.profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    profile.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "[${profile.sessionMode.name}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                profile.launchUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (service.profiles.size > 1) {
                            IconButton(
                                onClick = { onDeleteProfile(profile.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove Profile",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onAddProfile) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Account Profile")
                    }

                    if (service.id.startsWith("custom_")) {
                        TextButton(
                            onClick = onDeleteService,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Service")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomServiceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var colorHex by remember { mutableStateOf("#1A73E8") }

    val presetColors = listOf("#10A37F", "#D97706", "#22B8CF", "#1A73E8", "#4D6BFE", "#8B5CF6", "#EC4899", "#111827")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Web AI Service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Service Name (e.g., Qwen, Mistral)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Brand Accent Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetColors.take(5).forEach { hex ->
                        val parsed = try { Color(AndroidColor.parseColor(hex)) } catch (e: Exception) { Color.Blue }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parsed)
                                .clickable { colorHex = hex }
                                .then(
                                    if (colorHex.equals(hex, ignoreCase = true)) {
                                        Modifier.background(parsed)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (colorHex.equals(hex, ignoreCase = true)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onAdd(name.trim(), url.trim(), colorHex)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Add Service")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddAccountProfileDialog(
    service: WebAiService,
    onDismiss: () -> Unit,
    onAdd: (label: String, url: String, mode: WebAiSessionMode, targetPkg: String?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(service.baseUrl) }
    var sessionMode by remember { mutableStateOf(WebAiSessionMode.DEFAULT) }
    var targetPackage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Profile for ${service.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Profile Label (e.g. Work Account)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Launch URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Session Mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sessionMode == WebAiSessionMode.DEFAULT,
                        onClick = { sessionMode = WebAiSessionMode.DEFAULT },
                        label = { Text("Shared") }
                    )
                    FilterChip(
                        selected = sessionMode == WebAiSessionMode.EPHEMERAL,
                        onClick = { sessionMode = WebAiSessionMode.EPHEMERAL },
                        label = { Text("Incognito") }
                    )
                    FilterChip(
                        selected = sessionMode == WebAiSessionMode.CUSTOM_BROWSER,
                        onClick = { sessionMode = WebAiSessionMode.CUSTOM_BROWSER },
                        label = { Text("App Package") }
                    )
                }

                if (sessionMode == WebAiSessionMode.CUSTOM_BROWSER) {
                    OutlinedTextField(
                        value = targetPackage,
                        onValueChange = { targetPackage = it },
                        label = { Text("Browser Package (e.g., com.brave.browser)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank()) {
                        onAdd(label.trim(), url.trim(), sessionMode, targetPackage.ifBlank { null })
                    }
                },
                enabled = label.isNotBlank()
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
