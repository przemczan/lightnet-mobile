package com.lightnet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.DiscoveredDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.toDiscovered

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    devices: List<SavedDevice>,
    onDelete: (name: String) -> Unit,
    onOpenDevice: (DiscoveredDevice) -> Unit,
    onOpenDiscovery: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<SavedDevice?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Devices") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenDiscovery) {
                Icon(Icons.Default.Add, contentDescription = "Add device")
            }
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No saved devices.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenDiscovery) { Text("Search for devices") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(devices, key = { it.name }) { device ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDevice(device.toDiscovered()) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { deleteTarget = device }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete device") },
            text = { Text("Remove \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = { onDelete(target.name); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}
