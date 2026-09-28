package com.example.engine.brain

import android.content.Context
import com.example.engine.EngineRegistry
import com.example.engine.db.MemoryCategory
import com.example.engine.db.MemoryEntity
import com.example.engine.fs.LocalFileManager
import com.example.engine.tools.Tool
import com.example.engine.tools.ToolPermissionManager
import com.example.ui.chat.ChatMessage
import com.example.ui.chat.MessageRole
import com.example.ui.chat.OmniMessage
import com.example.ui.chat.ToolExecutionStatus
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Structured assembly payload containing the assembled composite system prompt,
 * pruned message history within token budget, injected memories, and workspace files.
 */
data class ContextAssemblyResult(
    val systemPrompt: String,
    val prunedMessages: List<OmniMessage>,
    val injectedMemories: List<MemoryEntity>,
    val workspaceRuleFilesLoaded: List<String>,
    val activeToolsCount: Int,
    val estimatedTokens: Int,
    val budgetReport: ContextBudgetReport? = null
)

/**
 * Mini-Phase 11.4: Dynamic Context & System Prompt Injector.
 * Part of Phase 11: The Brain, Memory & Agent Ecosystem.
 *
 * Dynamically synthesizes the multi-layer cognitive context:
 * 1. Base Agent Persona & Developer Guidelines
 * 2. Thread-specific Instructions
 * 3. Workspace Rule Directives (AGENTS.md, GEMINI.md, etc.)
 * 4. Semantic Memory Recall (from MemoryRepository)
 * 5. Active Tool & Sandbox Schemas with Permissions
 * 6. Automated Token Budgeting & Sliding-Window Pruning
 */
object PromptAssembler {

    private const val MAX_WORKSPACE_FILE_CHARS = 10_000

    private val WORKSPACE_RULE_FILE_NAMES = listOf(
        "AGENTS.md",
        "GEMINI.md",
        "RULES.md",
        ".omnivian/rules.md"
    )

    /**
     * Assembles the full dynamic context from raw OmniMessages.
     */
    suspend fun assemble(
        context: Context,
        sessionId: String,
        userPrompt: String,
        messages: List<OmniMessage>,
        targetModel: String,
        customSystemPrompt: String? = null,
        memoryLimit: Int = 5
    ): ContextAssemblyResult = withContext(Dispatchers.IO) {
        val loadedRuleFiles = mutableListOf<String>()
        val promptBuilder = StringBuilder()

        // 1. Base Identity & Developer Guidelines
        promptBuilder.append(buildBasePersona())
        promptBuilder.append("\n\n")

        // 2. Thread-Specific Persona / Instructions
        if (!customSystemPrompt.isNullOrBlank()) {
            promptBuilder.append("## Thread Instructions & Custom Persona\n")
            promptBuilder.append(customSystemPrompt.trim())
            promptBuilder.append("\n\n")
        }

        // 3. Workspace Directives (AGENTS.md, GEMINI.md, etc.)
        val workspaceDirectives = loadWorkspaceDirectives(sessionId, loadedRuleFiles)
        if (workspaceDirectives.isNotBlank()) {
            promptBuilder.append(workspaceDirectives)
            promptBuilder.append("\n\n")
        }

        // 4. Semantic Memory Recall from MemoryRepository
        val recalledMemories = recallMemories(context, userPrompt, memoryLimit)
        if (recalledMemories.isNotEmpty()) {
            promptBuilder.append("## Relevant Context & Architectural Memories\n")
            promptBuilder.append("The following persistent memories from previous sessions and guidelines match the current prompt:\n")
            recalledMemories.forEachIndexed { index, mem ->
                val badge = when (mem.category) {
                    MemoryCategory.PROJECT_RULE -> "[PROJECT_RULE]"
                    MemoryCategory.ARCHITECTURAL_DECISION -> "[ARCHITECTURAL_DECISION]"
                    MemoryCategory.USER_PREFERENCE -> "[USER_PREFERENCE]"
                    MemoryCategory.SNIPPET -> "[SNIPPET]"
                    else -> "[MEMORY]"
                }
                val pinBadge = if (mem.isPinned) " 📌" else ""
                promptBuilder.append("${index + 1}. $badge$pinBadge **${mem.key}**: ${mem.content}\n")
            }
            promptBuilder.append("\n")
        }

        // 5. Active Tools & Sandbox Schemas with Permissions
        val activeTools = resolveActiveTools(context, sessionId)
        if (activeTools.isNotEmpty()) {
            promptBuilder.append(formatToolsSection(sessionId, activeTools))
            promptBuilder.append("\n\n")
        }

        val fullSystemPrompt = promptBuilder.toString().trim()
        val estimatedTokens = ContextBudgetManager.estimateTokens(fullSystemPrompt)

        // 6. Context Window Budgeting & Sliding-Window Pruning
        val (prunedMessages, report) = ContextBudgetManager.prepareContext(
            messages = messages,
            modelName = targetModel,
            systemPromptOverride = fullSystemPrompt
        )

        LogKeeper.log(
            "PromptAssembler",
            "AssemblyComplete",
            "Assembled prompt with ${recalledMemories.size} injected memories, ${loadedRuleFiles.size} workspace rule files, and ${activeTools.size} tools (~$estimatedTokens tokens)"
        )

        ContextAssemblyResult(
            systemPrompt = fullSystemPrompt,
            prunedMessages = prunedMessages,
            injectedMemories = recalledMemories,
            workspaceRuleFilesLoaded = loadedRuleFiles,
            activeToolsCount = activeTools.size,
            estimatedTokens = estimatedTokens,
            budgetReport = report
        )
    }

