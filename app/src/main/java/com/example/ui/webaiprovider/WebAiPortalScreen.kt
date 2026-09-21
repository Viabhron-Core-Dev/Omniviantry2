package com.example.ui.webaiprovider

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.lifecycle.HeavyTaskThrottler
import com.example.engine.webaiprovider.WebAiAccountProfile
import com.example.engine.webaiprovider.WebAiPresetBundle
import com.example.engine.webaiprovider.WebAiProviderManager
import com.example.engine.webaiprovider.WebAiService
import com.example.engine.webaiprovider.WebAiSessionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAiPortalScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WebAiProviderManager.init(context)
        // Ensure heavy tasks are suspended while in Web AI Hub
        HeavyTaskThrottler.suspendHeavyTasks(context)
    }

    val services by WebAiProviderManager.services.collectAsState()
    val openTabCount by WebAiProviderManager.openTabCount.collectAsState()
    val lastActiveUrl by WebAiProviderManager.lastActiveUrl.collectAsState()
    val lastActiveServiceName by WebAiProviderManager.lastActiveServiceName.collectAsState()
    val isSuspended by HeavyTaskThrottler.isSuspended.collectAsState()
    val freedMemoryMb by HeavyTaskThrottler.lastFreedMemoryMb.collectAsState()

    val enabledServices = remember(services) { services.filter { it.enabled } }
    val selectedServiceIds = remember { mutableStateListOf<String>() }

    // State for Preset launch confirmation dialog
    var pendingPresetToLaunch by remember { mutableStateOf<WebAiPresetBundle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Web AI Hub", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            if (openTabCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("$openTabCount active", fontSize = 10.sp)
                                }
                            }
                        }
                        Text(
                            "Multi-service browser sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        HeavyTaskThrottler.resumeHeavyTasks(context)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Chat")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configure Providers")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedServiceIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedServiceIds.size} selected",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                val toLaunch = enabledServices.filter { selectedServiceIds.contains(it.id) }
                                launchServices(context, toLaunch)
                                WebAiProviderManager.updateOpenTabCount(openTabCount + toLaunch.size)
                                selectedServiceIds.clear()
                            },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Launch Selected (${selectedServiceIds.size})")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Memory Throttler Health Card
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuspended) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSuspended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isSuspended) Icons.Default.Speed else Icons.Default.Memory,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isSuspended) "RAM Throttle Active" else "Standard Engine Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(6.dp))
                                if (isSuspended && freedMemoryMb > 0) {
                                    Text(
                                        "+${freedMemoryMb}MB freed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (isSuspended) {
                                    "Local llama.cpp weights and media players are suspended to maximize RAM headroom for browser tabs."
                                } else {
                                    "Background tasks running normally."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Resume Active Session Card (Decision #3: Resume Last Active Session)
            if (openTabCount > 0 || !lastActiveUrl.isNullOrBlank()) {
                item {
                    val activeName = lastActiveServiceName ?: "Active Session"
                    val activeUrl = lastActiveUrl ?: "https://chatgpt.com"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Tab,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Resume Active Session",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10A37F))
                                    )
                                }
                                Text(
                                    "Last active: $activeName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    launchUrl(context, activeUrl, activeName)
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Quick-Launch Presets Carousel (Mini-Phase 3)
            item {
                Column {
                    Text(
                        "Quick-Launch Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (preset in WebAiProviderManager.defaultPresets) {
                            PresetBundleCard(
                                preset = preset,
                                onClick = {
                                    // Decision #1: Preset launch asks for confirmation
                                    pendingPresetToLaunch = preset
                                }
                            )
                        }
                    }
                }
            }

            // Section Header: Available Providers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AI Web Providers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            if (selectedServiceIds.size == enabledServices.size) {
                                selectedServiceIds.clear()
                            } else {
                                selectedServiceIds.clear()
                                selectedServiceIds.addAll(enabledServices.map { it.id })
                            }
                        }
                    ) {
                        Text(
                            if (selectedServiceIds.size == enabledServices.size) "Deselect All" else "Select All",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Providers Grid / List
            items(enabledServices.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (service in pair) {
                        val isSelected = selectedServiceIds.contains(service.id)
                        Box(modifier = Modifier.weight(1f)) {
                            WebAiServiceCard(
                                service = service,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    if (isSelected) {
                                        selectedServiceIds.remove(service.id)
                                    } else {
                                        selectedServiceIds.add(service.id)
                                    }
                                },
                                onProfileSelected = { profileId ->
                                    WebAiProviderManager.setActiveProfileId(service.id, profileId)
                                },
                                onLaunch = {
                                    launchServices(context, listOf(service))
                                    WebAiProviderManager.updateOpenTabCount(openTabCount + 1)
                                }
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (enabledServices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.WebAssetOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No providers enabled",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = onNavigateToSettings) {
                                Text("Configure in Settings")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Preset Launch Confirmation Dialog (Decision #1)
    pendingPresetToLaunch?.let { preset ->
        val targetServices = remember(preset, enabledServices) {
            if (preset.serviceIds.isEmpty()) {
                enabledServices
            } else {
                enabledServices.filter { preset.serviceIds.contains(it.id) }
            }
        }

        AlertDialog(
            onDismissRequest = { pendingPresetToLaunch = null },
            icon = {
                Icon(
                    when (preset.iconName) {
                        "Search" -> Icons.Default.Search
                        "Code" -> Icons.Default.Code
                        "Psychology" -> Icons.Default.Psychology
                        else -> Icons.Default.Layers
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(preset.title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Launch preset bundle with ${targetServices.size} providers?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    for (svc in targetServices) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(svc.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        launchServices(context, targetServices)
                        WebAiProviderManager.updateOpenTabCount(openTabCount + targetServices.size)
                        pendingPresetToLaunch = null
                    }
                ) {
                    Text("Launch Now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingPresetToLaunch = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PresetBundleCard(
    preset: WebAiPresetBundle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (preset.iconName) {
                            "Search" -> Icons.Default.Search
                            "Code" -> Icons.Default.Code
                            "Psychology" -> Icons.Default.Psychology
                            else -> Icons.Default.Layers
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                preset.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                preset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WebAiServiceCard(
    service: WebAiService,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onLaunch: () -> Unit
) {
    val brandColor = remember(service.brandColorHex) {
        try {
            Color(android.graphics.Color.parseColor(service.brandColorHex))
        } catch (e: Exception) {
            Color(0xFF1A73E8)
        }
    }

    // Active Profile detection
    val activeProfile = remember(service.profiles, service.activeProfileId) {
        service.profiles.firstOrNull { it.id == service.activeProfileId }
            ?: service.profiles.firstOrNull()
    }

    // Dropdown state for profile switcher (Decision #2: Compact dropdown on card)
    var isProfileDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brandColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        service.name.take(2).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        color = brandColor,
                        fontSize = 13.sp
                    )
                }
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                service.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Profile Dropdown Indicator (Decision #2: Dropdown directly on card)
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isProfileDropdownExpanded = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        activeProfile?.label ?: "Default Account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = isProfileDropdownExpanded,
                    onDismissRequest = { isProfileDropdownExpanded = false }
                ) {
                    for (p in service.profiles) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        p.label,
                                        fontWeight = if (p.id == activeProfile?.id) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        p.sessionMode.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onProfileSelected(p.id)
                                isProfileDropdownExpanded = false
                            },
                            leadingIcon = {
                                if (p.id == activeProfile?.id) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Session Mode Pill badge
            val sessionMode = activeProfile?.sessionMode ?: WebAiSessionMode.DEFAULT
            SessionModePill(mode = sessionMode)

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLaunch,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SessionModePill(mode: WebAiSessionMode) {
    val (label, color, icon) = when (mode) {
        WebAiSessionMode.DEFAULT -> Triple("Shared Session", MaterialTheme.colorScheme.primary, Icons.Default.Sync)
        WebAiSessionMode.EPHEMERAL -> Triple("Incognito Mode", Color(0xFF6B7280), Icons.Default.VisibilityOff)
        WebAiSessionMode.CUSTOM_BROWSER -> Triple("External App", Color(0xFF8B5CF6), Icons.Default.OpenInNew)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun launchServices(context: Context, services: List<WebAiService>) {
    if (services.isEmpty()) return
    com.example.engine.webaiprovider.WebAiCustomTabManager.launchSession(context, services)
}

private fun launchUrl(context: Context, url: String, serviceName: String) {
    val service = com.example.engine.webaiprovider.WebAiProviderManager.services.value.firstOrNull { it.baseUrl == url || it.name.equals(serviceName, ignoreCase = true) }
    if (service != null) {
        com.example.engine.webaiprovider.WebAiCustomTabManager.launchSession(context, listOf(service))
    } else {
        val tempService = com.example.engine.webaiprovider.WebAiService(
            id = "custom_" + System.currentTimeMillis(),
            name = serviceName,
            baseUrl = url,
            brandColorHex = "#1A73E8"
        )
        com.example.engine.webaiprovider.WebAiCustomTabManager.launchSession(context, listOf(tempService))
    }
}
