package com.example.ui.code

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Multimodal Staged Reference model with Google AI Studio-style contextual comments.
 * Represents any artifact slice (code lines, image region, media timestamp, document page, slide, or whole file)
 * staged into the AI Context Basket with optional user directive/comment.
 */
sealed class StagedReference(
    open val id: String = UUID.randomUUID().toString(),
    open val fileName: String,
    open val label: String,
    open val userComment: String? = null
) {
    data class CodeLines(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val startLine: Int,
        val endLine: Int,
        val excerpt: String = "",
        override val userComment: String? = null
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name}#L$startLine-L$endLine",
        userComment = userComment
    )

    data class ImageRegion(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val coordinates: String,
        val description: String = "",
        override val userComment: String? = null
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} [Region $coordinates]",
        userComment = userComment
    )

    data class MediaTimestamp(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val isVideo: Boolean,
        val timestampMs: Long,
        val formattedTime: String,
        override val userComment: String? = null
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} @ $formattedTime",
        userComment = userComment
    )

    data class DocumentPage(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val pageIndex: Int,
        val totalPages: Int,
        override val userComment: String? = null
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} (Page ${pageIndex + 1}/$totalPages)",
        userComment = userComment
    )

    data class PresentationSlide(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val slideIndex: Int,
        val slideTitle: String,
        override val userComment: String? = null
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "${file.name} (Slide ${slideIndex + 1}: $slideTitle)",
        userComment = userComment
    )

    data class GenericArtifact(
        override val id: String = UUID.randomUUID().toString(),
        val file: File,
        val summary: String = "",
        override val userComment: String? = null
    ) : StagedReference(
        id = id,
        fileName = file.name,
        label = "@artifact/${file.name}",
        userComment = userComment
    )
}

data class WorkspaceFileEditEvent(
    val filePath: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val editSource: String = "Manual Editor"
)

/**
 * Global bridge between the Workspace Tab and Chat composer.
 * Maintains an active multimodal "Context Basket" where multiple artifact references
 * across different files, time ranges, visual regions, and user directives can be
 * accumulated and passed to the AI prompt cleanly.
 */
object WorkspaceChatBridge {

    private val _stagedChatPrompt = MutableStateFlow<String?>(null)
    val stagedChatPrompt: StateFlow<String?> = _stagedChatPrompt.asStateFlow()

    private val _stagedReferences = MutableStateFlow<List<StagedReference>>(emptyList())
    val stagedReferences: StateFlow<List<StagedReference>> = _stagedReferences.asStateFlow()

    private val _recentFileEdits = MutableStateFlow<List<WorkspaceFileEditEvent>>(emptyList())
    val recentFileEdits: StateFlow<List<WorkspaceFileEditEvent>> = _recentFileEdits.asStateFlow()

    fun recordFileEdit(file: File, source: String = "Manual Editor") {
        val event = WorkspaceFileEditEvent(file.absolutePath, file.name, System.currentTimeMillis(), source)
        val current = _recentFileEdits.value.takeLast(9).toMutableList()
        current.add(event)
        _recentFileEdits.value = current
    }

    fun consumeRecentFileEdits(): List<WorkspaceFileEditEvent> {
        val edits = _recentFileEdits.value
        _recentFileEdits.value = emptyList()
        return edits
    }

    fun stagePrompt(prompt: String) {
        _stagedChatPrompt.value = prompt
    }

    fun consumeStagedPrompt(): String? {
        val current = _stagedChatPrompt.value
        _stagedChatPrompt.value = null
        return current
    }

    fun addStagedReference(ref: StagedReference) {
        addOrUpdateStagedReference(ref)
    }

