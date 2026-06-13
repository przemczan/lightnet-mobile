package com.lightnet.ui.screens.scene

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.http.model.SceneJson
import com.lightnet.device.LightnetDevice
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.SpeedSlider
import com.lightnet.ui.components.colorRefToColor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Experimental timeline scene editor ───────────────────────────────────────────
// An alternative to SceneEditorScreen that lays the whole scene out as a horizontal
// timeline: layers = stacked tracks, steps = draggable/resizable blocks. It edits the
// same EditableScene model and reuses the existing step/layer modal editors, conversion,
// preview and save logic — see SceneEditorModel.kt and SceneEditorScreen.kt.
//
// Time model: free placement with auto-gaps. A block's start = cumulative duration of all
// preceding steps; GAP steps render as empty space, never as blocks. Every edit operates on
// the placed-block view (absolute start times) and rebuilds layer.steps, synthesizing GAPs to
// realize positions. This round-trips through toSceneJson / sceneFromJson with no new model.

private const val TRACK_HEIGHT_DP = 56
private const val BLOCK_VERTICAL_INSET_DP = 4
private const val TIME_RULER_HEIGHT_DP = 36
private const val BLOCK_MIN_MS = 50
private const val DEFAULT_PX_PER_MS = 0.08f
private const val MIN_PX_PER_MS = 0.01f
private const val MIN_PX_PER_MS_FLOOR = 0.0005f / 3f
private const val MAX_PX_PER_MS = 0.5f
private const val CONTENT_TAIL_PX = 800f

// startAfter connector arrow. Target and source share the same time boundary, so the link is a
// straight vertical line between their rows.
private const val CONN_STROKE_DP = 2f
private const val CONN_ARROW_DP = 5f      // arrowhead leg length

/** A resolved `startAfter` link: source layer starts at [boundaryMs] when [targetId] (a layer, or a
 *  specific step within it) finishes. [downward] = source row sits below the target row on screen. */
private data class Connector(val sourceId: Long, val targetId: Long, val boundaryMs: Int, val downward: Boolean)

/** One non-gap step placed at an absolute start time on its layer's timeline. */
private data class Placed(val step: EditableStep, val startMs: Int)

/** Walks the contiguous step list, accruing time; gaps become spacing, real steps become blocks. */
private fun EditableLayer.placedBlocks(): List<Placed> {
    var t = 0
    val out = ArrayList<Placed>()
    for (s in steps) {
        if (s.anim != AnimId.GAP) out.add(Placed(s, t))
        t += s.durationMs
    }
    return out
}

/** Rebuilds layer.steps from absolute-positioned blocks, synthesizing GAP holds for the gaps. */
private fun EditableLayer.applyPlaced(placed: List<Placed>) {
    val sorted = placed.sortedBy { it.startMs }
    val newSteps = ArrayList<EditableStep>()
    var cursor = 0
    for (p in sorted) {
        val gap = p.startMs - cursor
        if (gap > 0) newSteps.add(EditableStep(anim = AnimId.GAP, durationMs = gap))
        newSteps.add(p.step)
        cursor = maxOf(cursor, p.startMs) + p.step.durationMs  // overlap → snap right after previous
    }
    steps.clear()
    steps.addAll(newSteps)
}

private fun EditableLayer.totalDurationMs(): Int = steps.sumOf { it.durationMs }

/** Cumulative duration through (and including) the step with the given `id`, or null if absent. */
private fun EditableLayer.durationUpToStep(stepId: String): Int? {
    var sum = 0
    for (s in steps) {
        sum += s.durationMs
        if (s.stepId?.trim() == stepId) return sum
    }
    return null
}

/**
 * Resolves each layer's absolute start offset on the timeline by following `startAfter` chains
 * (offset = dependency's offset + dependency's total duration, or — for `"group:stepId"` —
 * the cumulative duration through that step). Layers with no `startAfter` (or an
 * unresolved/cyclic one) start at 0.
 */
