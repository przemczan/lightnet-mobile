package com.lightnet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.SceneJson
import com.lightnet.device.ConnectionState
import com.lightnet.settings.AppPreferences
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.debug.DebugLog
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
    httpClient: LightnetHttpClient?,
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
    val livePreview     by device.livePreview.collectAsState()

    // Refresh panel states on screen entry when already connected.
    LaunchedEffect(device) { device?.refreshPanelStates() }

    // Turn off live preview when the visualizer screen leaves composition.
    DisposableEffect(device) { onDispose { device.setLivePreview(false) } }

    var wasConnected    by remember(device) { mutableStateOf(device.connectionState.value == ConnectionState.CONNECTED) }
    // Pre-seeded to true only when snapshot AND power state are both already cached, so
    // switching devices never renders the visualizer with the wrong on/off state.
    var deviceInfoReady by remember(device) { mutableStateOf(device.snapshot.value != null && device.cachedPowerState != null) }
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
    var palettes            by remember(device) { mutableStateOf<List<PaletteJson>>(emptyList()) }
    var palettesLoading     by remember(device) { mutableStateOf(false) }
    var baseColors          by remember(device) { mutableStateOf(device.cachedAppearance?.baseColors ?: emptyList()) }
    var paintColor          by remember { mutableStateOf<Color?>(null) }
    var showColorSheet      by remember { mutableStateOf(false) }
    var showPaletteSheet    by remember { mutableStateOf(false) }
    var showBrightnessSheet by remember { mutableStateOf(false) }
    var showScenesSheet     by remember { mutableStateOf(false) }
    var showSpeedSheet      by remember { mutableStateOf(false) }
    var allPanelsOn         by remember(device) { mutableStateOf(device.cachedPowerState ?: false) }
    var showRotateSheet   by remember(device) { mutableStateOf(false) }
    var rawRotationAngle  by remember(device) { mutableFloatStateOf(devicePrefs.visualizerRotation.value) }
    val rotationAngle     = (rawRotationAngle / 5f).roundToInt() * 5f

    LaunchedEffect(device, connectionState, httpClient) {
        if (connectionState == ConnectionState.CONNECTED && device != null) {
            // Fetch power state first so the visualizer unblocks with the correct on/off state.
            val power = device.getPowerState()
            if (power != null) allPanelsOn = power
            deviceInfoReady = true

            palettesLoading = true
            val app = device.loadAppearance()
            if (app != null) {
                brightness = app.brightness.toFloat()
                palette    = app.palette
                baseColors = app.baseColors
            }
            palettes        = httpClient?.runCatching { getPalettes().values.toList() }?.getOrNull() ?: emptyList()
            palettesLoading = false
        }
    }

    var showSettings      by remember { mutableStateOf(false) }
    var showDebug         by remember { mutableStateOf(false) }
    var showSwitcherSheet by remember { mutableStateOf(false) }
    var showOffMessage    by remember { mutableStateOf(false) }
    LaunchedEffect(showOffMessage) {
        if (showOffMessage) {
            delay(2000)
            showOffMessage = false
        }
    }

    if (showDebug) {
        DebugScreen(onBack = { showDebug = false })
        return
    }

    if (showSettings) {
        DeviceSettingsScreen(
            savedDevice = activeDevice,
            device      = device,
            httpClient  = httpClient,
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
                            panels        = snapshot!!.panels,
                            powerOn       = allPanelsOn,
                            brightness    = brightness,
                            paintMode     = PaintMode.Paint,
                            paintColor    = paintColor ?: Color(0xFFCF5B3C),
                            interactive   = !isReconnecting,
                            showPanelIds  = debugMode,
                            onTapWhileOff = { showOffMessage = true },
                            rotationDegrees = rotationAngle,
                            modifier      = Modifier.fillMaxSize(),
                        )
                    }

                    else -> LoadingState(label = "Connecting…")
                }

                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                ) {
                    AnimatedVisibility(
                        visible = showOffMessage,
                        enter   = fadeIn() + slideInVertically(),
                        exit    = fadeOut() + slideOutVertically(),
                    ) {
                        Snackbar { Text("Turn the device on first") }
                    }
                }

                // Bottom centered toolbar — always visible; buttons disabled when disconnected
                val isConnected = connectionState == ConnectionState.CONNECTED
                var showOverflowMenu by remember { mutableStateOf(false) }
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
                            IconButton(onClick = { showColorSheet = true }, enabled = isConnected) {
                                Icon(Icons.Default.Brush, contentDescription = "Pick color")
                            }
                            IconButton(onClick = { showPaletteSheet = true }, enabled = isConnected) {
                                Icon(Icons.Default.Gradient, contentDescription = "Choose palette")
                            }
                            IconButton(onClick = { showBrightnessSheet = true }, enabled = isConnected) {
                                Icon(Icons.Default.WbSunny, contentDescription = "Brightness")
                            }
                            IconButton(onClick = { showSpeedSheet = true }, enabled = isConnected) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed")
                            }
                            IconButton(onClick = { showScenesSheet = true }) {
                                Icon(Icons.Default.Movie, contentDescription = "Scenes")
                            }
                            FilledIconToggleButton(
                                checked         = livePreview,
                                onCheckedChange = { device.setLivePreview(it) },
                                enabled         = isConnected,
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = if (livePreview) "Stop live preview" else "Live preview",
                                )
                            }
                            FilledIconToggleButton(
                                checked         = allPanelsOn,
                                onCheckedChange = { on ->
                                    allPanelsOn = on
                                    scope.launch { device.setPowerState(on) }
                                },
                                enabled         = isConnected,
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = if (allPanelsOn) "Turn off" else "Turn on",
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded         = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text         = { Text("Rotate view") },
                                        leadingIcon  = { Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = null) },
                                        onClick      = { showOverflowMenu = false; showRotateSheet = true },
                                        enabled      = isConnected,
                                    )
                                }
                            }
                        }
                    }

                    FloatingActionButton(onClick = { showSwitcherSheet = true }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch device")
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
            palettes       = palettes,
            isLoading      = palettesLoading,
            currentPalette = palette,
            onSelect       = { name ->
                palette = name
                device.setAppearance(AppearanceRequest(palette = name))
            },
            onDismiss      = { showPaletteSheet = false },
        )
    }

    if (showScenesSheet) {
        ScenesSheet(
            httpClient = httpClient,
            onDismiss  = { showScenesSheet = false },
        )
    }

    if (showBrightnessSheet) {
        BrightnessSheet(
            initialBrightness   = brightness,
            onBrightnessChange  = { brightness = it },
            onApply             = { device.setAppearance(AppearanceRequest(brightness = it.toInt())) },
            onDismiss           = { showBrightnessSheet = false },
        )
    }

    if (showSpeedSheet) {
        SpeedSheet(
            httpClient = httpClient,
            onDismiss  = { showSpeedSheet = false },
        )
    }

    if (showRotateSheet) {
        RotateSheet(
            initial       = rawRotationAngle,
            onAngleChange = { rawRotationAngle = it },
            onConfirm     = {
                devicePrefs.setVisualizerRotation(rotationAngle)
                showRotateSheet = false
            },
            onDismiss     = {
                // Revert the live preview to the last saved angle.
                rawRotationAngle = devicePrefs.visualizerRotation.value
                showRotateSheet = false
            },
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

// ── Rotate sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotateSheet(
    initial: Float,
    onAngleChange: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var angle by remember { mutableFloatStateOf(initial) }
    val snapped = (angle / 5f).roundToInt() * 5f

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
            Text("Rotate view", style = MaterialTheme.typography.titleMedium)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = null)
                Slider(
                    value         = angle,
                    onValueChange = { angle = it; onAngleChange(it) },
                    valueRange    = 0f..360f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    "${snapped.roundToInt()}°",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onConfirm) { Text("OK") }
            }
        }
    }
}

