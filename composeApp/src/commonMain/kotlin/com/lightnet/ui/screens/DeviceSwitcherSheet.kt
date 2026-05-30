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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.displayAddress
import com.lightnet.ui.components.DeviceListItem
import com.lightnet.ui.components.DeviceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSwitcherSheet(
    devices: List<SavedDevice>,
    activeKey: String?,
    onSelect: (SavedDevice) -> Unit,
    onSelectDemo: () -> Unit,
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
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            // Live status per row would require a socket per device — same constraint as
            // MyDevicesScreen. We surface live status only via the AppBar chip in Control.
            devices.forEach { device ->
                DeviceListItem(
                    name = device.name,
                    subtitle = device.displayAddress(),
                    status = DeviceStatus.Unknown,
                    selected = activeKey == device.name,
                    onClick = { onSelect(device); onDismiss() },
                )
            }
            DeviceListItem(
                name = DEMO_DEVICE_NAME,
                subtitle = "Simulated controller",
                status = DeviceStatus.Unknown,
                selected = activeKey == DEMO_DEVICE_HOST,
                onClick = { onSelectDemo(); onDismiss() },
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            TextButton(onClick = { onManageDevices(); onDismiss() }) {
                Text("Manage devices ›")
            }
        }
    }
}