private fun computeLayerOffsets(layers: List<EditableLayer>): Map<Long, Int> {
    val byName = layers.associateBy { it.name.trim() }
    val offsets = mutableMapOf<Long, Int>()
    val resolving = mutableSetOf<Long>()
    fun resolve(layer: EditableLayer): Int {
        offsets[layer.id]?.let { return it }
        if (!resolving.add(layer.id)) return 0  // cycle guard
        val startAfter = layer.startAfter?.trim()?.takeIf { it.isNotBlank() }
        val (depName, depStep) = startAfter?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (null to null)
        val dep = depName?.let { byName[it] }
        val result = if (dep != null) {
            val depDuration = depStep?.let { dep.durationUpToStep(it) } ?: dep.totalDurationMs()
            resolve(dep) + depDuration
        } else 0
        resolving.remove(layer.id)
        offsets[layer.id] = result
        return result
    }
    layers.forEach { resolve(it) }
    return offsets
}

/** Total timeline span across all layers (each layer's `startAfter` offset + its own duration). */
private fun maxDurationMs(layers: List<EditableLayer>): Int {
    val layerOffsets = computeLayerOffsets(layers)
    return layers.maxOfOrNull { (layerOffsets[it.id] ?: 0) + it.totalDurationMs() }
        ?.coerceAtLeast(1000) ?: 1000
}

/** Zoom-out floor for manual zoom: the pxPerMs at which the first 10 steps of the busiest layer fill the viewport. */
private fun minPxPerMsFor(layers: List<EditableLayer>, viewportWidthPx: Float): Float {
    if (viewportWidthPx <= 0f) return MIN_PX_PER_MS
    val tenStepDurationMs = layers.maxOfOrNull { layer -> layer.steps.take(10).sumOf { it.durationMs } }
        ?.takeIf { it > 0 } ?: return MIN_PX_PER_MS
    return (viewportWidthPx / tenStepDurationMs).coerceIn(MIN_PX_PER_MS_FLOOR, MAX_PX_PER_MS)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimelineSceneEditorScreen(
    device: LightnetDevice?,
    httpClient: LightnetHttpClient?,
    initial: SceneJson?,
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

    var showPreviewModal by remember { mutableStateOf(false) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var pxPerMs by remember { mutableFloatStateOf(DEFAULT_PX_PER_MS) }
    var hasAutoFitted by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()
    var editing by remember { mutableStateOf<Editing?>(null) }

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

    // ── Modal editors (reused as full-screen dialogs) ────────────────────────────
    when (val e = editing) {
        is Editing.Step -> Dialog(
            onDismissRequest = { editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                StepEditorScreen(
                    step         = e.step,
                    panels       = panels,
                    paletteStops = stopsFor(e.layer),
                    baseColors   = baseColors,
                    onBack       = { editing = null },
                    onDelete     = if (e.layer.steps.size > 1) {
                        { e.layer.steps.remove(e.step); editing = null }
                    } else null,
                )
            }
        }
        is Editing.Layer -> Dialog(
            onDismissRequest = { editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                LayerEditorScreen(
                    layer        = e.layer,
                    index        = activeScene.layers.indexOf(e.layer),
                    panels       = panels,
                    paletteNames = paletteNames,
                    paletteStops = stopsFor(e.layer),
                    baseColors   = baseColors,
                    tags         = tags,
                    otherLayers  = activeScene.layers.filter { it !== e.layer },
                    onBack       = { editing = null },
                    onDelete     = {
                        val name = e.layer.name.trim()
                        activeScene.layers.forEach { l ->
                            val sa = l.startAfter?.trim()
                            if (sa == name || sa?.startsWith("$name:") == true) l.startAfter = null
                        }
                        activeScene.layers.remove(e.layer)
                        editing = null
                    },
                )
            }
        }
        null -> Unit
    }

    fun requestBack() {
        if (isDirty) showExitConfirm = true else onBack()
    }
    BackHandlerCompat(onBack = ::requestBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title          = { Text(activeScene.name.ifBlank { "Timeline" }) },
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
                                val ok = runCatching { AppPreferences.scenes.save(sceneJson) }.isSuccess
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
                                    if (alsoSaveToOther && !runCatching { AppPreferences.scenes.save(sceneJson) }.isSuccess)
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
                        showPreviewModal = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Preview")
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Column(Modifier.fillMaxSize()) {
            // Name + zoom controls (compact).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value         = activeScene.name,
                    onValueChange = { activeScene.name = it },
                    label         = { Text("NAME") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                )
            }

            SceneOptionsCard(
                scene           = activeScene,
                expanded        = optionsExpanded,
                onToggle        = { optionsExpanded = !optionsExpanded },
                origin          = origin,
                httpClient      = httpClient,
                alsoSaveToOther = alsoSaveToOther,
                onAlsoSaveToOtherChange = { alsoSaveToOther = it },
                paletteNames    = paletteNames,
            )

            val fitPxPerMs = if (viewportWidthPx > 0f) {
                (viewportWidthPx / maxDurationMs(activeScene.layers)).coerceIn(MIN_PX_PER_MS_FLOOR, MAX_PX_PER_MS)
            } else {
                MIN_PX_PER_MS_FLOOR
            }
            // The slider must be able to reach the fit level, even if that's more zoomed-out
            // than the "first 10 steps fill the viewport" heuristic.
            val dynamicMinPxPerMs = minOf(minPxPerMsFor(activeScene.layers, viewportWidthPx), fitPxPerMs)

            // Fit the timeline to the screen once, as soon as the viewport width is known.
            LaunchedEffect(viewportWidthPx, activeScene) {
                if (hasAutoFitted || viewportWidthPx <= 0f) return@LaunchedEffect
                hasAutoFitted = true
                pxPerMs = fitPxPerMs
                hScroll.scrollTo(0)
            }

            ZoomControls(
                pxPerMs     = pxPerMs,
                minPxPerMs  = dynamicMinPxPerMs,
                onPxPerMs   = { pxPerMs = it.coerceIn(dynamicMinPxPerMs, MAX_PX_PER_MS) },
                onFit       = {
                    if (viewportWidthPx > 0f) {
                        pxPerMs = fitPxPerMs
                        scope.launch { hScroll.scrollTo(0) }
                    }
                },
            )

            SceneTimeline(
                scene        = activeScene,
                pxPerMs      = pxPerMs,
                hScroll      = hScroll,
                baseColors   = baseColors,
                stopsFor     = ::stopsFor,
                onEditStep   = { layer, step -> editing = Editing.Step(layer, step) },
                onEditLayer  = { layer -> editing = Editing.Layer(layer) },
                viewportWidthPx = viewportWidthPx,
                onRemoveLayer = { layer ->
                    val name = layer.name.trim()
                    activeScene.layers.forEach { l ->
                        val sa = l.startAfter?.trim()
                        if (sa == name || sa?.startsWith("$name:") == true) l.startAfter = null
                    }
                    activeScene.layers.remove(layer)
                },
                modifier     = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title            = { Text("Discard changes?") },
            text             = { Text("You have unsaved changes. Leave without saving?") },
            confirmButton    = { TextButton(onClick = { showExitConfirm = false; onBack() }) { Text("Discard") } },
            dismissButton    = { TextButton(onClick = { showExitConfirm = false }) { Text("Keep editing") } },
        )
    }

    if (showPreviewModal) {
        Dialog(onDismissRequest = { stopPreview(); showPreviewModal = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    LightnetDeviceVisualizer(
                        panels      = panels,
                        modifier    = Modifier.fillMaxWidth().height(320.dp),
                        interactive = false,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { stopPreview(); showPreviewModal = false }) { Text("Close") }
                    }
                }
            }
        }
    }
}

