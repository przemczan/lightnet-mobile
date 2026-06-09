package com.lightnet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.BlendMode
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevice
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.ColorRefPickerSheet
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.SpeedSlider
import com.lightnet.ui.components.colorRefToColor
import com.lightnet.ui.colorToHex
import com.lightnet.ui.parseHexColor
import com.lightnet.ui.screens.scene.AnimId
import com.lightnet.ui.screens.scene.AsyncMode
import com.lightnet.ui.screens.scene.ColorMode
import com.lightnet.ui.screens.scene.EditableLayer
import com.lightnet.ui.screens.scene.EditableScene
import com.lightnet.ui.screens.scene.EditableStep
import com.lightnet.ui.screens.scene.RunnerAnimates
import com.lightnet.ui.screens.scene.RunnerModShape
import com.lightnet.ui.screens.scene.RunnerSrc
import com.lightnet.ui.screens.scene.TargetKind
import com.lightnet.ui.screens.scene.clone
import com.lightnet.ui.screens.scene.sceneFromJson
import com.lightnet.ui.screens.scene.toPreviewSceneJson
import com.lightnet.ui.screens.scene.toSceneJson
import com.lightnet.ui.screens.scene.validationError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

/** Where a scene was loaded from — controls which store is primary on Save. */
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

    // Reference data for colour previews / dropdowns.
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

    // Build the editable model once. For an existing scene we wait until panels are
    // loaded so panel targets map correctly; a new scene starts immediately.
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

    // Track dirty state once panels are loaded so panel-ID mapping is stable.
    val activeSceneForTracking = scene
    LaunchedEffect(activeSceneForTracking, panels.isEmpty()) {
        if (activeSceneForTracking == null || panels.isEmpty()) return@LaunchedEffect
        val initialJson = activeSceneForTracking.toSceneJson(panels)
        snapshotFlow { activeSceneForTracking.toSceneJson(panels) }
            .drop(1)
            .collect { current -> isDirty = current != initialJson }
    }

    // Best-effort cleanup that survives leaving the screen.
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

    // Effective palette stops for a layer's colour previews (layer override → scene default).
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
            otherNames    = activeScene.layers.filter { it !== layer }.map { it.name },
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
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@TextButton }
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
                item { SettingsSectionTitle("LAYERS") }
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
                                    onTogglePreview = { layer.includedInPreview = !layer.includedInPreview },
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

// ── Visualizer preview (pinned above the scrolling form, foldable) ───────────────

@Composable
private fun VisualizerPreviewCard(
    panels: List<LightnetDevicePanel>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse preview" else "Expand preview",
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                LightnetDeviceVisualizer(
                    panels      = panels,
                    modifier    = Modifier.fillMaxWidth().height(220.dp),
                    interactive = false,
                )
            }
        }
    }
}

// ── Layer row (summary in the scene list) ───────────────────────────────────────

