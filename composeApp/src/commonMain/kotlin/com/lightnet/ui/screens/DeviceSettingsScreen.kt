package com.lightnet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.effectiveHost
import com.lightnet.ui.BackHandlerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    savedDevice: SavedDevice,
    device: LightnetDevice?,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit = {},
) {
    BackHandlerCompat(onBack = onBack)

    val scope = rememberCoroutineScope()
    val snapshot by remember(device) {
        device?.snapshot ?: MutableStateFlow(null)
    }.collectAsState()

    var powerStateOnBoot by remember { mutableStateOf<Int?>(null) }
    var powerMenuExpanded by remember { mutableStateOf(false) }
    val powerOptions = listOf("Always on", "Always off", "Restore last")

    LaunchedEffect(device) {
        val config = device?.getConfiguration() ?: return@LaunchedEffect
        powerStateOnBoot = config.powerStateOnBoot
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsSectionTitle("CONFIGURATION")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = powerMenuExpanded,
                            onExpandedChange = { powerMenuExpanded = it },
                        ) {
                            TextField(
                                value = powerStateOnBoot?.let { powerOptions.getOrNull(it) } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Power on boot") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = powerMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = powerMenuExpanded,
                                onDismissRequest = { powerMenuExpanded = false },
                            ) {
                                powerOptions.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            powerStateOnBoot = index
                                            powerMenuExpanded = false
                                            scope.launch {
                                                device?.setConfiguration(ConfigurationRequest(powerStateOnBoot = index))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionTitle("FIRMWARE")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Update panel firmware") },
                        trailingContent = {
                            Text(
                                "Coming soon",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            item {
                SettingsSectionTitle("ABOUT")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        val hostname = savedDevice.hostName
                            ?: savedDevice.effectiveHost.ifEmpty { "—" }
                        val panelCount = snapshot?.panels?.size?.toString() ?: "—"
                        ListItem(
                            headlineContent = { Text("Hostname") },
                            trailingContent = { Text(hostname) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Firmware") },
                            trailingContent = { Text("—") },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Panels") },
                            trailingContent = { Text(panelCount) },
                        )
                    }
                }
            }

            item {
                SettingsSectionTitle("DEVELOPER")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Debug console") },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOpenDebug),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