    fun addOrUpdateStagedReference(ref: StagedReference) {
        val current = _stagedReferences.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.label == ref.label }
        if (existingIndex >= 0) {
            current[existingIndex] = ref
        } else {
            current.add(ref)
        }
        _stagedReferences.value = current
    }

    fun updateStagedReferenceComment(id: String, comment: String?) {
        val current = _stagedReferences.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = current[index]
            val updated: StagedReference = when (old) {
                is StagedReference.CodeLines -> old.copy(userComment = comment)
                is StagedReference.ImageRegion -> old.copy(userComment = comment)
                is StagedReference.MediaTimestamp -> old.copy(userComment = comment)
                is StagedReference.DocumentPage -> old.copy(userComment = comment)
                is StagedReference.PresentationSlide -> old.copy(userComment = comment)
                is StagedReference.GenericArtifact -> old.copy(userComment = comment)
            }
            current[index] = updated
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
     * clean prompt for the AI agent, highlighting attached Google AI Studio-style comments.
     */
    fun compileBasketPrompt(userMessage: String): String {
        val refs = _stagedReferences.value
        if (refs.isEmpty()) return userMessage

        val sb = StringBuilder()
        sb.append("Referenced Artifact Context & Directives:\n")
        refs.forEach { ref ->
            when (ref) {
                is StagedReference.CodeLines -> {
                    sb.append("- @artifact/${ref.file.name} (Lines ${ref.startLine}-${ref.endLine})")
                    if (!ref.userComment.isNullOrBlank()) {
                        sb.append("\n  Attached Directive: \"${ref.userComment}\"")
                    }
                    if (ref.excerpt.isNotBlank()) {
                        val formattedExcerpt = if (ref.excerpt.lines().size > 60) {
                            extractSemanticOutline(ref.excerpt)
                        } else {
                            ref.excerpt.trim()
                        }
                        sb.append(":\n```\n").append(formattedExcerpt).append("\n```\n")
                    } else {
                        sb.append("\n")
                    }
                }
                is StagedReference.ImageRegion -> {
                    sb.append("- @artifact/${ref.file.name} [Region: ${ref.coordinates}]")
                    if (!ref.userComment.isNullOrBlank()) {
                        sb.append("\n  Attached Directive: \"${ref.userComment}\"")
                    }
                    sb.append("\n")
                }
                is StagedReference.MediaTimestamp -> {
                    val mediaType = if (ref.isVideo) "Video" else "Audio"
                    sb.append("- @artifact/${ref.file.name} ($mediaType @ ${ref.formattedTime})")
                    if (!ref.userComment.isNullOrBlank()) {
                        sb.append("\n  Attached Directive: \"${ref.userComment}\"")
                    }
                    sb.append("\n")
                }
                is StagedReference.DocumentPage -> {
                    sb.append("- @artifact/${ref.file.name} (Page ${ref.pageIndex + 1} of ${ref.totalPages})")
                    if (!ref.userComment.isNullOrBlank()) {
                        sb.append("\n  Attached Directive: \"${ref.userComment}\"")
                    }
                    sb.append("\n")
                }
                is StagedReference.PresentationSlide -> {
                    sb.append("- @artifact/${ref.file.name} (Slide ${ref.slideIndex + 1}: ${ref.slideTitle})")
                    if (!ref.userComment.isNullOrBlank()) {
                        sb.append("\n  Attached Directive: \"${ref.userComment}\"")
                    }
                    sb.append("\n")
                }
                is StagedReference.GenericArtifact -> {
                    sb.append("- @artifact/${ref.file.name}")
                    if (!ref.userComment.isNullOrBlank()) {
                        sb.append("\n  Attached Directive: \"${ref.userComment}\"")
                    }
                    sb.append("\n")
                }
            }
        }
        sb.append("\n")
        if (userMessage.isNotBlank()) {
            sb.append(userMessage)
        } else {
            sb.append("Please analyze and address the referenced artifacts and directives above.")
        }
        return sb.toString()
    }

    /**
     * Extracts structural declarations (classes, interfaces, functions, variables, annotations)
     * from large code snippets, condensing bulky function bodies to preserve context window tokens
     * for local GGUF models.
     */
    fun extractSemanticOutline(code: String): String {
        val lines = code.lines()
        val sb = StringBuilder()
        var blockSkippedLines = 0

        for (line in lines) {
            val trimmed = line.trim()
            val isDeclaration = trimmed.startsWith("package ") ||
                    trimmed.startsWith("import ") ||
                    trimmed.startsWith("class ") ||
                    trimmed.startsWith("data class ") ||
                    trimmed.startsWith("sealed class ") ||
                    trimmed.startsWith("interface ") ||
                    trimmed.startsWith("object ") ||
                    trimmed.startsWith("enum class ") ||
                    trimmed.startsWith("fun ") ||
                    trimmed.startsWith("override fun ") ||
                    trimmed.startsWith("private fun ") ||
                    trimmed.startsWith("public fun ") ||
                    trimmed.startsWith("val ") ||
                    trimmed.startsWith("var ") ||
                    trimmed.startsWith("const val ") ||
                    trimmed.startsWith("@") ||
                    trimmed.startsWith("//") ||
                    trimmed.startsWith("/*") ||
                    trimmed.startsWith("*") ||
                    trimmed.startsWith("def ") ||
                    trimmed.startsWith("export ") ||
                    trimmed.startsWith("const ") ||
                    trimmed.startsWith("function ")

            if (isDeclaration) {
                if (blockSkippedLines > 0) {
                    sb.append("    // ... (${blockSkippedLines} implementation lines omitted for brevity) ...\n")
                    blockSkippedLines = 0
                }
                sb.append(line).append("\n")
            } else if (trimmed == "}" || trimmed == "};") {
                if (blockSkippedLines > 0) {
                    sb.append("    // ... (${blockSkippedLines} implementation lines omitted for brevity) ...\n")
                    blockSkippedLines = 0
                }
                sb.append(line).append("\n")
            } else {
                blockSkippedLines++
            }
        }
        if (blockSkippedLines > 0) {
            sb.append("    // ... (${blockSkippedLines} implementation lines omitted for brevity) ...\n")
        }
        return sb.toString().trim()
    }
}
