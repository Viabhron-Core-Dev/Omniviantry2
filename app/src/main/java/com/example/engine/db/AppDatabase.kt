package com.example.engine.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.engine.db.ProviderPrepopulator

@Database(entities = [
    ChatMessageEntity::class, 
    WorkspaceConfigEntity::class, 
    WorkspaceIssueEntity::class, 
    WorkspacePullRequestEntity::class,
    ApiProviderEntity::class,
    ApiKeyEntity::class,
    FallbackChainEntity::class,
    TokenUsageEntity::class,
    ModelRatingEntity::class,
    RequestLogEntity::class,
    AiModelEntity::class,
    ChatSettingsEntity::class,
    ArtifactEntity::class,
    KnowledgeBitEntity::class,
    McpServerEntity::class,
    com.example.engine.skills.SkillEntity::class,
    com.example.engine.plugins.PluginEntity::class,
    com.example.engine.omniroot.artifact.ArtifactProviderEntity::class
], version = 21, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceConfigDao(): WorkspaceConfigDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun workspaceIssueDao(): WorkspaceIssueDao
    abstract fun workspacePullRequestDao(): WorkspacePullRequestDao
    abstract fun apiProviderDao(): ApiProviderDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun fallbackChainDao(): FallbackChainDao
    abstract fun metricsDao(): MetricsDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun modelRatingDao(): ModelRatingDao
    abstract fun chatSettingsDao(): ChatSettingsDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun knowledgeBitDao(): KnowledgeBitDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun skillDao(): com.example.engine.skills.SkillDao
    abstract fun pluginDao(): com.example.engine.plugins.PluginDao
    abstract fun artifactProviderDao(): com.example.engine.omniroot.artifact.ArtifactProviderDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_models ADD COLUMN inputType TEXT NOT NULL DEFAULT 'TEXT'")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN outputType TEXT NOT NULL DEFAULT 'TEXT'")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN modelName TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN providerId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `model_ratings` (`id` TEXT NOT NULL, `modelName` TEXT NOT NULL, `providerId` TEXT NOT NULL, `isPositive` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `model_ratings`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `model_ratings` (`messageId` TEXT NOT NULL, `modelName` TEXT NOT NULL, `providerId` TEXT NOT NULL, `isPositive` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`messageId`))")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_models ADD COLUMN description TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_settings` (`workspaceId` TEXT NOT NULL, `temperature` REAL NOT NULL, `minP` REAL NOT NULL, `topP` REAL NOT NULL, `maxTokens` INTEGER NOT NULL, `systemPrompt` TEXT NOT NULL, `contextSize` INTEGER NOT NULL, `numThreads` INTEGER NOT NULL, `useMmap` INTEGER NOT NULL, `useMlock` INTEGER NOT NULL, PRIMARY KEY(`workspaceId`))")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_settings ADD COLUMN unfoldOnScreen INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isFolded INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `artifacts` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `content` TEXT NOT NULL, `isPinned` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL, `workspaceId` TEXT, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artifacts ADD COLUMN iconUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN isLightweight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN manifestJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN settingsJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `knowledge_bits` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `sourceUrl` TEXT,
                        `contentType` TEXT NOT NULL DEFAULT 'NOTE',
                        `originalTimestamp` INTEGER NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        `lastAccessedAt` INTEGER NOT NULL,
                        `lastVerifiedAt` INTEGER NOT NULL,
                        `accessCount` INTEGER NOT NULL DEFAULT 1,
                        `isPinned` INTEGER NOT NULL DEFAULT 0,
                        `ttlSeconds` INTEGER NOT NULL DEFAULT 86400,
                        `workspaceId` TEXT,
                        `summary` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mcp_servers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `endpointUrl` TEXT NOT NULL,
                        `transportType` TEXT NOT NULL,
                        `headersJson` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `autoConnect` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolCallId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolArgsJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolOutput TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolStatus TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN routedViaFallback INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN fallbackReason TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN thoughtContent TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN planContent TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN thinkingDurationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mcp_servers ADD COLUMN accountAlias TEXT NOT NULL DEFAULT 'Default'")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `skills` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `examples` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `sourceUrl` TEXT,
                        `isBuiltIn` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `plugins` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `selectedSkillIds` TEXT NOT NULL,
                        `selectedToolIds` TEXT NOT NULL,
                        `selectedMcpServerIds` TEXT NOT NULL,
                        `customInstructions` TEXT NOT NULL,
                        `restrictions` TEXT NOT NULL,
                        `sourceUrl` TEXT,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `artifact_providers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `owner` TEXT NOT NULL DEFAULT 'Claude.ai',
                        `hashedPasskey` TEXT,
                        `priority` INTEGER NOT NULL DEFAULT 2,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `sessionTokens` INTEGER NOT NULL DEFAULT 0,
                        `sessionCalls` INTEGER NOT NULL DEFAULT 0,
                        `tokenPct` REAL NOT NULL DEFAULT 0.0,
                        `windowRemainingMs` INTEGER NOT NULL DEFAULT 0,
                        `windowStartMs` INTEGER NOT NULL DEFAULT 0,
                        `lastActiveMs` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omnivian_database"
                )
                .addCallback(DatabaseCallback())
                .addMigrations(
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                    MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                    MIGRATION_20_21
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val providerDao = database.apiProviderDao()
                    providerDao.insertProviders(ProviderPrepopulator.defaultProviders)
                }
            }
        }
    }
}
