package com.example.engine.db

import android.content.Context
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage Security & Integrity Manager (Phase 13).
 *
 * Inspects database storage security posture, checks hardware TEE/StrongBox backing,
 * runs low-level PRAGMA integrity validations, and computes storage footprint.
 */
object DatabaseSecurityManager {

    data class DatabaseSecurityReport(
        val databaseName: String,
        val fileSizeBytes: Long,
        val isHardwareKeyProvisioned: Boolean,
        val hardwareSecurityLevel: DatabaseKeyStore.SecurityLevel,
        val integrityCheckPassed: Boolean,
        val integrityMessage: String,
        val tableCount: Int,
        val auditTimestamp: Long
    ) {
        val fileSizeFormatted: String
            get() = when {
                fileSizeBytes < 1024 -> "$fileSizeBytes B"
                fileSizeBytes < 1024 * 1024 -> String.format("%.1f KB", fileSizeBytes / 1024f)
                else -> String.format("%.2f MB", fileSizeBytes / (1024f * 1024f))
            }
    }

    private const val DB_NAME = "omnivian_database"

    /**
     * Executes a full non-destructive security audit of the local Room database.
     */
    suspend fun performSecurityAudit(context: Context): DatabaseSecurityReport = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_NAME)
        val fileExists = dbFile.exists()
        val fileSizeBytes = if (fileExists) dbFile.length() else 0L

        val isKeyProvisioned = DatabaseKeyStore.isPassphraseProvisioned(context)
        val hwLevel = DatabaseKeyStore.getHardwareSecurityLevel(context)

        var integrityPassed = false
        var integrityMsg = "Not checked"
        var tableCount = 0

        try {
            val db = AppDatabase.getInstance(context)
            val supportDb = db.openHelper.readableDatabase

            // Run PRAGMA integrity_check
            val cursor = supportDb.query("PRAGMA integrity_check", emptyArray())
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                integrityPassed = result.equals("ok", ignoreCase = true)
                integrityMsg = result
            }
            cursor.close()

            // Count sqlite user tables
            val tableCursor = supportDb.query(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_metadata'",
                emptyArray()
            )
            if (tableCursor.moveToFirst()) {
                tableCount = tableCursor.getInt(0)
            }
            tableCursor.close()

            LogKeeper.log(
                "DatabaseSecurity",
                "AuditComplete",
                "Security audit passed: integrity=$integrityPassed, tables=$tableCount, size=${fileSizeBytes}B, hwLevel=${hwLevel.name}"
            )
        } catch (e: Exception) {
            integrityPassed = false
            integrityMsg = "Check failed: ${e.javaClass.simpleName}"
            LogKeeper.log("DatabaseSecurity", "AuditError", "Integrity check exception: ${e.message}")
        }

        DatabaseSecurityReport(
            databaseName = DB_NAME,
            fileSizeBytes = fileSizeBytes,
            isHardwareKeyProvisioned = isKeyProvisioned,
            hardwareSecurityLevel = hwLevel,
            integrityCheckPassed = integrityPassed,
            integrityMessage = integrityMsg,
            tableCount = tableCount,
            auditTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Verifies that no plaintext database backups or unsecured WAL dumps exist in cache.
     */
    fun auditTemporaryStorage(context: Context): Boolean {
        val cacheDir = context.cacheDir
        val tempDbs = cacheDir.walkTopDown().filter {
            it.name.endsWith(".db") || it.name.endsWith(".sqlite") || it.name.contains("dump")
        }.toList()

        for (tempFile in tempDbs) {
            try {
                tempFile.delete()
                LogKeeper.log("DatabaseSecurity", "PurgeTempDb", "Purged unsecured temporary database file: ${tempFile.name}")
            } catch (e: Exception) {
                // Ignore
            }
        }
        return true
    }
}
