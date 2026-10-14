package com.example.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

/**
 * Google AI Studio-style Contextual Comment & Artifact Bottom Sheet.
 * Allows users to attach targeted prompts/directives to any workspace artifact,
 * file, or sliced selection before staging it into the active AI context basket.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactCommentBottomSheet(
    reference: StagedReference,
    onDismiss: () -> Unit,
    onNavigateToChat: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var commentText by remember { mutableStateOf(reference.userComment ?: "") }

    val presetChips = listOf(
        "Refactor",
        "Explain code",
        "Find bugs & edge cases",
        "Add unit tests",
        "Optimize performance",
        "Add documentation",
        "Check security"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("artifact_comment_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Header: Artifact Info & Type Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon: ImageVector = when (reference) {
                            is StagedReference.CodeLines -> Icons.Default.Code
                            is StagedReference.ImageRegion -> Icons.Default.Image
                            is StagedReference.MediaTimestamp -> if (reference.isVideo) Icons.Default.Movie else Icons.Default.GraphicEq
                            is StagedReference.DocumentPage -> Icons.Default.Description
                            is StagedReference.PresentationSlide -> Icons.Default.Slideshow
                            is StagedReference.GenericArtifact -> Icons.Default.InsertDriveFile
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reference.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = reference.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Excerpt preview (if code/slice excerpt is available)
            if (reference is StagedReference.CodeLines && reference.excerpt.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Lines ${reference.startLine} - ${reference.endLine}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = reference.excerpt.lines().take(6).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (reference is StagedReference.ImageRegion && reference.coordinates.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Bounded Region: ${reference.coordinates}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Quick Preset Directive Chips (Google AI Studio style)
            Text(
                "Quick Directives",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetChips.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            commentText = if (commentText.isBlank()) {
                                "$preset for ${reference.fileName}"
                            } else {
                                "$commentText\n• $preset"
                            }
                        },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // User Comment / Instruction Field
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text("Attached Directive / Comment for AI") },
                placeholder = { Text("e.g. Refactor this block to handle coroutine cancellation gracefully") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 160.dp)
                    .testTag("artifact_comment_input"),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (commentText.isNotEmpty()) {
                        IconButton(onClick = { commentText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear comment", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            Spacer(Modifier.height(18.dp))

            // Staging Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val finalComment = commentText.trim().ifEmpty { null }
                        val enrichedRef: StagedReference = when (reference) {
                            is StagedReference.CodeLines -> reference.copy(userComment = finalComment)
                            is StagedReference.ImageRegion -> reference.copy(userComment = finalComment)
                            is StagedReference.MediaTimestamp -> reference.copy(userComment = finalComment)
                            is StagedReference.DocumentPage -> reference.copy(userComment = finalComment)
                            is StagedReference.PresentationSlide -> reference.copy(userComment = finalComment)
                            is StagedReference.GenericArtifact -> reference.copy(userComment = finalComment)
                        }
                        WorkspaceChatBridge.addOrUpdateStagedReference(enrichedRef)
                        Toast.makeText(
                            context,
                            "Staged '${reference.fileName}' to Basket (${WorkspaceChatBridge.getBasketCount()})",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("stage_to_basket_button")
                ) {
                    Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to Basket", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        val finalComment = commentText.trim().ifEmpty { null }
                        val enrichedRef: StagedReference = when (reference) {
                            is StagedReference.CodeLines -> reference.copy(userComment = finalComment)
                            is StagedReference.ImageRegion -> reference.copy(userComment = finalComment)
                            is StagedReference.MediaTimestamp -> reference.copy(userComment = finalComment)
                            is StagedReference.DocumentPage -> reference.copy(userComment = finalComment)
                            is StagedReference.PresentationSlide -> reference.copy(userComment = finalComment)
                            is StagedReference.GenericArtifact -> reference.copy(userComment = finalComment)
                        }
                        WorkspaceChatBridge.addOrUpdateStagedReference(enrichedRef)
                        Toast.makeText(
                            context,
                            "Staged '${reference.fileName}' with comment",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                        onNavigateToChat?.invoke()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("stage_and_chat_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stage & Chat", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}
