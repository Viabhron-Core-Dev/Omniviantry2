package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.media.ExoPlayerPool
import com.example.utils.LogKeeper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

/**
 * Categorization of non-text outputs in Chat according to the "Non-Text Master Rule".
 */
enum class ArtifactType {
    HTML_WEB_APP,
    AUDIO_MEDIA,
    VIDEO_MEDIA,
    IMAGE_MEDIA,
    SLIDES_PPT,
    DOCUMENT_PDF,
    MODEL_3D,
    CODE_FILE,
    GENERIC_FILE
}

data class InChatArtifactInfo(
    val title: String,
    val filePath: String,
    val type: ArtifactType,
    val sizeBytes: Long = 0L,
    val fileExtension: String = "",
    val snippetPreview: String? = null
)

object ArtifactExtractor {

    private val CODE_EXTENSIONS = setOf(
        "kt", "java", "py", "js", "ts", "jsx", "tsx", "html", "css", "json", "xml",
        "sql", "c", "cpp", "h", "hpp", "rs", "go", "sh", "yaml", "yml", "gradle", "kts"
    )

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "ogg", "aac", "flac")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp")
    private val PPT_EXTENSIONS = setOf("ppt", "pptx")
    private val PDF_EXTENSIONS = setOf("pdf")
    private val MODEL_3D_EXTENSIONS = setOf("gltf", "glb", "stl", "obj", "scad", "3mf")

    /**
     * Extracts non-text artifacts from plain text strings (e.g. AI message body or file paths).
     */
    fun extractArtifacts(textToScan: String, workspaceBaseDir: File? = null): List<InChatArtifactInfo> {
        val results = mutableListOf<InChatArtifactInfo>()
        val seenPaths = mutableSetOf<String>()

        val fileRegex = Regex("""(?:TargetFile|target_file|file_path|path|TargetContent|filename)["']?\s*[:=]\s*["']([^"'\n]+)["']""", RegexOption.IGNORE_CASE)
        fileRegex.findAll(textToScan).forEach { match ->
            val candidatePath = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (candidatePath.isNotBlank() && candidatePath.contains(".") && seenPaths.add(candidatePath)) {
                resolveArtifact(candidatePath, workspaceBaseDir)?.let { results.add(it) }
            }
        }

        val genericPathRegex = Regex("""(?:/[a-zA-Z0-9_.\-]+)+/([a-zA-Z0-9_.\-]+\.([a-zA-Z0-9]{2,5}))""")
        genericPathRegex.findAll(textToScan).forEach { match ->
            val fullMatch = match.value
            if (seenPaths.add(fullMatch)) {
                resolveArtifact(fullMatch, workspaceBaseDir)?.let { results.add(it) }
            }
        }

        // Also check if textToScan is itself a direct path or filename
        val trimmed = textToScan.trim()
        if (trimmed.contains(".") && !trimmed.contains("\n") && seenPaths.add(trimmed)) {
            resolveArtifact(trimmed, workspaceBaseDir)?.let { results.add(it) }
        }

        return results
    }

    /**
     * Extracts non-text artifacts from tool call/output or message content.
     */
    fun extractArtifactsFromMessage(
        message: ChatMessage,
        workspaceBaseDir: File?
    ): List<InChatArtifactInfo> {
        val results = mutableListOf<InChatArtifactInfo>()
        val seenPaths = mutableSetOf<String>()

        // 1. From edited files list
        message.editedFiles.forEach { (path, _) ->
            if (path.isNotBlank() && seenPaths.add(path)) {
                resolveArtifact(path, workspaceBaseDir)?.let { results.add(it) }
            }
        }

        // 2. From tool args JSON or output (e.g. create_file, edit_file, generate_image, save_file)
        val textToScan = listOfNotNull(message.toolArgsJson, message.toolOutput, message.text).joinToString("\n")
        val fileRegex = Regex("""(?:TargetFile|target_file|file_path|path|TargetContent|filename)["']?\s*[:=]\s*["']([^"'\n]+)["']""", RegexOption.IGNORE_CASE)
        fileRegex.findAll(textToScan).forEach { match ->
            val candidatePath = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (candidatePath.isNotBlank() && candidatePath.contains(".") && seenPaths.add(candidatePath)) {
                resolveArtifact(candidatePath, workspaceBaseDir)?.let { results.add(it) }
            }
        }

        // 3. Scan for direct absolute or relative paths with extensions in text
        val genericPathRegex = Regex("""(?:/[\w.\-]+)+/([\w.\-]+\.([a-zA-Z0-9]{2,5}))""")
        genericPathRegex.findAll(textToScan).forEach { match ->
            val fullMatch = match.value
            if (seenPaths.add(fullMatch)) {
                resolveArtifact(fullMatch, workspaceBaseDir)?.let { results.add(it) }
            }
        }

        return results
    }

    private fun resolveArtifact(rawPath: String, workspaceBaseDir: File?): InChatArtifactInfo? {
        val candidate = File(rawPath)
        val actualFile = when {
            candidate.isAbsolute && candidate.exists() -> candidate
            workspaceBaseDir != null -> {
                val relative = File(workspaceBaseDir, rawPath.removePrefix("/"))
                if (relative.exists()) relative else candidate
            }
            else -> candidate
        }

        val name = actualFile.name.ifBlank { rawPath.substringAfterLast("/") }
        val ext = name.substringAfterLast(".", "").lowercase(Locale.ROOT)
        if (ext.isBlank()) return null

        val type = when {
            ext == "html" || ext == "htm" -> ArtifactType.HTML_WEB_APP
            ext in AUDIO_EXTENSIONS -> ArtifactType.AUDIO_MEDIA
            ext in VIDEO_EXTENSIONS -> ArtifactType.VIDEO_MEDIA
            ext in IMAGE_EXTENSIONS -> ArtifactType.IMAGE_MEDIA
            ext in PPT_EXTENSIONS -> ArtifactType.SLIDES_PPT
            ext in PDF_EXTENSIONS -> ArtifactType.DOCUMENT_PDF
            ext in MODEL_3D_EXTENSIONS -> ArtifactType.MODEL_3D
            ext in CODE_EXTENSIONS -> ArtifactType.CODE_FILE
            else -> ArtifactType.GENERIC_FILE
        }

        val size = if (actualFile.exists()) actualFile.length() else 0L

        return InChatArtifactInfo(
            title = name,
            filePath = actualFile.absolutePath,
            type = type,
            sizeBytes = size,
            fileExtension = ext
        )
    }
}

