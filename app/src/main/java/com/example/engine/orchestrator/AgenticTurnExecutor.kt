package com.example.engine.orchestrator

import android.content.Context
import com.example.engine.EngineRegistry
import com.example.engine.db.AppDatabase
import com.example.engine.router.FallbackChainRouter
import com.example.engine.router.FallbackExecutionResult
import com.example.engine.tools.ToolPermissionManager
import com.example.ui.chat.ChatMessage
import com.example.ui.chat.MessageRole
import com.example.ui.chat.OmniFunctionCall
import com.example.ui.chat.OmniFunctionDef
import com.example.ui.chat.OmniMessage
import com.example.ui.chat.OmniTool
import com.example.ui.chat.OmniToolCall
import com.example.ui.chat.ToolExecutionStatus
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class UserToolDecision {
    ALLOW_ONCE,
    ALLOW_ALWAYS_SESSION,
    REJECT
}

sealed class AgenticEvent {
    data class MessageAdded(val message: ChatMessage) : AgenticEvent()
    data class MessageUpdated(val message: ChatMessage) : AgenticEvent()
    data class TokenChunk(val chunk: String) : AgenticEvent()
    data class TurnCompleted(val finalMessage: ChatMessage) : AgenticEvent()
    data class RequiresUserApproval(val toolMessage: ChatMessage) : AgenticEvent()
    data class TurnFailed(val error: String) : AgenticEvent()
}

/**
 * Universal Agentic Tool Calling Orchestrator.
 * Handles the complete multi-turn LLM reasoning and deterministic tool execution loop
 * with failover auto-routing, schema serialization, XML/JSON parsing, and human-in-the-loop approvals.
 */
object AgenticTurnExecutor {

    private const val MAX_AGENTIC_ITERATIONS = 10

    suspend fun executeTurn(
        context: Context,
        sessionId: String,
        history: List<ChatMessage>,
        targetModel: String,
        systemPrompt: String = "",
        temperature: Float? = null,
        topP: Float? = null,
        maxTokens: Int? = null,
        onEvent: suspend (AgenticEvent) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val workingMessages = history.toMutableList()
        var currentIteration = 0
        val activeTools = getActiveOmniTools(context, sessionId)

        LogKeeper.log(
            "AgenticTurnExecutor",
            "TurnStarted",
            "Session '$sessionId', Target Model '$targetModel', Active Tools: ${activeTools.size}"
        )

        while (currentIteration < MAX_AGENTIC_ITERATIONS) {
            currentIteration++
            val omniMessages = prepareOmniMessages(workingMessages, systemPrompt)

            // Step 1: Query Provider with Fallback Chain Auto-Routing
            val executionResult: FallbackExecutionResult = FallbackChainRouter.dispatchWithFallback(
                context = context,
                messages = omniMessages,
                targetModel = targetModel,
                tools = if (activeTools.isNotEmpty()) activeTools else null,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                onChunk = { chunk ->
                    kotlinx.coroutines.runBlocking {
                        onEvent(AgenticEvent.TokenChunk(chunk))
                    }
                }
            )

            if (!executionResult.isSuccess || executionResult.response == null) {
                val errorMsg = executionResult.errorMessage ?: "AI provider failed to generate a response."
                LogKeeper.log("AgenticTurnExecutor", "TurnError", "Iteration $currentIteration failed: $errorMsg")
                onEvent(AgenticEvent.TurnFailed(errorMsg))
                return@withContext
            }

            val choice = executionResult.response.choices?.firstOrNull()
            val assistantMsg = choice?.message
            val rawText = assistantMsg?.content ?: ""
            val toolCalls = mutableListOf<OmniToolCall>()

            // Step 2: Extract tool calls from OpenAI structured tool_calls or XML <tool_call> tags
            if (assistantMsg?.tool_calls != null && assistantMsg.tool_calls.isNotEmpty()) {
                toolCalls.addAll(assistantMsg.tool_calls)
            } else {
                val extractedXmlTools = parseXmlToolCalls(rawText)
                if (extractedXmlTools.isNotEmpty()) {
                    toolCalls.addAll(extractedXmlTools)
                }
            }

            // Step 3: If no tool calls requested, emit the final AI response and finish turn
            if (toolCalls.isEmpty()) {
                val cleanWithoutXml = cleanFinalText(rawText)
                val reasoning = com.example.engine.brain.ReasoningParser.parse(cleanWithoutXml)
                val finalAiMessage = ChatMessage(
                    text = reasoning.cleanAnswer,
                    role = MessageRole.AI,
                    modelName = executionResult.actualModelName,
                    providerId = executionResult.actualProviderId,
                    routedViaFallback = executionResult.routedViaFallback,
                    fallbackReason = executionResult.fallbackReason,
                    thoughtContent = reasoning.thoughtContent,
                    planContent = reasoning.planContent,
                    thinkingDurationMs = reasoning.thinkingDurationMs,
                    isFolded = false
                )
                workingMessages.add(finalAiMessage)
                onEvent(AgenticEvent.TurnCompleted(finalAiMessage))
                LogKeeper.log(
                    "AgenticTurnExecutor",
                    "TurnCompleted",
                    "Completed in $currentIteration iterations via ${executionResult.actualModelName}"
                )
                return@withContext
            }

            // Step 4: Tool calls requested -> Process each tool call
            LogKeeper.log(
                "AgenticTurnExecutor",
                "ToolCallsRequested",
                "Iteration $currentIteration: ${toolCalls.size} tool call(s) requested: ${toolCalls.map { it.function.name }}"
            )

            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                val rawArgsJson = toolCall.function.arguments.ifBlank { "{}" }
                val parsedArgs = parseArgsToMap(rawArgsJson)
                val toolCallId = toolCall.id.ifBlank { UUID.randomUUID().toString() }

                // Check if tool is forbidden by policy
                if (ToolPermissionManager.isForbidden(toolName)) {
                    val rejectedMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.TOOL,
                        toolCallId = toolCallId,
                        toolName = toolName,
                        toolArgsJson = rawArgsJson,
                        toolStatus = ToolExecutionStatus.FAILED,
                        toolOutput = "Error: Tool '$toolName' is disabled by policy in Global Settings.",
                        modelName = executionResult.actualModelName,
                        providerId = executionResult.actualProviderId,
                        isFolded = false
                    )
                    workingMessages.add(rejectedMessage)
                    onEvent(AgenticEvent.MessageAdded(rejectedMessage))
                    LogKeeper.log("AgenticTurnExecutor", "ToolForbidden", "Tool '$toolName' call blocked by policy.")
                    continue
                }

                // Check permissions
                val requiresApproval = ToolPermissionManager.requiresApproval(sessionId, toolName)

                if (requiresApproval) {
                    val pendingMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.TOOL,
                        toolCallId = toolCallId,
                        toolName = toolName,
                        toolArgsJson = rawArgsJson,
                        toolStatus = ToolExecutionStatus.PENDING_APPROVAL,
                        modelName = executionResult.actualModelName,
                        providerId = executionResult.actualProviderId,
                        isFolded = false
                    )
                    workingMessages.add(pendingMessage)
                    onEvent(AgenticEvent.RequiresUserApproval(pendingMessage))
                    LogKeeper.log("AgenticTurnExecutor", "ApprovalRequired", "Tool '$toolName' paused for user approval.")
                    return@withContext // Pause turn until user confirms or rejects
                }

