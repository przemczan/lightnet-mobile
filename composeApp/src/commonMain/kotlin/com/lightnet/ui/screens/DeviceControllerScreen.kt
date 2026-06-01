package com.lightnet.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.AppearanceRequest
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControllerScreen(
    device: LightnetDevice?,
    activeDevice: SavedDevice?,
    httpClient: LightnetHttpClient?,
    onOpenDeviceSwitcher: () -> Unit,
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

    var brightness           by remember(device) { mutableStateOf(128f) }
    var palette              by remember(device) { mutableStateOf<String?>(null) }
    var paletteNames         by remember(device) { mutableStateOf<List<String>>(emptyList()) }
    var paletteNamesLoading  by remember(device) { mutableStateOf(false) }
    var baseColors           by remember(device) { mutableStateOf<List<String>>(emptyList()) }
    var paintColor           by remember { mutableStateOf(Color(0xFFCF5B3C)) }
    var showColorSheet       by remember { mutableStateOf(false) }
    var showPaletteSheet     by remember { mutableStateOf(false) }
    var showBrightnessSheet  by remember { mutableStateOf(false) }
    var allPanelsOn          by remember { mutableStateOf(false) }

    LaunchedEffect(httpClient, connectionState) {
        if (connectionState == ConnectionState.CONNECTED && httpClient != null) {
            paletteNamesLoading = true
            val app = httpClient.runCatching { getAppearance() }.getOrNull()
            if (app != null) {
                brightness = app.brightness.toFloat()
                palette    = app.palette
                baseColors = app.baseColors
            }
            paletteNames        = httpClient.runCatching { getPalettes().keys.toList() }.getOrNull() ?: emptyList()
            paletteNamesLoading = false
            val power = httpClient.runCatching { getPowerState() }.getOrNull()
            if (power != null) allPanelsOn = power
        }
    }
    var showSettings       by remember { mutableStateOf(false) }

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
                            panels      = snapshot!!.panels,
                            powerOn     = allPanelsOn,
                            paintMode   = PaintMode.Paint,
                            paintColor  = paintColor,
                            interactive = !isReconnecting,
                            modifier    = Modifier.fillMaxSize(),
                        )
                    }

                    else -> LoadingState(label = "Connecting…")
                }

                // Left vertical toolbar — visible once panels are ready
                if (snapshot != null && !isFirstLoading) {
                    Surface(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .alpha(if (isReconnecting) 0.45f else 1f),
                        color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape          = MaterialTheme.shapes.medium,
                        tonalElevation = 6.dp,
                    ) {
                        Column(
                            Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Color
                            IconButton(onClick = { showColorSheet = true }) {
                                Icon(Icons.Default.Colorize, contentDescription = "Pick color", modifier = Modifier.size(26.dp))
                            }

                            // Palette
                            IconButton(onClick = { showPaletteSheet = true }) {
                                Icon(Icons.Default.Gradient, contentDescription = "Choose palette", modifier = Modifier.size(26.dp))
                            }

                            // Brightness
                            IconButton(onClick = { showBrightnessSheet = true }) {
                                Icon(Icons.Default.WbSunny, contentDescription = "Brightness", modifier = Modifier.size(26.dp))
                            }

                            // Power — visually distinct on/off states
                            IconToggleButton(
                                checked         = allPanelsOn,
                                onCheckedChange = { on ->
                                    allPanelsOn = on
                                    scope.launch { httpClient?.runCatching { setPowerState(on) } }
                                },
                                colors = IconButtonDefaults.iconToggleButtonColors(
                                    containerColor        = Color.Transparent,
                                    contentColor          = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    checkedContentColor   = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = if (allPanelsOn) "Turn off" else "Turn on",
                                    modifier = Modifier.size(26.dp),
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
                httpClient?.runCatching { setAppearance(AppearanceRequest(palette = name)) }
            },
            onDismiss      = { showPaletteSheet = false },
        )
    }

    if (showBrightnessSheet) {
        BrightnessSheet(
            initialBrightness = brightness,
            onSave            = { newBrightness ->
                brightness = newBrightness
                httpClient?.runCatching { setAppearance(AppearanceRequest(brightness = newBrightness.toInt())) }
            },
            onDismiss         = { showBrightnessSheet = false },
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
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Brightness", style = MaterialTheme.typography.titleMedium)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.WbSunny,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            Button(
                onClick  = {
                    scope.launch {
                        isSaving = true
                        onSave(brightness)
                        isSaving = false
                        onDismiss()
                    }
                },
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth(),
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
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
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
                            applyingPalette == name -> CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            name == currentPalette  -> Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("OK") }
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