/**
 * Universal Tier-1 In-Chat Artifact Card
 * Renders non-text files as interactive, visually polished cards within the chat stream.
 */
@Composable
fun ArtifactChatCard(
    artifact: InChatArtifactInfo,
    onOpenFullscreen: (InChatArtifactInfo) -> Unit,
    onOpenInWorkspace: (InChatArtifactInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Type Icon, Title, Size Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getArtifactContainerColor(artifact.type),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getArtifactIcon(artifact.type),
                            contentDescription = artifact.type.name,
                            tint = getArtifactIconTint(artifact.type),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = getArtifactTypeName(artifact.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (artifact.sizeBytes > 0) {
                            Text(
                                text = " • ${formatFileSize(artifact.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Specialized Mini Preview per Artifact Type
            when (artifact.type) {
                ArtifactType.AUDIO_MEDIA -> {
                    AudioMiniPreviewCard(artifact = artifact)
                }
                ArtifactType.VIDEO_MEDIA -> {
                    VideoMiniPreviewCard(artifact = artifact, onPlayClick = { onOpenFullscreen(artifact) })
                }
                ArtifactType.IMAGE_MEDIA -> {
                    ImageMiniPreviewCard(artifact = artifact, onClick = { onOpenFullscreen(artifact) })
                }
                ArtifactType.HTML_WEB_APP -> {
                    WebMiniPreviewCard(artifact = artifact, onLaunchClick = { onOpenFullscreen(artifact) })
                }
                ArtifactType.SLIDES_PPT -> {
                    PresentationMiniPreviewCard(artifact = artifact, onOpenSlides = { onOpenFullscreen(artifact) })
                }
                ArtifactType.DOCUMENT_PDF -> {
                    DocumentMiniPreviewCard(artifact = artifact, onOpenDoc = { onOpenFullscreen(artifact) })
                }
                ArtifactType.MODEL_3D -> {
                    Model3DMiniPreviewCard(artifact = artifact, onInspectClick = { onOpenFullscreen(artifact) })
                }
                ArtifactType.CODE_FILE, ArtifactType.GENERIC_FILE -> {
                    CodeMiniPreviewCard(artifact = artifact, onInspectClick = { onOpenFullscreen(artifact) })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions Row: Open Fullscreen & Open in Workspace Tab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        LogKeeper.log("ArtifactChatCard", "OpenWorkspace", "Routing to workspace: ${artifact.filePath}")
                        onOpenInWorkspace(artifact)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Workspace", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        LogKeeper.log("ArtifactChatCard", "OpenFullscreen", "Opening sheet: ${artifact.filePath}")
                        onOpenFullscreen(artifact)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Dedicated In-Chat Mini Audio Player backed by ExoPlayerPool
 */
@Composable
private fun AudioMiniPreviewCard(artifact: InChatArtifactInfo) {
    val context = LocalContext.current
    val playbackState by ExoPlayerPool.playbackState.collectAsState()
    val isThisActive = playbackState.activeMediaUri == artifact.filePath
    val isPlaying = isThisActive && playbackState.isPlaying

    var currentPos by remember { mutableStateOf(0L) }

    LaunchedEffect(isThisActive, isPlaying) {
        while (isActive && isThisActive && isPlaying) {
            currentPos = ExoPlayerPool.getCurrentPosition()
            delay(250)
        }
    }

    val duration = if (isThisActive && playbackState.durationMs > 0) playbackState.durationMs else 1L
    val progress = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        ExoPlayerPool.togglePlayPause(context, artifact.filePath)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = progress,
                        onValueChange = { newProg ->
                            val targetMs = (newProg * duration).toLong()
                            currentPos = targetMs
                            if (isThisActive) {
                                ExoPlayerPool.seekTo(targetMs)
                            }
                        },
                        modifier = Modifier.height(20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTimeMs(currentPos),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (playbackState.durationMs > 0 && isThisActive) formatTimeMs(playbackState.durationMs) else "Audio Track",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Playback speed pill
                val currentSpeed = if (isThisActive) playbackState.speed else 1.0f
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        val nextSpeed = when (currentSpeed) {
                            1.0f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        ExoPlayerPool.setSpeed(nextSpeed)
                    }
                ) {
                    Text(
                        text = "${currentSpeed}x",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoMiniPreviewCard(artifact: InChatArtifactInfo, onPlayClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayCircleFilled,
                        contentDescription = "Play Video",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Video Stream (${artifact.fileExtension.uppercase()})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Tap to open hardware-accelerated video preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImageMiniPreviewCard(artifact: InChatArtifactInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Image Asset (${artifact.fileExtension.uppercase()})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Tap to inspect on Skia GPU canvas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WebMiniPreviewCard(artifact: InChatArtifactInfo, onLaunchClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunchClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = "Web App",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interactive Web Artifact",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Sandboxed HTML5/JS app • Tap to run live",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PresentationMiniPreviewCard(artifact: InChatArtifactInfo, onOpenSlides: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSlides() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Slideshow,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Presentation Slide Deck",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Tap to review slides in slide carousel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentMiniPreviewCard(artifact: InChatArtifactInfo, onOpenDoc: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDoc() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PDF Document",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Rendered via native Android PdfRenderer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Model3DMiniPreviewCard(artifact: InChatArtifactInfo, onInspectClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspectClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ViewInAr,
                contentDescription = null,
                tint = Color(0xFF7C3AED),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "3D Spatial Mesh (${artifact.fileExtension.uppercase()})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Tap to orbit and inspect 3D geometry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CodeMiniPreviewCard(artifact: InChatArtifactInfo, onInspectClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspectClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Source File (.${artifact.fileExtension})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = artifact.filePath.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Helpers
private fun getArtifactIcon(type: ArtifactType): ImageVector = when (type) {
    ArtifactType.HTML_WEB_APP -> Icons.Default.Language
    ArtifactType.AUDIO_MEDIA -> Icons.Default.Audiotrack
    ArtifactType.VIDEO_MEDIA -> Icons.Default.Movie
    ArtifactType.IMAGE_MEDIA -> Icons.Default.Image
    ArtifactType.SLIDES_PPT -> Icons.Default.Slideshow
    ArtifactType.DOCUMENT_PDF -> Icons.Default.PictureAsPdf
    ArtifactType.MODEL_3D -> Icons.Default.ViewInAr
    ArtifactType.CODE_FILE -> Icons.Default.Code
    ArtifactType.GENERIC_FILE -> Icons.Default.InsertDriveFile
}

@Composable
private fun getArtifactContainerColor(type: ArtifactType): Color = when (type) {
    ArtifactType.HTML_WEB_APP -> MaterialTheme.colorScheme.tertiaryContainer
    ArtifactType.AUDIO_MEDIA -> MaterialTheme.colorScheme.primaryContainer
    ArtifactType.VIDEO_MEDIA -> MaterialTheme.colorScheme.secondaryContainer
    ArtifactType.IMAGE_MEDIA -> MaterialTheme.colorScheme.surfaceContainerHigh
    ArtifactType.SLIDES_PPT -> Color(0xFFFEF3C7)
    ArtifactType.DOCUMENT_PDF -> Color(0xFFFEE2E2)
    ArtifactType.MODEL_3D -> Color(0xFFEDE9FE)
    ArtifactType.CODE_FILE, ArtifactType.GENERIC_FILE -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun getArtifactIconTint(type: ArtifactType): Color = when (type) {
    ArtifactType.HTML_WEB_APP -> MaterialTheme.colorScheme.onTertiaryContainer
    ArtifactType.AUDIO_MEDIA -> MaterialTheme.colorScheme.onPrimaryContainer
    ArtifactType.VIDEO_MEDIA -> MaterialTheme.colorScheme.onSecondaryContainer
    ArtifactType.IMAGE_MEDIA -> MaterialTheme.colorScheme.primary
    ArtifactType.SLIDES_PPT -> Color(0xFFB45309)
    ArtifactType.DOCUMENT_PDF -> Color(0xFFDC2626)
    ArtifactType.MODEL_3D -> Color(0xFF6D28D9)
    ArtifactType.CODE_FILE, ArtifactType.GENERIC_FILE -> MaterialTheme.colorScheme.onSurface
}

private fun getArtifactTypeName(type: ArtifactType): String = when (type) {
    ArtifactType.HTML_WEB_APP -> "Web Application"
    ArtifactType.AUDIO_MEDIA -> "Audio"
    ArtifactType.VIDEO_MEDIA -> "Video"
    ArtifactType.IMAGE_MEDIA -> "Image"
    ArtifactType.SLIDES_PPT -> "Presentation"
    ArtifactType.DOCUMENT_PDF -> "Document"
    ArtifactType.MODEL_3D -> "3D Model"
    ArtifactType.CODE_FILE -> "Code"
    ArtifactType.GENERIC_FILE -> "File"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.ROOT, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatTimeMs(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.ROOT, "%02d:%02d", min, sec)
}