    /**
     * Overload accepting chat history as ChatMessage list.
     */
    suspend fun assembleFromChatMessages(
        context: Context,
        sessionId: String,
        userPrompt: String,
        messages: List<ChatMessage>,
        targetModel: String,
        customSystemPrompt: String? = null,
        memoryLimit: Int = 5
    ): ContextAssemblyResult {
        val omniMessages = convertChatMessagesToOmni(messages)
        return assemble(
            context = context,
            sessionId = sessionId,
            userPrompt = userPrompt,
            messages = omniMessages,
            targetModel = targetModel,
            customSystemPrompt = customSystemPrompt,
            memoryLimit = memoryLimit
        )
    }

    private fun buildBasePersona(): String {
        return """
# Assistant Identity & Engineering Core

You are Omnivian, an autonomous senior mobile and systems software engineering agent embedded directly within an Android development workspace.
You solve real engineering tasks, inspect codebases, execute deterministic tools, and deploy applications.

## Operational Directives:
1. Action Over Talk: Propose and execute real tool calls instead of describing hypothetical actions.
2. No Mock Data: Never substitute with fake mock APIs, simulated hardware, or stubbed fallbacks when real tools or integrations are available.
3. Surgical Modifications: When updating code, prioritize exact unique target substrings and clean replacements.
4. Structured Reasoning: When solving complex problems or planning multi-step tool executions, formulate your thoughts within <thought>...</thought> tags before issuing tool calls or responses.
5. Concise Responses: Summarize accomplishments clearly and accurately without conversational filler.
        """.trimIndent()
    }

    private fun loadWorkspaceDirectives(sessionId: String, loadedFiles: MutableList<String>): String {
        val wsDir = resolveWorkspaceDirectory(sessionId) ?: return ""
        val directives = StringBuilder()

        for (fileName in WORKSPACE_RULE_FILE_NAMES) {
            val file = File(wsDir, fileName)
            if (file.exists() && file.isFile && file.canRead()) {
                try {
                    val rawText = file.readText().trim()
                    if (rawText.isNotBlank()) {
                        val textToInclude = if (rawText.length > MAX_WORKSPACE_FILE_CHARS) {
                            rawText.take(MAX_WORKSPACE_FILE_CHARS) + "\n\n...[truncated for token budget]..."
                        } else {
                            rawText
                        }
                        directives.append("## Workspace Directives: $fileName\n")
                        directives.append(textToInclude)
                        directives.append("\n\n")
                        loadedFiles.add(fileName)
                    }
                } catch (e: Exception) {
                    LogKeeper.log("PromptAssembler", "RuleReadError", "Failed reading $fileName: ${e.message}")
                }
            }
        }

        return directives.toString().trim()
    }