                // Execute tool immediately if approved or freely usable
                val executingMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.TOOL,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    toolArgsJson = rawArgsJson,
                    toolStatus = ToolExecutionStatus.EXECUTING,
                    modelName = executionResult.actualModelName,
                    providerId = executionResult.actualProviderId,
                    isFolded = false
                )
                workingMessages.add(executingMessage)
                onEvent(AgenticEvent.MessageAdded(executingMessage))

                val startTime = System.currentTimeMillis()
                val toolInstance = EngineRegistry.getTool(toolName)
                val (output, status) = if (toolInstance != null) {
                    try {
                        val result = toolInstance.execute(parsedArgs)
                        Pair(result, ToolExecutionStatus.COMPLETED)
                    } catch (e: Exception) {
                        Pair("Tool execution failed: ${e.message}", ToolExecutionStatus.FAILED)
                    }
                } else {
                    Pair("Error: Tool '$toolName' is not registered in EngineRegistry.", ToolExecutionStatus.FAILED)
                }
                val durationMs = System.currentTimeMillis() - startTime

                val completedMessage = executingMessage.copy(
                    toolOutput = output,
                    toolDurationMs = durationMs,
                    toolStatus = status
                )

                // Update in memory and notify UI
                val idx = workingMessages.indexOfFirst { it.id == executingMessage.id }
                if (idx != -1) {
                    workingMessages[idx] = completedMessage
                }
                onEvent(AgenticEvent.MessageUpdated(completedMessage))

                LogKeeper.log(
                    "AgenticTurnExecutor",
                    "ToolExecuted",
                    "Tool '$toolName' finished with status $status in ${durationMs}ms"
                )
            }
            // Loop back to next iteration with tool outputs attached to context!
        }

        onEvent(AgenticEvent.TurnFailed("Reached maximum agentic loop limit ($MAX_AGENTIC_ITERATIONS iterations)."))
    }

    /**
     * Resumes an agentic turn after the user approves or rejects a pending tool execution.
     */
    suspend fun resumeAfterApproval(
        context: Context,
        sessionId: String,
        history: List<ChatMessage>,
        pendingMessageId: String,
        decision: UserToolDecision,
        targetModel: String,
        systemPrompt: String = "",
        temperature: Float? = null,
        topP: Float? = null,
        maxTokens: Int? = null,
        onEvent: suspend (AgenticEvent) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val workingMessages = history.toMutableList()
        val pendingIdx = workingMessages.indexOfFirst { it.id == pendingMessageId }
        if (pendingIdx == -1) {
            onEvent(AgenticEvent.TurnFailed("Pending tool message not found."))
            return@withContext
        }

        val pendingMsg = workingMessages[pendingIdx]
        val toolName = pendingMsg.toolName ?: "unknown"

        if (decision == UserToolDecision.REJECT) {
            val rejectedMsg = pendingMsg.copy(
                toolStatus = ToolExecutionStatus.REJECTED,
                toolOutput = "Execution rejected by user."
            )
            workingMessages[pendingIdx] = rejectedMsg
            onEvent(AgenticEvent.MessageUpdated(rejectedMsg))
            LogKeeper.log("AgenticTurnExecutor", "ToolRejected", "User rejected tool '$toolName'")
        } else {
            if (decision == UserToolDecision.ALLOW_ALWAYS_SESSION) {
                ToolPermissionManager.approveForSession(sessionId, toolName)
            }

            // Update to Executing
            val execMsg = pendingMsg.copy(toolStatus = ToolExecutionStatus.EXECUTING)
            workingMessages[pendingIdx] = execMsg
            onEvent(AgenticEvent.MessageUpdated(execMsg))

            val startTime = System.currentTimeMillis()
            val parsedArgs = parseArgsToMap(pendingMsg.toolArgsJson ?: "{}")
            val toolInstance = EngineRegistry.getTool(toolName)

            val (output, status) = if (toolInstance != null) {
                try {
                    val result = toolInstance.execute(parsedArgs)
                    Pair(result, ToolExecutionStatus.COMPLETED)
                } catch (e: Exception) {
                    Pair("Tool execution error: ${e.message}", ToolExecutionStatus.FAILED)
                }
            } else {
                Pair("Error: Tool '$toolName' is not registered.", ToolExecutionStatus.FAILED)
            }
            val durationMs = System.currentTimeMillis() - startTime

            val completedMsg = execMsg.copy(
                toolOutput = output,
                toolDurationMs = durationMs,
                toolStatus = status
            )
            workingMessages[pendingIdx] = completedMsg
            onEvent(AgenticEvent.MessageUpdated(completedMsg))
        }

        // Resume remaining agentic turn
        executeTurn(
            context = context,
            sessionId = sessionId,
            history = workingMessages,
            targetModel = targetModel,
            systemPrompt = systemPrompt,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            onEvent = onEvent
        )
    }

    private suspend fun getActiveOmniTools(context: Context, sessionId: String): List<OmniTool> {
        val db = AppDatabase.getDatabase(context)
        val config = db.workspaceConfigDao().getConfig(sessionId)
        val integrations = config?.integrations?.trim() ?: ""

        // If explicitly disabled or set to none/off, do not provision tools
        if (integrations.equals("none", ignoreCase = true) ||
            integrations.equals("off", ignoreCase = true) ||
            integrations.equals("disabled", ignoreCase = true)
        ) {
            return emptyList()
        }

        val allTools = EngineRegistry.getAllTools()

        // If user defined specific tools / integrations (comma or semicolon separated)
        val selectedTools = if (integrations.isNotBlank() &&
            !integrations.contains("default", ignoreCase = true) &&
            !integrations.contains("all", ignoreCase = true)
        ) {
            val parts = integrations.split(",", ";").map { it.trim().lowercase() }
            allTools.filter { tool ->
                parts.any { p -> tool.name.lowercase().contains(p) || p.contains(tool.name.lowercase()) }
            }
        } else {
            allTools
        }

        val permittedTools = selectedTools.filter { tool -> !ToolPermissionManager.isForbidden(tool.name) }

        return permittedTools.map { tool ->
            OmniTool(
                type = "function",
                function = OmniFunctionDef(
                    name = tool.name,
                    description = tool.description,
                    parameters = mapOf(
                        "type" to "object",
                        "properties" to tool.parametersSchema
                    )
                )
            )
        }
    }

    private fun prepareOmniMessages(messages: List<ChatMessage>, systemPrompt: String): List<OmniMessage> {
        val list = mutableListOf<OmniMessage>()
        if (systemPrompt.isNotBlank()) {
            list.add(OmniMessage(role = "system", content = systemPrompt))
        }

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
                    // App actions can be mapped as assistant notifications
                    list.add(OmniMessage(role = "assistant", content = "[Action] ${msg.text}"))
                }
            }
        }
        return list
    }

    private fun parseArgsToMap(jsonStr: String): Map<String, Any> {
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, Any>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                map[key] = value
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseXmlToolCalls(text: String): List<OmniToolCall> {
        val list = mutableListOf<OmniToolCall>()
        val regex = Regex("<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(text).forEach { match ->
            try {
                val jsonStr = match.groupValues[1]
                val obj = JSONObject(jsonStr)
                val name = obj.optString("name")
                val args = if (obj.has("arguments")) obj.optJSONObject("arguments")?.toString() ?: obj.optString("arguments") else "{}"
                if (name.isNotBlank()) {
                    list.add(
                        OmniToolCall(
                            id = UUID.randomUUID().toString(),
                            type = "function",
                            function = OmniFunctionCall(name = name, arguments = args)
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        return list
    }

    private fun cleanFinalText(text: String): String {
        return text.replace(Regex("<tool_call>.*?</tool_call>", RegexOption.DOT_MATCHES_ALL), "").trim()
    }
}
