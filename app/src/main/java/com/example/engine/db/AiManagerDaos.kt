package com.example.engine.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiProviderDao {
    @Query("SELECT * FROM api_providers ORDER BY name ASC")
    fun getAllProviders(): Flow<List<ApiProviderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<ApiProviderEntity>)
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY createdAt DESC")
    fun getAllKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY createdAt DESC")
    suspend fun getAllKeysList(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId")
    fun getKeysForProvider(providerId: String): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId")
    suspend fun getKeysForProviderList(providerId: String): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id LIMIT 1")
    suspend fun getKeyById(id: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: ApiKeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeys(keys: List<ApiKeyEntity>)

    @Query("UPDATE api_keys SET isActive = CASE WHEN id = :keyId THEN 1 ELSE 0 END WHERE providerId = :providerId")
    suspend fun setActiveKeyForProvider(providerId: String, keyId: String)
    
    @Delete
    suspend fun deleteKey(key: ApiKeyEntity)
}

@Dao
interface FallbackChainDao {
    @Query("SELECT * FROM fallback_chains")
    fun getAllChains(): Flow<List<FallbackChainEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChain(chain: FallbackChainEntity)

    @Delete
    suspend fun deleteChain(chain: FallbackChainEntity)
}

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models ORDER BY providerId ASC, modelId ASC")
    fun getAllModels(): Flow<List<AiModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AiModelEntity>)

    @Query("DELETE FROM ai_models WHERE providerId = :providerId")
    suspend fun deleteModelsForProvider(providerId: String)
}

@Dao
interface MetricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenUsage(usage: TokenUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequestLog(log: RequestLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelRating(rating: ModelRatingEntity)
    
    @Query("SELECT SUM(tokensUsed) FROM token_usage")
    fun getTotalTokensUsed(): Flow<Int?>
    
    @Query("SELECT COUNT(*) FROM request_logs")
    fun getTotalRequestCount(): Flow<Int>
    
    @Query("SELECT SUM(estimatedCost) FROM request_logs")
    fun getTotalEstimatedCost(): Flow<Double?>
    
    @Query("SELECT SUM(tokensUsed) FROM token_usage WHERE timestamp >= :since")
    fun getTokensUsedSince(since: Long): Flow<Int?>
    
    @Query("SELECT COUNT(*) FROM request_logs WHERE timestamp >= :since")
    fun getRequestCountSince(since: Long): Flow<Int>
}
