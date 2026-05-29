package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.ui.components.DeviceStatus
import com.lightnet.ui.components.EmptyState
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.LoadingState
import com.lightnet.ui.components.ReconnectingBanner
import com.lightnet.ui.components.StatusDot
import com.lightnet.ui.components.toDeviceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControllerScreen(
    device: LightnetDevice?,
    activeDeviceName: String?,
    onOpenDeviceSwitcher: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    if (device == null || activeDeviceName == null) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Control") }) },
            bottomBar = bottomBar,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "No device connected",
                    body = "Control and Library need a device. Pick one to get started.",
                    primaryActionLabel = "Choose a device",
                    onPrimaryAction = onOpenDeviceSwitcher,
                )
            }
        }
        return
    }

    val connectionState by device.connectionState.collectAsState()
    val snapshot by device.snapshot.collectAsState()

    // Distinguish first-connect (Loading) from a reconnect-after-drop (Reconnecting).
    var wasConnected by remember(device) { mutableStateOf(false) }
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) wasConnected = true
    }

    val isReconnecting = connectionState == ConnectionState.CONNECTING && wasConnected
    val isFirstLoading = connectionState == ConnectionState.CONNECTING && !wasConnected
    val hasEmptyTopology = connectionState == ConnectionState.CONNECTED && snapshot?.panels?.isEmpty() == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    DeviceChip(
                        name = activeDeviceName,
                        status = connectionState.toDeviceStatus(),
                        onClick = onOpenDeviceSwitcher,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Device settings")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isReconnecting) ReconnectingBanner()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isFirstLoading -> LoadingState(label = "Discovering panels…")

                    connectionState == ConnectionState.DISCONNECTED && snapshot == null -> EmptyState(
                        title = "Disconnected",
                        body  = "Couldn't reach the controller.",
                        primaryActionLabel = "Retry",
                        onPrimaryAction = { device.load() },
                    )

                    hasEmptyTopology -> EmptyState(
                        title = "No panels discovered",
                        body  = "Controller responded with 0 panels.",
                        primaryActionLabel = "Retry",
                        onPrimaryAction = { device.load() },
                    )

                    snapshot != null -> Box(
                        Modifier
                            .fillMaxSize()
                            .alpha(if (isReconnecting) 0.55f else 1f),
                    ) {
                        LightnetDeviceVisualizer(
                            panels = snapshot!!.panels,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> LoadingState(label = "Connecting…")
                }
            }
        }
    }
}

@Composable
private fun DeviceChip(
    name: String,
    status: DeviceStatus,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(status, size = 8.dp)
            Text(name, style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}
