package com.example.engine.omniroot.artifact

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Entity representing a registered Claude.ai Artifact Provider.
 * NOTE: [sessionTokens] is a cache only; the artifact is the source of truth.
 * On app restart, ArtifactProviderPool resyncs with getState().
 * [hashedPasskey] is stored here for artifact hash verification;
 * the original passkey is stored securely in ArtifactKeyStore.
 */
@Entity(tableName = "artifact_providers")
data class ArtifactProviderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val owner: String = "Claude.ai",
    val hashedPasskey: String? = null,
    val priority: Int = 2, // 1 = High, 2 = Normal, 3 = Low
    val enabled: Boolean = true,
    val sessionTokens: Long = 0L, // Cache only! Artifact is source of truth.
    val sessionCalls: Int = 0,
    val tokenPct: Float = 0f,
    val windowRemainingMs: Long = 0L,
    val windowStartMs: Long = 0L,
    val lastActiveMs: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
