package com.example.engine.omniroot.tools

import android.content.Context
import com.example.engine.EngineRegistry
import com.example.engine.fs.LocalFileManager
import com.example.engine.tools.KnowledgeBitsTool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

object NativeToolExecutor {
    
    fun execute(context: Context, functionName: String, argumentsJson: String): String {
        return try {
            LocalFileManager.init(context)
            val args = JSONObject(argumentsJson)
            val argsMap = mutableMapOf<String, Any>()
            val keys = args.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                argsMap[k] = args.get(k)
            }

            // Check if registered directly in EngineRegistry
            val directTool = EngineRegistry.getTool(functionName)
            if (directTool != null) {
                return runBlocking {
                    directTool.execute(argsMap)
                }
            }

            // Map legacy aliases
            when (functionName) {
                "read_file" -> {
                    val tool = EngineRegistry.getTool("view_file")
                    if (tool != null) {
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "write_file" -> {
                    val tool = EngineRegistry.getTool("create_file")
                    if (tool != null) {
                        if (!argsMap.containsKey("TargetFile") && argsMap.containsKey("path")) {
                            argsMap["TargetFile"] = argsMap["path"]!!
                        }
                        if (!argsMap.containsKey("Content") && argsMap.containsKey("content")) {
                            argsMap["Content"] = argsMap["content"]!!
                        }
                        argsMap["Overwrite"] = true
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "list_files" -> {
                    val tool = EngineRegistry.getTool("list_dir")
                    if (tool != null) {
                        if (!argsMap.containsKey("DirectoryPath") && argsMap.containsKey("path")) {
                            argsMap["DirectoryPath"] = argsMap["path"]!!
                        }
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "eval_js", "evaluate_javascript", "js", "run_js" -> {
                    val tool = EngineRegistry.getTool("js_sandbox")
                    if (tool != null) {
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "shell", "terminal", "exec" -> {
                    val tool = EngineRegistry.getTool("run_command")
                    if (tool != null) {
                        if (!argsMap.containsKey("CommandLine") && argsMap.containsKey("command")) {
                            argsMap["CommandLine"] = argsMap["command"]!!
                        }
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "python", "py", "execute_python" -> {
                    val tool = EngineRegistry.getTool("run_python")
                    if (tool != null) {
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "unzip", "zip", "archive", "compress" -> {
                    val tool = EngineRegistry.getTool("archive_manage")
                    if (tool != null) {
                        if (!argsMap.containsKey("Action")) {
                            argsMap["Action"] = functionName
                        }
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "read_doc", "read_pdf", "read_docx", "read_pptx", "read_xlsx", "extract_document" -> {
                    val tool = EngineRegistry.getTool("parse_document")
                    if (tool != null) {
                        if (!argsMap.containsKey("FilePath") && argsMap.containsKey("path")) {
                            argsMap["FilePath"] = argsMap["path"]!!
                        }
                        return runBlocking { tool.execute(argsMap) }
                    }
                }
                "knowledge_bits", "save_knowledge_bit", "fetch_and_cache_bit", "query_knowledge_bits", "read_knowledge_bit", "refresh_knowledge_bit" -> {
                    return runBlocking {
                        if (functionName != "knowledge_bits" && !argsMap.containsKey("action")) {
                            argsMap["action"] = when (functionName) {
                                "save_knowledge_bit" -> "save"
                                "fetch_and_cache_bit" -> "fetch_and_cache"
                                "query_knowledge_bits" -> "query"
                                "read_knowledge_bit" -> "read"
                                "refresh_knowledge_bit" -> "refresh"
                                else -> "query"
                            }
                        }
                        KnowledgeBitsTool(context).execute(argsMap)
                    }
                }
            }

            "Error: Unknown tool '$functionName'"
        } catch (e: Exception) {
            "Error executing tool $functionName: ${e.message}"
        }
    }
}