private sealed interface Editing {
    data class Step(val layer: EditableLayer, val step: EditableStep) : Editing
    data class Layer(val layer: EditableLayer) : Editing
}

/** Foldable scene-wide options: also-save-to-other, loop, speed, default palette, background. */
@Composable
private fun SceneOptionsCard(
    scene: EditableScene,
    expanded: Boolean,
    onToggle: () -> Unit,
    origin: SceneOrigin,
    httpClient: LightnetHttpClient?,
    alsoSaveToOther: Boolean,
    onAlsoSaveToOtherChange: (Boolean) -> Unit,
    paletteNames: List<String>,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Text(
                    "Scene options",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse scene options" else "Expand scene options",
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
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
                            .clickable(enabled = checkboxEnabled) { onAlsoSaveToOtherChange(!alsoSaveToOther) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked         = alsoSaveToOther,
                            onCheckedChange = onAlsoSaveToOtherChange,
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
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Loop", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = scene.loop, onCheckedChange = { scene.loop = it })
                    }
                    HorizontalDivider()
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Speed", style = MaterialTheme.typography.bodyLarge)
                        SpeedSlider(
                            speed         = scene.speed,
                            onSpeedChange = { scene.speed = it },
                        )
                    }
                    HorizontalDivider()
                    PaletteDropdown(
                        label    = "Default palette",
                        value    = scene.palette,
                        options  = paletteNames,
                        onSelect = { scene.palette = it },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    HorizontalDivider()
                    BackgroundColorRow(
                        hex      = scene.background,
                        onChange = { scene.background = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomControls(pxPerMs: Float, minPxPerMs: Float, onPxPerMs: (Float) -> Unit, onFit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPxPerMs(pxPerMs / 1.4f) }) {
            Icon(Icons.Default.Remove, contentDescription = "Zoom out")
        }
        Slider(
            value         = pxPerMs.coerceIn(minPxPerMs, MAX_PX_PER_MS),
            onValueChange = onPxPerMs,
            valueRange    = minPxPerMs..MAX_PX_PER_MS,
            modifier      = Modifier.weight(1f),
        )
        IconButton(onClick = { onPxPerMs(pxPerMs * 1.4f) }) {
            Icon(Icons.Default.Add, contentDescription = "Zoom in")
        }
        IconButton(onClick = onFit) {
            Icon(Icons.Default.CropFree, contentDescription = "Fit timeline to screen")
        }
    }
}

@Composable
private fun SceneTimeline(
    scene: EditableScene,
    pxPerMs: Float,
    hScroll: androidx.compose.foundation.ScrollState,
    baseColors: List<String>,
    stopsFor: (EditableLayer) -> List<PaletteStop>?,
    onEditStep: (EditableLayer, EditableStep) -> Unit,
    onEditLayer: (EditableLayer) -> Unit,
    viewportWidthPx: Float,
    onRemoveLayer: (EditableLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollScope = rememberCoroutineScope()

    val layerOffsets = computeLayerOffsets(scene.layers)
    val maxDurationMs = maxDurationMs(scene.layers)
    val contentWidthPx = maxDurationMs * pxPerMs + CONTENT_TAIL_PX
    val contentWidthDp = with(density) { contentWidthPx.toDp() }

    // Cross-layer drag session: how many rows the finger has moved from the source layer.
    val rowHeightPx = with(density) { TRACK_HEIGHT_DP.dp.toPx() }
    var dragSourceIndex by remember { mutableStateOf<Int?>(null) }
    var dragRowDelta by remember { mutableFloatStateOf(0f) }
    val dropTargetIndex: Int? = dragSourceIndex?.let { src ->
        (src + (dragRowDelta / rowHeightPx).roundToInt()).coerceIn(0, scene.layers.lastIndex)
    }

    // startAfter connector arrows: capture each track's window position so a fixed overlay can
    // draw meandering links from a target layer/step end into the dependent layer's first step.
    val trackPos = remember { mutableStateMapOf<Long, Offset>() }
    var canvasPos by remember { mutableStateOf<Offset?>(null) }
    val connColor = MaterialTheme.colorScheme.primary
    val connectors = run {
        val byName = scene.layers.associateBy { it.name.trim() }
        val indexById = scene.layers.withIndex().associate { (i, l) -> l.id to i }
        scene.layers.mapIndexedNotNull { si, src ->
            val dep = src.startAfter?.trim()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val target = byName[dep.substringBefore(":")]?.takeIf { it !== src } ?: return@mapIndexedNotNull null
            val ti = indexById[target.id] ?: return@mapIndexedNotNull null
            Connector(src.id, target.id, layerOffsets[src.id] ?: 0, downward = si > ti)
        }
    }

    Box(modifier.onGloballyPositioned { canvasPos = it.positionInWindow() }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            TimeRuler(maxDurationMs, pxPerMs, hScroll, contentWidthDp, contentWidthPx, viewportWidthPx, scrollScope)
            HorizontalDivider()

            scene.layers.forEachIndexed { i, layer ->
                LayerTrackRow(
                    layer          = layer,
                    index          = i,
                    canMoveUp      = i > 0,
                    canMoveDown    = i < scene.layers.lastIndex,
                    offsetMs       = layerOffsets[layer.id] ?: 0,
                    maxDurationMs  = maxDurationMs,
                    pxPerMs        = pxPerMs,
                    hScroll        = hScroll,
                    contentWidthDp = contentWidthDp,
                    paletteStops   = stopsFor(layer),
                    baseColors     = baseColors,
                    isDropTarget   = dropTargetIndex == i && dropTargetIndex != dragSourceIndex,
                    isDragSource   = dragSourceIndex == i,
                    onTrackPositioned = { trackPos[layer.id] = it },
                    onEditLayer    = { onEditLayer(layer) },
                    onEditStep     = { step -> onEditStep(layer, step) },
                    onAddBlock     = {
                        val placed = layer.placedBlocks()
                        val start  = placed.maxOfOrNull { it.startMs + it.step.durationMs } ?: 0
                        layer.applyPlaced(placed + Placed(EditableStep(), start))
                    },
                    onMoveUp       = { scene.layers.move(i, i - 1) },
                    onMoveDown     = { scene.layers.move(i, i + 1) },
                    onRemoveLayer  = { onRemoveLayer(layer) },
                    onBlockMove    = { _, _, dyPx -> dragSourceIndex = i; dragRowDelta = dyPx },
                    onBlockMoveEnd = { step, dxPx, dyPx ->
                        val targetIndex = (i + (dyPx / rowHeightPx).roundToInt()).coerceIn(0, scene.layers.lastIndex)
                        val target = scene.layers[targetIndex]
                        val deltaMs = (dxPx / pxPerMs).roundToInt()
                        val base = layer.placedBlocks().firstOrNull { it.step === step }?.startMs ?: 0
                        val sourceOffset = layerOffsets[layer.id] ?: 0
                        val targetOffset = layerOffsets[target.id] ?: 0
                        val absoluteNew = base + sourceOffset + deltaMs
                        val newStart = (absoluteNew - targetOffset).coerceAtLeast(0)
                        moveBlock(scene.layers, layer, target, step, newStart)
                        dragSourceIndex = null
                        dragRowDelta = 0f
                    },
                    onBlockResizeLeft = { step, dxPx ->
                        val placed = layer.placedBlocks()
                        val p = placed.firstOrNull { it.step === step } ?: return@LayerTrackRow
                        val end = p.startMs + step.durationMs
                        val deltaMs = (dxPx / pxPerMs).roundToInt()
                        val newStart = (p.startMs + deltaMs).coerceIn(0, end - BLOCK_MIN_MS)
                        step.durationMs = end - newStart
                        layer.applyPlaced(placed.map { if (it.step === step) Placed(step, newStart) else it })
                    },
                    onBlockResizeRight = { step, dxPx ->
                        val placed = layer.placedBlocks()
                        val deltaMs = (dxPx / pxPerMs).roundToInt()
                        step.durationMs = (step.durationMs + deltaMs).coerceAtLeast(BLOCK_MIN_MS)
                        layer.applyPlaced(placed)
                    },
                )
                HorizontalDivider()
            }

            TextButton(
                onClick  = { scene.layers.add(EditableLayer(name = scene.nextLayerName())) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("+ Add layer") }
            Spacer(Modifier.height(24.dp))
        }

        ConnectorArrows(
            connectors = connectors,
            trackPos   = trackPos,
            canvasPos  = canvasPos,
            pxPerMs    = pxPerMs,
            scrollX    = hScroll.value.toFloat(),
            color      = connColor,
            modifier   = Modifier.matchParentSize(),
        )
    }
}

/**
 * Fixed overlay that draws each `startAfter` link as a straight vertical line from the target
 * layer/step's end edge into the dependent layer's first step (target and source share the same
 * time boundary, so they're always vertically aligned). All coordinates are derived from window
 * positions ([trackPos], [canvasPos]) so the arrows track both vertical scrolling of the rows and
 * horizontal scrolling of the tracks.
 */
@Composable
private fun ConnectorArrows(
    connectors: List<Connector>,
    trackPos: Map<Long, Offset>,
    canvasPos: Offset?,
    pxPerMs: Float,
    scrollX: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clipToBounds()) {
        val origin = canvasPos ?: return@Canvas
        val rowH   = TRACK_HEIGHT_DP.dp.toPx()
        val inset  = BLOCK_VERTICAL_INSET_DP.dp.toPx()
        val arrow  = CONN_ARROW_DP.dp.toPx()
        val stroke = CONN_STROKE_DP.dp.toPx()

        connectors.forEach { c ->
            val sPos = trackPos[c.sourceId] ?: return@forEach
            val tPos = trackPos[c.targetId] ?: return@forEach
            val x = (tPos.x - origin.x) + c.boundaryMs * pxPerMs - scrollX

            // Exit the target's near edge, arrow into the source's near edge (down when below).
            val start = if (c.downward) Offset(x, (tPos.y - origin.y) + rowH - inset) else Offset(x, (tPos.y - origin.y) + inset)
            val tip   = if (c.downward) Offset(x, (sPos.y - origin.y) + inset) else Offset(x, (sPos.y - origin.y) + rowH - inset)
            val dir   = if (c.downward) 1f else -1f

            drawLine(color, start, tip, stroke, StrokeCap.Round)
            drawPath(
                path = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(x - arrow, tip.y - dir * arrow)
                    lineTo(x + arrow, tip.y - dir * arrow)
                    close()
                },
                color = color,
            )
        }
    }
}