// ── Brightness sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessSheet(
    initialBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onApply: suspend (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var brightness by remember { mutableFloatStateOf(initialBrightness) }
    // Conflated channel: holds at most one pending value. Consumer calls API immediately,
    // then waits 250 ms before picking up the next — throttles to at most 1 call per 250 ms.
    val pending = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for (value in pending) {
            onApply(value)
            delay(250)
        }
    }

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
                    valueRange    = 1f / 255f..1f,
                    onValueChange = {
                        brightness = it * 255f
                        onBrightnessChange(brightness)
                        pending.trySend(brightness)
                    },
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
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

// ── Speed sheet ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    httpClient: LightnetHttpClient?,
    onDismiss: () -> Unit,
) {
    var speed by remember { mutableFloatStateOf(1f) }
    val pending = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for (value in pending) {
            httpClient?.runCatching { setSceneSpeed(value) }
            delay(250)
        }
    }

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
            Text("Speed", style = MaterialTheme.typography.titleMedium)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Slider(
                    value         = speed,
                    valueRange    = 0.1f..10f,
                    onValueChange = {
                        speed = (it * 10).roundToInt() / 10f
                        pending.trySend(speed)
                    },
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    "${speed.oneDecimal()}×",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

// ── Palette sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteSheet(
    palettes: List<PaletteJson>,
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
                palettes.isEmpty() -> Text(
                    "No palettes available on this device.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> palettes.forEach { pal ->
                    val gradientStops = remember(pal.stops) {
                        pal.stops.sortedBy { it.position }.map { stop ->
                            (stop.position / 255f) to (parseHexColor(stop.color) ?: Color.White)
                        }.toTypedArray()
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = applyingPalette == null) {
                                scope.launch {
                                    applyingPalette = pal.name
                                    onSelect(pal.name)
                                    applyingPalette = null
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (gradientStops.isNotEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(Brush.horizontalGradient(colorStops = gradientStops))
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(pal.name, style = MaterialTheme.typography.bodyMedium)
                            when {
                                applyingPalette == pal.name -> CircularProgressIndicator(Modifier.size(20.dp))
                                pal.name == currentPalette  -> Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
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

// ── Scenes sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenesSheet(
    httpClient: LightnetHttpClient?,
    onDismiss: () -> Unit,
) {
    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var scenes   by remember { mutableStateOf(AppPreferences.scenes.getAll()) }
    var playing  by remember { mutableStateOf<String?>(null) }

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
                "Scenes",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                scenes.isEmpty() -> Text(
                    "No scenes yet. Add scenes in Settings → Scenes.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> scenes.forEach { scene ->
                    val name = scene.name ?: "Unnamed"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = playing == null) {
                                scope.launch {
                                    if (httpClient == null) {
                                        snackbar.showSnackbar("Connect a device to play scenes.")
                                        return@launch
                                    }
                                    playing = name
                                    runCatching { httpClient.playSceneInline(scene) }
                                        .onFailure { snackbar.showSnackbar("Failed to play \"$name\".") }
                                    playing = null
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        if (playing == name) CircularProgressIndicator(Modifier.size(20.dp))
                    }
                    HorizontalDivider()
                }
            }
        }

        SnackbarHost(snackbar)
    }
}

private fun Float.oneDecimal(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}
