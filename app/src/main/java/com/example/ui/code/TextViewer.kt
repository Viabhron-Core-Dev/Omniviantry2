package com.example.ui.code

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun TextViewer(
    file: File,
    editorState: CodeEditorState,
    onNavigateToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLinePicker by remember { mutableStateOf(false) }
    var commentReferenceToStage by remember { mutableStateOf<StagedReference?>(null) }

    LaunchedEffect(file) {
        editorState.loadFile(file)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (editorState.error != null) {
            Text(
                text = "Error: ${editorState.error}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (editorState.currentFile == file) {
            val scrollState = rememberScrollState()
            val hScrollState = rememberScrollState()
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .then(
                        if (!editorState.isLineWrapEnabled) Modifier.horizontalScroll(hScrollState) 
                        else Modifier
                    )
                    .padding(16.dp)
            ) {
                BasicTextField(
                    value = editorState.content,
                    onValueChange = { 
                        if (!editorState.isLiveGeneration) {
                            editorState.content = it 
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    readOnly = editorState.isLiveGeneration,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = if (editorState.isLineWrapEnabled) Modifier.fillMaxWidth() else Modifier
                )
            }

            // Floating Contextual Action Button (Comment / Stage Lines)
            val basketCount by remember { derivedStateOf { WorkspaceChatBridge.getBasketCount() } }
            FloatingActionButton(
                onClick = {
                    val text = editorState.content.text
                    val sel = editorState.content.selection
                    val hasSelection = sel.start != sel.end && text.isNotEmpty()
                    if (hasSelection) {
                        val selMin = minOf(sel.start, sel.end).coerceIn(0, text.length)
                        val selMax = maxOf(sel.start, sel.end).coerceIn(0, text.length)
                        val startLine = text.substring(0, selMin).count { it == '\n' } + 1
                        val endLine = text.substring(0, selMax).count { it == '\n' } + 1
                        val excerpt = text.substring(selMin, selMax)
                        commentReferenceToStage = StagedReference.CodeLines(
                            file = file,
                            startLine = startLine,
                            endLine = endLine,
                            excerpt = excerpt
                        )
                    } else {
                        showLinePicker = true
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sel = editorState.content.selection
                    val hasSelection = sel.start != sel.end
                    Icon(
                        if (hasSelection) Icons.Default.AddComment else Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (hasSelection) "Comment Selection"
                        else if (basketCount > 0) "Comment Lines ($basketCount)"
                        else "Comment Lines",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Line Selection Dialog for Staging Code
        if (showLinePicker) {
            val totalLines = remember(editorState.content) {
                editorState.content.text.lines().size.coerceAtLeast(1)
            }
            var startLineStr by remember { mutableStateOf("1") }
            var endLineStr by remember { mutableStateOf(minOf(totalLines, 50).toString()) }

            AlertDialog(
                onDismissRequest = { showLinePicker = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Stage Lines to AI Context", style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Select line range from '${file.name}' (Total: $totalLines lines) to add into your active multimodal context basket.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = startLineStr,
                                onValueChange = { startLineStr = it },
                                label = { Text("Start Line") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endLineStr,
                                onValueChange = { endLineStr = it },
                                label = { Text("End Line") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val start = (startLineStr.toIntOrNull() ?: 1).coerceIn(1, totalLines)
                                val end = (endLineStr.toIntOrNull() ?: totalLines).coerceIn(start, totalLines)
                                val lines = editorState.content.text.lines()
                                val excerpt = lines.subList(start - 1, end).joinToString("\n")
                                WorkspaceChatBridge.addStagedReference(
                                    StagedReference.CodeLines(
                                        file = file,
                                        startLine = start,
                                        endLine = end,
                                        excerpt = excerpt
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    "Staged lines $start-$end to AI Basket (Total: ${WorkspaceChatBridge.getBasketCount()})",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showLinePicker = false
                            }
                        ) {
                            Text("Quick Stage")
                        }
                        Button(
                            onClick = {
                                val start = (startLineStr.toIntOrNull() ?: 1).coerceIn(1, totalLines)
                                val end = (endLineStr.toIntOrNull() ?: totalLines).coerceIn(start, totalLines)
                                val lines = editorState.content.text.lines()
                                val excerpt = lines.subList(start - 1, end).joinToString("\n")
                                commentReferenceToStage = StagedReference.CodeLines(
                                    file = file,
                                    startLine = start,
                                    endLine = end,
                                    excerpt = excerpt
                                )
                                showLinePicker = false
                            }
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Comment")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLinePicker = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (commentReferenceToStage != null) {
            ArtifactCommentBottomSheet(
                reference = commentReferenceToStage!!,
                onDismiss = { commentReferenceToStage = null },
                onNavigateToChat = onNavigateToChat
            )
        }
    }
}
