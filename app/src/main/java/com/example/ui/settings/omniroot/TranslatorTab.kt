package com.example.ui.settings.omniroot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.engine.db.AppDatabase
import com.example.engine.db.FallbackChainEntity
import com.example.engine.omniroot.pipeline.TranslationEngine
import com.example.ui.chat.OmniRequest
import com.example.ui.chat.OmniMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorTab() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    val fallbackChains by db.fallbackChainDao().getAllChains().collectAsState(initial = emptyList())
    val allKeys by db.apiKeyDao().getAllKeys().collectAsState(initial = emptyList())
    var showCreateChainDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Combo Routing (Fallback Chains)", style = MaterialTheme.typography.titleLarge)
            Text(
                "Create dynamic routing chains across providers and multiple accounts. If a model fails or hits HTTP 429 rate limits, OmniRoot auto-pools to your other account keys before falling back to the next model in the chain.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showCreateChainDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Chain")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Custom Chain")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("One-Tap Zero-Card Presets", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val arr = JSONArray()
                        arr.put("groq/llama-3.3-70b-versatile")
                        arr.put("cerebras/llama-3.3-70b")
                        arr.put("google_ai_studio/gemini-2.5-flash")
                        scope.launch {
                            db.fallbackChainDao().insertChain(
                                FallbackChainEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = "Zero-Cost Ultra Speed",
                                    chainDataJson = arr.toString(),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                ) {
                    Text("Ultra-Speed Preset")
                }
                OutlinedButton(
                    onClick = {
                        val arr = JSONArray()
                        arr.put("google_ai_studio/gemini-2.5-pro")
                        arr.put("mistral/codestral-latest")
                        arr.put("openrouter/meta-llama/llama-3.3-70b-instruct:free")
                        scope.launch {
                            db.fallbackChainDao().insertChain(
                                FallbackChainEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = "Zero-Cost Frontier Coding",
                                    chainDataJson = arr.toString(),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                ) {
                    Text("Coding Preset")
                }
            }
        }
        
        items(fallbackChains) { chain ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(chain.name, style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = {
                                scope.launch {
                                    db.fallbackChainDao().deleteChain(chain)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Chain", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val list = try {
                        val arr = JSONArray(chain.chainDataJson)
                        List(arr.length()) { arr.getString(it) }
                    } catch (e: Exception) { emptyList() }
                    
                    list.forEachIndexed { index, modelStr ->
                        val atIdx = modelStr.indexOf('@')
                        val modelOnly = if (atIdx != -1) modelStr.substring(0, atIdx) else modelStr
                        val accountOnly = if (atIdx != -1) modelStr.substring(atIdx + 1) else null
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${index + 1}. $modelOnly", style = MaterialTheme.typography.bodyMedium)
                            Surface(
                                color = if (accountOnly != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = if (accountOnly != null) "Account: $accountOnly" else "Account: Auto Pool",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (accountOnly != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Payload Translation Playground", style = MaterialTheme.typography.titleLarge)
            Text("See how OmniRoot translates OpenAI-compatible payloads into native provider formats before sending them over the wire.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            TranslatorPlayground()
        }
    }

    if (showCreateChainDialog) {
        var chainName by remember { mutableStateOf("") }
        var provider1 by remember { mutableStateOf("google_ai_studio/gemini-2.5-pro") }
        var account1 by remember { mutableStateOf("") }
        var provider2 by remember { mutableStateOf("google_ai_studio/gemini-2.5-flash") }
        var account2 by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showCreateChainDialog = false },
            title = { Text("New Multi-Account Fallback Chain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = chainName,
                        onValueChange = { chainName = it },
                        label = { Text("Chain Name") },
                        placeholder = { Text("e.g. Gemini Pro to Flash (Work/Personal)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = provider1,
                        onValueChange = { provider1 = it },
                        label = { Text("Priority 1 Model") },
                        placeholder = { Text("provider/model") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = account1,
                        onValueChange = { account1 = it },
                        label = { Text("Priority 1 Account Alias (Optional)") },
                        placeholder = { Text("Leave blank for Auto Pool, or e.g. Work") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = provider2,
                        onValueChange = { provider2 = it },
                        label = { Text("Priority 2 Model") },
                        placeholder = { Text("provider/model") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = account2,
                        onValueChange = { account2 = it },
                        label = { Text("Priority 2 Account Alias (Optional)") },
                        placeholder = { Text("Leave blank for Auto Pool, or e.g. Personal") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val arr = JSONArray()
                    if (provider1.isNotBlank()) {
                        val item1 = if (account1.isNotBlank()) "${provider1.trim()}@${account1.trim()}" else provider1.trim()
                        arr.put(item1)
                    }
                    if (provider2.isNotBlank()) {
                        val item2 = if (account2.isNotBlank()) "${provider2.trim()}@${account2.trim()}" else provider2.trim()
                        arr.put(item2)
                    }
                    val json = arr.toString()
                    
                    scope.launch {
                        db.fallbackChainDao().insertChain(
                            FallbackChainEntity(
                                id = UUID.randomUUID().toString(),
                                name = chainName.ifBlank { "Multi-Account Chain" },
                                chainDataJson = json,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        showCreateChainDialog = false
                    }
                }) {
                    Text("Save Chain")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateChainDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorPlayground() {
    var inputJson by remember { mutableStateOf(
        """
        {
          "model": "gemini",
          "messages": [
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": "Hello!"}
          ]
        }
        """.trimIndent()
    ) }
    
    var outputFormat by remember { mutableStateOf(TranslationEngine.ProviderFormat.ANTHROPIC) }
    var outputJson by remember { mutableStateOf("") }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = inputJson,
            onValueChange = { inputJson = it },
            label = { Text("OpenAI JSON Payload") },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = outputFormat == TranslationEngine.ProviderFormat.ANTHROPIC,
                onClick = { outputFormat = TranslationEngine.ProviderFormat.ANTHROPIC },
                label = { Text("Anthropic Messages API") }
            )
            FilterChip(
                selected = outputFormat == TranslationEngine.ProviderFormat.GEMINI,
                onClick = { outputFormat = TranslationEngine.ProviderFormat.GEMINI },
                label = { Text("Gemini Native API") }
            )
        }
        
        Button(
            onClick = {
                try {
                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    val req = moshi.adapter(OmniRequest::class.java).fromJson(inputJson)
                    if (req != null) {
                        outputJson = TranslationEngine.translateRequest(req, outputFormat)
                    } else {
                        outputJson = "Error: Could not parse request."
                    }
                } catch (e: Exception) {
                    outputJson = "Error: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Translate, contentDescription = "Translate")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translate Payload")
        }
        
        if (outputJson.isNotEmpty()) {
            OutlinedTextField(
                value = outputJson,
                onValueChange = {},
                readOnly = true,
                label = { Text("Translated Output") },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
