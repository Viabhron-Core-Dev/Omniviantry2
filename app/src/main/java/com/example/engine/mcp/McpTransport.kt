package com.example.engine.mcp

import com.example.utils.LogKeeper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface McpTransport {
    val state: StateFlow<McpConnectionState>
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun sendRequest(method: String, params: Map<String, Any?>? = null, timeoutMs: Long = 20000L): Result<JSONObject>
    suspend fun sendNotification(method: String, params: Map<String, Any?>? = null): Result<Unit>
}

/**
 * Standard MCP SSE Transport.
 * Streams Server-Sent Events from `endpointUrl`, parses endpoint discovery URIs,
 * and routes JSON-RPC 2.0 requests over HTTP POST.
 */
class McpSseTransport(
    private val config: McpServerConfig,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for SSE stream
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) : McpTransport {

    private val _state = MutableStateFlow(McpConnectionState.DISCONNECTED)
    override val state: StateFlow<McpConnectionState> = _state.asStateFlow()

    private var activeStreamCall: Call? = null
    private var streamJob: Job? = null
    private var postEndpointUrl: String = config.endpointUrl
    private val requestIdCounter = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value == McpConnectionState.CONNECTED || _state.value == McpConnectionState.CONNECTING) {
            return@withContext Result.success(Unit)
        }

        _state.value = McpConnectionState.CONNECTING
        val connectionReady = CompletableDeferred<Unit>()

        try {
            val requestBuilder = Request.Builder()
                .url(config.endpointUrl)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")

            config.headers.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            val call = okHttpClient.newCall(requestBuilder.build())
            activeStreamCall = call

            streamJob = scope.launch {
                try {
                    val response = call.execute()
                    if (!response.isSuccessful) {
                        _state.value = McpConnectionState.ERROR
                        if (!connectionReady.isCompleted) {
                            connectionReady.completeExceptionally(Exception("HTTP ${response.code}: ${response.message}"))
                        }
                        return@launch
                    }

                    _state.value = McpConnectionState.CONNECTED
                    if (!connectionReady.isCompleted) {
                        connectionReady.complete(Unit)
                    }

                    val inputStream = response.body?.byteStream()
                    if (inputStream == null) {
                        _state.value = McpConnectionState.ERROR
                        return@launch
                    }

                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    var currentEventType: String? = null
                    val dataBuffer = StringBuilder()

                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) {
                            // Dispatch accumulated event
                            if (dataBuffer.isNotEmpty()) {
                                val dataStr = dataBuffer.toString().trim()
                                handleSseMessage(currentEventType, dataStr)
                                dataBuffer.clear()
                                currentEventType = null
                            }
                        } else if (line.startsWith("event:")) {
                            currentEventType = line.removePrefix("event:").trim()
                        } else if (line.startsWith("data:")) {
                            val chunk = line.removePrefix("data:").trim()
                            if (dataBuffer.isNotEmpty()) dataBuffer.append("\n")
                            dataBuffer.append(chunk)
                        }
                    }

                    _state.value = McpConnectionState.DISCONNECTED
                    failPendingRequests("SSE stream disconnected.")
                } catch (e: Exception) {
                    if (isActive) {
                        _state.value = McpConnectionState.ERROR
                        if (!connectionReady.isCompleted) {
                            connectionReady.completeExceptionally(e)
                        }
                        failPendingRequests("SSE connection failed: ${e.message}")
                    }
                }
            }

            // Wait up to 10 seconds for initial connection
            withTimeout(10000L) {
                connectionReady.await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = McpConnectionState.ERROR
            disconnect()
            Result.failure(e)
        }
    }

    private fun handleSseMessage(eventType: String?, data: String) {
        when (eventType) {
            "endpoint" -> {
                val resolved = resolveRelativeUrl(config.endpointUrl, data.trim())
                postEndpointUrl = resolved
                LogKeeper.log("McpSseTransport", "Endpoint Discovered", "POST Endpoint: $postEndpointUrl")
            }
            "message", null -> {
                handleIncomingJsonRpc(data)
            }
            else -> {
                handleIncomingJsonRpc(data)
            }
        }
    }

    private fun handleIncomingJsonRpc(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            if (json.has("id")) {
                val id = json.optLong("id", -1L)
                if (id != -1L && pendingRequests.containsKey(id)) {
                    val deferred = pendingRequests.remove(id)
                    deferred?.complete(json)
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("McpSseTransport", "ParseError", "Malformed JSON-RPC: ${e.message}")
        }
    }

    private fun failPendingRequests(reason: String) {
        val iterator = pendingRequests.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            entry.value.completeExceptionally(Exception("Request aborted: $reason"))
        }
    }

    override suspend fun sendRequest(
        method: String,
        params: Map<String, Any?>?,
        timeoutMs: Long
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (_state.value != McpConnectionState.CONNECTED) {
            val connRes = connect()
            if (connRes.isFailure) {
                return@withContext Result.failure(connRes.exceptionOrNull() ?: Exception("Failed to connect to MCP server"))
            }
        }

        val id = requestIdCounter.getAndIncrement()
        val requestJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) {
                put("params", JSONObject(params))
            } else {
                put("params", JSONObject())
            }
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        try {
            val postBody = requestJson.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(postEndpointUrl)
                .post(postBody)

            config.headers.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            val call = okHttpClient.newCall(requestBuilder.build())
            val response = call.execute()

            if (!response.isSuccessful) {
                pendingRequests.remove(id)
                return@withContext Result.failure(Exception("HTTP POST failed with code ${response.code}: ${response.body?.string()}"))
            }

            // In some HTTP-bridged SSE implementations, the JSON-RPC response is returned directly in HTTP body
            val bodyString = response.body?.string()?.trim()
            if (!bodyString.isNullOrEmpty() && (bodyString.startsWith("{") || bodyString.startsWith("["))) {
                try {
                    val directJson = JSONObject(bodyString)
                    if (directJson.optLong("id", -1L) == id) {
                        pendingRequests.remove(id)
                        return@withContext Result.success(directJson)
                    }
                } catch (_: Exception) {}
            }

            // Otherwise, wait for SSE stream message with corresponding ID
            val result = withTimeout(timeoutMs) {
                deferred.await()
            }
            Result.success(result)
        } catch (e: Exception) {
            pendingRequests.remove(id)
            Result.failure(e)
        }
    }

    override suspend fun sendNotification(
        method: String,
        params: Map<String, Any?>?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val notificationJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) {
                put("params", JSONObject(params))
            }
        }

        try {
            val postBody = notificationJson.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(postEndpointUrl)
                .post(postBody)

            config.headers.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code} sending notification $method"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        try {
            streamJob?.cancel()
            streamJob = null
            activeStreamCall?.cancel()
            activeStreamCall = null
            _state.value = McpConnectionState.DISCONNECTED
            failPendingRequests("Disconnected")
        } catch (_: Exception) {}
    }

    private fun resolveRelativeUrl(baseUrl: String, path: String): String {
        return try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                val uri = java.net.URI(baseUrl)
                uri.resolve(path).toString()
            }
        } catch (_: Exception) {
            baseUrl
        }
    }
}

