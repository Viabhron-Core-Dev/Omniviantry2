package com.example.ui.code

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Native Skia GPU-accelerated Image Viewer (Mini-Phase 3 Deliverable 1).
 * Supports:
 * - 120 FPS pinch-to-zoom, pan, 90-degree rotation.
 * - Reset zoom/pan.
 * - Interactive rectangular Bounding Box selection markup with "Mark-to-Chat" bridge.
 */
@Composable
fun ImageViewer(
    file: File,
    onPassSelectionToChat: ((boundingBoxCoords: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isMarkupMode by remember { mutableStateOf(false) }

    // Bounding Box markup state (coordinates in normalized 0..1 or screen coords)
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxEnd by remember { mutableStateOf<Offset?>(null) }

    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (!isMarkupMode) {
            scale = (scale * zoomChange).coerceIn(0.5f, 6.0f)
            offset += offsetChange
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isMarkupMode) {
                            Modifier.transformable(state = transformState)
                        } else {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { startPt ->
                                        boxStart = startPt
                                        boxEnd = startPt
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        boxEnd = change.position
                                    }
                                )
                            }
                        }
                    )
            ) {
                // Drawing image via Compose Canvas (backed by Skia GPU pipeline)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotationAngle
                        )
                ) {
                    val bmp = bitmap ?: return@Canvas
                    val dstWidth = size.width
                    val dstHeight = (bmp.height.toFloat() / bmp.width.toFloat()) * dstWidth
                    val top = (size.height - dstHeight) / 2f
                    val left = 0f

                    drawImage(
                        image = bmp,
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(dstWidth.toInt(), dstHeight.toInt())
                    )
                }

                // Bounding Box overlay in Markup Mode
                if (isMarkupMode && boxStart != null && boxEnd != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = boxStart!!
                        val e = boxEnd!!
                        val left = minOf(s.x, e.x)
                        val top = minOf(s.y, e.y)
                        val width = kotlin.math.abs(s.x - e.x)
                        val height = kotlin.math.abs(s.y - e.y)

                        drawRect(
                            color = Color(0x332196F3),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                        drawRect(
                            color = Color(0xFF2196F3),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Loading image...", color = Color.White)
            }
        }

        // Top Control Overlay Chip
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.7f),
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                        boxStart = null
                        boxEnd = null
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                VerticalDivider(modifier = Modifier.height(16.dp), color = Color.Gray)
                FilterChip(
                    selected = isMarkupMode,
                    onClick = {
                        isMarkupMode = !isMarkupMode
                        if (!isMarkupMode) {
                            boxStart = null
                            boxEnd = null
                        }
                    },
                    label = { Text(if (isMarkupMode) "Markup: ON" else "Select Region", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // Markup Action Pill when Bounding Box exists
        if (isMarkupMode && boxStart != null && boxEnd != null) {
            val width = kotlin.math.abs(boxStart!!.x - boxEnd!!.x)
            val height = kotlin.math.abs(boxStart!!.y - boxEnd!!.y)
            if (width > 20 && height > 20) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Region Selected [${width.toInt()}x${height.toInt()}px]",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = {
                                val s = boxStart!!
                                val e = boxEnd!!
                                val coords = "x1=${minOf(s.x, e.x).toInt()}, y1=${minOf(s.y, e.y).toInt()}, w=${width.toInt()}, h=${height.toInt()}"
                                val promptText = "@artifact/${file.name} [Region: $coords] Please examine or modify this section of the image."
                                WorkspaceChatBridge.stagePrompt(promptText)
                                WorkspaceChatBridge.addStagedReference(
                                    StagedReference.ImageRegion(
                                        file = file,
                                        coordinates = coords,
                                        description = "Region [${width.toInt()}x${height.toInt()}px]"
                                    )
                                )
                                onPassSelectionToChat?.invoke(coords)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark to Chat", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
