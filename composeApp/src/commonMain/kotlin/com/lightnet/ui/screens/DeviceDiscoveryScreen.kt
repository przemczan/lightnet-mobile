package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.DiscoveredDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.ServiceDiscovery

private const val MOCK_HOST = "mock"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(
    serviceDiscovery: ServiceDiscovery,
    savedDevices: List<SavedDevice>,
    onAdd: (DiscoveredDevice) -> Unit,
    onOpenDevice: (DiscoveredDevice) -> Unit,
    onNavigateBack: () -> Unit,
) {
    DisposableEffect(serviceDiscovery) {
        serviceDiscovery.start()
        onDispose { serviceDiscovery.stop() }
    }

    val discovered by serviceDiscovery.devices.collectAsState(initial = emptyList())
    val savedNames = savedDevices.map { it.name }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (discovered.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                "Searching for devices…",
                                Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            items(discovered, key = { it.name }) { device ->
                DiscoveredDeviceRow(
                    device = device,
                    alreadySaved = device.name in savedNames,
                    onAdd = { onAdd(device) },
                    onOpen = { onOpenDevice(device) },
                )
                HorizontalDivider()
            }

            // Demo device — always at the bottom
            item {
                val demo = DiscoveredDevice(name = "Demo Device", host = MOCK_HOST, port = 0)
                DiscoveredDeviceRow(
                    device = demo,
                    alreadySaved = MOCK_HOST in savedNames,
                    onAdd = { onAdd(demo) },
                    onOpen = { onOpenDevice(demo) },
                    subtitle = "Simulated device — no hardware needed",
                )
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: DiscoveredDevice,
    alreadySaved: Boolean,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
    subtitle: String? = if (device.host != MOCK_HOST) "${device.host}:${device.port}" else null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            }
        }
        if (alreadySaved) {
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.Check, contentDescription = "Open")
            }
        } else {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }
}
