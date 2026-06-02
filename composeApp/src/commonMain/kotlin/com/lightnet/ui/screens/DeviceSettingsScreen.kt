package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.effectiveHost
import com.lightnet.settings.AppPreferences
import com.lightnet.settings.DevicePreferences
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.colorToHex
import com.lightnet.ui.parseHexColor
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
    val devicePrefs = remember(savedDevice.name) { AppPreferences.forDevice(savedDevice.name) }

    var showDeviceInfo   by remember { mutableStateOf(false) }
    var showAppearance   by remember { mutableStateOf(false) }

    if (showDeviceInfo) {
        DeviceInfoScreen(
            savedDevice = savedDevice,
            device      = device,
            onBack      = { showDeviceInfo = false },
        )
        return
    }

    if (showAppearance) {
        AppearanceSettingsScreen(
            devicePrefs = devicePrefs,
            onBack      = { showAppearance = false },
        )
        return
    }

    BackHandlerCompat(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        SettingsMenuItem(
                            icon    = Icons.Default.Router,
                            label   = "Device",
                            onClick = { showDeviceInfo = true },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SettingsMenuItem(
                            icon    = Icons.Default.Tune,
                            label   = "Appearance",
                            onClick = { showAppearance = true },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SettingsMenuItem(
                            icon    = Icons.Default.BugReport,
                            label   = "Debug console",
                            onClick = onOpenDebug,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        leadingContent  = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier        = Modifier.clickable(onClick = onClick),
    )
}

// ── Device info sub-screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceInfoScreen(
    savedDevice: SavedDevice,
    device: LightnetDevice?,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)

    val scope = rememberCoroutineScope()
    val snapshot by remember(device) {
        device?.snapshot ?: MutableStateFlow(null)
    }.collectAsState()

    var powerStateOnBoot  by remember { mutableStateOf<Int?>(null) }
    var powerMenuExpanded by remember { mutableStateOf(false) }
    val powerOptions = listOf("Always on", "Always off", "Restore last")

    LaunchedEffect(device) {
        val config = device?.getConfiguration() ?: return@LaunchedEffect
        powerStateOnBoot = config.powerStateOnBoot
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device") },
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
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start  = 16.dp,
                end    = 16.dp,
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
                            expanded         = powerMenuExpanded,
                            onExpandedChange = { powerMenuExpanded = it },
                        ) {
                            TextField(
                                value         = powerStateOnBoot?.let { powerOptions.getOrNull(it) } ?: "",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Power on boot") },
                                trailingIcon  = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = powerMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded         = powerMenuExpanded,
                                onDismissRequest = { powerMenuExpanded = false },
                            ) {
                                powerOptions.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = {
                                            powerStateOnBoot  = index
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
                        val hostname   = savedDevice.hostName
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
        }
    }
}

// ── Appearance settings sub-screen ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsScreen(devicePrefs: DevicePreferences, onBack: () -> Unit) {
    BackHandlerCompat(onBack = onBack)

    val bgEnabled  by devicePrefs.visualizerBgColorEnabled.collectAsState()
    val bgColorHex by devicePrefs.visualizerBgColor.collectAsState()
    val bgColor    = bgColorHex?.let { parseHexColor(it) } ?: Color.Black

    var showColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
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
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start  = 16.dp,
                end    = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsSectionTitle("VISUALIZER")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Custom background color") },
                            trailingContent = {
                                Switch(
                                    checked         = bgEnabled,
                                    onCheckedChange = { devicePrefs.setVisualizerBgEnabled(it) },
                                )
                            },
                        )
                        if (bgEnabled) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            ListItem(
                                headlineContent = { Text("Background color") },
                                trailingContent = {
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(bgColor)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    )
                                },
                                modifier = Modifier.clickable { showColorPicker = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            initial        = bgColor,
            showBaseColors = false,
            onPick         = { color -> devicePrefs.setVisualizerBgColor(colorToHex(color)) },
            onDismiss      = { showColorPicker = false },
        )
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
internal fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
