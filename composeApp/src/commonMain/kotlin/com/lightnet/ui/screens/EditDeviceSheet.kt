package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lightnet.discovery.SavedDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceSheet(
    device: SavedDevice,
    onSave: (original: SavedDevice, updated: SavedDevice) -> Unit,
    onDelete: (SavedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(device.name) }
    var host by remember { mutableStateOf(device.host) }
    var port by remember { mutableStateOf(device.port.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun isValid() = name.isNotBlank() && port.toIntOrNull() != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit device", style = MaterialTheme.typography.titleLarge)

            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Friendly name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // mDNS hostname — informational only, not editable
            if (device.hostName != null) {
                Text(
                    "mDNS: ${device.hostName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Override IP (optional)") },
                    placeholder = { Text("192.168.1.40") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                TextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = {
                        onSave(
                            device,
                            // Preserve hostName and lastIP — user cannot change them.
                            device.copy(
                                name = name.trim(),
                                host = host.trim(),
                                port = port.toIntOrNull() ?: device.port,
                            ),
                        )
                        onDismiss()
                    },
                    enabled = isValid(),
                ) {
                    Text("Save changes")
                }
            }

            HorizontalDivider()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete device")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title   = { Text("Delete \"${device.name}\"?") },
            text    = { Text("This removes the device from this phone. The controller itself is unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(device)
                        onDismiss()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
