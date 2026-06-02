package com.lightnet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightnet.debug.ConnectStatus
import com.lightnet.debug.DebugLog
import com.lightnet.debug.DebugLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val entries by DebugLog.entries.collectAsState()
    val displayEntries = remember(entries) { entries.asReversed() }
    val debugMode by DebugLog.debugMode.collectAsState()

    var selectedEntry by remember { mutableStateOf<DebugLogEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { DebugLog.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear log")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Debug mode", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = debugMode,
                    onCheckedChange = { DebugLog.debugMode.value = it },
                )
            }
            HorizontalDivider()

            if (displayEntries.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(displayEntries, key = { it.id }) { entry ->
                        LogRow(entry, onClick = { selectedEntry = entry })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        EntryDetailDialog(entry, onDismiss = { selectedEntry = null })
    }
}

// ── Row ───────────────────────────────────────────────────────────────────────

@Composable
private fun LogRow(entry: DebugLogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.offsetMs.toOffsetLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            is DebugLogEntry.WsConnect -> {
                val (glyph, color) = when (entry.status) {
                    ConnectStatus.ATTEMPT      -> "⋯" to MaterialTheme.colorScheme.onSurfaceVariant
                    ConnectStatus.CONNECTED    -> "✓" to wsRxColor
                    ConnectStatus.FAILED       -> "✗" to errorColor
                    ConnectStatus.DISCONNECTED -> "⊘" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(glyph, color = color, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = entry.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "${entry.host}:${entry.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = errorColor,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

// ── Detail dialog ─────────────────────────────────────────────────────────────

@Composable
private fun EntryDetailDialog(entry: DebugLogEntry, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val title = when (entry) {
        is DebugLogEntry.WsSent      -> "→ ${entry.type.name}"
        is DebugLogEntry.WsReceived  -> "← ${entry.type.name}"
        is DebugLogEntry.WsConnect   -> "${entry.status.name} ${entry.host}:${entry.port}"
        is DebugLogEntry.Http        -> "${entry.method} ${entry.path}"
    }
    val body = when (entry) {
        is DebugLogEntry.WsSent     -> buildWsDetail("Sent", entry.bytes, entry.payload)
        is DebugLogEntry.WsReceived -> buildWsDetail("Received", entry.bytes, entry.payload, entry.durationMs)
        is DebugLogEntry.WsConnect  -> buildConnectDetail(entry)
        is DebugLogEntry.Http       -> buildHttpDetail(entry)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
        },
        text = {
            Box(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            IconButton(onClick = { clipboard.setText(AnnotatedString(body)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy to clipboard")
            }
        },
    )
}

// ── Detail builders ───────────────────────────────────────────────────────────

private fun buildWsDetail(
    direction: String,
    bytes: Int,
    payload: ByteArray?,
    durationMs: Long? = null,
): String = buildString {
    append("$direction  $bytes bytes")
    durationMs?.let { append("  (${it} ms)") }
    if (payload != null) {
        append("\n\n")
        append(payload.toHexDump())
    } else {
        append("\n\n(no payload captured)")
    }
}

private fun buildConnectDetail(entry: DebugLogEntry.WsConnect): String = buildString {
    append("Status: ${entry.status.name}\n")
    append("Host:   ${entry.host}\n")
    append("Port:   ${entry.port}")
    entry.detail?.let { append("\nError:  $it") }
}

private fun buildHttpDetail(entry: DebugLogEntry.Http): String = buildString {
    append("${entry.method} ${entry.path}\n")
    append("Host:     ${entry.host}\n")
    append("Status:   ${entry.statusCode}\n")
    append("Duration: ${entry.durationMs} ms")
    if (entry.body != null) {
        append("\n\n")
        append(entry.body.tryPrettyPrintJson())
    } else {
        append("\n\n(response body pending…)")
    }
}

// ── Formatters ────────────────────────────────────────────────────────────────

private fun ByteArray.toHexDump(): String {
    if (isEmpty()) return "(empty)"
    val sb = StringBuilder()
    for (i in indices step 16) {
        val end   = minOf(i + 16, size)
        val chunk = slice(i until end)
        val hex   = chunk.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val ascii = chunk.map { b ->
            val c = (b.toInt() and 0xFF).toChar()
            if (c.code in 32..126) c else '.'
        }.joinToString("")
        sb.append("%04X:  %-47s  |%-16s|\n".format(i, hex, ascii))
    }
    return sb.toString().trimEnd()
}

private fun String.tryPrettyPrintJson(): String {
    if (isBlank()) return "(empty body)"
    return try {
        val trimmed = trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return this
        // Manual indent — avoids pulling in a full JSON library just for pretty-print
        var indent = 0
        val result = StringBuilder()
        var inString = false
        var escape = false
        for (ch in trimmed) {
            when {
                escape           -> { result.append(ch); escape = false }
                ch == '\\'       -> { result.append(ch); escape = true }
                ch == '"'        -> { result.append(ch); inString = !inString }
                inString         -> result.append(ch)
                ch == '{' || ch == '[' -> {
                    result.append(ch)
                    indent++
                    result.append('\n')
                    result.append("  ".repeat(indent))
                }
                ch == '}' || ch == ']' -> {
                    indent--
                    result.append('\n')
                    result.append("  ".repeat(indent))
                    result.append(ch)
                }
                ch == ','        -> { result.append(ch); result.append('\n'); result.append("  ".repeat(indent)) }
                ch == ':'        -> result.append(": ")
                ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' -> { /* collapse whitespace */ }
                else             -> result.append(ch)
            }
        }
        result.toString()
    } catch (_: Exception) {
        this
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.toOffsetLabel(): String {
    val m  = this / 60_000
    val s  = (this % 60_000) / 1000
    val ms = this % 1000
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${ms.toString().padStart(3, '0')}"
}

private val wsColor       = Color(0xFF64B5F6)
private val wsRxColor     = Color(0xFF81C784)
private val httpColor     = Color(0xFFFFB74D)
private val durationColor = Color(0xFFFF8A65)
private val errorColor    = Color(0xFFEF5350)
