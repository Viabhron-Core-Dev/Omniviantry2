package com.example.engine.tools

import org.json.JSONArray
import org.json.JSONObject

interface Tool {
    val name: String
    val description: String
    val permission: ToolPermission get() = ToolPermission.USE_FREELY
    val parametersSchema: Map<String, Any> get() = emptyMap()

    suspend fun execute(args: Map<String, Any>): String

    fun toOpenAiToolDefinition(): JSONObject {
        val root = JSONObject()
        root.put("type", "function")
        val fn = JSONObject()
        fn.put("name", name)
        fn.put("description", description)

        val schemaObj = JSONObject()
        schemaObj.put("type", "object")
        val propsObj = JSONObject()
        val requiredArray = JSONArray()

        parametersSchema.forEach { (propName, propDef) ->
            if (propDef is Map<*, *>) {
                val propJson = JSONObject()
                propDef.forEach { (k, v) ->
                    if (k != null && v != null) {
                        if (k != "required") {
                            propJson.put(k.toString(), v)
                        }
                    }
                }
                propsObj.put(propName, propJson)
                if (propDef["required"] == true) {
                    requiredArray.put(propName)
                }
            }
        }
        schemaObj.put("properties", propsObj)
        if (requiredArray.length() > 0) {
            schemaObj.put("required", requiredArray)
        }
        fn.put("parameters", schemaObj)
        root.put("function", fn)
        return root
    }
}

