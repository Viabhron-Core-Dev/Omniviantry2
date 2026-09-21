package com.example.engine.mcp

import org.json.JSONArray
import org.json.JSONObject

enum class McpTransportType {
    SSE,
    HTTP,
    WEBSOCKET,
    STDIO
}

enum class McpConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class McpServerConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val transportType: McpTransportType = McpTransportType.SSE,
    val headers: Map<String, String> = emptyMap(),
    val accountAlias: String = "Default",
    val isEnabled: Boolean = true,
    val autoConnect: Boolean = true,
    val lastPingMs: Long = 0L,
    val lastError: String? = null
)

data class McpToolDefinition(
    val name: String,
    val description: String?,
    val inputSchema: Map<String, Any?> = emptyMap()
)

data class McpResourceDefinition(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

data class McpPromptDefinition(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList()
)

data class McpServerCapabilities(
    val tools: Boolean = true,
    val resources: Boolean = false,
    val prompts: Boolean = false,
    val logging: Boolean = false
)

data class McpServerInfo(
    val name: String,
    val version: String,
    val protocolVersion: String = "2024-11-05"
)
