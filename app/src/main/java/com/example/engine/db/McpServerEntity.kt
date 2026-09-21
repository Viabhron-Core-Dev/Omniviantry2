package com.example.engine.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.engine.mcp.McpServerConfig
import com.example.engine.mcp.McpTransportType
import org.json.JSONObject

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val endpointUrl: String,
    val transportType: String,
    val headersJson: String,
    val accountAlias: String = "Default",
    val isEnabled: Boolean = true,
    val autoConnect: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toConfig(): McpServerConfig {
        val headerMap = mutableMapOf<String, String>()
        try {
            if (headersJson.isNotBlank()) {
                val json = JSONObject(headersJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headerMap[k] = json.getString(k)
                }
            }
        } catch (_: Exception) {}

        val tType = try {
            McpTransportType.valueOf(transportType)
        } catch (_: Exception) {
            McpTransportType.SSE
        }

        return McpServerConfig(
            id = id,
            name = name,
            endpointUrl = endpointUrl,
            transportType = tType,
            headers = headerMap,
            accountAlias = accountAlias,
            isEnabled = isEnabled,
            autoConnect = autoConnect
        )
    }

    companion object {
        fun fromConfig(config: McpServerConfig): McpServerEntity {
            val json = JSONObject(config.headers as Map<*, *>)
            return McpServerEntity(
                id = config.id,
                name = config.name,
                endpointUrl = config.endpointUrl,
                transportType = config.transportType.name,
                headersJson = json.toString(),
                accountAlias = config.accountAlias,
                isEnabled = config.isEnabled,
                autoConnect = config.autoConnect
            )
        }
    }
}
