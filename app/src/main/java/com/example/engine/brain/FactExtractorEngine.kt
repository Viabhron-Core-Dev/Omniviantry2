package com.example.engine.brain

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.engine.db.MemoryCategory
import com.example.engine.db.MemoryEntity
import com.example.utils.LogKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Automated Background Fact & Memory Promotion Engine.
 *
 * Runs asynchronously upon completion of AI generation turns.
 * Analyzes conversational pairs for durable architectural decisions, explicit user preferences,
 * and project invariants, automatically promoting them to Room-backed persistent memory
 * (MemoryRepository) without requiring manual tagging by the user.
 */
object FactExtractorEngine {

    private val engineScope = CoroutineScope(Dispatchers.IO)
    private val _promotedMemoriesFlow = MutableSharedFlow<MemoryEntity>(extraBufferCapacity = 16)
    val promotedMemoriesFlow: SharedFlow<MemoryEntity> = _promotedMemoriesFlow.asSharedFlow()

    // Architectural pattern matchers
    private val ARCHITECTURE_PATTERNS = listOf(
        Regex("""(?i)\b(?:we will use|decided to use|architecture is|chosen framework is|database is|engine is)\s+([A-Za-z0-9_\-\.\s]{3,60})"""),
        Regex("""(?i)\b(?:target sdk|compilesdk|minsdk|kotlin version|java version)\s*(?:is|to|=|:)?\s*([A-Za-z0-9_\-\.]{1,20})"""),
        Regex("""(?i)\b(?:encryption|security protocol|keystore type|pattern)\s*(?:is|:)\s*([A-Za-z0-9_\-\.\s]{3,50})"""),
        Regex("""(?i)\b(?:room database|sqlcipher|jetpack compose|retrofit|ktor|moshi|coil)\b""")
    )

    // User preference matchers
    private val PREFERENCE_PATTERNS = listOf(
        Regex("""(?i)\b(?:i prefer|my preference is|always format|always write|prefer using)\s+([A-Za-z0-9_\-\.\s]{3,60})"""),
        Regex("""(?i)\b(?:don't use|do not use|never use|avoid using|no mock data|zero deviation)\b\s*([A-Za-z0-9_\-\.\s]{0,50})""")
    )

    // Project rule matchers
    private val RULE_PATTERNS = listOf(
        Regex("""(?i)\b(?:rule|mandate|invariant|policy)\s*[:\-]\s*([A-Za-z0-9_\-\.\s]{3,80})"""),
        Regex("""(?i)\b(?:zero committed credentials|receipts log|blueprint first|surgical edit)\b""")
    )

    /**
     * Inspects a user prompt and AI response pair and extracts notable persistent facts.
     */
    fun extractAndPromoteAsync(
        context: Context,
        sessionId: String,
        userPrompt: String,
        aiResponse: String,
        onPromoted: ((MemoryEntity) -> Unit)? = null
    ) {
        engineScope.launch {
            extractAndPromote(context, sessionId, userPrompt, aiResponse, onPromoted)
        }
    }

    suspend fun extractAndPromote(
        context: Context,
        sessionId: String,
        userPrompt: String,
        aiResponse: String,
        onPromoted: ((MemoryEntity) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val memoryRepo = MemoryRepository(db.memoryDao())

            val combinedText = "$userPrompt\n$aiResponse"
            val candidates = mutableListOf<ExtractedCandidate>()

            // 1. Scan for Architectural Decisions
            ARCHITECTURE_PATTERNS.forEach { regex ->
                regex.findAll(combinedText).forEach { match ->
                    val raw = match.value.trim()
                    val key = generateKey("Architecture", raw)
                    candidates.add(
                        ExtractedCandidate(
                            key = key,
                            content = raw,
                            category = MemoryCategory.ARCHITECTURAL_DECISION,
                            isPinned = true, // Critical architectural choices default to pinned
                            relevanceScore = 1.0f
                        )
                    )
                }
            }

            // 2. Scan for User Preferences
            PREFERENCE_PATTERNS.forEach { regex ->
                regex.findAll(userPrompt).forEach { match -> // Preferences strictly sourced from user input
                    val raw = match.value.trim()
                    val key = generateKey("Preference", raw)
                    candidates.add(
                        ExtractedCandidate(
                            key = key,
                            content = raw,
                            category = MemoryCategory.USER_PREFERENCE,
                            isPinned = false,
                            relevanceScore = 0.9f
                        )
                    )
                }
            }

            // 3. Scan for Project Rules & Invariants
            RULE_PATTERNS.forEach { regex ->
                regex.findAll(combinedText).forEach { match ->
                    val raw = match.value.trim()
                    val key = generateKey("Rule", raw)
                    candidates.add(
                        ExtractedCandidate(
                            key = key,
                            content = raw,
                            category = MemoryCategory.PROJECT_RULE,
                            isPinned = true,
                            relevanceScore = 1.0f
                        )
                    )
                }
            }

            // Deduplicate candidates
            val uniqueCandidates = candidates.distinctBy { it.key.lowercase(Locale.ROOT) }

            uniqueCandidates.forEach { cand ->
                val existing = db.memoryDao().getMemoryByKey(cand.key)
                if (existing == null) {
                    val id = memoryRepo.saveMemory(
                        key = cand.key,
                        content = cand.content,
                        category = cand.category,
                        isPinned = cand.isPinned,
                        relevanceScore = cand.relevanceScore
                    )
                    val insertedEntity = db.memoryDao().getMemoryById(id)
                    if (insertedEntity != null) {
                        _promotedMemoriesFlow.tryEmit(insertedEntity)
                        onPromoted?.invoke(insertedEntity)
                        LogKeeper.log(
                            "FactExtractorEngine",
                            "MemoryAutoPromoted",
                            "Extracted [${cand.category}] key='${cand.key}', pinned=${cand.isPinned}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("FactExtractorEngine", "ExtractionError", "Failed to extract facts: ${e.message}")
        }
    }

    private fun generateKey(prefix: String, rawContent: String): String {
        val clean = rawContent
            .replace(Regex("""[^A-Za-z0-9\s]"""), " ")
            .split(" ")
            .filter { it.isNotBlank() && it.length > 2 }
            .take(4)
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        return "$prefix: $clean".take(40)
    }

    private data class ExtractedCandidate(
        val key: String,
        val content: String,
        val category: String,
        val isPinned: Boolean,
        val relevanceScore: Float
    )
}
