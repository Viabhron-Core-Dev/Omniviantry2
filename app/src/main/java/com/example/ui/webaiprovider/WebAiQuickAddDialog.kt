package com.example.ui.webaiprovider

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.webaiprovider.*
import com.example.ui.theme.MyApplicationTheme

/**
 * Transparent translucent Activity that acts as a modal overlay over
 * Chrome Custom Tabs when [+] is pressed on the RemoteViews bottom toolbar.
 */
class WebAiQuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WebAiQuickAddDialog(
                    onDismiss = { finish() },
                    onServiceSelected = { service, profile ->
                        val targetUrl = profile.launchUrl.ifBlank { service.baseUrl }
                        val newTab = WebAiActiveTab(
                            id = "${service.id}_${System.currentTimeMillis()}",
                            serviceId = service.id,
                            serviceName = service.name,
                            brandColorHex = service.brandColorHex,
                            url = targetUrl,
                            sessionMode = profile.sessionMode,
                            profileLabel = profile.label,
                            targetPackage = profile.targetPackage,
                            isCurrent = true
                        )
                        WebAiCustomTabManager.appendAndSwitchTab(this, newTab)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun WebAiQuickAddDialog(
    onDismiss: () -> Unit,
    onServiceSelected: (WebAiService, WebAiAccountProfile) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WebAiProviderManager.init(context)
    }

    val services by WebAiProviderManager.services.collectAsState()
    val enabledServices = remember(services) { services.filter { it.enabled } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Add Provider Tab",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Select an AI model or profile to append",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(enabledServices) { service ->
                        val brandColor = remember(service.brandColorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(service.brandColorHex))
                            } catch (e: Exception) {
                                Color(0xFF1A73E8)
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(brandColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            service.name.take(2).uppercase(),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = brandColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            service.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            service.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Profiles list for this service
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (profile in service.profiles) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onServiceSelected(service, profile)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        profile.label,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        when (profile.sessionMode) {
                                                            WebAiSessionMode.DEFAULT -> "Shared Session (Cookies Kept)"
                                                            WebAiSessionMode.EPHEMERAL -> "Incognito / Ephemeral"
                                                            WebAiSessionMode.CUSTOM_BROWSER -> "Target: ${profile.targetPackage ?: "External Browser"}"
                                                        },
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
