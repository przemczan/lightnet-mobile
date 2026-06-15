package com.lightnet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.ui.components.DeviceStatus
import com.lightnet.ui.components.EmptyState
import com.lightnet.ui.components.deviceStatus
import com.lightnet.ui.components.groupedListItemShape
import com.lightnet.ui.components.StatusDot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lightnet.composeapp.generated.resources.Res
import lightnet.composeapp.generated.resources.logo_dark
import lightnet.composeapp.generated.resources.logo_light
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    devices: List<SavedDevice>,
    devicePool: Map<String, LightnetDevice>,
    onOpenDevice: (SavedDevice) -> Unit,
    onAddDevice: () -> Unit,
    onEditDevice: (SavedDevice) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isDark = isSystemInDarkTheme()
            Image(
                painter           = painterResource(if (isDark) Res.drawable.logo_dark else Res.drawable.logo_light),
                contentDescription = "Lightnet",
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp, bottom = 24.dp),
            )

            if (devices.isEmpty()) {
                EmptyState(
                    modifier           = Modifier.fillMaxSize(),
                    title              = "No devices yet",
                    body               = "Add a controller to get started.",
                    primaryActionLabel = "Add device",
                    onPrimaryAction    = onAddDevice,
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(devices, key = { _, it -> it.id }) { index, device ->
                            HomeDeviceCard(
                                device          = device,
                                connectionState = devicePool[device.id]?.connectionState,
                                isOnline        = devicePool[device.id]?.isOnline,
                                shape           = groupedListItemShape(index, devices.size),
                                onClick         = { onOpenDevice(device) },
                                onEditClick     = { onEditDevice(device) },
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick  = onAddDevice,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add device")
                    }
                }
            }
        }
    }
}

private val idleConnectionState = MutableStateFlow(ConnectionState.IDLE)
private val unknownIsOnline = MutableStateFlow<Boolean?>(null)

@Composable
private fun HomeDeviceCard(
    device: SavedDevice,
    connectionState: StateFlow<ConnectionState>?,
    isOnline: StateFlow<Boolean?>?,
    shape: Shape,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    val connection by (connectionState ?: idleConnectionState).collectAsState()
    val online by (isOnline ?: unknownIsOnline).collectAsState()
    val status = deviceStatus(connection, online)

    Card(
        onClick  = onClick,
        shape    = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = device.panelCount?.let { "$it panels" } ?: "— panels"
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusDot(status, modifier = Modifier.padding(end = 12.dp))
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Edit device")
            }
        }
    }
}
