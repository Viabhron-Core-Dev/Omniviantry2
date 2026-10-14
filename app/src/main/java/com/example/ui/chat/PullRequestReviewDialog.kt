package com.example.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.db.AppDatabase
import com.example.engine.db.WorkspacePullRequestEntity
import com.example.engine.orchestrator.WorkspacePrManager
import kotlinx.coroutines.launch

@Composable
fun PullRequestReviewDialog(
    prId: String,
    onDismiss: () -> Unit,
    onMerged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pr by remember { mutableStateOf<WorkspacePullRequestEntity?>(null) }
    var isMerging by remember { mutableStateOf(false) }

    LaunchedEffect(prId) {
        pr = AppDatabase.getInstance(context).workspacePullRequestDao().getPullRequestById(prId)
    }

    val currentPr = pr ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MergeType,
                            contentDescription = "Pull Request",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = currentPr.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Target: ${currentPr.targetFile ?: "Workspace"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Diff Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    DiffPreviewCard(
                        diffText = currentPr.diff,
                        targetFilePath = currentPr.targetFile,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            isMerging = true
                            scope.launch {
                                val result = WorkspacePrManager.mergePullRequest(context, currentPr.id)
                                isMerging = false
                                when (result) {
                                    is WorkspacePrManager.MergeResult.Success -> {
                                        Toast.makeText(context, "Merged cleanly (${result.hunksApplied} hunks)!", Toast.LENGTH_SHORT).show()
                                        onMerged()
                                        onDismiss()
                                    }
                                    is WorkspacePrManager.MergeResult.Failure -> {
                                        Toast.makeText(context, "Merge failed: ${result.reason}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isMerging && currentPr.status != "merged",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isMerging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Merging...")
                        } else if (currentPr.status == "merged") {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Already Merged")
                        } else {
                            Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve & Merge Diff")
                        }
                    }
                }
            }
        }
    }
}