/**
 * Moves a block to [target] at [newStartMs], rebuilding both source and target layers. If the
 * step crosses into a different layer and carries a `stepId`, re-points any `startAfter:
 * "source:stepId"` references to the new layer — regenerating the id first if it collides
 * with one already used in [target].
 */
private fun moveBlock(allLayers: List<EditableLayer>, source: EditableLayer, target: EditableLayer, step: EditableStep, newStartMs: Int) {
    if (source === target) {
        source.applyPlaced(source.placedBlocks().map { if (it.step === step) Placed(step, newStartMs) else it })
        return
    }
    val oldId = step.stepId?.trim()?.takeIf(String::isNotBlank)
    source.applyPlaced(source.placedBlocks().filter { it.step !== step })
    target.applyPlaced(target.placedBlocks() + Placed(step, newStartMs))

    if (oldId != null) {
        val collides = target.steps.any { it !== step && it.stepId?.trim() == oldId }
        val newId = if (collides) nextStepId(target) else oldId
        step.stepId = newId
        val oldRef = "${source.name.trim()}:$oldId"
        val newRef = "${target.name.trim()}:$newId"
        allLayers.forEach { l -> if (l.startAfter?.trim() == oldRef) l.startAfter = newRef }
    }
}

/**
 * Time ruler: tick labels scroll in sync with the tracks (drag-to-pan via [hScroll]); a
 * translucent "window" rect representing the currently visible time range is drawn behind the
 * labels and doubles as a scrollbar thumb (drag to jump-scroll).
 */