/** Sentinel shown in the blend dropdown for "no explicit blend" (null → firmware default). */
private const val BLEND_DEFAULT = "default"

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
    onTogglePreview: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (layer.includedInPreview) 1f else 0.5f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onTogglePreview) {
                    Icon(
                        if (layer.includedInPreview) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (layer.includedInPreview) "Hide from preview" else "Show in preview",
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

// ── Layer editor (name + panel targeting + step sequence) ────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerEditorScreen(
    layer: EditableLayer,
    index: Int,
    panels: List<LightnetDevicePanel>,
    paletteNames: List<String>,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    tags: List<String>,
    otherNames: List<String>,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)
    var editingStep by remember { mutableStateOf<EditableStep?>(null) }

    editingStep?.let { step ->
        StepEditorScreen(
            step         = step,
            panels       = panels,
            paletteStops = paletteStops,
            baseColors   = baseColors,
            onBack       = { editingStep = null },
        )
        return
    }

    Scaffold(topBar = { EditorTopBar(layer.name.ifBlank { "Layer ${index + 1}" }, onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextField(
                    value         = layer.name,
                    onValueChange = { layer.name = it },
                    label         = { Text("LAYER NAME") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }

            item { SettingsSectionTitle("PANELS") }
            item { PanelTargetEditor(layer, panels, tags) }

            item { SettingsSectionTitle("PLAYBACK") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        val asyncEnabled = layer.startAfter.isNullOrBlank()
                        Text("Async", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(layer.asyncMode == AsyncMode.Off,  { if (asyncEnabled) layer.asyncMode = AsyncMode.Off },  { Text("Off") },  enabled = asyncEnabled || layer.asyncMode == AsyncMode.Off)
                            FilterChip(layer.asyncMode == AsyncMode.Loop, { if (asyncEnabled) layer.asyncMode = AsyncMode.Loop }, { Text("Loop") }, enabled = asyncEnabled || layer.asyncMode == AsyncMode.Loop)
                            FilterChip(layer.asyncMode == AsyncMode.Free, { if (asyncEnabled) layer.asyncMode = AsyncMode.Free }, { Text("Free") }, enabled = asyncEnabled || layer.asyncMode == AsyncMode.Free)
                        }
                        HorizontalDivider()
                        LabeledDropdown(
                            label    = "Start after",
                            value    = layer.startAfter?.takeIf { it.isNotBlank() } ?: "Nothing (start immediately)",
                            options  = listOf("Nothing (start immediately)") + otherNames,
                            onSelect = { layer.startAfter = if (it == "Nothing (start immediately)") null else it },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        HorizontalDivider()
                        LabeledDropdown(
                            label    = "Blend (how this layer composites)",
                            value    = layer.blend ?: BLEND_DEFAULT,
                            options  = listOf(BLEND_DEFAULT) + BlendMode.all,
                            onSelect = { layer.blend = if (it == BLEND_DEFAULT) null else it },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            if (paletteNames.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        PaletteDropdown(
                            label    = "Palette override (optional)",
                            value    = layer.palette,
                            options  = paletteNames,
                            onSelect = { layer.palette = it },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            item { SettingsSectionTitle("STEPS") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        layer.steps.forEachIndexed { i, step ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            StepRow(
                                step         = step,
                                paletteStops = paletteStops,
                                baseColors   = baseColors,
                                canRemove    = layer.steps.size > 1,
                                canMoveUp    = i > 0,
                                canMoveDown  = i < layer.steps.lastIndex,
                                onClick      = { editingStep = step },
                                onRemove     = { layer.steps.remove(step) },
                                onMoveUp     = { layer.steps.move(i, i - 1) },
                                onMoveDown   = { layer.steps.move(i, i + 1) },
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        TextButton(
                            onClick  = { layer.steps.add(EditableStep()) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text("+ Add step") }
                    }
                }
            }
        }
    }
}

// ── Panel target editor ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelTargetEditor(
    layer: EditableLayer,
    panels: List<LightnetDevicePanel>,
    tags: List<String>,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(layer.targetKind == TargetKind.All, { layer.targetKind = TargetKind.All }, { Text("All") })
                FilterChip(layer.targetKind == TargetKind.Specific, { layer.targetKind = TargetKind.Specific }, { Text("Specific") })
                FilterChip(layer.targetKind == TargetKind.Selector, { layer.targetKind = TargetKind.Selector }, { Text("By role") })
                if (layer.targetKind == TargetKind.Advanced) {
                    FilterChip(true, {}, { Text("Advanced") })
                }
            }

            when (layer.targetKind) {
                TargetKind.All -> Unit
                TargetKind.Specific -> {
                    Text(
                        "Tap panels to include (${layer.selected.size} selected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LightnetDeviceVisualizer(
                        panels            = panels,
                        modifier          = Modifier.fillMaxWidth().height(260.dp),
                        interactive       = false,
                        selectionMode     = true,
                        selectedPanels    = layer.selected,
                        onSelectionChange = { layer.selected = it },
                        paintMode         = PaintMode.Paint,
                    )
                }
                TargetKind.Selector -> SelectorEditor(layer, panels, tags)
                TargetKind.Advanced -> Text(
                    "This layer uses an advanced selector that can't be edited here. " +
                        "Pick All, Specific, or By role to replace it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class SelectorKind(val key: String, val label: String, val arg: ArgKind)
private enum class ArgKind { None, DepthBand, Panel, Count, Fraction, Tag }

private val SELECTOR_KINDS = listOf(
    SelectorKind("root", "Root", ArgKind.None),
    SelectorKind("leaves", "Leaves (tips)", ArgKind.None),
    SelectorKind("branches", "Branches (forks)", ArgKind.None),
    SelectorKind("even", "Even", ArgKind.None),
    SelectorKind("odd", "Odd", ArgKind.None),
    SelectorKind("depth", "Depth ring", ArgKind.DepthBand),
    SelectorKind("subtree", "Subtree of panel", ArgKind.Panel),
    SelectorKind("neighbors", "Neighbors of panel", ArgKind.Panel),
    SelectorKind("first", "First N", ArgKind.Count),
    SelectorKind("last", "Last N", ArgKind.Count),
    SelectorKind("fraction", "Fraction (front..back)", ArgKind.Fraction),
    SelectorKind("tag", "Tag", ArgKind.Tag),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorEditor(
    layer: EditableLayer,
    panels: List<LightnetDevicePanel>,
    tags: List<String>,
) {
    val token = layer.selectorToken
    val kindKey = token.substringBefore(':').ifBlank { "leaves" }
    val kind = SELECTOR_KINDS.firstOrNull { it.key == kindKey } ?: SELECTOR_KINDS[1]
    val arg = token.substringAfter(':', "")

    fun defaultToken(k: SelectorKind): String = when (k.arg) {
        ArgKind.None      -> k.key
        ArgKind.DepthBand -> "${k.key}:1"
        ArgKind.Panel     -> "${k.key}:${panels.firstOrNull()?.info?.id ?: 1}"
        ArgKind.Count     -> "${k.key}:1"
        ArgKind.Fraction  -> "${k.key}:0-0.5"
        ArgKind.Tag       -> "${k.key}:${tags.firstOrNull() ?: "accent"}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledDropdown(
            label    = "Role",
            value    = kind.label,
            options  = SELECTOR_KINDS.map { it.label },
            onSelect = { sel -> SELECTOR_KINDS.firstOrNull { it.label == sel }?.let { layer.selectorToken = defaultToken(it) } },
        )

        when (kind.arg) {
            ArgKind.None -> Unit
            ArgKind.DepthBand -> OutlinedTextField(
                value         = arg,
                onValueChange = { layer.selectorToken = "${kind.key}:$it" },
                label         = { Text("Depth (e.g. 1 or 1-2)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            ArgKind.Count -> OutlinedTextField(
                value           = arg,
                onValueChange   = { v -> layer.selectorToken = "${kind.key}:${v.filter { it.isDigit() }}" },
                label           = { Text("Count") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier        = Modifier.fillMaxWidth(),
            )
            ArgKind.Fraction -> OutlinedTextField(
                value         = arg,
                onValueChange = { layer.selectorToken = "${kind.key}:$it" },
                label         = { Text("Range 0-1 (e.g. 0-0.5)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            ArgKind.Tag -> OutlinedTextField(
                value         = arg,
                onValueChange = { layer.selectorToken = "${kind.key}:$it" },
                label         = { Text("Tag name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            ArgKind.Panel -> PanelPickerField(
                label           = "Panel",
                selectedPanelId = arg.toIntOrNull(),
                panels          = panels,
                onPick          = { layer.selectorToken = "${kind.key}:$it" },
            )
        }
    }
}

@Composable
private fun StepRow(
    step: EditableStep,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    canRemove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.anim.colorMode != ColorMode.None) {
            Box(
                Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(colorRefToColor(step.colorA, paletteStops, baseColors))
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(step.anim.display, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${step.durationMs} ms" + if (step.loop) " · loop" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}

// ── Step editor (dynamic form driven by the animation metadata) ──────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StepEditorScreen(
    step: EditableStep,
    panels: List<LightnetDevicePanel>,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)
    var colorSlot by remember { mutableStateOf<Int?>(null) }  // 0 = A/from/single, 1 = B/to

    Scaffold(topBar = { EditorTopBar("Step", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSectionTitle("TYPE")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimId.panelTypes.forEach { t ->
                        FilterChip(selected = step.anim == t, onClick = { step.changeAnim(t) }, label = { Text(t.display) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Runners (motion across panels)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimId.runnerTypes.forEach { t ->
                        FilterChip(selected = step.anim == t, onClick = { step.changeAnim(t) }, label = { Text(t.display) })
                    }
                }
            }

            // All runners show their colour picker inside their respective Animates section.
            if (!step.anim.isRunner) {
                when (step.anim.colorMode) {
                    ColorMode.Single -> item {
                        ColorSlotRow("Color", step.colorA, paletteStops, baseColors) { colorSlot = 0 }
                    }
                    ColorMode.FromTo -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorSlotRow("From", step.colorA, paletteStops, baseColors) { colorSlot = 0 }
                            ColorSlotRow("To", step.colorB, paletteStops, baseColors) { colorSlot = 1 }
                        }
                    }
                    ColorMode.None -> Unit
                }
            }

            item { DurationEditor(step) }

            // Runner directionality + what it animates + width — WHEEL gets its own pivot/spin
            // editor instead (always geometric, always looping).
            if (step.anim == AnimId.WHEEL) {
                item { WheelEditor(step, panels, paletteStops, baseColors) { colorSlot = 0 } }
            } else if (step.anim.isRunner) {
                item { RunnerDirectionEditor(step, panels) }
                item {
                    RunnerAnimatesEditor(
                        step         = step,
                        paletteStops = paletteStops,
                        baseColors   = baseColors,
                        onColorClick = { colorSlot = 0 },
                    )
                }
                if (step.anim.hasWidth) {
                    item {
                        Column {
                            Text("${step.anim.widthLabel}  ${step.width}", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value         = step.width.toFloat(),
                                onValueChange = { step.width = it.roundToInt().coerceAtLeast(1) },
                                valueRange    = 1f..16f,
                            )
                        }
                    }
                }
            }

            step.anim.params.forEachIndexed { i, spec ->
                item {
                    val value = step.params.getOrElse(i) { spec.default }
                    Column {
                        Text("${spec.label}  $value", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value         = value.toFloat(),
                            onValueChange = { v ->
                                step.params = step.params.toMutableList().also {
                                    while (it.size <= i) it.add(0)
                                    it[i] = v.roundToInt()
                                }
                            },
                            valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        )
                    }
                }
            }

            if (step.anim.supportsLoopFlags) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            ToggleRow("Loop", step.loop) { step.loop = it }
                            HorizontalDivider()
                            ToggleRow("Ping-pong", step.pingpong) { step.pingpong = it }
                        }
                    }
                }
            }
        }
    }

    colorSlot?.let { slot ->
        val current = if (slot == 0) step.colorA else step.colorB
        ColorRefPickerSheet(
            title        = if (step.anim.colorMode == ColorMode.FromTo) (if (slot == 0) "From colour" else "To colour") else "Colour",
            initial      = current,
            paletteStops = paletteStops,
            baseColors   = baseColors,
            onPick       = { picked: ColorRef -> if (slot == 0) step.colorA = picked else step.colorB = picked },
            onDismiss    = { colorSlot = null },
        )
    }
}

/** Duration control: a slider for quick adjustment plus a numeric field for precise entry, kept in sync. */
@Composable
private fun DurationEditor(step: EditableStep) {
    var text by remember(step.id) { mutableStateOf(step.durationMs.toString()) }
    LaunchedEffect(step.durationMs) {
        if (step.durationMs.toString() != text) text = step.durationMs.toString()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value         = step.durationMs.toFloat(),
                    onValueChange = { step.durationMs = it.roundToInt() },
                    valueRange    = 0f..30000f,
                    modifier      = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value           = text,
                    onValueChange   = { v ->
                        text = v.filter(Char::isDigit).take(5)
                        text.toIntOrNull()?.let { step.durationMs = it.coerceIn(0, 30000) }
                    },
                    singleLine      = true,
                    suffix          = { Text("ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.width(110.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RunnerDirectionEditor(step: EditableStep, panels: List<LightnetDevicePanel>) {
    val isRipple = step.anim == AnimId.RIPPLE
    // Geometric WAVE/CHASE sweep a straight axis (steered by `angle`, no origin → no source).
    // Geometric RIPPLE expands as circular rings, so it has no axis (`angle` ignored) but uses
    // `source` as its centre. Topology mode always uses `source`.
    val showAngle  = step.geometric && !isRipple
    val showSource = !step.geometric || isRipple

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Directionality", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Topology = sweep the wiring graph; Geometric = use the physical layout (axis
                // sweep for wave/chase, circular rings for ripple).
                FilterChip(!step.geometric, { step.geometric = false }, { Text("Topology") })
                FilterChip(step.geometric, { step.geometric = true }, { Text("Geometric") })
            }

            if (showAngle) {
                Column {
                    Text("Angle  ${step.angle}°", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.angle.toFloat(),
                        onValueChange = { step.angle = it.roundToInt() },
                        valueRange    = 0f..359f,
                    )
                }
            }

            if (showSource) {
                // For a geometric ripple this is the ring centre; "Leaves" then means one ripple
                // per leaf, expanding inward together.
                Text(
                    if (step.geometric) "Ripple centre" else "Source",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(step.source == RunnerSrc.Root,   { step.source = RunnerSrc.Root },   { Text("Root") })
                    FilterChip(step.source == RunnerSrc.Leaves, { step.source = RunnerSrc.Leaves }, { Text("Leaves") })
                    FilterChip(step.source == RunnerSrc.Panel,  { step.source = RunnerSrc.Panel },  { Text("Panel") })
                    // "All" is meaningful for RIPPLE (concentric uniform pulse) — analogous to Wheel's Pivot→All.
                    if (isRipple || step.source == RunnerSrc.All)
                        FilterChip(step.source == RunnerSrc.All, { step.source = RunnerSrc.All }, { Text("All") })
                }
                if (step.source == RunnerSrc.Panel) {
                    PanelPickerField(
                        label           = "From panel",
                        selectedPanelId = step.sourcePanel,
                        panels          = panels,
                        onPick          = { step.sourcePanel = it },
                    )
                }
            }

            ToggleRow("Reverse direction", step.reverse) { step.reverse = it }
        }
    }
}

/**
 * WHEEL pivot + spin controls. Unlike WAVE/RIPPLE/CHASE, a wheel always uses the geometric
 * (planar) layout and always loops — there's no topology/geometric toggle and no `angle` (it
 * spins about its `source`, not along an axis). `Leaves`/`All` average to a single centre point.
 */
@Composable
private fun WheelEditor(
    step: EditableStep,
    panels: List<LightnetDevicePanel>,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onColorClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pivot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.source == RunnerSrc.Root,   { step.source = RunnerSrc.Root },   { Text("Root") })
                FilterChip(step.source == RunnerSrc.Leaves, { step.source = RunnerSrc.Leaves }, { Text("Leaves") })
                FilterChip(step.source == RunnerSrc.Panel,  { step.source = RunnerSrc.Panel },  { Text("Panel") })
                FilterChip(step.source == RunnerSrc.All,    { step.source = RunnerSrc.All },    { Text("All") })
            }
            if (step.source == RunnerSrc.Panel) {
                PanelPickerField(
                    label           = "Pivot panel",
                    selectedPanelId = step.sourcePanel,
                    panels          = panels,
                    onPick          = { step.sourcePanel = it },
                )
            }

            ToggleRow("Spin the other way", step.reverse) { step.reverse = it }
            HorizontalDivider()

            Column {
                Text("Blades  ${step.lines}", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value         = step.lines.toFloat(),
                    onValueChange = { step.lines = it.roundToInt().coerceIn(1, 6) },
                    valueRange    = 1f..6f,
                )
            }
            Column {
                Text("Blade thickness  ${step.thickness}°", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value         = step.thickness.toFloat(),
                    onValueChange = { step.thickness = it.roundToInt().coerceIn(0, 180) },
                    valueRange    = 0f..180f,
                )
            }
            HorizontalDivider()
            Text("Animates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.animates == RunnerAnimates.Color,      { step.animates = RunnerAnimates.Color },      { Text("Color") })
                FilterChip(step.animates == RunnerAnimates.Brightness, { step.animates = RunnerAnimates.Brightness }, { Text("Brightness") })
                FilterChip(step.animates == RunnerAnimates.Saturation, { step.animates = RunnerAnimates.Saturation }, { Text("Saturation") })
                FilterChip(step.animates == RunnerAnimates.Hue,        { step.animates = RunnerAnimates.Hue },        { Text("Hue") })
                FilterChip(step.animates == RunnerAnimates.Invert,     { step.animates = RunnerAnimates.Invert },     { Text("Invert") })
            }
            if (step.animates == RunnerAnimates.Color) {
                ColorSwatchRow(
                    label          = "Color",
                    color          = step.colorA,
                    paletteStops   = paletteStops,
                    baseColors     = baseColors,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier       = Modifier.clickable(onClick = onColorClick),
                )
            } else {
                Column {
                    Text("Peak amount  ${step.amount}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.amount.toFloat(),
                        onValueChange = { step.amount = it.roundToInt() },
                        valueRange    = 0f..255f,
                    )
                }
            }
        }
    }
}

/**
 * What the runner's sweep modulates. `Color` (default) sweeps a colour `PULSE`; the others drive
 * a brightness/saturation/hue/invert modifier sweep instead — `amount` sets its peak intensity,
 * decaying back to that property's identity over the lit window (scene-authoring §7.3).
 */
@Composable
private fun RunnerAnimatesEditor(
    step: EditableStep,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onColorClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Animates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.animates == RunnerAnimates.Color,      { step.animates = RunnerAnimates.Color },      { Text("Color") })
                FilterChip(step.animates == RunnerAnimates.Brightness, { step.animates = RunnerAnimates.Brightness }, { Text("Brightness") })
                FilterChip(step.animates == RunnerAnimates.Saturation, { step.animates = RunnerAnimates.Saturation }, { Text("Saturation") })
                FilterChip(step.animates == RunnerAnimates.Hue,        { step.animates = RunnerAnimates.Hue },        { Text("Hue") })
                FilterChip(step.animates == RunnerAnimates.Invert,     { step.animates = RunnerAnimates.Invert },     { Text("Invert") })
            }
            if (step.animates == RunnerAnimates.Color) {
                ColorSwatchRow(
                    label          = "Color",
                    color          = step.colorA,
                    paletteStops   = paletteStops,
                    baseColors     = baseColors,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier       = Modifier.clickable(onClick = onColorClick),
                )
                HorizontalDivider()
                // Continuous train of evenly-spaced sweeps instead of a single pass — colour-only,
                // since the modifier ramp (brightness/saturation/hue/invert) can't loop cleanly.
                ToggleRow("Repeat — continuous train", step.repeat) { step.repeat = it }
                if (step.repeat) {
                    HorizontalDivider()
                    Column {
                        Text("Waves  ${step.repeatCount}", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value         = step.repeatCount.toFloat(),
                            onValueChange = { step.repeatCount = it.roundToInt().coerceAtLeast(1) },
                            valueRange    = 1f..16f,
                        )
                    }
                }
            } else {
                Column {
                    Text("Peak amount  ${step.amount}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.amount.toFloat(),
                        onValueChange = { step.amount = it.roundToInt() },
                        valueRange    = 0f..255f,
                    )
                }
                HorizontalDivider()
                Text("Shape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(step.modShape == RunnerModShape.Fall, { step.modShape = RunnerModShape.Fall }, { Text("Fall") })
                    FilterChip(step.modShape == RunnerModShape.Rise, { step.modShape = RunnerModShape.Rise }, { Text("Rise") })
                    FilterChip(step.modShape == RunnerModShape.Bell, { step.modShape = RunnerModShape.Bell }, { Text("Bell") })
                }
            }
        }
    }
}

/** Background colour picker row: tap the swatch to open [ColorPickerSheet]; "Reset" clears the override (→ black). */
@Composable
private fun BackgroundColorRow(
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

/** Label + colour swatch row, shared by the standalone [ColorSlotRow] card and inline uses (e.g. inside the Animates section). */
@Composable
private fun ColorSwatchRow(
    label: String,
    color: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(contentPadding),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box(
            Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
                .background(colorRefToColor(color, paletteStops, baseColors))
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        )
    }
}

@Composable
private fun ColorSlotRow(
    label: String,
    color: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        ColorSwatchRow(label, color, paletteStops, baseColors, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteDropdown(
    label: String,
    value: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value         = value ?: "Device default",
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Device default") }, onClick = { onSelect(null); expanded = false })
            options.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value         = value,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}

// ── Visual panel picker ────────────────────────────────────────────────────────
// Replaces a panel-id dropdown with a tappable field that opens the device
// visualizer; tapping a panel selects it and closes the sheet.

@Composable
private fun PanelPickerField(
    label: String,
    selectedPanelId: Int?,
    panels: List<LightnetDevicePanel>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth().clickable { show = true }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedPanelId?.let { "Panel $it" } ?: "Tap to choose",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose panel")
        }
    }
    if (show) {
        PanelPickerSheet(
            title           = label,
            panels          = panels,
            selectedPanelId = selectedPanelId,
            onPick          = { onPick(it); show = false },
            onDismiss       = { show = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelPickerSheet(
    title: String,
    panels: List<LightnetDevicePanel>,
    selectedPanelId: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Visualizer selection works on list indices; map to/from panel ids at the edges.
    val selectedIndex = remember(panels, selectedPanelId) {
        panels.indexOfFirst { it.info.id == selectedPanelId }
    }
    val selectedSet = if (selectedIndex >= 0) setOf(selectedIndex) else emptySet()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a panel to select it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LightnetDeviceVisualizer(
                panels            = panels,
                modifier          = Modifier.fillMaxWidth().height(340.dp),
                interactive       = false,
                selectionMode     = true,
                selectedPanels    = selectedSet,
                showPanelIds      = true,
                onSelectionChange = { newSet ->
                    // Newly tapped panel (toggle semantics); tapping the current one keeps it.
                    val picked = (newSet - selectedSet).firstOrNull() ?: selectedIndex.takeIf { it >= 0 }
                    if (picked != null) onPick(panels[picked].info.id) else onDismiss()
                },
            )
        }
    }
}

private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

