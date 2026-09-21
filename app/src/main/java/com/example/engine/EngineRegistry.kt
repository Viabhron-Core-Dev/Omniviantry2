package com.example.engine

import com.example.engine.mcp.McpProvider
import com.example.engine.skills.Skill
import com.example.engine.tools.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object EngineRegistry {
    private val defaultTools = listOf(
        ViewFileTool(),
        EditFileTool(),
        MultiEditFileTool(),
        CreateFileTool(),
        DeleteFileTool(),
        MoveFileTool(),
        ListDirTool(),
        GrepSearchTool(),
        JsSandboxTool(),
        ShellProcessTool(),
        PRootLinuxTool(),
        RunPythonTool(),
        ArchiveTool(),
        DocumentParserTool(),
        McpManageTool()
    )

    private val _tools = MutableStateFlow<List<Tool>>(defaultTools)
    val tools: StateFlow<List<Tool>> = _tools.asStateFlow()

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    private val _mcpProviders = MutableStateFlow<List<McpProvider>>(emptyList())
    val mcpProviders: StateFlow<List<McpProvider>> = _mcpProviders.asStateFlow()

    fun registerTool(tool: Tool) {
        _tools.value = _tools.value.filterNot { it.name.equals(tool.name, ignoreCase = true) } + tool
    }

    fun unregisterTool(name: String) {
        _tools.value = _tools.value.filterNot { it.name.equals(name, ignoreCase = true) }
    }

    fun getTool(name: String): Tool? {
        return _tools.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun getAllTools(): List<Tool> {
        return _tools.value
    }

    fun getOpenAiToolDefinitions(): JSONArray {
        val array = JSONArray()
        _tools.value.forEach { tool ->
            array.put(tool.toOpenAiToolDefinition())
        }
        return array
    }

    fun registerSkill(skill: Skill) {
        _skills.value = _skills.value.filterNot { it.name.equals(skill.name, ignoreCase = true) } + skill
    }

    fun registerMcpProvider(provider: McpProvider) {
        _mcpProviders.value = _mcpProviders.value + provider
    }
}

