package com.lightnet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.ui.screens.scene.SceneEditorScreen
import com.lightnet.ui.screens.scene.SceneOrigin
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
import com.lightnet.ui.components.SpeedSlider
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControllerScreen(
    device: LightnetDevice?,
    activeDevice: SavedDevice,
    devices: List<SavedDevice>,
    devicePool: Map<String, LightnetDevice>,
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
    var showAdjustSheet     by remember { mutableStateOf(false) }
    var showScenesSheet     by remember { mutableStateOf(false) }
    var allPanelsOn         by remember(device) { mutableStateOf(device.cachedPowerState ?: false) }
    // Rotation is a persisted per-device view preference, edited in Appearance settings.
    val savedRotation       by devicePrefs.visualizerRotation.collectAsState()
    val rotationAngle       = (savedRotation / 5f).roundToInt() * 5f

    var appState             by remember(device) { mutableStateOf<AppStateBody?>(null) }
    var sceneStatusRefresh   by remember(device) { mutableStateOf(0) }
    val isScenePlaying       = appState?.playing == true
    val lastPlayedScene      = appState?.lastPlayedScene ?: ""
    val playToolbarSceneName = lastPlayedScene

    // Refresh scene playback status + last-played-scene name on screen open and after a play/stop action.
    LaunchedEffect(device, connectionState, httpClient, sceneStatusRefresh) {
        if (connectionState == ConnectionState.CONNECTED) {
            httpClient?.runCatching { getAppState() }?.getOrNull()?.let { appState = it }
        }
    }

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
    var showSwitcherSheet   by remember { mutableStateOf(false) }
    var editingScene        by remember { mutableStateOf<SceneJson?>(null) }
    var editingSceneOrigin  by remember { mutableStateOf(SceneOrigin.GLOBAL) }
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

    editingScene?.let { scene ->
        SceneEditorScreen(
            device     = device,
            httpClient = httpClient,
            initial    = scene,
            origin     = editingSceneOrigin,
            onBack     = { editingScene = null; sceneStatusRefresh++ },
        )
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

    val isConnected = connectionState == ConnectionState.CONNECTED

    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                // Device quick-select lives on the left of the toolbar so it is always visible.
                navigationIcon = {
                    IconButton(onClick = { showSwitcherSheet = true }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch device")
                    }
                },
                title   = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Image(
                            painter           = painterResource(Res.drawable.logo_mark),
                            contentDescription = null,
                            modifier          = Modifier.size(28.dp),
                        )
                        Text(activeDevice.name)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Device settings")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { showColorSheet = true }, enabled = isConnected) {
                        Icon(Icons.Default.Brush, contentDescription = "Pick color")
                    }
                    IconButton(onClick = { showAdjustSheet = true }, enabled = isConnected) {
                        Icon(Icons.Default.Tune, contentDescription = "Adjust brightness, palette and speed")
                    }
                    IconButton(onClick = { showScenesSheet = true }, enabled = isConnected) {
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
                },
                floatingActionButton = {
                    // Power is the primary control — a color-coded FAB makes the on/off
                    // state glanceable and gives it the largest touch target.
                    FloatingActionButton(
                        onClick = {
                            if (!isConnected) return@FloatingActionButton
                            val on = !allPanelsOn
                            allPanelsOn = on
                            scope.launch { device.setPowerState(on) }
                        },
                        containerColor = when {
                            !isConnected -> MaterialTheme.colorScheme.surfaceVariant
                            allPanelsOn  -> MaterialTheme.colorScheme.primaryContainer
                            else         -> BottomAppBarDefaults.bottomAppBarFabColor
                        },
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = if (allPanelsOn) "Turn off" else "Turn on",
                        )
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

                // Floating scene-playback toolbar — sits just above the docked toolbar.
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
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text     = playToolbarSceneName.ifBlank { "No scene" },
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            FilledIconToggleButton(
                                checked         = isScenePlaying,
                                onCheckedChange = {
                                    scope.launch {
                                        if (isScenePlaying) {
                                            httpClient?.runCatching { stopScene() }
                                        } else {
                                            httpClient?.runCatching { playLastScene() }
                                        }
                                        sceneStatusRefresh++
                                    }
                                },
                                enabled = isConnected && (isScenePlaying || lastPlayedScene.isNotBlank()),
                            ) {
                                Icon(
                                    if (isScenePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isScenePlaying) "Stop scene" else "Play \"$lastPlayedScene\"",
                                )
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

    if (showAdjustSheet) {
        AdjustSheet(
            initialBrightness  = brightness,
            onBrightnessChange = { brightness = it },
            onBrightnessApply  = { device.setAppearance(AppearanceRequest(brightness = it.toInt())) },
            palettes           = palettes,
            palettesLoading    = palettesLoading,
            currentPalette     = palette,
            onSelectPalette    = { name ->
                palette = name
                device.setAppearance(AppearanceRequest(palette = name))
            },
            httpClient         = httpClient,
            onDismiss          = { showAdjustSheet = false },
        )
    }

    if (showScenesSheet) {
        ScenesSheet(
            httpClient    = httpClient,
            onDismiss     = { showScenesSheet = false },
            onScenePlayed = { sceneStatusRefresh++ },
            onEdit        = { scene, origin -> showScenesSheet = false; editingSceneOrigin = origin; editingScene = scene },
        )
    }

    if (showSwitcherSheet) {
        DeviceSwitcherSheet(
            devices         = devices,
            devicePool      = devicePool,
            activeKey       = activeDevice.name,
            onSelect        = { onSwitchDevice(it) },
            onManageDevices = onManageDevices,
            onDismiss       = { showSwitcherSheet = false },
        )
    }
}

// ── Adjust sheet (brightness · palette · speed) ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSheet(
    initialBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessApply: suspend (Float) -> Unit,
    palettes: List<PaletteJson>,
    palettesLoading: Boolean,
    currentPalette: String?,
    onSelectPalette: suspend (String) -> Unit,
    httpClient: LightnetHttpClient?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var brightness     by remember { mutableFloatStateOf(initialBrightness) }
    var speed          by remember { mutableFloatStateOf(1f) }
    var applyingPalette by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(httpClient) {
        httpClient?.runCatching { getAppState() }?.getOrNull()?.let { speed = it.speed }
    }

    // Conflated channels throttle each control to at most one API call per 250 ms.
    val brightnessPending = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for (value in brightnessPending) { onBrightnessApply(value); delay(250) }
    }
    val speedPending = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for (value in speedPending) { httpClient?.runCatching { setSceneSpeed(value) }; delay(250) }
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Brightness
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Brightness", style = MaterialTheme.typography.titleSmall)
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
                            brightnessPending.trySend(brightness)
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
            }

            // Speed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speed", style = MaterialTheme.typography.titleSmall)
                SpeedSlider(
                    speed         = speed,
                    onSpeedChange = { speed = it; speedPending.trySend(it) },
                    modifier      = Modifier.fillMaxWidth(),
                )
            }

            // Palette
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Palette", style = MaterialTheme.typography.titleSmall)
                when {
                    palettesLoading -> Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    palettes.isEmpty() -> Text(
                        "No palettes available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        onSelectPalette(pal.name)
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

// ── Scenes sheet ──────────────────────────────────────────────────────────────

private sealed class ScenesSheetItem {
    abstract val name: String
    data class Global(val scene: SceneJson)  : ScenesSheetItem() { override val name = scene.name ?: "Unnamed" }
    data class Device(val info: SceneInfo)   : ScenesSheetItem() { override val name = info.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenesSheet(
    httpClient: LightnetHttpClient?,
    onDismiss: () -> Unit,
    onScenePlayed: () -> Unit,
    onEdit: (SceneJson, SceneOrigin) -> Unit,
) {
    val scope       = rememberCoroutineScope()
    val snackbar    = remember { SnackbarHostState() }
    val globalScenes = remember { AppPreferences.scenes.getAll() }
    var deviceScenes by remember { mutableStateOf<List<SceneInfo>>(emptyList()) }
    var playing     by remember { mutableStateOf<String?>(null) }
    var loadingEdit by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(httpClient) {
        deviceScenes = httpClient?.runCatching { getScenes() }?.getOrNull() ?: emptyList()
    }

    val items = remember(globalScenes, deviceScenes) {
        globalScenes.map { ScenesSheetItem.Global(it) } + deviceScenes.map { ScenesSheetItem.Device(it) }
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
        ) {
            Text(
                "Scenes",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                items.isEmpty() -> Text(
                    "No scenes yet. Add scenes in Settings → Scenes.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> items.forEach { item ->
                    val name = item.name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = playing == null && loadingEdit == null) {
                                scope.launch {
                                    if (httpClient == null && item is ScenesSheetItem.Device) {
                                        snackbar.showSnackbar("Connect a device to play device scenes.")
                                        return@launch
                                    }
                                    playing = name
                                    val ok = when (item) {
                                        is ScenesSheetItem.Global -> {
                                            if (httpClient == null) {
                                                snackbar.showSnackbar("Connect a device to play scenes.")
                                                playing = null; return@launch
                                            }
                                            runCatching { httpClient.playSceneInline(item.scene) }.isSuccess
                                        }
                                        is ScenesSheetItem.Device ->
                                            runCatching { httpClient!!.playSceneByName(item.info.name) }.isSuccess
                                    }
                                    if (ok) onScenePlayed() else snackbar.showSnackbar("Failed to play \"$name\".")
                                    playing = null
                                    if (ok) onDismiss()
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector        = if (item is ScenesSheetItem.Global) Icons.Default.Smartphone else Icons.Default.Router,
                                contentDescription = if (item is ScenesSheetItem.Global) "Global scene" else "Device scene",
                                modifier           = Modifier.size(18.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (playing == name) CircularProgressIndicator(Modifier.size(20.dp))
                            IconButton(
                                enabled = loadingEdit == null && playing == null,
                                onClick = {
                                    when (item) {
                                        is ScenesSheetItem.Global -> onEdit(item.scene, SceneOrigin.GLOBAL)
                                        is ScenesSheetItem.Device -> scope.launch {
                                            if (httpClient == null) return@launch
                                            loadingEdit = name
                                            val full = httpClient.runCatching { getScene(item.info.name) }.getOrNull()
                                            loadingEdit = null
                                            if (full != null) onEdit(full, SceneOrigin.DEVICE)
                                            else snackbar.showSnackbar("Failed to load \"$name\".")
                                        }
                                    }
                                },
                            ) {
                                if (loadingEdit == name) CircularProgressIndicator(Modifier.size(20.dp))
                                else Icon(Icons.Default.Edit, contentDescription = "Edit \"$name\"")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        SnackbarHost(snackbar)
    }
}

