package com.lightnet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.debug.DebugLog
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.colorToHex
import com.lightnet.ui.parseHexColor
import lightnet.composeapp.generated.resources.Res
import lightnet.composeapp.generated.resources.logo_mark
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.EmptyState
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.LoadingState
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.ReconnectingBanner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControllerScreen(
    device: LightnetDevice?,
    activeDevice: SavedDevice,
    devices: List<SavedDevice>,
    onBack: () -> Unit,
    onSwitchDevice: (SavedDevice) -> Unit,
    onManageDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null guard before any composable calls — device is null for a brief window
    // while App.kt creates it; show loading and keep back handler active.
    if (device == null) {
        BackHandlerCompat(onBack = onBack)
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingState(label = "Connecting…")
        }
        return
    }

    BackHandlerCompat(onBack = onBack)

    val scope = rememberCoroutineScope()

    val connectionState by device.connectionState.collectAsState()
    val snapshot        by device.snapshot.collectAsState()

    // Refresh panel states on screen entry when already connected.
    LaunchedEffect(device) { device?.refreshPanelStates() }

    var wasConnected    by remember(device) { mutableStateOf(device.connectionState.value == ConnectionState.CONNECTED) }
    // Pre-seeded to true when the pool already has a snapshot so switching to a live
    // device never shows a loading screen while appearance/power fetch in the background.
    var deviceInfoReady by remember(device) { mutableStateOf(device.snapshot.value != null) }
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) wasConnected = true
    }

    val isReconnecting   = connectionState == ConnectionState.CONNECTING && wasConnected
    val hasEmptyTopology = connectionState == ConnectionState.CONNECTED && snapshot?.panels?.isEmpty() == true
    // Show a loader on first entry until panels + appearance/power are all loaded.
    // deviceInfoReady persists across reconnects so re-entry never shows the loader again.
    val isInitialLoading = !isReconnecting &&
        connectionState != ConnectionState.DISCONNECTED &&
        (!deviceInfoReady || snapshot == null)

    val debugMode by DebugLog.debugMode.collectAsState()

    val devicePrefs   = remember(activeDevice.name) { AppPreferences.forDevice(activeDevice.name) }
    val vizBgEnabled  by devicePrefs.visualizerBgColorEnabled.collectAsState()
    val vizBgColorHex by devicePrefs.visualizerBgColor.collectAsState()
    val vizBgColor    = if (vizBgEnabled && vizBgColorHex != null)
        parseHexColor(vizBgColorHex!!) else null

    var brightness          by remember(device) { mutableStateOf(device.cachedAppearance?.brightness?.toFloat() ?: 128f) }
    var palette             by remember(device) { mutableStateOf(device.cachedAppearance?.palette) }
    var paletteNames        by remember(device) { mutableStateOf<List<String>>(emptyList()) }
    var paletteNamesLoading by remember(device) { mutableStateOf(false) }
    var baseColors          by remember(device) { mutableStateOf(device.cachedAppearance?.baseColors ?: emptyList()) }
    var paintColor          by remember { mutableStateOf<Color?>(null) }
    var showColorSheet      by remember { mutableStateOf(false) }
    var showPaletteSheet    by remember { mutableStateOf(false) }
    var showBrightnessSheet by remember { mutableStateOf(false) }
    var allPanelsOn         by remember(device) { mutableStateOf(device.cachedPowerState ?: false) }

    LaunchedEffect(device, connectionState) {
        if (connectionState == ConnectionState.CONNECTED && device != null) {
            paletteNamesLoading = true
            val app = device.loadAppearance()
            if (app != null) {
                brightness = app.brightness.toFloat()
                palette    = app.palette
                baseColors = app.baseColors
            }
            paletteNames        = device.getPalettes()
            paletteNamesLoading = false
            val power = device.getPowerState()
            if (power != null) allPanelsOn = power
            deviceInfoReady = true
        }
    }

    var showSettings      by remember { mutableStateOf(false) }
    var showDebug         by remember { mutableStateOf(false) }
    var showSwitcherSheet by remember { mutableStateOf(false) }

    if (showDebug) {
        DebugScreen(onBack = { showDebug = false })
        return
    }

    if (showSettings) {
        DeviceSettingsScreen(
            savedDevice = activeDevice,
            device      = device,
            onBack      = { showSettings = false },
            onOpenDebug = { showDebug = true },
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painter           = painterResource(Res.drawable.logo_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(start = 8.dp),
                    )
                },
                title   = { Text(activeDevice.name) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Device settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isReconnecting) ReconnectingBanner()

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(vizBgColor ?: MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isInitialLoading -> LoadingState(label = "Loading…")

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

                    snapshot != null -> Box(Modifier.fillMaxSize()) {
                        LightnetDeviceVisualizer(
                            panels       = snapshot!!.panels,
                            powerOn      = allPanelsOn,
                            paintMode    = PaintMode.Paint,
                            paintColor   = paintColor ?: Color(0xFFCF5B3C),
                            interactive  = !isReconnecting,
                            showPanelIds = debugMode,
                            modifier     = Modifier.fillMaxSize(),
                        )
                    }

                    else -> LoadingState(label = "Connecting…")
                }

                // Bottom centered toolbar — visible once panels are ready
                if (snapshot != null && !isInitialLoading) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color          = MaterialTheme.colorScheme.surface,
                            shape          = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 6.dp,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { showColorSheet = true }) {
                                    Icon(Icons.Default.Brush, contentDescription = "Pick color")
                                }
                                IconButton(onClick = { showPaletteSheet = true }) {
                                    Icon(Icons.Default.Gradient, contentDescription = "Choose palette")
                                }
                                IconButton(onClick = { showBrightnessSheet = true }) {
                                    Icon(Icons.Default.WbSunny, contentDescription = "Brightness")
                                }
                                FilledIconToggleButton(
                                    checked         = allPanelsOn,
                                    onCheckedChange = { on ->
                                        allPanelsOn = on
                                        scope.launch { device.setPowerState(on) }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.PowerSettingsNew,
                                        contentDescription = if (allPanelsOn) "Turn off" else "Turn on",
                                    )
                                }
                            }
                        }

                        Surface(
                            color          = MaterialTheme.colorScheme.surface,
                            shape          = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 6.dp,
                        ) {
                            IconButton(onClick = { showSwitcherSheet = true }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Switch device")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showColorSheet) {
        ColorPickerSheet(
            initial    = paintColor,
            baseColors = baseColors,
            onPick     = { paintColor = it },
            onUpdateBaseColor = { i, color ->
                baseColors = baseColors.toMutableList().also { list ->
                    while (list.size <= i) list.add("#FFFFFF")
                    list[i] = colorToHex(color)
                }
                scope.launch { device.setAppearance(AppearanceRequest(baseColors = baseColors)) }
            },
            onDismiss  = { showColorSheet = false },
        )
    }

    if (showPaletteSheet) {
        PaletteSheet(
            paletteNames   = paletteNames,
            isLoading      = paletteNamesLoading,
            currentPalette = palette,
            onSelect       = { name ->
                palette = name
                device.setAppearance(AppearanceRequest(palette = name))
            },
            onDismiss      = { showPaletteSheet = false },
        )
    }

    if (showBrightnessSheet) {
        BrightnessSheet(
            initialBrightness = brightness,
            onSave            = { newBrightness ->
                brightness = newBrightness
                device.setAppearance(AppearanceRequest(brightness = newBrightness.toInt()))
            },
            onDismiss         = { showBrightnessSheet = false },
        )
    }

    if (showSwitcherSheet) {
        DeviceSwitcherSheet(
            devices         = devices,
            activeKey       = activeDevice.name,
            onSelect        = { onSwitchDevice(it) },
            onManageDevices = onManageDevices,
            onDismiss       = { showSwitcherSheet = false },
        )
    }
}

// ── Brightness sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessSheet(
    initialBrightness: Float,
    onSave: suspend (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var brightness by remember { mutableFloatStateOf(initialBrightness) }
    var isSaving   by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Brightness", style = MaterialTheme.typography.titleMedium)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = null)
                Slider(
                    value         = brightness / 255f,
                    onValueChange = { brightness = it * 255f },
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    "${(brightness / 255f * 100).roundToInt()}%",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            onSave(brightness)
                            isSaving = false
                            onDismiss()
                        }
                    },
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// ── Palette sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteSheet(
    paletteNames: List<String>,
    isLoading: Boolean,
    currentPalette: String?,
    onSelect: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var applyingPalette by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "Palette",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                isLoading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                paletteNames.isEmpty() -> Text(
                    "No palettes available on this device.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> paletteNames.forEach { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = applyingPalette == null) {
                                scope.launch {
                                    applyingPalette = name
                                    onSelect(name)
                                    applyingPalette = null
                                }
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        when {
                            applyingPalette == name -> CircularProgressIndicator()
                            name == currentPalette  -> Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}
