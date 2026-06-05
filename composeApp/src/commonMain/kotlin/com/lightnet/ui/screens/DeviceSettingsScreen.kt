package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.PaletteJson
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
    httpClient: LightnetHttpClient?,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit = {},
) {
    val devicePrefs = remember(savedDevice.name) { AppPreferences.forDevice(savedDevice.name) }

    var showDeviceInfo   by remember { mutableStateOf(false) }
    var showAppearance   by remember { mutableStateOf(false) }
    var showPalettes     by remember { mutableStateOf(false) }
    var showScenes       by remember { mutableStateOf(false) }

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

    if (showPalettes) {
        PalettesSettingsScreen(
            httpClient = httpClient,
            onBack     = { showPalettes = false },
        )
        return
    }

    if (showScenes) {
        ScenesSettingsScreen(
            device     = device,
            httpClient = httpClient,
            onBack     = { showScenes = false },
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
                            icon    = Icons.Default.Palette,
                            label   = "Palettes",
                            onClick = { showPalettes = true },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SettingsMenuItem(
                            icon    = Icons.Default.Movie,
                            label   = "Scenes",
                            onClick = { showScenes = true },
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
                        val lastIp     = savedDevice.lastIP ?: "—"
                        ListItem(
                            headlineContent = { Text("Hostname") },
                            trailingContent = { Text(hostname) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Last IP") },
                            trailingContent = { Text(lastIp) },
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

// ── Palettes sub-screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PalettesSettingsScreen(
    httpClient: LightnetHttpClient?,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)

    val scope = rememberCoroutineScope()

    var palettes       by remember { mutableStateOf<List<PaletteJson>>(emptyList()) }
    var isLoading      by remember { mutableStateOf(false) }
    var deleteTarget   by remember { mutableStateOf<PaletteJson?>(null) }
    var editingPalette by remember { mutableStateOf<PaletteJson?>(null) }
    var showEditor     by remember { mutableStateOf(false) }

    suspend fun reload() {
        if (httpClient == null) return
        isLoading = true
        palettes  = httpClient.runCatching { getPalettes().values.toList() }.getOrNull() ?: emptyList()
        isLoading = false
    }

    LaunchedEffect(httpClient) { reload() }

    if (showEditor) {
        PaletteEditorScreen(
            initial    = editingPalette,
            httpClient = httpClient,
            onBack     = {
                showEditor     = false
                editingPalette = null
                scope.launch { reload() }
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Palettes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingPalette = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "New palette")
            }
        },
    ) { padding ->
        when {
            httpClient == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Connect a device to manage palettes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            isLoading && palettes.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            palettes.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No palettes found on device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding      = PaddingValues(
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(palettes, key = { it.name }) { palette ->
                    PaletteSettingsItem(
                        palette  = palette,
                        onEdit   = { editingPalette = palette; showEditor = true },
                        onDelete = { deleteTarget = palette },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text("Delete palette") },
            text             = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        httpClient?.runCatching { deletePalette(target.name) }
                        reload()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PaletteSettingsItem(
    palette: PaletteJson,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val gradientStops = remember(palette.stops) {
        palette.stops.sortedBy { it.position }.map { stop ->
            (stop.position / 255f) to (parseHexColor(stop.color) ?: Color.White)
        }.toTypedArray()
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (gradientStops.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Brush.horizontalGradient(colorStops = gradientStops))
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(palette.name, style = MaterialTheme.typography.labelMedium)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text    = { Text("Delete") },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        }
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
