package com.example.engine.tools

import com.example.engine.mcp.McpServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class McpManageTool : Tool {
    override val name: String = "mcp_manage"
    override val description: String = "Manage and inspect Model Context Protocol (MCP) servers. Allows listing connected servers, viewing discovered dynamic tools, and reading external MCP resources."
    override val permission: ToolPermission = ToolPermission.USE_FREELY

    override val parametersSchema: Map<String, Any> = mapOf(
        "Action" to mapOf(
            "type" to "string",
            "description" to "Operation to perform: 'list_servers', 'list_tools', 'read_resource', or 'ping'.",
            "required" to true
        ),
        "ServerId" to mapOf(
            "type" to "string",
            "description" to "Target MCP server ID (for 'ping' or 'read_resource')."
        ),
        "ResourceUri" to mapOf(
            "type" to "string",
            "description" to "Resource URI for 'read_resource' (e.g., 'file:///data/schema.sql' or 'github://repo/issues')."
        )
    )

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val action = (args["Action"] as? String ?: args["action"] as? String ?: "list_servers").lowercase()

        when (action) {
            "list_servers", "servers" -> {
                val servers = McpServerManager.servers.value
                val discoveredTools = McpServerManager.getAllDiscoveredAdapters()

                buildString {
                    appendLine("=== Configured MCP Servers (${servers.size}) ===")
                    if (servers.isEmpty()) {
                        appendLine("No MCP servers configured yet.")
                    } else {
                        servers.forEach { server ->
                            val client = McpServerManager.getClient(server.id)
                            val state = client?.connectionState?.value?.name ?: "DISCONNECTED"
                            val toolCount = discoveredTools.count { it.name.startsWith(server.name.lowercase()) || it.description.contains(server.name) }
                            appendLine("• **${server.name}** [ID: `${server.id}`]")
                            appendLine("  Endpoint: `${server.endpointUrl}` | Transport: ${server.transportType}")
                            appendLine("  State: **$state** | Registered Tools: $toolCount")
                            if (server.lastPingMs > 0) appendLine("  Latency: ${server.lastPingMs}ms")
                            if (server.lastError != null) appendLine("  Last Error: ${server.lastError}")
                        }
                    }
                }
            }

            "list_tools", "tools" -> {
                val adapters = McpServerManager.getAllDiscoveredAdapters()
                buildString {
                    appendLine("=== Discovered Dynamic MCP Tools (${adapters.size}) ===")
                    if (adapters.isEmpty()) {
                        appendLine("No external MCP tools currently active.")
                    } else {
                        adapters.forEach { tool ->
                            appendLine("• **`${tool.name}`**")
                            appendLine("  ${tool.description}")
                        }
                    }
                }
            }

            "read_resource", "resource" -> {
                val serverId = args["ServerId"] as? String ?: args["serverId"] as? String
                val uri = args["ResourceUri"] as? String ?: args["uri"] as? String
                    ?: return@withContext "Error: 'ResourceUri' parameter is required for 'read_resource'."

                val servers = McpServerManager.servers.value
                val targetServer = if (!serverId.isNullOrBlank()) {
                    servers.firstOrNull { it.id == serverId || it.name.equals(serverId, ignoreCase = true) }
                } else {
                    servers.firstOrNull()
                }

                if (targetServer == null) {
                    return@withContext "Error: No active MCP server found."
                }

                val client = McpServerManager.getClient(targetServer.id)
                    ?: return@withContext "Error: Server '${targetServer.name}' is not connected."

                val res = client.transport.sendRequest("resources/read", mapOf("uri" to uri))
                if (res.isFailure) {
                    return@withContext "Error reading resource: ${res.exceptionOrNull()?.message}"
                }

                val json = res.getOrThrow()
                val resultObj = json.optJSONObject("result") ?: JSONObject()
                val contents = resultObj.optJSONArray("contents")

                buildString {
                    appendLine("=== MCP Resource: `$uri` ===")
                    if (contents != null) {
                        for (i in 0 until contents.length()) {
                            val c = contents.getJSONObject(i)
                            val text = c.optString("text")
                            val blob = c.optString("blob")
                            if (text.isNotEmpty()) appendLine(text)
                            else if (blob.isNotEmpty()) appendLine("[Binary Blob: ${c.optString("mimeType")}]")
                        }
                    } else {
                        appendLine(json.toString(2))
                    }
                }
            }

            "ping" -> {
                val serverId = args["ServerId"] as? String ?: args["serverId"] as? String
                val servers = McpServerManager.servers.value
                val target = servers.firstOrNull { it.id == serverId || it.name.equals(serverId, ignoreCase = true) }
                    ?: return@withContext "Error: Server '$serverId' not found."

                val res = McpServerManager.pingServer(target)
                if (res.isSuccess) {
                    "Success: Ping to '${target.name}' took ${res.getOrThrow()}ms."
                } else {
                    "Error: Ping failed: ${res.exceptionOrNull()?.message}"
                }
            }

            else -> "Error: Unknown action '$action'. Valid actions: 'list_servers', 'list_tools', 'read_resource', 'ping'."
        }
    }
}
