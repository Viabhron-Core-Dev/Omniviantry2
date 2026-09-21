package com.example.ui.artifacts

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.db.AppDatabase
import com.example.engine.db.ArtifactEntity
import com.example.engine.pwa.PwaAppSettings
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Dedicated Full-Screen Settings Page for an individual PWA / Artifact.
 * Supports:
 * 1. Storage & Cache Cleaning (WebView cache, IndexedDB / WebStorage, file sizes).
 * 2. Persistent Network Speed & Latency Control (across sessions via settingsJson).
 * 3. Icon & Name Customization (gallery photo picker & rename).
 * 4. Display preferences (Keep Screen On, Orientation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactSettingsScreen(
    artifact: ArtifactEntity,
    onBack: () -> Unit,
    onArtifactUpdated: (ArtifactEntity) -> Unit,
    onArtifactDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var titleText by remember { mutableStateOf(artifact.title) }
    var currentIconUri by remember { mutableStateOf(artifact.iconUri) }

    // Parse persistent settings
    val currentSettings = remember(artifact.settingsJson) {
        PwaAppSettings.fromJson(artifact.settingsJson)
    }
    var networkThrottle by remember { mutableStateOf(currentSettings.networkThrottle) }
    var keepScreenOn by remember { mutableStateOf(currentSettings.keepScreenOn) }
    var orientation by remember { mutableStateOf(currentSettings.orientation) }

    // Storage measurements
    var repoSizeBytes by remember { mutableLongStateOf(0L) }
    var iconSizeBytes by remember { mutableLongStateOf(0L) }
    var isCalculatingStorage by remember { mutableStateOf(true) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showResetStorageConfirmDialog by remember { mutableStateOf(false) }

    fun calculateSizes() {
        scope.launch(Dispatchers.IO) {
            isCalculatingStorage = true
            val repoDir = File(context.filesDir, "artifacts/${artifact.id}")
            val iconFile = currentIconUri?.let { File(it) }

            fun getFolderSize(dir: File): Long {
                var size = 0L
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        size += if (file.isDirectory) getFolderSize(file) else file.length()
                    }
                }
                return size
            }

            val rSize = getFolderSize(repoDir)
            val iSize = if (iconFile != null && iconFile.exists()) iconFile.length() else 0L

            withContext(Dispatchers.Main) {
                repoSizeBytes = rSize
                iconSizeBytes = iSize
                isCalculatingStorage = false
            }
        }
    }

    LaunchedEffect(artifact.id, currentIconUri) {
        calculateSizes()
    }

    // Photo Picker launcher to select custom app icon
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val iconsDir = File(context.filesDir, "pwa_icons")
                    if (!iconsDir.exists()) iconsDir.mkdirs()
                    val destFile = File(iconsDir, "${artifact.id}_custom_icon.png")

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val updatedIconPath = destFile.absolutePath
                    val updatedEntity = artifact.copy(
                        iconUri = updatedIconPath,
                        updatedAt = System.currentTimeMillis()
                    )
                    AppDatabase.getDatabase(context).artifactDao().updateArtifact(updatedEntity)
                    withContext(Dispatchers.Main) {
                        currentIconUri = updatedIconPath
                        onArtifactUpdated(updatedEntity)
                        Toast.makeText(context, "App icon updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    LogKeeper.log("INFO", "ArtifactSettings", "Updated icon for '${artifact.title}'")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to update icon: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    LogKeeper.log("ERROR", "ArtifactSettings", "Icon update error: ${e.message}")
                }
            }
        }
    }

    fun saveSettings(
        newThrottle: String = networkThrottle,
        newKeepScreenOn: Boolean = keepScreenOn,
        newOrientation: String = orientation,
        newTitle: String = titleText
    ) {
        scope.launch(Dispatchers.IO) {
            val latency = PwaAppSettings.getPresetLatency(newThrottle)
            val updatedSettings = PwaAppSettings(
                networkThrottle = newThrottle,
                latencyMs = latency,
                keepScreenOn = newKeepScreenOn,
                orientation = newOrientation,
                displayMode = currentSettings.displayMode
            )
            val updatedJson = PwaAppSettings.toJson(updatedSettings)
            val updatedEntity = artifact.copy(
                title = newTitle.trim(),
                iconUri = currentIconUri,
                settingsJson = updatedJson,
                updatedAt = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).artifactDao().updateArtifact(updatedEntity)
            withContext(Dispatchers.Main) {
                onArtifactUpdated(updatedEntity)
            }
            LogKeeper.log("INFO", "ArtifactSettings", "Saved settings for '${artifact.title}': throttle=$newThrottle, screenOn=$newKeepScreenOn")
        }
    }

    val iconBitmap = remember(currentIconUri) {
        if (!currentIconUri.isNullOrBlank()) {
            val f = File(currentIconUri!!)
            if (f.exists() && f.isFile) {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            } else null
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifact Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        saveSettings()
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Identity & Name / Icon Customization
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "App Identity & Icon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = "App Icon",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = if (artifact.type == "PWA") Icons.Default.Web else Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Edit badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = titleText,
                                onValueChange = {
                                    titleText = it
                                    saveSettings(newTitle = it)
                                },
                                label = { Text("App Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap the icon to pick a custom image from gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 2: Internet Speed & Network Throttling
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Internet Speed & Connectivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Simulate real-world network conditions. This setting is saved and automatically applied across sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val throttleOptions = listOf(
                        PwaAppSettings.THROTTLE_NO_LIMIT to "No Limit (Full Speed)",
                        PwaAppSettings.THROTTLE_FAST_3G to "Fast 3G (150ms delay)",
                        PwaAppSettings.THROTTLE_SLOW_3G to "Slow 3G (450ms delay)",
                        PwaAppSettings.THROTTLE_EDGE to "Edge / 2G (850ms delay)",
                        PwaAppSettings.THROTTLE_OFFLINE to "Offline Mode (Block all internet)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        throttleOptions.forEach { (key, label) ->
                            val isSelected = networkThrottle == key
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        networkThrottle = key
                                        saveSettings(newThrottle = key)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                networkThrottle = key
                                                saveSettings(newThrottle = key)
                                            }
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }

                                    if (key == PwaAppSettings.THROTTLE_OFFLINE) {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Storage & Cache Cleaning
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Cache & Storage Management",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("App Package Files:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (isCalculatingStorage) "..." else formatBytes(repoSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("App Icon:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (isCalculatingStorage) "..." else formatBytes(iconSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button 1: Clear WebView Cache
                        OutlinedButton(
                            onClick = {
                                try {
                                    val dummyWebView = WebView(context)
                                    dummyWebView.clearCache(true)
                                    dummyWebView.destroy()
                                    Toast.makeText(context, "WebView cache cleared", Toast.LENGTH_SHORT).show()
                                    LogKeeper.log("INFO", "ArtifactSettings", "Cleared web cache for '${artifact.title}'")
                                    calculateSizes()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error clearing cache: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cached, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear Cache", fontSize = 12.sp)
                        }

                        // Button 2: Reset App Storage (IndexedDB/WebStorage)
                        Button(
                            onClick = { showResetStorageConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset Data", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Section 4: Display & Player Preferences
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Display Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Keep Screen On", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Prevent device display from sleeping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = {
                                keepScreenOn = it
                                saveSettings(newKeepScreenOn = it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Default Orientation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = orientation == "unspecified",
                                onClick = {
                                    orientation = "unspecified"
                                    saveSettings(newOrientation = "unspecified")
                                },
                                label = { Text("Auto / Sensor") }
                            )
                            FilterChip(
                                selected = orientation == "portrait",
                                onClick = {
                                    orientation = "portrait"
                                    saveSettings(newOrientation = "portrait")
                                },
                                label = { Text("Portrait") }
                            )
                            FilterChip(
                                selected = orientation == "landscape",
                                onClick = {
                                    orientation = "landscape"
                                    saveSettings(newOrientation = "landscape")
                                },
                                label = { Text("Landscape") }
                            )
                        }
                    }
                }
            }

            // Section 5: Danger Zone
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Danger Zone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = "Permanently remove this artifact and delete all extracted files, icons, and databases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Artifact")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Reset Data Confirmation Dialog
    if (showResetStorageConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetStorageConfirmDialog = false },
            title = { Text("Reset Web Storage?") },
            text = {
                Text("This will wipe all cookies, LocalStorage, and IndexedDB records for this app. The app code files will NOT be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetStorageConfirmDialog = false
                        try {
                            WebStorage.getInstance().deleteAllData()
                            Toast.makeText(context, "Web storage cleared", Toast.LENGTH_SHORT).show()
                            LogKeeper.log("INFO", "ArtifactSettings", "Deleted all WebStorage origins for '${artifact.title}'")
                            calculateSizes()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetStorageConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Artifact?") },
            text = {
                Text("Are you sure you want to permanently delete '${artifact.title}'? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch(Dispatchers.IO) {
                            // 1. Delete files
                            val artifactDir = File(context.filesDir, "artifacts/${artifact.id}")
                            if (artifactDir.exists()) artifactDir.deleteRecursively()
                            // 2. Delete icon
                            if (!artifact.iconUri.isNullOrBlank()) {
                                val iconF = File(artifact.iconUri)
                                if (iconF.exists()) iconF.delete()
                            }
                            // 3. Delete Room entry
                            AppDatabase.getDatabase(context).artifactDao().deleteById(artifact.id)
                            LogKeeper.log("INFO", "ArtifactSettings", "Deleted artifact '${artifact.title}' (${artifact.id})")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Artifact deleted", Toast.LENGTH_SHORT).show()
                                onArtifactDeleted()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups])
}
