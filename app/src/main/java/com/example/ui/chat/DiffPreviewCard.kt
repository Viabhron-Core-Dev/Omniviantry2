package com.example.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.brain.PatchApplier
import com.example.engine.brain.UnifiedDiffParser
import kotlinx.coroutines.launch

/**
 * Material 3 UI component presenting a unified diff preview with red deletions,
 * green additions, hunk statistics, and a 1-tap "Review & Apply" preview action.
 */
@Composable
fun DiffPreviewCard(
    diffText: String,
    modifier: Modifier = Modifier,
    targetFilePath: String? = null,
    onApplyComplete: ((Boolean, String) -> Unit)? = null
) {
    if (diffText.isBlank()) return

    val parsedFiles = remember(diffText) { UnifiedDiffParser.parse(diffText) }
    val primaryFile = parsedFiles.firstOrNull()

    val totalAdditions = remember(parsedFiles) { parsedFiles.sumOf { it.totalAdditions } }
    val totalDeletions = remember(parsedFiles) { parsedFiles.sumOf { it.totalDeletions } }
    val resolvedPath = remember(parsedFiles, targetFilePath) {
        primaryFile?.targetPath?.takeIf { it.isNotBlank() } ?: targetFilePath ?: "Patch Diff"
    }

    var isExpanded by remember { mutableStateOf(true) }
    var isApplying by remember { mutableStateOf(false) }
    var applyResult by remember { mutableStateOf<PatchApplier.PatchResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "diff_expand_arrow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("diff_preview_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: File Name, Stats Badges, Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Diff",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedPath,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${primaryFile?.hunks?.size ?: 0} hunk(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Green Additions Pill
                if (totalAdditions > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "+$totalAdditions",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Red Deletions Pill
                if (totalDeletions > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFC62828).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "-$totalDeletions",
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("diff_toggle_expand")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse diff" else "Expand diff",
                        modifier = Modifier.rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable Diff Lines View
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            if (parsedFiles.isEmpty()) {
                                Text(
                                    text = diffText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                parsedFiles.forEach { file ->
                                    file.hunks.forEach { hunk ->
                                        // Hunk Header Banner
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "@@ -${hunk.oldStart},${hunk.oldLength} +${hunk.newStart},${hunk.newLength} @@",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (hunk.sectionHeader.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = hunk.sectionHeader,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        // Hunk Diff Lines
                                        hunk.lines.forEach { line ->
                                            val (bgColor, textColor, prefixChar) = when (line.type) {
                                                UnifiedDiffParser.DiffLineType.ADD -> Triple(
                                                    Color(0xFF2E7D32).copy(alpha = 0.16f),
                                                    Color(0xFF2E7D32),
                                                    "+"
                                                )
                                                UnifiedDiffParser.DiffLineType.DELETE -> Triple(
                                                    Color(0xFFC62828).copy(alpha = 0.16f),
                                                    Color(0xFFC62828),
                                                    "-"
                                                )
                                                UnifiedDiffParser.DiffLineType.CONTEXT -> Triple(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                    " "
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(bgColor)
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Line numbers
                                                Text(
                                                    text = (line.oldLineNumber?.toString() ?: "").padStart(4),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                    modifier = Modifier.width(28.dp)
                                                )
                                                Text(
                                                    text = (line.newLineNumber?.toString() ?: "").padStart(4),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                    modifier = Modifier.width(28.dp)
                                                )
                                                // Marker prefix
                                                Text(
                                                    text = prefixChar,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = textColor,
                                                    modifier = Modifier.width(16.dp)
                                                )
                                                // Code content
                                                Text(
                                                    text = line.content,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Status Message Display
                    statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val isSuccess = applyResult is PatchApplier.PatchResult.Success
                        val statusColor = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = statusColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = statusColor.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Row: "Review & Apply" + Copy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(diffText))
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Diff",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Diff", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val isApplied = applyResult is PatchApplier.PatchResult.Success
                        Button(
                            onClick = {
                                scope.launch {
                                    isApplying = true
                                    statusMessage = null
                                    val res = PatchApplier.applyPatch(
                                        diffText = diffText,
                                        targetFilePathOverride = targetFilePath
                                    )
                                    applyResult = res
                                    isApplying = false

                                    when (res) {
                                        is PatchApplier.PatchResult.Success -> {
                                            statusMessage = "Successfully applied ${res.hunksApplied} hunk(s) to ${res.relativePath}"
                                            onApplyComplete?.invoke(true, statusMessage!!)
                                        }
                                        is PatchApplier.PatchResult.Failure -> {
                                            statusMessage = res.reason
                                            onApplyComplete?.invoke(false, res.reason)
                                        }
                                    }
                                }
                            },
                            enabled = !isApplying && !isApplied,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isApplied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("apply_patch_button")
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Applying...", fontSize = 12.sp)
                            } else if (isApplied) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Applied",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Applied", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Review & Apply",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Review & Apply", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