    private fun resolveWorkspaceDirectory(sessionId: String): File? {
        return try {
            val baseDir = LocalFileManager.getBaseDir()
            if (baseDir != null && sessionId.isNotBlank()) {
                val candidate = File(baseDir, "workspaces/$sessionId")
                if (candidate.exists() && candidate.isDirectory) {
                    return candidate
                }
            }
            LocalFileManager.getWorkspaceDir()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun recallMemories(context: Context, userPrompt: String, limit: Int): List<MemoryEntity> {
        if (userPrompt.isBlank()) return emptyList()
        return try {
            val repo = MemoryRepository.getInstance(context)
            val threshold = BrainSettingsStore.getRecallThreshold(context)
            repo.recallRelevantMemories(prompt = userPrompt, limit = limit, minScoreThreshold = threshold)
        } catch (e: Exception) {
            LogKeeper.log("PromptAssembler", "MemoryRecallError", "Failed memory recall: ${e.message}")
            emptyList()
        }
    }

    private fun resolveActiveTools(context: Context, sessionId: String): List<Tool> {
        return try {
            ToolPermissionManager.init(context)
            val allTools = EngineRegistry.getAllTools()
            allTools.filter { tool -> !ToolPermissionManager.isForbidden(tool.name) }
        } catch (e: Exception) {
            LogKeeper.log("PromptAssembler", "ToolsError", "Error resolving tools: ${e.message}")
            EngineRegistry.getAllTools()
        }
    }

    private fun formatToolsSection(sessionId: String, tools: List<Tool>): String {
        val sb = StringBuilder()
        sb.append("## Available Tools & Sandboxes\n")
        sb.append("You have access to the following deterministic workspace tools. Invoke them using standard function call semantics or XML `<tool_call>{\"name\": \"...\", \"arguments\": {...}}</tool_call>` blocks:\n\n")

        tools.forEach { tool ->
            val requiresApproval = ToolPermissionManager.requiresApproval(sessionId, tool.name)
            val permBadge = if (requiresApproval) "[Requires Approval]" else "[Auto-Approved]"
            sb.append("- **`${tool.name}`** $permBadge: ${tool.description}\n")

            if (tool.parametersSchema.isNotEmpty()) {
                val paramsList = tool.parametersSchema.keys.joinToString(", ") { paramKey ->
                    val paramDef = tool.parametersSchema[paramKey] as? Map<*, *>
                    val type = paramDef?.get("type") ?: "any"
                    val req = if (paramDef?.get("required") == true) "required" else "optional"
                    "$paramKey: $type ($req)"
                }
                sb.append("  - Parameters: `$paramsList`\n")
            }
        }

        return sb.toString().trim()
    }

    private fun convertChatMessagesToOmni(messages: List<ChatMessage>): List<OmniMessage> {
        val list = mutableListOf<OmniMessage>()
        messages.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> list.add(OmniMessage(role = "user", content = msg.text))
                MessageRole.AI -> list.add(OmniMessage(role = "assistant", content = msg.text))
                MessageRole.SYSTEM -> list.add(OmniMessage(role = "system", content = msg.text))
                MessageRole.TOOL -> {
                    list.add(
                        OmniMessage(
                            role = "tool",
                            tool_call_id = msg.toolCallId,
                            name = msg.toolName,
                            content = msg.toolOutput ?: (if (msg.toolStatus == ToolExecutionStatus.REJECTED) "User rejected execution." else "Executing...")
                        )
                    )
                }
                MessageRole.APP_ACTION -> {
                    list.add(OmniMessage(role = "assistant", content = "[Action] ${msg.text}"))
                }
            }
        }
        return list
    }
}
