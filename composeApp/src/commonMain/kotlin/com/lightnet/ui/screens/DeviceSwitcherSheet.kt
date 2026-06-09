package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.displayAddress
import com.lightnet.ui.components.DeviceListItem
import com.lightnet.ui.components.deviceStatus
import kotlinx.coroutines.flow.MutableStateFlow

private val idleConnectionState = MutableStateFlow(ConnectionState.IDLE)
private val unknownIsOnline     = MutableStateFlow<Boolean?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSwitcherSheet(
    devices: List<SavedDevice>,
    devicePool: Map<String, LightnetDevice>,
    activeKey: String?,
    onSelect: (SavedDevice) -> Unit,
    onManageDevices: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Switch device",
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            devices.forEach { device ->
                SwitcherDeviceItem(
                    device     = device,
                    liveDevice = devicePool[device.id],
                    selected   = activeKey == device.name,
                    onClick    = { onSelect(device); onDismiss() },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            TextButton(onClick = { onManageDevices(); onDismiss() }) {
                Text("Manage devices ›")
            }
        }
    }
}

@Composable
private fun SwitcherDeviceItem(
    device: SavedDevice,
    liveDevice: LightnetDevice?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val connection by (liveDevice?.connectionState ?: idleConnectionState).collectAsState()
    val online     by (liveDevice?.isOnline        ?: unknownIsOnline).collectAsState()
    DeviceListItem(
        name     = device.name,
        subtitle = device.displayAddress(),
        status   = deviceStatus(connection, online),
        selected = selected,
        onClick  = onClick,
    )
}
