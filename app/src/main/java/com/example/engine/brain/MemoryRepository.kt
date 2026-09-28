package com.example.engine.brain

import android.content.Context
import com.example.engine.db.AppDatabase
import com.example.engine.db.MemoryCategory
import com.example.engine.db.MemoryDao
import com.example.engine.db.MemoryEntity
import com.example.utils.LogKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modular Memory Store & Semantic Recall Repository.
 * Part of Phase 11: The Brain, Memory & Agent Ecosystem.
 *
 * Provides persistent long-term memory for agent contexts, architectural decisions,
 * user preferences, and project rules with keyword scoring and semantic relevance ranking.
 */
class MemoryRepository(private val memoryDao: MemoryDao) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Common stop words to strip during prompt keyword extraction
    private val stopWords = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can", "can't", "cannot", "could",
        "did", "do", "does", "doing", "don't", "down", "during", "each", "few", "for",
        "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers",
        "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is",
        "isn't", "it", "it's", "its", "itself", "just", "me", "more", "most", "my",
        "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other",
        "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "she",
        "should", "so", "some", "such", "than", "that", "the", "their", "theirs",
        "them", "themselves", "then", "there", "these", "they", "this", "those",
        "through", "to", "too", "under", "until", "up", "very", "was", "we", "were",
        "what", "when", "where", "which", "while", "who", "whom", "why", "with", "would",
        "you", "your", "yours", "yourself", "yourselves"
    )

    suspend fun saveMemory(
        key: String,
        content: String,
        category: String = MemoryCategory.USER_PREFERENCE,
        isPinned: Boolean = false,
        relevanceScore: Float = 1.0f
    ): Long = withContext(Dispatchers.IO) {
        val trimmedKey = key.trim()
        val trimmedContent = content.trim()

        val existing = memoryDao.getMemoryByKey(trimmedKey)
        val id = if (existing != null) {
            val updated = existing.copy(
                content = trimmedContent,
                category = category,
                isPinned = isPinned,
                relevanceScore = relevanceScore,
                lastAccessedAt = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            existing.id
        } else {
            val newEntity = MemoryEntity(
                key = trimmedKey,
                content = trimmedContent,
                category = category,
                relevanceScore = relevanceScore,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                isPinned = isPinned,
                accessCount = 0
            )
            memoryDao.insertMemory(newEntity)
        }

        LogKeeper.log("MemoryRepository", "SaveMemory", "Persisted memory key=$trimmedKey category=$category id=$id")
        id
    }

    suspend fun updateMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.updateMemory(memory)
        LogKeeper.log("MemoryRepository", "UpdateMemory", "Updated memory id=${memory.id} key=${memory.key}")
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteById(id)
        LogKeeper.log("MemoryRepository", "DeleteMemory", "Deleted memory id=$id")
    }

    suspend fun setPinned(id: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        memoryDao.setPinned(id, isPinned)
        LogKeeper.log("MemoryRepository", "PinMemory", "Set pinned=$isPinned on memory id=$id")
    }

    fun getAllMemories(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesByCategory(category)

    fun searchMemories(query: String): Flow<List<MemoryEntity>> =
        memoryDao.searchMemories(query)

    suspend fun getMemoryById(id: Long): MemoryEntity? = withContext(Dispatchers.IO) {
        memoryDao.getMemoryById(id)
    }

    suspend fun getMemoryByKey(key: String): MemoryEntity? = withContext(Dispatchers.IO) {
        memoryDao.getMemoryByKey(key)
    }

    /**
     * Semantic Recall Engine:
     * Extracts keyword tokens from user prompt or agent conversation turn,
     * scores candidate memories based on keyword matches, category priority,
     * pinned status, and recency, returning top ranked memories.
     */
    suspend fun recallRelevantMemories(
        prompt: String,
        limit: Int = 5,
        minScoreThreshold: Float = 1.0f
    ): List<MemoryEntity> = withContext(Dispatchers.IO) {
        val keywords = extractKeywords(prompt)
        val allMemories = memoryDao.getAllMemoriesSync()

        if (allMemories.isEmpty()) {
            return@withContext emptyList()
        }

        val scoredList = allMemories.map { memory ->
            val score = calculateMemoryScore(memory, keywords)
            Pair(memory, score)
        }

        val matchingMemories = scoredList
            .filter { it.second >= minScoreThreshold }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        LogKeeper.log(
            "MemoryRepository",
            "RecallQuery",
            "Queried memories for keywords; found ${matchingMemories.size} matches (evaluated ${allMemories.size})"
        )

        // Asynchronously update access timestamps and count
        if (matchingMemories.isNotEmpty()) {
            coroutineScope.launch {
                val now = System.currentTimeMillis()
                matchingMemories.forEach { memory ->
                    memoryDao.updateAccess(memory.id, now)
                }
            }
        }

        matchingMemories
    }

    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9_\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    private fun calculateMemoryScore(memory: MemoryEntity, promptKeywords: Set<String>): Float {
        var score = memory.relevanceScore

        // Pinned memories receive high architectural priority
        if (memory.isPinned) {
            score += 2.5f
        }

        // Category priority weighting
        when (memory.category) {
            MemoryCategory.PROJECT_RULE -> score += 1.5f
            MemoryCategory.ARCHITECTURAL_DECISION -> score += 1.2f
            MemoryCategory.USER_PREFERENCE -> score += 1.0f
            MemoryCategory.SNIPPET -> score += 0.5f
        }

        if (promptKeywords.isEmpty()) {
            return score
        }

        val keyTokens = extractKeywords(memory.key)
        val contentTokens = extractKeywords(memory.content)

        // Strong match on key tokens (+1.5f per match)
        val keyMatches = keyTokens.intersect(promptKeywords).size
        score += keyMatches * 1.5f

        // Content token matches (+0.3f per match)
        val contentMatches = contentTokens.intersect(promptKeywords).size
        score += contentMatches * 0.3f

        // Exact substring bonus
        val lowerPrompt = promptKeywords.joinToString(" ")
        if (lowerPrompt.contains(memory.key.lowercase())) {
            score += 2.0f
        }

        // Recency bonus (last 48 hours)
        val hoursSinceAccess = (System.currentTimeMillis() - memory.lastAccessedAt) / (1000f * 60f * 60f)
        if (hoursSinceAccess < 48f) {
            score += 0.5f * (1f - (hoursSinceAccess / 48f))
        }

        return score
    }

    companion object {
        @Volatile
        private var INSTANCE: MemoryRepository? = null

        fun getInstance(context: Context): MemoryRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val repo = MemoryRepository(db.memoryDao())
                INSTANCE = repo
                repo
            }
        }
    }
}