/**
 * Direct HTTP POST JSON-RPC 2.0 Transport for stateless MCP servers.
 */
class McpHttpTransport(
    private val config: McpServerConfig,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : McpTransport {

    private val _state = MutableStateFlow(McpConnectionState.DISCONNECTED)
    override val state: StateFlow<McpConnectionState> = _state.asStateFlow()
    private val requestIdCounter = AtomicLong(1)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = McpConnectionState.CONNECTED
        Result.success(Unit)
    }

    override suspend fun disconnect() {
        _state.value = McpConnectionState.DISCONNECTED
    }

    override suspend fun sendRequest(
        method: String,
        params: Map<String, Any?>?,
        timeoutMs: Long
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        val id = requestIdCounter.getAndIncrement()
        val requestJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) {
                put("params", JSONObject(params))
            } else {
                put("params", JSONObject())
            }
        }

        try {
            val postBody = requestJson.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(config.endpointUrl)
                .post(postBody)

            config.headers.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: $body"))
            }

            val resultJson = JSONObject(body)
            Result.success(resultJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendNotification(
        method: String,
        params: Map<String, Any?>?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val notificationJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) {
                put("params", JSONObject(params))
            }
        }

        try {
            val postBody = notificationJson.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(config.endpointUrl)
                .post(postBody)

            config.headers.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
