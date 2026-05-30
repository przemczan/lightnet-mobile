package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.DiscoveredDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.ServiceDiscovery
import com.lightnet.ui.components.SectionHeader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.launch

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Testing : TestConnectionState
    data class Success(val message: String) : TestConnectionState
    data class Failure(val message: String) : TestConnectionState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceSheet(
    serviceDiscovery: ServiceDiscovery,
    httpClient: HttpClient,
    existingNames: Set<String>,
    onAdd: (SavedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Tie discovery lifecycle to sheet visibility.
    DisposableEffect(serviceDiscovery) {
        serviceDiscovery.start()
        onDispose { serviceDiscovery.stop() }
    }
    val discovered by serviceDiscovery.devices.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var testState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }

    fun isValid() = name.isNotBlank() && host.isNotBlank() && port.toIntOrNull() != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add device", style = MaterialTheme.typography.titleLarge)

            // ── Discover section ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("Discovered")
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                if (discovered.isEmpty()) {
                    Text(
                        "Searching the local network…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(discovered, key = { it.name }) { d ->
                            DiscoveredDeviceRow(
                                device       = d,
                                alreadySaved = d.name in existingNames,
                                onAdd        = {
                                    onAdd(SavedDevice(
                                        name     = d.name,
                                        host     = "",
                                        port     = d.port,
                                        hostName = d.hostName,
                                        lastIP   = d.host,
                                    ))
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Manual section ────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Add manually")
                TextField(
                    value = name,
                    onValueChange = { name = it; testState = TestConnectionState.Idle },
                    label = { Text("Friendly name") },
                    placeholder = { Text("Studio panels") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = host,
                        onValueChange = { host = it; testState = TestConnectionState.Idle },
                        label = { Text("Override IP (optional)") },
                        placeholder = { Text("192.168.1.40") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    TextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5); testState = TestConnectionState.Idle },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                TestConnectionRow(
                    state   = testState,
                    enabled = isValid() && testState !is TestConnectionState.Testing,
                    onTest  = {
                        val h = host.trim()
                        val p = port.toIntOrNull() ?: return@TestConnectionRow
                        coroutineScope.launch {
                            testState = TestConnectionState.Testing
                            testState = testConnection(httpClient, h, p)
                        }
                    },
                )

                Button(
                    onClick = {
                        onAdd(SavedDevice(
                            name   = name.trim(),
                            host   = host.trim(),
                            port   = port.toIntOrNull() ?: 80,
                        ))
                        onDismiss()
                    },
                    enabled = isValid(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

/** Probes `GET http://host:port/api/appearance`; 2xx → Success, anything else → Failure. */
private suspend fun testConnection(
    httpClient: HttpClient,
    host: String,
    port: Int,
): TestConnectionState = try {
    val response: HttpResponse = httpClient.get("http://$host:$port/api/appearance")
    if (response.status.value in 200..299)
        TestConnectionState.Success("Reached $host:$port")
    else
        TestConnectionState.Failure("HTTP ${response.status.value}")
} catch (t: Throwable) {
    TestConnectionState.Failure(t.message ?: "Unreachable")
}

@Composable
private fun DiscoveredDeviceRow(
    device: DiscoveredDevice,
    alreadySaved: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                device.hostName ?: device.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            if (device.hostName != null) {
                Text(
                    device.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (alreadySaved) {
            Text(
                "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@Composable
private fun TestConnectionRow(
    state: TestConnectionState,
    enabled: Boolean,
    onTest: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onTest, enabled = enabled) { Text("Test connection") }
        Box(Modifier.weight(1f)) {
            when (state) {
                TestConnectionState.Idle -> {}
                TestConnectionState.Testing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Testing…", style = MaterialTheme.typography.bodySmall)
                }
                is TestConnectionState.Success -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
                is TestConnectionState.Failure -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
