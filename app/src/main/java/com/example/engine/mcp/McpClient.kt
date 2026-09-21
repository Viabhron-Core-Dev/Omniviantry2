package com.example.engine.mcp

import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class McpClient(
    val config: McpServerConfig,
    val transport: McpTransport
) {
    val connectionState: StateFlow<McpConnectionState> = transport.state
    var serverInfo: McpServerInfo? = null
        private set

    /**
     * Connects to the MCP server and negotiates protocol capabilities.
     */
    suspend fun initialize(): Result<McpServerInfo> = withContext(Dispatchers.IO) {
        val connectRes = transport.connect()
        if (connectRes.isFailure) {
            return@withContext Result.failure(connectRes.exceptionOrNull() ?: Exception("Transport connection failed"))
        }

        val initParams = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "roots" to mapOf("listChanged" to true),
                "sampling" to emptyMap<String, Any>()
            ),
            "clientInfo" to mapOf(
                "name" to "Omnivian-MCP-Client",
                "version" to "1.0.0"
            )
        )

        val responseRes = transport.sendRequest("initialize", initParams, timeoutMs = 15000L)
        if (responseRes.isFailure) {
            return@withContext Result.failure(responseRes.exceptionOrNull() ?: Exception("Initialize handshake failed"))
        }

        val responseJson = responseRes.getOrThrow()
        if (responseJson.has("error")) {
            val err = responseJson.getJSONObject("error")
            val msg = err.optString("message", "Unknown initialize error")
            return@withContext Result.failure(Exception("MCP Initialize Error ($msg)"))
        }

        val resultObj = responseJson.optJSONObject("result") ?: JSONObject()
        val protocolVer = resultObj.optString("protocolVersion", "2024-11-05")
        val serverInfoObj = resultObj.optJSONObject("serverInfo") ?: JSONObject()
        val sName = serverInfoObj.optString("name", config.name)
        val sVer = serverInfoObj.optString("version", "1.0.0")

        val info = McpServerInfo(name = sName, version = sVer, protocolVersion = protocolVer)
        serverInfo = info

        // Send notifications/initialized as required by MCP spec
        transport.sendNotification("notifications/initialized")
        LogKeeper.log("McpClient", "Handshake", "Connected to ${info.name} v${info.version} (Protocol $protocolVer)")
        Result.success(info)
    }

    /**
     * Queries available tools from the MCP server.
     */
    suspend fun listTools(): Result<List<McpToolDefinition>> = withContext(Dispatchers.IO) {
        val res = transport.sendRequest("tools/list", emptyMap())
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Failed to list tools"))
        }

        val json = res.getOrThrow()
        if (json.has("error")) {
            return@withContext Result.failure(Exception(json.getJSONObject("error").optString("message")))
        }

        val resultObj = json.optJSONObject("result") ?: JSONObject()
        val toolsArray = resultObj.optJSONArray("tools") ?: JSONArray()

        val list = mutableListOf<McpToolDefinition>()
        for (i in 0 until toolsArray.length()) {
            val toolObj = toolsArray.getJSONObject(i)
            val name = toolObj.getString("name")
            val desc = toolObj.optString("description", "")
            val inputSchemaObj = toolObj.optJSONObject("inputSchema") ?: JSONObject()
            val schemaMap = jsonObjectToMap(inputSchemaObj)

            list.add(McpToolDefinition(name = name, description = desc, inputSchema = schemaMap))
        }

        LogKeeper.log("McpClient", "ToolsList", "Discovered ${list.size} tools from ${config.name}")
        Result.success(list)
    }

    /**
     * Executes a tool on the remote/local MCP server.
     */
    suspend fun callTool(name: String, arguments: Map<String, Any?>): Result<String> = withContext(Dispatchers.IO) {
        val params = mapOf(
            "name" to name,
            "arguments" to arguments
        )

        val startTime = System.currentTimeMillis()
        val res = transport.sendRequest("tools/call", params, timeoutMs = 30000L)
        val duration = System.currentTimeMillis() - startTime

        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Tool call failed"))
        }

        val json = res.getOrThrow()
        if (json.has("error")) {
            val err = json.getJSONObject("error")
            return@withContext Result.failure(Exception("MCP Tool Error (${err.optInt("code")}): ${err.optString("message")}"))
        }

        val resultObj = json.optJSONObject("result") ?: JSONObject()
        val isError = resultObj.optBoolean("isError", false)
        val contentArray = resultObj.optJSONArray("content") ?: JSONArray()

        val outputBuilder = StringBuilder()
        for (i in 0 until contentArray.length()) {
            val item = contentArray.getJSONObject(i)
            val type = item.optString("type", "text")
            when (type) {
                "text" -> {
                    outputBuilder.appendLine(item.optString("text", ""))
                }
                "image" -> {
                    outputBuilder.appendLine("[Image data: ${item.optString("mimeType", "image/*")}]")
                }
                "resource" -> {
                    val resObj = item.optJSONObject("resource")
                    outputBuilder.appendLine("[Resource: ${resObj?.optString("uri", "unknown")}]")
                }
                else -> {
                    outputBuilder.appendLine(item.toString())
                }
            }
        }

        val finalOutput = outputBuilder.toString().trim()
        LogKeeper.log("McpClient", if (isError) "ToolCallFail" else "ToolCallSuccess", "Executed $name in ${duration}ms (error=$isError)")

        if (isError) {
            Result.failure(Exception(if (finalOutput.isNotEmpty()) finalOutput else "Tool reported execution error"))
        } else {
            Result.success(finalOutput)
        }
    }

    /**
     * Disconnects the underlying transport and reclaims resources.
     */
    suspend fun disconnect() {
        transport.disconnect()
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(array: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            list.add(
                when (value) {
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            )
        }
        return list
    }
}
