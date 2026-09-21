package com.example.engine.mcp

import android.content.Context
import com.example.engine.EngineRegistry
import com.example.engine.db.AppDatabase
import com.example.engine.db.McpServerEntity
import com.example.engine.tools.Tool
import com.example.utils.LogKeeper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object McpServerManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeClients = ConcurrentHashMap<String, McpClient>()
    private val registeredAdapters = ConcurrentHashMap<String, List<McpDynamicToolAdapter>>()

    private val _servers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun init(context: Context) {
        if (_isInitialized.value) return
        _isInitialized.value = true

        scope.launch {
            val db = AppDatabase.getDatabase(context)
            db.mcpServerDao().getAllServers().collectLatest { entities ->
                val configs = entities.map { it.toConfig() }
                _servers.value = configs

                // Auto-connect enabled servers
                configs.filter { it.isEnabled && it.autoConnect }.forEach { config ->
                    if (!activeClients.containsKey(config.id)) {
                        connectServer(config)
                    }
                }
            }
        }
    }

    suspend fun addServer(context: Context, config: McpServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            db.mcpServerDao().insertServer(McpServerEntity.fromConfig(config))
            LogKeeper.log("McpServerManager", "AddServer", "Added MCP server '${config.name}' (${config.endpointUrl})")

            if (config.isEnabled && config.autoConnect) {
                connectServer(config)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogKeeper.log("McpServerManager", "Error", "Failed to add MCP server: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun removeServer(context: Context, serverId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnectServer(serverId)
            val db = AppDatabase.getDatabase(context)
            db.mcpServerDao().deleteServerById(serverId)
            LogKeeper.log("McpServerManager", "RemoveServer", "Removed MCP server ID: $serverId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun connectServer(config: McpServerConfig): Result<List<McpToolDefinition>> = withContext(Dispatchers.IO) {
        disconnectServer(config.id)

        val transport: McpTransport = when (config.transportType) {
            McpTransportType.SSE -> McpSseTransport(config)
            McpTransportType.HTTP -> McpHttpTransport(config)
            McpTransportType.WEBSOCKET, McpTransportType.STDIO -> McpHttpTransport(config) // Fallback to HTTP for zero-dependency portability
        }

        val client = McpClient(config, transport)
        activeClients[config.id] = client

        val initRes = client.initialize()
        if (initRes.isFailure) {
            val err = initRes.exceptionOrNull()?.message ?: "Handshake error"
            LogKeeper.log("McpServerManager", "ConnectFail", "Failed handshake with ${config.name}: $err")
            updateServerStatus(config.id, lastError = err)
            return@withContext Result.failure(initRes.exceptionOrNull() ?: Exception(err))
        }

        val toolsRes = client.listTools()
        if (toolsRes.isSuccess) {
            val tools = toolsRes.getOrThrow()
            val adapters = tools.map { def ->
                McpDynamicToolAdapter(
                    serverConfig = config,
                    mcpDefinition = def,
                    client = client
                )
            }

            registeredAdapters[config.id] = adapters
            adapters.forEach { adapter ->
                EngineRegistry.registerTool(adapter)
            }

            LogKeeper.log("McpServerManager", "ToolsRegistered", "Registered ${adapters.size} dynamic tools for ${config.name}")
            updateServerStatus(config.id, lastError = null)
            Result.success(tools)
        } else {
            val err = toolsRes.exceptionOrNull()?.message ?: "Failed listing tools"
            updateServerStatus(config.id, lastError = err)
            Result.failure(toolsRes.exceptionOrNull() ?: Exception(err))
        }
    }

    suspend fun disconnectServer(serverId: String) = withContext(Dispatchers.IO) {
        val client = activeClients.remove(serverId)
        if (client != null) {
            client.disconnect()
            LogKeeper.log("McpServerManager", "Disconnect", "Disconnected MCP server ID: $serverId")
        }

        // Unregister tools from registry
        val adapters = registeredAdapters.remove(serverId) ?: emptyList()
        adapters.forEach { adapter ->
            EngineRegistry.unregisterTool(adapter.name)
        }
    }

    suspend fun pingServer(config: McpServerConfig): Result<Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val client = activeClients[config.id] ?: run {
            val connRes = connectServer(config)
            if (connRes.isFailure) {
                return@withContext Result.failure(connRes.exceptionOrNull() ?: Exception("Cannot connect"))
            }
            activeClients[config.id]!!
        }

        val toolsRes = client.listTools()
        val duration = System.currentTimeMillis() - startTime
        if (toolsRes.isSuccess) {
            updateServerStatus(config.id, lastPing = duration, lastError = null)
            Result.success(duration)
        } else {
            val err = toolsRes.exceptionOrNull()?.message ?: "Ping failed"
            updateServerStatus(config.id, lastError = err)
            Result.failure(Exception(err))
        }
    }

    private fun updateServerStatus(serverId: String, lastPing: Long? = null, lastError: String? = null) {
        _servers.value = _servers.value.map { s ->
            if (s.id == serverId) {
                s.copy(
                    lastPingMs = lastPing ?: s.lastPingMs,
                    lastError = lastError
                )
            } else s
        }
    }

    fun getClient(serverId: String): McpClient? = activeClients[serverId]

    fun getAllDiscoveredAdapters(): List<Tool> {
        return registeredAdapters.values.flatten()
    }
}
