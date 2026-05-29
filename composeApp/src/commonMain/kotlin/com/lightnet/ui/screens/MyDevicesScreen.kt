package com.lightnet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.SavedDevice
import com.lightnet.ui.components.DeviceListItem
import com.lightnet.ui.components.DeviceStatus
import com.lightnet.ui.components.EmptyState

const val DEMO_DEVICE_HOST = "mock"
const val DEMO_DEVICE_NAME = "Demo Device"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    devices: List<SavedDevice>,
    onOpenDevice: (SavedDevice) -> Unit,
    onOpenDemoDevice: () -> Unit,
    onAddDevice: () -> Unit,
    onEditDevice: (SavedDevice) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                actions = {
                    IconButton(onClick = onAddDevice) {
                        Icon(Icons.Default.Add, contentDescription = "Add device")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (devices.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Box(Modifier.weight(1f)) {
                    EmptyState(
                        title = "No devices yet",
                        body = "Add a controller, or try the Demo device to explore.",
                        primaryActionLabel = "Add device",
                        onPrimaryAction = onAddDevice,
                    )
                }
                DemoDeviceCard(
                    onClick = onOpenDemoDevice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.name }) { device ->
                    DeviceListItem(
                        name = device.name,
                        subtitle = "${device.host}:${device.port}",
                        // No background connection per row — design 1.1 shows live dots but
                        // we'd need one socket per device for that. Surface live status only
                        // for the active device (in the Control chip). TODO: lightweight ping.
                        status = DeviceStatus.Unknown,
                        onClick = { onOpenDevice(device) },
                        trailing = {
                            IconButton(onClick = { onEditDevice(device) }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Edit device")
                            }
                        },
                    )
                }
                item {
                    DemoDeviceCard(onClick = onOpenDemoDevice)
                }
            }
        }
    }
}

@Composable
private fun DemoDeviceCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(DEMO_DEVICE_NAME, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Simulated controller — always available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Default,
                )
            }
        }
    }
}
