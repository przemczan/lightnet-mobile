package com.lightnet.ui.screens.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevice
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.SectionHeader
import com.lightnet.ui.components.SpeedSlider
import com.lightnet.ui.components.colorRefToColor
import com.lightnet.ui.colorToHex
import com.lightnet.ui.parseHexColor
import com.lightnet.ui.screens.ColorPickerSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

enum class SceneOrigin { GLOBAL, DEVICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorScreen(
    device: LightnetDevice?,
    httpClient: LightnetHttpClient?,
    initial: com.lightnet.api.http.model.SceneJson?,
    origin: SceneOrigin = SceneOrigin.GLOBAL,
    onBack: () -> Unit,
) {
    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val snapshot by remember(device) { device?.snapshot ?: MutableStateFlow(null) }.collectAsState()
    val panels = snapshot?.panels ?: emptyList()

    var palettesMap by remember { mutableStateOf<Map<String, PaletteJson>>(emptyMap()) }
    var baseColors  by remember { mutableStateOf<List<String>>(emptyList()) }
    var tags        by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(httpClient) {
        palettesMap = httpClient?.runCatching { getPalettes() }?.getOrNull() ?: emptyMap()
    }
    LaunchedEffect(device) {
        baseColors = device?.loadAppearance()?.baseColors ?: device?.cachedAppearance?.baseColors ?: emptyList()
        tags = device?.getTopology()?.tags?.values?.flatten()?.distinct()?.sorted() ?: emptyList()
    }
    val paletteNames = remember(palettesMap) { palettesMap.keys.sorted() }

    var scene by remember { mutableStateOf<EditableScene?>(null) }
    LaunchedEffect(initial, panels.size) {
        if (scene != null) return@LaunchedEffect
        scene = when {
            initial == null     -> EditableScene()
            panels.isNotEmpty()  -> sceneFromJson(initial, panels)
            else                 -> null
        }
    }

    var isDirty by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var alsoSaveToOther by remember { mutableStateOf(false) }

    val activeSceneForTracking = scene
    LaunchedEffect(activeSceneForTracking, panels.isEmpty()) {
        if (activeSceneForTracking == null || panels.isEmpty()) return@LaunchedEffect
        val initialJson = activeSceneForTracking.toSceneJson(panels)
        snapshotFlow { activeSceneForTracking.toSceneJson(panels) }
            .drop(1)
            .collect { current -> isDirty = current != initialJson }
    }

    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun stopPreview() {
        device?.setLivePreview(false)
        val c = httpClient
        if (c != null) cleanupScope.launch(NonCancellable) { runCatching { c.stopScene() } }
    }

    var editingLayer by remember { mutableStateOf<EditableLayer?>(null) }
    var previewExpanded by remember { mutableStateOf(true) }

    val activeScene = scene
    if (activeScene == null) {
        Scaffold(topBar = { EditorTopBar(if (initial == null) "New scene" else "Edit scene", onBack) }) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                if (device == null || httpClient == null) {
                    Text(
                        "Connect a device to edit scenes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else CircularProgressIndicator()
            }
        }
        return
    }

    fun stopsFor(layer: EditableLayer): List<PaletteStop>? =
        (layer.palette ?: activeScene.palette)?.let { palettesMap[it]?.stops }

    editingLayer?.let { layer ->
        LayerEditorScreen(
            layer         = layer,
            index         = activeScene.layers.indexOf(layer),
            panels        = panels,
            paletteNames  = paletteNames,
            paletteStops  = stopsFor(layer),
            baseColors    = baseColors,
            tags          = tags,
            otherLayers   = activeScene.layers.filter { it !== layer },
            onBack        = { editingLayer = null },
        )
        return
    }

    fun requestBack() {
        if (isDirty) showExitConfirm = true else onBack()
    }

    BackHandlerCompat(onBack = ::requestBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title          = { Text(activeScene.name.ifBlank { "New scene" }) },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scene = activeScene.clone("${activeScene.name.ifBlank { "Scene" }} copy")
                        isDirty = true
                    }) { Text("Clone") }
                    TextButton(onClick = {
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@TextButton }
                        activeScene.clearUnusedStepIds()
                        val sceneJson = activeScene.toSceneJson(panels)
                        when (origin) {
                            SceneOrigin.GLOBAL -> {
                                val ok = runCatching { com.lightnet.settings.AppPreferences.scenes.save(sceneJson) }.isSuccess
                                if (!ok) { scope.launch { snackbar.showSnackbar("Failed to save scene.") }; return@TextButton }
                                isDirty = false
                                if (alsoSaveToOther && httpClient != null) {
                                    scope.launch {
                                        if (!httpClient.runCatching { saveScene(sceneJson) }.isSuccess)
                                            snackbar.showSnackbar("Saved locally but failed to save to device.")
                                        onBack()
                                    }
                                } else {
                                    onBack()
                                }
                            }
                            SceneOrigin.DEVICE -> {
                                if (httpClient == null) { scope.launch { snackbar.showSnackbar("Connect a device to save.") }; return@TextButton }
                                scope.launch {
                                    if (!httpClient.runCatching { saveScene(sceneJson) }.isSuccess) {
                                        snackbar.showSnackbar("Failed to save scene to device."); return@launch
                                    }
                                    isDirty = false
                                    if (alsoSaveToOther && !runCatching { com.lightnet.settings.AppPreferences.scenes.save(sceneJson) }.isSuccess)
                                        snackbar.showSnackbar("Saved to device but failed to save locally.")
                                    onBack()
                                }
                            }
                        }
                    }) { Text("Save") }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                OutlinedButton(
                    onClick = {
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@OutlinedButton }
                        device?.setLivePreview(true)
                        scope.launch { httpClient?.runCatching { playSceneInline(activeScene.toPreviewSceneJson(panels)) } }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Preview")
                }
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = { stopPreview() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Stop")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (panels.isNotEmpty()) {
                VisualizerPreviewCard(
                    panels   = panels,
                    expanded = previewExpanded,
                    onToggle = { previewExpanded = !previewExpanded },
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    top = 8.dp, bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp, end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    TextField(
                        value         = activeScene.name,
                        onValueChange = { activeScene.name = it },
                        label         = { Text("NAME") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    val checkboxEnabled = when (origin) {
                        SceneOrigin.GLOBAL -> httpClient != null
                        SceneOrigin.DEVICE -> true
                    }
                    val checkboxLabel = when (origin) {
                        SceneOrigin.GLOBAL -> "Also save to device"
                        SceneOrigin.DEVICE -> "Also save to Global"
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = checkboxEnabled) { alsoSaveToOther = !alsoSaveToOther }
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked         = alsoSaveToOther,
                            onCheckedChange = { alsoSaveToOther = it },
                            enabled         = checkboxEnabled,
                        )
                        Column {
                            Text(checkboxLabel, style = MaterialTheme.typography.bodyMedium)
                            if (origin == SceneOrigin.GLOBAL && httpClient == null) {
                                Text(
                                    "Connect a device to enable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("Loop", style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = activeScene.loop, onCheckedChange = { activeScene.loop = it })
                            }
                            HorizontalDivider()
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text("Speed", style = MaterialTheme.typography.bodyLarge)
                                SpeedSlider(
                                    speed         = activeScene.speed,
                                    onSpeedChange = { activeScene.speed = it },
                                )
                            }
                            HorizontalDivider()
                            PaletteDropdown(
                                label    = "Default palette",
                                value    = activeScene.palette,
                                options  = paletteNames,
                                onSelect = { activeScene.palette = it },
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            HorizontalDivider()
                            BackgroundColorRow(
                                hex      = activeScene.background,
                                onChange = { activeScene.background = it },
                            )
                        }
                    }
                }
                item { SectionHeader("Layers") }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            activeScene.layers.forEachIndexed { i, layer ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                LayerRow(
                                    layer        = layer,
                                    panelCount   = panels.size,
                                    paletteStops = stopsFor(layer),
                                    baseColors   = baseColors,
                                    canMoveUp    = i > 0,
                                    canMoveDown  = i < activeScene.layers.lastIndex,
                                    onEdit       = { editingLayer = layer },
                                    onClone      = {
                                        activeScene.layers.add(i + 1, layer.clone(activeScene.cloneLayerName(layer.name)))
                                    },
                                    onDelete     = { activeScene.layers.remove(layer) },
                                    onMoveUp     = { activeScene.layers.move(i, i - 1) },
                                    onMoveDown   = { activeScene.layers.move(i, i + 1) },
                                    onToggleEnabled = { layer.enabled = !layer.enabled },
                                )
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            TextButton(
                                onClick  = { activeScene.layers.add(EditableLayer(name = activeScene.nextLayerName())) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) { Text("+ Add layer") }
                        }
                    }
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title            = { Text("Discard changes?") },
            text             = { Text("You have unsaved changes. Leave without saving?") },
            confirmButton    = {
                TextButton(onClick = { showExitConfirm = false; onBack() }) { Text("Discard") }
            },
            dismissButton    = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Keep editing") }
            },
        )
    }
}

