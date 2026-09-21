package com.example.ui.settings.omniroot

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectToKeyWebViewScreen(
    providerId: String,
    onNavigateBack: () -> Unit,
    viewModel: AiManagerViewModel = viewModel()
) {
    val providers by viewModel.providers.collectAsState()
    val provider = providers.find { it.id == providerId }
    var pastedKey by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Get Key: ${provider?.name ?: "Unknown"}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (provider == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading provider details...")
                }
            } else {
                Text(
                    "We use Chrome Custom Tabs so you can securely log in with your existing browser sessions or switch accounts easily.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                val targetUrl = provider.loginUrl.takeIf { it.isNotEmpty() } ?: provider.baseUrl
                val googleChooserUrl = "https://accounts.google.com/AccountChooser?continue=" +
                        java.net.URLEncoder.encode(targetUrl, "UTF-8")

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val customTabsIntent = CustomTabsIntent.Builder().build()
                            customTabsIntent.launchUrl(context, Uri.parse(googleChooserUrl))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("Sign In with Google Account Chooser")
                    }

                    OutlinedButton(
                        onClick = {
                            val customTabsIntent = CustomTabsIntent.Builder().build()
                            customTabsIntent.launchUrl(context, Uri.parse(targetUrl))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open ${provider.name} Console Direct")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "After generating your API key, return here to save it under an account alias.",
                    style = MaterialTheme.typography.titleSmall
                )

                // Quick account alias suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Personal", "Work", "Account 2", "Backup").forEach { tag ->
                        FilterChip(
                            selected = alias == tag,
                            onClick = { alias = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Account Alias (e.g., Personal, Work)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = pastedKey,
                    onValueChange = { pastedKey = it },
                    label = { 
                        Text(
                            when (providerId) {
                                "cloudflare_ai" -> "Key (account_id:api_token)"
                                "github_models" -> "GitHub Token (ghp_... or github_pat_...)"
                                "huggingface" -> "User Access Token (hf_...)"
                                "cerebras" -> "Cerebras API Key (csk-...)"
                                "sambanova" -> "SambaNova API Key"
                                "mistral" -> "Mistral API Key"
                                "cohere" -> "Cohere Trial Key"
                                "glhf" -> "GLHF API Key (glhf_...)"
                                else -> "API Key"
                            }
                        ) 
                    },
                    placeholder = {
                        Text(
                            when (providerId) {
                                "cloudflare_ai" -> "e.g. 1a2b3c4d5e:xyz_token"
                                "github_models" -> "ghp_..."
                                "huggingface" -> "hf_..."
                                "cerebras" -> "csk-..."
                                "glhf" -> "glhf_..."
                                else -> "Enter key here..."
                            }
                        )
                    },
                    supportingText = {
                        when (providerId) {
                            "cloudflare_ai" -> Text("Format: account_id:api_token (find Account ID in Cloudflare dashboard)")
                            "github_models" -> Text("Free tier: personal access token with no special scopes needed")
                            "huggingface" -> Text("Free tier: Settings -> Access Tokens -> New Token (Read scope)")
                            "cerebras" -> Text("Free tier: 1M tokens/day with zero credit card required")
                            "mistral" -> Text("Free tier: Experiment plan with zero credit card required")
                            "sambanova" -> Text("Free tier: 200k tokens/day with zero credit card required")
                            "cohere" -> Text("Free tier: Trial key with 1,000 monthly API calls")
                            "glhf" -> Text("Free beta: Register at glhf.chat/users/settings/api with zero credit card")
                            else -> null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        if (pastedKey.isNotBlank()) {
                            viewModel.saveRealKey(
                                providerId = providerId,
                                alias = alias.ifBlank { "${provider.name} Key" },
                                keyValue = pastedKey
                            )
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pastedKey.isNotBlank()
                ) {
                    Text("Save API Key")
                }
            }
        }
    }
}
