package com.lightnet.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevice
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.ColorRefPickerSheet
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.colorRefToColor
import com.lightnet.ui.screens.scene.AnimId
import com.lightnet.ui.screens.scene.ColorMode
import com.lightnet.ui.screens.scene.EditableLayer
import com.lightnet.ui.screens.scene.EditableScene
import com.lightnet.ui.screens.scene.EditableStep
import com.lightnet.ui.screens.scene.RunnerSrc
import com.lightnet.ui.screens.scene.TargetKind
import com.lightnet.ui.screens.scene.sceneFromJson
import com.lightnet.ui.screens.scene.toSceneJson
import com.lightnet.ui.screens.scene.validationError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorScreen(
    device: LightnetDevice?,
    httpClient: LightnetHttpClient?,
    initial: com.lightnet.api.http.model.SceneJson?,
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

    // Best-effort cleanup that survives leaving the screen.
    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun stopPreview() {
        device?.setLivePreview(false)
        val c = httpClient
        if (c != null) cleanupScope.launch(NonCancellable) { runCatching { c.stopScene() } }
    }

    // Stop any preview playback whenever the editor leaves composition for good.
    // Registered before the sub-screen early-returns so navigating into a layer
    // (which swaps this screen out via return) does not trigger it.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { stopPreview() } }

    var editingLayer by remember { mutableStateOf<EditableLayer?>(null) }

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

    BackHandlerCompat(onBack = { stopPreview(); onBack() })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title          = { Text(activeScene.name.ifBlank { "New scene" }) },
                navigationIcon = {
                    IconButton(onClick = { stopPreview(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@TextButton }
                        runCatching {
                            com.lightnet.settings.AppPreferences.scenes.save(activeScene.toSceneJson(panels))
                        }.onSuccess { stopPreview(); onBack() }
                         .onFailure { scope.launch { snackbar.showSnackbar("Failed to save scene.") } }
                    }) { Text("Save") }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@OutlinedButton }
                        device?.setLivePreview(true)
                        scope.launch { httpClient?.runCatching { playSceneInline(activeScene.toSceneJson(panels)) } }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Preview")
                }
                OutlinedButton(onClick = { stopPreview() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Stop")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (panels.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        LightnetDeviceVisualizer(
                            panels      = panels,
                            modifier    = Modifier.fillMaxWidth().height(220.dp),
                            interactive = false,
                        )
                    }
                }
            }
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Loop", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = activeScene.loop, onCheckedChange = { activeScene.loop = it })
                        }
                        HorizontalDivider()
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text("Speed  ${activeScene.speed.oneDecimal()}×", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value         = activeScene.speed,
                                onValueChange = { activeScene.speed = (it * 10).roundToInt() / 10f },
                                valueRange    = 0.1f..10f,
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
                    }
                }
            }
            item { SettingsSectionTitle("LAYERS") }
            activeScene.layers.forEach { layer ->
                item(key = layer.id) {
                    LayerCard(
                        layer        = layer,
                        panelCount   = panels.size,
                        paletteStops = stopsFor(layer),
                        baseColors   = baseColors,
                        onEdit       = { editingLayer = layer },
                        onDelete     = { activeScene.layers.remove(layer) },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick  = { activeScene.layers.add(EditableLayer(name = activeScene.nextLayerName())) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Add layer")
                }
            }
        }
    }
}

// ── Layer card (summary in the scene list) ──────────────────────────────────────

private fun targetSummary(layer: EditableLayer, panelCount: Int): String = when (layer.targetKind) {
    TargetKind.All      -> "All panels"
    TargetKind.Specific -> "${layer.selected.size} of $panelCount panels"
    TargetKind.Selector -> layer.selectorToken
    TargetKind.Advanced -> "Advanced selector"
}

@Composable
private fun LayerCard(
    layer: EditableLayer,
    panelCount: Int,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(layer.name.ifBlank { "Layer" }, style = MaterialTheme.typography.titleSmall)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            Text(
                buildString {
                    append(targetSummary(layer, panelCount))
                    if (layer.async) append(" · async")
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
                        ToggleRow(
                            "Async (loop independently)",
                            layer.async,
                            enabled = layer.startAfter.isNullOrBlank(),
                        ) { layer.async = it }
                        HorizontalDivider()
                        LabeledDropdown(
                            label    = "Start after",
                            value    = layer.startAfter?.takeIf { it.isNotBlank() } ?: "Nothing (start immediately)",
                            options  = listOf("Nothing (start immediately)") + otherNames,
                            onSelect = { layer.startAfter = if (it == "Nothing (start immediately)") null else it },
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
            ArgKind.Panel -> LabeledDropdown(
                label    = "Panel",
                value    = arg.ifBlank { "—" },
                options  = panels.map { it.info.id.toString() },
                onSelect = { layer.selectorToken = "${kind.key}:$it" },
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

            item {
                Column {
                    Text("Duration  ${step.durationMs} ms", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.durationMs.toFloat(),
                        onValueChange = { step.durationMs = it.roundToInt() },
                        valueRange    = 0f..30000f,
                    )
                }
            }

            // Runner directionality + width.
            if (step.anim.isRunner) {
                item { RunnerDirectionEditor(step, panels) }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RunnerDirectionEditor(step: EditableStep, panels: List<LightnetDevicePanel>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Source", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.source == RunnerSrc.Root, { step.source = RunnerSrc.Root }, { Text("Root") })
                FilterChip(step.source == RunnerSrc.Leaves, { step.source = RunnerSrc.Leaves }, { Text("Leaves") })
                FilterChip(step.source == RunnerSrc.Panel, { step.source = RunnerSrc.Panel }, { Text("Panel") })
                if (step.source == RunnerSrc.All) FilterChip(true, {}, { Text("All") })
            }
            if (step.source == RunnerSrc.Panel) {
                LabeledDropdown(
                    label    = "From panel",
                    value    = step.sourcePanel.toString(),
                    options  = panels.map { it.info.id.toString() },
                    onSelect = { step.sourcePanel = it.toIntOrNull() ?: step.sourcePanel },
                )
            }
            ToggleRow("Reverse direction", step.reverse) { step.reverse = it }
        }
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
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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

private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

private fun Float.oneDecimal(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}
