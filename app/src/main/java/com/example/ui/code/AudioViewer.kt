package com.example.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.media.ExoPlayerPool
import java.io.File
import java.util.Locale

/**
 * Audio Player for the Workspace Tab (Mini-Phase 3).
 * Connected directly to the singleton ExoPlayerPool to eliminate codec collisions
 * and manage lifecycle-safe playback with duration and scrub controls.
 */
@Composable
fun AudioViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by ExoPlayerPool.playbackState.collectAsState()
    val isThisFilePlaying = playbackState.activeMediaUri == file.absolutePath && playbackState.isPlaying

    var currentSliderPos by remember { mutableFloatStateOf(0f) }
    var isUserScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.currentPositionMs) {
        if (!isUserScrubbing && playbackState.durationMs > 0) {
            currentSliderPos = playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vinyl / Disc Waveform Disc
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Audiotrack,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${file.length() / 1024} KB • Audio Stream",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // Timecode & Slider
                Slider(
                    value = currentSliderPos,
                    onValueChange = {
                        isUserScrubbing = true
                        currentSliderPos = it
                    },
                    onValueChangeFinished = {
                        isUserScrubbing = false
                        if (playbackState.durationMs > 0) {
                            val targetMs = (currentSliderPos * playbackState.durationMs).toLong()
                            ExoPlayerPool.seekTo(targetMs)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val posSec = (playbackState.currentPositionMs / 1000).toInt()
                    val durSec = (playbackState.durationMs / 1000).toInt()
                    Text(
                        String.format(Locale.US, "%02d:%02d", posSec / 60, posSec % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format(Locale.US, "%02d:%02d", durSec / 60, durSec % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Play / Pause / Speed controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newSpeed = when (playbackState.speed) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                            ExoPlayerPool.setSpeed(newSpeed)
                        }
                    ) {
                        Text("${playbackState.speed}x", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    FilledIconButton(
                        onClick = {
                            ExoPlayerPool.togglePlayPause(context, file.absolutePath)
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isThisFilePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isThisFilePlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            ExoPlayerPool.pause()
                            ExoPlayerPool.seekTo(0L)
                        }
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Mark Audio Timestamp to AI
                OutlinedButton(
                    onClick = {
                        val posMs = playbackState.currentPositionMs
                        val posSec = (posMs / 1000).toInt()
                        val formatted = String.format(java.util.Locale.US, "%02d:%02d", posSec / 60, posSec % 60)
                        WorkspaceChatBridge.addStagedReference(
                            StagedReference.MediaTimestamp(
                                file = file,
                                isVideo = false,
                                timestampMs = posMs,
                                formattedTime = formatted
                            )
                        )
                        android.widget.Toast.makeText(
                            context,
                            "Staged audio @ $formatted to AI Basket (Total: ${WorkspaceChatBridge.getBasketCount()})",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark Timestamp to AI", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
