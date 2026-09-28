package com.example.ui.code

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Multimodal Staged Reference model (Mini-Phase 5 Deliverable 2).
 * Represents any artifact slice (code lines, image region, media timestamp, document page, or slide)
 * staged into the AI Context Basket.
 */
sealed class StagedReference(
    open val id: String = UUID.randomUUID().toString(),
    open val fileName: String,
    open val label: String
) {
    data class CodeLines(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val startLine: Int,
        val endLine: Int,
        val excerpt: String = ""
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name}#L$startLine-L$endLine"
    )

    data class ImageRegion(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val coordinates: String,
        val description: String = ""
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} [Region $coordinates]"
    )

    data class MediaTimestamp(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val isVideo: Boolean,
        val timestampMs: Long,
        val formattedTime: String
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} @ $formattedTime"
    )

    data class DocumentPage(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val pageIndex: Int,
        val totalPages: Int
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} (Page ${pageIndex + 1}/$totalPages)"
    )

    data class PresentationSlide(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val slideIndex: Int,
        val slideTitle: String
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} (Slide ${slideIndex + 1}: $slideTitle)"
    )

    data class GenericArtifact(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val summary: String = ""
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "@artifact/${file.name}"
    )
}

/**
 * Global bridge between the Workspace Tab and Chat composer.
 * Maintains an active multimodal "Context Basket" where multiple artifact references
 * across different files, time ranges, and visual regions can be accumulated and passed
 * to the AI prompt cleanly.
 */
object WorkspaceChatBridge {

    private val _stagedChatPrompt = MutableStateFlow<String?>(null)
    val stagedChatPrompt: StateFlow<String?> = _stagedChatPrompt.asStateFlow()

    private val _stagedReferences = MutableStateFlow<List<StagedReference>>(emptyList())
    val stagedReferences: StateFlow<List<StagedReference>> = _stagedReferences.asStateFlow()

    fun stagePrompt(prompt: String) {
        _stagedChatPrompt.value = prompt
    }

    fun consumeStagedPrompt(): String? {
        val current = _stagedChatPrompt.value
        _stagedChatPrompt.value = null
        return current
    }

    fun addStagedReference(ref: StagedReference) {
        val current = _stagedReferences.value.toMutableList()
        // Prevent duplicate exact references
        if (current.none { it.label == ref.label }) {
            current.add(ref)
            _stagedReferences.value = current
        }
    }

    fun removeStagedReference(id: String) {
        val current = _stagedReferences.value.toMutableList()
        current.removeAll { it.id == id }
        _stagedReferences.value = current
    }

    fun clearStagedReferences() {
        _stagedReferences.value = emptyList()
    }

    fun getBasketCount(): Int = _stagedReferences.value.size

    /**
     * Formats all staged references in the basket and optional user text into a single
     * clean prompt for the AI agent.
     */
    fun compileBasketPrompt(userMessage: String): String {
        val refs = _stagedReferences.value
        if (refs.isEmpty()) return userMessage

        val sb = StringBuilder()
        sb.append("Referenced Artifact Context:\n")
        refs.forEach { ref ->
            when (ref) {
                is StagedReference.CodeLines -> {
                    sb.append("- @artifact/${ref.file.name} (Lines ${ref.startLine}-${ref.endLine})")
                    if (ref.excerpt.isNotBlank()) {
                        sb.append(":\n```\n").append(ref.excerpt.trim()).append("\n```")
                    } else {
                        sb.append("\n")
                    }
                }
                is StagedReference.ImageRegion -> {
                    sb.append("- @artifact/${ref.file.name} [Region: ${ref.coordinates}]\n")
                }
                is StagedReference.MediaTimestamp -> {
                    val mediaType = if (ref.isVideo) "Video" else "Audio"
                    sb.append("- @artifact/${ref.file.name} ($mediaType @ ${ref.formattedTime})\n")
                }
                is StagedReference.DocumentPage -> {
                    sb.append("- @artifact/${ref.file.name} (Page ${ref.pageIndex + 1} of ${ref.totalPages})\n")
                }
                is StagedReference.PresentationSlide -> {
                    sb.append("- @artifact/${ref.file.name} (Slide ${ref.slideIndex + 1}: ${ref.slideTitle})\n")
                }
                is StagedReference.GenericArtifact -> {
                    sb.append("- @artifact/${ref.file.name}\n")
                }
            }
        }
        sb.append("\n")
        if (userMessage.isNotBlank()) {
            sb.append(userMessage)
        } else {
            sb.append("Please analyze the referenced artifacts above.")
        }
        return sb.toString()
    }
}
