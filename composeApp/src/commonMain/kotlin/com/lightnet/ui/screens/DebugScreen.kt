package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightnet.debug.DebugLog
import com.lightnet.debug.DebugLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(bottomBar: @Composable () -> Unit) {
    val entries by DebugLog.entries.collectAsState()
    val displayEntries = remember(entries) { entries.asReversed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Log") },
                actions = {
                    IconButton(onClick = { DebugLog.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear log")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (displayEntries.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No entries yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(displayEntries, key = { it.id }) { entry ->
                    LogRow(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: DebugLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.offsetMs.toOffsetLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
        )
        when (entry) {
            is DebugLogEntry.WsSent -> {
                Text("→", color = wsColor, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = entry.type.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = wsColor,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${entry.bytes} B",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            is DebugLogEntry.WsReceived -> {
                Text("←", color = wsRxColor, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = entry.type.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = wsRxColor,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${entry.bytes} B",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                entry.durationMs?.let {
                    Text(
                        text = "${it} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = durationColor,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            is DebugLogEntry.Http -> {
                val statusColor = if (entry.statusCode in 200..299) wsRxColor else errorColor
                Text(
                    text = entry.method,
                    style = MaterialTheme.typography.labelSmall,
                    color = httpColor,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = entry.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Text(
                    text = "${entry.statusCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "${entry.durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = durationColor,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun Long.toOffsetLabel(): String {
    val m  = this / 60_000
    val s  = (this % 60_000) / 1000
    val ms = this % 1000
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${ms.toString().padStart(3, '0')}"
}

private val wsColor      = Color(0xFF64B5F6)  // blue  — sent
private val wsRxColor    = Color(0xFF81C784)  // green — received
private val httpColor    = Color(0xFFFFB74D)  // amber — http method
private val durationColor = Color(0xFFFF8A65) // orange — timing
private val errorColor   = Color(0xFFEF5350)  // red — HTTP error status
