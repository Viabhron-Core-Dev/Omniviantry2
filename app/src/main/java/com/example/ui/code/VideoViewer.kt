package com.example.ui.code

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Native hardware-accelerated Video Player (Mini-Phase 3).
 * Uses Android VideoView + MediaCodec hardware acceleration with standard media transport controls.
 */
@Composable
fun VideoViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var activeCommentRef by remember { mutableStateOf<StagedReference?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                    }
                    videoViewInstance = this
                }
            },
            update = { view ->
                // Handled via callbacks
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating info & Mark to AI badge at bottom
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(file.name, color = Color.White, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("${file.length() / 1024} KB", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val posMs = videoViewInstance?.currentPosition?.toLong() ?: 0L
                        val posSec = (posMs / 1000).toInt()
                        val formatted = String.format(java.util.Locale.US, "%02d:%02d", posSec / 60, posSec % 60)
                        WorkspaceChatBridge.addStagedReference(
                            StagedReference.MediaTimestamp(
                                file = file,
                                isVideo = true,
                                timestampMs = posMs,
                                formattedTime = formatted
                            )
                        )
                        android.widget.Toast.makeText(
                            context,
                            "Staged video @ $formatted to AI Basket (Total: ${WorkspaceChatBridge.getBasketCount()})",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mark", fontSize = 11.sp)
                }

                Spacer(Modifier.width(6.dp))

                FilledTonalButton(
                    onClick = {
                        val posMs = videoViewInstance?.currentPosition?.toLong() ?: 0L
                        val posSec = (posMs / 1000).toInt()
                        val formatted = String.format(java.util.Locale.US, "%02d:%02d", posSec / 60, posSec % 60)
                        activeCommentRef = StagedReference.MediaTimestamp(
                            file = file,
                            isVideo = true,
                            timestampMs = posMs,
                            formattedTime = formatted
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Comment", fontSize = 11.sp)
                }
            }
        }

        if (activeCommentRef != null) {
            ArtifactCommentBottomSheet(
                reference = activeCommentRef!!,
                onDismiss = { activeCommentRef = null }
            )
        }
    }
}