@Composable
private fun TimeRuler(
    durationMs: Int,
    pxPerMs: Float,
    hScroll: androidx.compose.foundation.ScrollState,
    contentWidthDp: androidx.compose.ui.unit.Dp,
    contentWidthPx: Float,
    viewportWidthPx: Float,
    scrollScope: CoroutineScope,
) {
    val density = LocalDensity.current
    val minSpacingPx = with(density) { 56.dp.toPx() }
    val intervalMs = niceIntervalMs(pxPerMs, minSpacingPx)
    val ticks = remember(durationMs, intervalMs) { (0..durationMs step intervalMs).toList() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(TIME_RULER_HEIGHT_DP.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            Box(Modifier.width(contentWidthDp).fillMaxHeight()) {
                ticks.forEach { ms ->
                    val x = (ms * pxPerMs).roundToInt()
                    Text(
                        formatMs(ms),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart).offset { IntOffset(x, 0) }.padding(start = 2.dp),
                    )
                }
            }
        }
        TimelineWindowThumb(hScroll, contentWidthPx, viewportWidthPx, scrollScope)
    }
}

/** A translucent "visible window" rect, drawn over the ruler; dragging it jump-scrolls [hScroll]. */
@Composable
private fun TimelineWindowThumb(
    hScroll: androidx.compose.foundation.ScrollState,
    contentWidthPx: Float,
    viewportWidthPx: Float,
    scrollScope: CoroutineScope,
) {
    val density = LocalDensity.current
    val trackWidthPx = viewportWidthPx.coerceAtLeast(1f)
    val thumbFraction = (viewportWidthPx / contentWidthPx).coerceIn(0.04f, 1f)
    val thumbWidthPx = trackWidthPx * thumbFraction
    val maxScroll = hScroll.maxValue.toFloat()
    val travelPx = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
    val scrollFraction = if (maxScroll > 0f) (hScroll.value / maxScroll).coerceIn(0f, 1f) else 0f
    val thumbOffsetPx = travelPx * scrollFraction

    Box(
        Modifier
            .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
            .width(with(density) { thumbWidthPx.toDp() })
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .pointerInput(maxScroll, travelPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        if (maxScroll > 0f && travelPx > 0f) {
                            val deltaScroll = dx / travelPx * maxScroll
                            scrollScope.launch { hScroll.scrollBy(deltaScroll) }
                        }
                    },
                )
            },
    )
}

