package com.example.engine.mcp

import com.example.engine.tools.Tool
import com.example.engine.tools.ToolPermission
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridges an MCP server-defined tool into OmniRoot's universal Tool engine.
 * Allows MCP tools to seamlessly integrate into LLM prompt tool-calling schemas.
 */
class McpDynamicToolAdapter(
    val serverConfig: McpServerConfig,
    val mcpDefinition: McpToolDefinition,
    private val client: McpClient
) : Tool {

    // Prefix with server name and account alias to avoid collisions and clearly designate multi-account instances
    override val name: String = sanitizeToolName(
        if (serverConfig.accountAlias.isNotBlank() && !serverConfig.accountAlias.equals("Default", ignoreCase = true)) {
            "${serverConfig.id}_${serverConfig.accountAlias}_${mcpDefinition.name}"
        } else {
            "${serverConfig.id}_${mcpDefinition.name}"
        }
    )
    override val description: String = "[MCP: ${serverConfig.name} (${serverConfig.accountAlias})] ${mcpDefinition.description ?: "Dynamic MCP tool"}"
    override val permission: ToolPermission = ToolPermission.ALWAYS_ASK

    override val parametersSchema: Map<String, Any> = extractProperties(mcpDefinition.inputSchema)

    override suspend fun execute(args: Map<String, Any>): String {
        val result = client.callTool(mcpDefinition.name, args)
        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            "Error executing MCP tool '${mcpDefinition.name}': ${result.exceptionOrNull()?.message}"
        }
    }

    override fun toOpenAiToolDefinition(): JSONObject {
        val root = JSONObject()
        root.put("type", "function")
        val fn = JSONObject()
        fn.put("name", name)
        fn.put("description", description)

        val schema = JSONObject(mcpDefinition.inputSchema)
        if (!schema.has("type")) {
            schema.put("type", "object")
        }
        fn.put("parameters", schema)
        root.put("function", fn)
        return root
    }

    private fun sanitizeToolName(raw: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return clean
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractProperties(schema: Map<String, Any?>): Map<String, Any> {
        val props = schema["properties"] as? Map<String, Any>
        return props ?: emptyMap()
    }
}
