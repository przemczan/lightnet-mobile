package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.ui.components.DeviceStatus
import com.lightnet.ui.components.EmptyState
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.LoadingState
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.ReconnectingBanner
import com.lightnet.ui.components.StatusDot
import com.lightnet.ui.components.toDeviceStatus
import com.lightnet.ui.toColorRgb
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControllerScreen(
    device: LightnetDevice?,
    activeDevice: SavedDevice?,
    httpClient: LightnetHttpClient?,
    onOpenDeviceSwitcher: () -> Unit,
    onAddPalette: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    if (device == null || activeDevice == null) {
        Scaffold(
            topBar    = { TopAppBar(title = { Text("Control") }) },
            bottomBar = bottomBar,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title              = "No device connected",
                    body               = "Control and Library need a device. Pick one to get started.",
                    primaryActionLabel = "Choose a device",
                    onPrimaryAction    = onOpenDeviceSwitcher,
                )
            }
        }
        return
    }

    val scope = rememberCoroutineScope()

    val connectionState by device.connectionState.collectAsState()
    val snapshot        by device.snapshot.collectAsState()

    var wasConnected by remember(device) { mutableStateOf(false) }
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) wasConnected = true
    }

    val isReconnecting   = connectionState == ConnectionState.CONNECTING && wasConnected
    val isFirstLoading   = connectionState == ConnectionState.CONNECTING && !wasConnected
    val hasEmptyTopology = connectionState == ConnectionState.CONNECTED && snapshot?.panels?.isEmpty() == true

    // Appearance state loaded once on connect
    var brightness  by remember(device) { mutableStateOf(128f) }
    var palette     by remember(device) { mutableStateOf<String?>(null) }
    var paletteNames by remember(device) { mutableStateOf<List<String>>(emptyList()) }
    var baseColors  by remember(device) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(httpClient, connectionState) {
        if (connectionState == ConnectionState.CONNECTED && httpClient != null) {
            val app = httpClient.runCatching { getAppearance() }.getOrNull()
            if (app != null) {
                brightness = app.brightness.toFloat()
                palette    = app.palette
                baseColors = app.baseColors
            }
            paletteNames = httpClient.runCatching { getPaletteNames() }.getOrNull() ?: emptyList()
        }
    }

    var paintMode      by remember { mutableStateOf(PaintMode.Paint) }
    var paintColor     by remember { mutableStateOf(Color(0xFFCF5B3C)) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectionMode  by remember { mutableStateOf(false) }
    var selectedPanels by remember { mutableStateOf(emptySet<Int>()) }
    var showSettings   by remember { mutableStateOf(false) }

    if (showSettings) {
        DeviceSettingsScreen(
            savedDevice = activeDevice,
            device      = device,
            httpClient  = httpClient,
            onBack      = { showSettings = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    DeviceChip(
                        name    = activeDevice.name,
                        status  = connectionState.toDeviceStatus(),
                        onClick = onOpenDeviceSwitcher,
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
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
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isFirstLoading -> LoadingState(label = "Discovering panels…")

                    connectionState == ConnectionState.DISCONNECTED && snapshot == null -> EmptyState(
                        title              = "Disconnected",
                        body               = "Couldn't reach the controller.",
                        primaryActionLabel = "Retry",
                        onPrimaryAction    = { device.load() },
                    )

                    hasEmptyTopology -> EmptyState(
                        title              = "No panels discovered",
                        body               = "Controller responded with 0 panels.",
                        primaryActionLabel = "Retry",
                        onPrimaryAction    = { device.load() },
                    )

                    snapshot != null -> Box(
                        Modifier
                            .fillMaxSize()
                            .alpha(if (isReconnecting) 0.55f else 1f),
                    ) {
                        LightnetDeviceVisualizer(
                            panels              = snapshot!!.panels,
                            paintMode           = paintMode,
                            paintColor          = paintColor,
                            interactive         = !isReconnecting,
                            selectionMode       = selectionMode,
                            selectedPanels      = selectedPanels,
                            onSelectionChange   = { selectedPanels = it },
                            onEnterSelectionMode = { firstIdx ->
                                selectedPanels = setOf(firstIdx)
                                selectionMode  = true
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> LoadingState(label = "Connecting…")
                }
            }

            if (snapshot != null && !isFirstLoading) {
                val dockAlpha = if (isReconnecting) 0.45f else 1f
                if (selectionMode) {
                    SelectionDock(
                        selectedCount       = selectedPanels.size,
                        paintColor          = paintColor,
                        brightness          = brightness,
                        onBrightnessChange  = { brightness = it },
                        onBrightnessFinished = {
                            val b = brightness.toInt()
                            scope.launch { httpClient?.runCatching { setBrightness(b) } }
                        },
                        onColorClick = { showColorPicker = true },
                        onDismiss    = { selectionMode = false; selectedPanels = emptySet() },
                        modifier     = Modifier.alpha(dockAlpha),
                    )
                } else {
                    BrushDock(
                        brightness           = brightness,
                        onBrightnessChange   = { brightness = it },
                        onBrightnessFinished = {
                            val b = brightness.toInt()
                            scope.launch { httpClient?.runCatching { setBrightness(b) } }
                        },
                        palette              = palette,
                        paletteNames         = paletteNames,
                        onPaletteChange      = { name ->
                            palette = name
                            scope.launch { httpClient?.runCatching { setPaletteName(name) } }
                        },
                        onAddPalette         = onAddPalette,
                        paintColor           = paintColor,
                        onColorSwatchClick   = { showColorPicker = true },
                        paintMode            = paintMode,
                        onPaintModeChange    = { paintMode = it },
                        modifier             = Modifier.alpha(dockAlpha),
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            initial      = paintColor,
            httpClient   = httpClient,
            paletteNames = paletteNames,
            baseColors   = baseColors,
            onPick       = { color -> paintColor = color },
            onDismiss    = {
                showColorPicker = false
                if (selectionMode) {
                    val rgb = paintColor.toColorRgb()
                    snapshot?.panels?.let { panels ->
                        selectedPanels.forEach { idx ->
                            panels.getOrNull(idx)?.let { panel ->
                                panel.setColor(rgb)
                                panel.toggle(on = true)
                            }
                        }
                    }
                }
            },
        )
    }
}

// ── Brightness slider ─────────────────────────────────────────────────────────

/** Inset-icon slider that maps the 0–255 brightness to the 0–1 [Slider] range. */
@Composable
private fun BrightnessSlider(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Icon(
            Icons.Default.WbSunny,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value                 = brightness / 255f,
            onValueChange         = { onBrightnessChange(it * 255f) },
            onValueChangeFinished = onBrightnessFinished,
            modifier              = Modifier.weight(1f),
        )
    }
}

// ── Selection dock ────────────────────────────────────────────────────────────

@Composable
private fun SelectionDock(
    selectedCount: Int,
    paintColor: Color,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    onColorClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier       = modifier.fillMaxWidth(),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.spacedBy(10.dp),
            Alignment.CenterVertically,
        ) {
            Text(
                "$selectedCount panels",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier
                    .size(40.dp)
                    .background(paintColor, MaterialTheme.shapes.small)
                    .clickable { onColorClick() }
            )
            BrightnessSlider(
                brightness           = brightness,
                onBrightnessChange   = onBrightnessChange,
                onBrightnessFinished = onBrightnessFinished,
                modifier             = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Exit selection mode", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Brush dock ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrushDock(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    palette: String?,
    paletteNames: List<String>,
    onPaletteChange: (String) -> Unit,
    onAddPalette: () -> Unit,
    paintColor: Color,
    onColorSwatchClick: () -> Unit,
    paintMode: PaintMode,
    onPaintModeChange: (PaintMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier       = modifier.fillMaxWidth(),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Color swatch + scrollable palette list
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(10.dp),
                Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(paintColor, MaterialTheme.shapes.small)
                        .clickable { onColorSwatchClick() }
                )
                if (paletteNames.isEmpty()) {
                    TextButton(
                        onClick  = onAddPalette,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Add palette →", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    LazyRow(
                        modifier              = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(paletteNames) { name ->
                            FilterChip(
                                selected = name == palette,
                                onClick  = { onPaletteChange(name) },
                                label    = { Text(name, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }

            // Brightness slider with icon
            BrightnessSlider(
                brightness           = brightness,
                onBrightnessChange   = onBrightnessChange,
                onBrightnessFinished = onBrightnessFinished,
                modifier             = Modifier.fillMaxWidth(),
            )

            // Paint mode selector
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaintMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = paintMode == mode,
                        onClick  = { onPaintModeChange(mode) },
                        shape    = SegmentedButtonDefaults.itemShape(i, PaintMode.entries.size),
                        label    = {
                            Text(
                                when (mode) {
                                    PaintMode.Paint -> "Paint"
                                    PaintMode.Erase -> "Erase"
                                    PaintMode.Stamp -> "Stamp"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}

// ── Device chip ───────────────────────────────────────────────────────────────

@Composable
private fun DeviceChip(
    name: String,
    status: DeviceStatus,
    onClick: () -> Unit,
) {
    Surface(
        shape    = MaterialTheme.shapes.large,
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(status, size = 8.dp)
            Text(name, style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}