@Composable
private fun LayerTrackRow(
    layer: EditableLayer,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    offsetMs: Int,
    maxDurationMs: Int,
    pxPerMs: Float,
    hScroll: androidx.compose.foundation.ScrollState,
    contentWidthDp: androidx.compose.ui.unit.Dp,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    isDropTarget: Boolean,
    isDragSource: Boolean,
    onTrackPositioned: (Offset) -> Unit,
    onEditLayer: () -> Unit,
    onEditStep: (EditableStep) -> Unit,
    onAddBlock: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemoveLayer: () -> Unit,
    onBlockMove: (EditableStep, Float, Float) -> Unit,
    onBlockMoveEnd: (EditableStep, Float, Float) -> Unit,
    onBlockResizeLeft: (EditableStep, Float) -> Unit,
    onBlockResizeRight: (EditableStep, Float) -> Unit,
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().zIndex(if (isDragSource) 1f else 0f)) {
        // Header (full width, not scrolled).
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { layer.enabled = !layer.enabled }, modifier = Modifier.alpha(if (layer.enabled) 1f else 0.5f)) {
                Icon(
                    if (layer.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (layer.enabled) "Hide layer" else "Show layer",
                )
            }
            Text(
                layer.name.ifBlank { "Layer ${index + 1}" },
                style    = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alpha(if (layer.enabled) 1f else 0.5f),
            )
            if (layer.asyncMode != AsyncMode.Off || offsetMs > 0) {
                Text(
                    buildString {
                        if (layer.asyncMode != AsyncMode.Off) append(layer.asyncMode.name.lowercase())
                        if (offsetMs > 0) append(" @${formatMs(offsetMs)}")
                    }.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move layer up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move layer down")
            }
            IconButton(onClick = onEditLayer) {
                Icon(Icons.Default.Edit, contentDescription = "Edit layer")
            }
            IconButton(onClick = onAddBlock) {
                Icon(Icons.Default.Add, contentDescription = "Add block")
            }
            IconButton(onClick = { showRemoveConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove layer")
            }
        }

        // Track (full width, horizontally scrollable canvas).
        val trackBg =
            if (isDropTarget) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT_DP.dp)
                .onGloballyPositioned { onTrackPositioned(it.positionInWindow()) }
                .background(trackBg)
                .horizontalScroll(hScroll),
        ) {
            Box(Modifier.width(contentWidthDp).fillMaxHeight()) {
                layer.placedBlocks().forEach { placed ->
                    BlockView(
                        step          = placed.step,
                        startMs       = placed.startMs + offsetMs,
                        pxPerMs       = pxPerMs,
                        paletteStops  = paletteStops,
                        baseColors    = baseColors,
                        onEdit        = { onEditStep(placed.step) },
                        onMove        = { dx, dy -> onBlockMove(placed.step, dx, dy) },
                        onMoveEnd     = { dx, dy -> onBlockMoveEnd(placed.step, dx, dy) },
                        onResizeLeft  = { dx -> onBlockResizeLeft(placed.step, dx) },
                        onResizeRight = { dx -> onBlockResizeRight(placed.step, dx) },
                    )
                }
                // Scene-end marker: aligned across all layers at the overall scene end.
                val endPx = (maxDurationMs * pxPerMs).roundToInt()
                Box(
                    Modifier
                        .offset { IntOffset(endPx, 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                )
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title            = { Text("Remove layer?") },
            text             = { Text("\"${layer.name.ifBlank { "Layer ${index + 1}" }}\" and all its steps will be removed.") },
            confirmButton    = { TextButton(onClick = { showRemoveConfirm = false; onRemoveLayer() }) { Text("Remove") } },
            dismissButton    = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BlockView(
    step: EditableStep,
    startMs: Int,
    pxPerMs: Float,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onEdit: () -> Unit,
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    onMoveEnd: (dxPx: Float, dyPx: Float) -> Unit,
    onResizeLeft: (dxPx: Float) -> Unit,
    onResizeRight: (dxPx: Float) -> Unit,
) {
    val density = LocalDensity.current

    val baseStartPx = startMs * pxPerMs
    val baseWidthPx = (step.durationMs * pxPerMs).coerceAtLeast(8f)

    // Live visual deltas during a drag (committed once on release).
    var moveDx by remember(step.id) { mutableFloatStateOf(0f) }
    var moveDy by remember(step.id) { mutableFloatStateOf(0f) }
    var leftDx by remember(step.id) { mutableFloatStateOf(0f) }
    var rightDx by remember(step.id) { mutableFloatStateOf(0f) }
    var dragging by remember(step.id) { mutableStateOf(false) }

    val offsetPx = (baseStartPx + moveDx + leftDx).roundToInt()
    val offsetYPx = (with(density) { BLOCK_VERTICAL_INSET_DP.dp.toPx() } + moveDy).roundToInt()
    val widthPx = (baseWidthPx - leftDx + rightDx).coerceAtLeast(8f)
    val widthDp = with(density) { widthPx.toDp() }

    Box(
        Modifier
            .offset { IntOffset(offsetPx, offsetYPx) }
            .height((TRACK_HEIGHT_DP - 2 * BLOCK_VERTICAL_INSET_DP).dp)
            .width(widthDp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (dragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (dragging) 2.dp else 1.dp,
                color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp),
            )
            .pointerInput(step.id) {
                detectTapGestures(onTap = { onEdit() })
            }
            .pointerInput(step.id, pxPerMs) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; moveDx = 0f; moveDy = 0f },
                    onDragEnd   = { dragging = false; onMoveEnd(moveDx, moveDy); moveDx = 0f; moveDy = 0f },
                    onDragCancel = { dragging = false; moveDx = 0f; moveDy = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        moveDx += dragAmount.x
                        moveDy += dragAmount.y
                        onMove(moveDx, moveDy)
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step.anim.colorMode != ColorMode.None) {
                Box(
                    Modifier.size(16.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(colorRefToColor(step.colorA, paletteStops, baseColors))
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
                )
            }
            if (step.anim.colorMode == ColorMode.FromTo) {
                Box(
                    Modifier.size(16.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(colorRefToColor(step.colorB, paletteStops, baseColors))
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
                )
            }
            Text(
                step.anim.display,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Left resize handle.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(14.dp)
                .pointerInput(step.id, pxPerMs) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onResizeLeft(leftDx); leftDx = 0f },
                        onDragCancel = { leftDx = 0f },
                        onHorizontalDrag = { change, dx -> change.consume(); leftDx += dx },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ResizeGrip(MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Right resize handle.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(14.dp)
                .pointerInput(step.id, pxPerMs) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onResizeRight(rightDx); rightDx = 0f },
                        onDragCancel = { rightDx = 0f },
                        onHorizontalDrag = { change, dx -> change.consume(); rightDx += dx },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ResizeGrip(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small vertical-grip indicator drawn inside a block's resize-handle zone. */
@Composable
private fun ResizeGrip(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = 0.5f)),
            )
        }
    }
}

// ── helpers ──────────────────────────────────────────────────────────────────────

private fun niceIntervalMs(pxPerMs: Float, minSpacingPx: Float): Int {
    val rawMs = minSpacingPx / pxPerMs
    val nice = listOf(50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000, 20000, 30000, 60000, 120000, 300000, 600000)
    return nice.firstOrNull { it >= rawMs } ?: nice.last()
}

private fun formatMs(ms: Int): String =
    if (ms < 1000) "${ms}ms"
    else {
        val s = ms / 1000.0
        if (s == s.toLong().toDouble()) "${s.toLong()}s" else "${(s * 10).roundToInt() / 10.0}s"
    }
