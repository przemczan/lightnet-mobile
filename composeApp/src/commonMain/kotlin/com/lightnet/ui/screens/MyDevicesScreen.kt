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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.SavedDevice
import com.lightnet.ui.components.EmptyState
import lightnet.composeapp.generated.resources.Res
import lightnet.composeapp.generated.resources.logo_dark
import lightnet.composeapp.generated.resources.logo_light
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    devices: List<SavedDevice>,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(devices, key = { it.id }) { device ->
                            HomeDeviceCard(
                                device      = device,
                                onClick     = { onOpenDevice(device) },
                                onEditClick = { onEditDevice(device) },
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

@Composable
private fun HomeDeviceCard(
    device: SavedDevice,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    Card(
        onClick  = onClick,
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
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Edit device")
            }
        }
    }
}
