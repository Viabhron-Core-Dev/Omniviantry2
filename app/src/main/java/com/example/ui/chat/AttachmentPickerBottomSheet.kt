package com.example.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerBottomSheet(
    onDismiss: () -> Unit,
    onOptionSelected: (AttachmentOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showGithubDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onOptionSelected(AttachmentOption.ImageUri(it)) }
        if (uri != null) onDismiss()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onOptionSelected(AttachmentOption.FileUri(it)) }
        if (uri != null) onDismiss()
    }

    if (showGithubDialog) {
        var repoUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGithubDialog = false },
            title = { Text("Import from GitHub") },
            text = {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("Repository URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGithubDialog = false
                        onOptionSelected(AttachmentOption.GithubRepo(repoUrl))
                        onDismiss()
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGithubDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add Attachment",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Row 1: Camera, Gallery/Images, Files, Knowledge Bits
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GeminiSquircleTile(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFEFF6FF),
                    iconTint = androidx.compose.ui.graphics.Color(0xFF2563EB),
                    onClick = {
                        onOptionSelected(AttachmentOption.LaunchCamera)
                        onDismiss()
                    }
                )
                GeminiSquircleTile(
                    icon = Icons.Default.Image,
                    label = "Gallery",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F3FF),
                    iconTint = androidx.compose.ui.graphics.Color(0xFF7C3AED),
                    onClick = { imagePickerLauncher.launch("image/*") }
                )
                GeminiSquircleTile(
                    icon = Icons.Default.InsertDriveFile,
                    label = "Files",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFECFDF5),
                    iconTint = androidx.compose.ui.graphics.Color(0xFF059669),
                    onClick = { filePickerLauncher.launch("*/*") }
                )
                GeminiSquircleTile(
                    icon = Icons.Default.AutoAwesome,
                    label = "Knowledge",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFFFBEB),
                    iconTint = androidx.compose.ui.graphics.Color(0xFFD97706),
                    onClick = {
                        onOptionSelected(AttachmentOption.KnowledgeBits)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: GitHub, Google Drive, Workspace, Audio Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GeminiSquircleTile(
                    icon = Icons.Default.Code,
                    label = "GitHub",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                    iconTint = androidx.compose.ui.graphics.Color(0xFF334155),
                    onClick = { showGithubDialog = true }
                )
                GeminiSquircleTile(
                    icon = Icons.Default.Cloud,
                    label = "Drive",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF0FDF4),
                    iconTint = androidx.compose.ui.graphics.Color(0xFF16A34A),
                    onClick = { 
                        onOptionSelected(AttachmentOption.GoogleDrive)
                        onDismiss()
                    }
                )
                GeminiSquircleTile(
                    icon = Icons.Default.Folder,
                    label = "Workspace",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFEF2F2),
                    iconTint = androidx.compose.ui.graphics.Color(0xFFDC2626),
                    onClick = { 
                        onOptionSelected(AttachmentOption.Workspace)
                        onDismiss()
                    }
                )
                GeminiSquircleTile(
                    icon = Icons.Default.GraphicEq,
                    label = "Audio",
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFAF5FF),
                    iconTint = androidx.compose.ui.graphics.Color(0xFF9333EA),
                    onClick = { 
                        onOptionSelected(AttachmentOption.AudioSettings)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun GeminiSquircleTile(
    icon: ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = containerColor,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

sealed class AttachmentOption {
    object LaunchCamera : AttachmentOption()
    data class ImageUri(val uri: Uri) : AttachmentOption()
    data class FileUri(val uri: Uri) : AttachmentOption()
    object KnowledgeBits : AttachmentOption()
    data class GithubRepo(val url: String) : AttachmentOption()
    object GoogleDrive : AttachmentOption()
    object Workspace : AttachmentOption()
    object AudioSettings : AttachmentOption()
}
