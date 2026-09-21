package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.utils.LogEntry
import com.example.utils.LogKeeper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogKeeperScreen(onNavigateBack: () -> Unit) {
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val isEnabled by LogKeeper.isEnabled.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableStateOf(3) }
    val tabs = listOf("6h", "12h", "24h", "All")

    fun loadLogs() {
        coroutineScope.launch {
            isLoading = true
            logs = LogKeeper.readLogsFromDisk()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
        LogKeeper.logUpdateSignal.collect {
            logs = LogKeeper.readLogsFromDisk()
        }
    }

    val filteredLogs = remember(logs, selectedTabIndex) {
        val now = System.currentTimeMillis()
        val timeLimit = when (selectedTabIndex) {
            0 -> now - 6 * 60 * 60 * 1000L
            1 -> now - 12 * 60 * 60 * 1000L
            2 -> now - 24 * 60 * 60 * 1000L
            else -> 0L
        }
        logs.filter { it.timestamp >= timeLimit }.sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Keeper", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { LogKeeper.toggle(it) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = { loadLogs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Logs")
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = filteredLogs.joinToString("\n") { 
                            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(it.timestamp))
                            "[$time] [${it.type}] ${it.component}\n${it.message}"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Logs", text))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val success = LogKeeper.exportAndClear(context)
                            if (success) {
                                logs = emptyList()
                                Toast.makeText(context, "Logs exported to Downloads and disk cleared", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No logs to export", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export & Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            ) 
                        }
                    )
                }
            }

            if (filteredLogs.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs recorded on disk.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(filteredLogs) { log ->
                        LogEntryCard(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(log: LogEntry) {
    val timeString = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(log.timestamp))
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.component,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (log.stackTrace != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.stackTrace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
