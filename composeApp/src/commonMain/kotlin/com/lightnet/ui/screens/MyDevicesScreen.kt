package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.displayAddress
import com.lightnet.ui.components.DeviceListItem
import com.lightnet.ui.components.DeviceStatus
import com.lightnet.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    devices: List<SavedDevice>,
    onOpenDevice: (SavedDevice) -> Unit,
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
            EmptyState(
                modifier           = Modifier.fillMaxSize().padding(padding),
                title              = "No devices yet",
                body               = "Add a controller to get started.",
                primaryActionLabel = "Add device",
                onPrimaryAction    = onAddDevice,
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical   = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.name }) { device ->
                    DeviceListItem(
                        name     = device.name,
                        subtitle = device.displayAddress(),
                        status   = DeviceStatus.Unknown,
                        onClick  = { onOpenDevice(device) },
                        trailing = {
                            IconButton(onClick = { onEditDevice(device) }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Edit device")
                            }
                        },
                    )
                }
            }
        }
    }
}