private fun targetSummary(layer: EditableLayer, panelCount: Int): String = when (layer.targetKind) {
    TargetKind.All      -> "All panels"
    TargetKind.Specific -> "${layer.selected.size} of $panelCount panels"
    TargetKind.Selector -> layer.selectorToken
    TargetKind.Advanced -> "Advanced selector"
}

@Composable
private fun LayerRow(
    layer: EditableLayer,
    panelCount: Int,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (layer.enabled) 1f else 0.5f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onToggleEnabled) {
                    Icon(
                        if (layer.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (layer.enabled) "Disable layer" else "Enable layer",
                    )
                }
                Text(layer.name.ifBlank { "Layer" }, style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Clone") }, onClick = { showMenu = false; onClone() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
        Text(
            buildString {
                append(targetSummary(layer, panelCount))
                append(" · blend: ${layer.blend ?: "default"}")
                if (layer.asyncMode != AsyncMode.Off) append(" · async:${layer.asyncMode.name.lowercase()}")
                layer.startAfter?.takeIf { it.isNotBlank() }?.let { append(" · after $it") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            layer.steps.forEach { step -> StepChip(step, paletteStops, baseColors) }
        }
    }
}

@Composable
private fun StepChip(step: EditableStep, paletteStops: List<PaletteStop>?, baseColors: List<String>) {
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.anim.colorMode != ColorMode.None) {
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(colorRefToColor(step.colorA, paletteStops, baseColors))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        Text(step.anim.display, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun BackgroundColorRow(
    hex: String?,
    onChange: (String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val color = remember(hex) { parseHexColor(hex ?: "#000000") ?: Color.Black }

    Row(
        Modifier.fillMaxWidth().clickable { showPicker = true }.padding(vertical = 8.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Column {
            Text("Background", style = MaterialTheme.typography.bodyLarge)
            Text(
                hex ?: "Default (black)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hex != null) {
                TextButton(onClick = { onChange(null) }) { Text("Reset") }
            }
            Box(
                Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            )
        }
    }

    if (showPicker) {
        ColorPickerSheet(
            initial        = color,
            showBaseColors = false,
            onPick         = { onChange(colorToHex(it)) },
            onDismiss      = { showPicker = false },
        )
    }
}
