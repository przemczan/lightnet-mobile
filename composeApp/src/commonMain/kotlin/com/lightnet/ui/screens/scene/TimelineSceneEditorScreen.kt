package com.lightnet.ui.screens.scene

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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.model.PaletteOption
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.SceneDeviceLink
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.device.LightnetDevice
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.device.OfflineSceneService
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.LoadingOverlay
import com.lightnet.ui.components.SpeedSlider
import com.lightnet.ui.components.colorRefToColor
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// ── Timeline scene editor ────────────────────────────────────────────────────────
// Lays the whole scene out as a horizontal timeline: layers = stacked tracks, steps =
// draggable/resizable blocks. Edits an EditableScene model and reuses the existing
// step/layer modal editors, conversion, preview and save logic — see SceneEditorModel.kt.
//
// Time model: free placement with auto-gaps. A block's start = cumulative duration of all
// preceding steps; GAP steps render as empty space, never as blocks. Every edit operates on
// the placed-block view (absolute start times) and rebuilds layer.steps, synthesizing GAPs to
// realize positions. This round-trips through toSceneJson / sceneFromJson with no new model.

private const val TRACK_HEIGHT_DP = 48
private const val BLOCK_VERTICAL_INSET_DP = 3
private const val TIME_RULER_HEIGHT_DP = 36
private const val BLOCK_MIN_MS = 50
private const val DEFAULT_PX_PER_MS = 0.08f
private const val MIN_PX_PER_MS = 0.01f
private const val MIN_PX_PER_MS_FLOOR = 0.0005f / 3f
private const val MAX_PX_PER_MS = 0.5f
private const val CONTENT_TAIL_PX = 800f
private const val TIMELINE_CONTENT_MARGIN_DP = 8
private const val SNAP_DISTANCE_PX = 32f

// startAfter connector arrow. Target and source share the same time boundary, so the link is a
// straight vertical line between their rows.
private const val CONN_STROKE_DP = 2f
private const val CONN_ARROW_DP = 5f      // arrowhead leg length

/** A resolved `startAfter` link: source layer starts at [boundaryMs] when [targetId] (a layer, or a
 *  specific step within it) finishes. [downward] = source row sits below the target row on screen. */
private data class Connector(val sourceId: Long, val targetId: Long, val boundaryMs: Int, val downward: Boolean)

/** One non-gap step placed at an absolute start time on its layer's timeline. */
private data class Placed(val step: EditableStep, val startMs: Int)

/** A floating copy of a block being dragged, rendered in an unclipped overlay so it can cross
 *  into other layers' rows. [topLeftPx] is relative to the timeline canvas's window position. */
private data class DragGhost(
    val step: EditableStep,
    val paletteStops: List<PaletteStop>?,
    val baseColors: List<String>,
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp,
    val topLeftPx: Offset,
)

/** A subtle vertical line + arrowhead drawn from a dragged/resized block's snapped edge to the
 *  row of the element it snapped to. Coordinates are relative to the timeline canvas's window
 *  position, shown only while a snap is active during a drag. */
private data class SnapIndicator(
    val xPx: Float,
    val fromYPx: Float,
    val toYPx: Float,
)

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
    deviceId: String,
    httpClient: DeviceHttpApi?,
    initial: SceneJson?,
    origin: SceneOrigin = SceneOrigin.GLOBAL,
    onBack: () -> Unit,
) {
    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val previewJson = remember { Json { encodeDefaults = false } }

    val snapshot by remember(device) { device?.snapshot ?: MutableStateFlow(null) }.collectAsState()
    val panels = snapshot?.panels ?: emptyList()

    val devicePalettes by remember(device) { device?.palettes ?: MutableStateFlow(null) }.collectAsState()
    val palettesMap = remember(devicePalettes) {
        devicePalettes?.associateBy { it.name } ?: emptyMap()
    }
    var baseColors  by remember { mutableStateOf<List<String>>(emptyList()) }
    var devicePalette by remember { mutableStateOf<String?>(null) }
    var configuration by remember { mutableStateOf<ConfigurationResponse?>(null) }
    LaunchedEffect(device) {
        device?.loadPalettes()
        device?.loadScenes()
    }
    LaunchedEffect(device) {
        val appearance = device?.loadAppearance() ?: device?.cachedAppearance
        baseColors = appearance?.baseColors ?: emptyList()
        devicePalette = appearance?.palette
        configuration = device?.getConfiguration()
    }
    val deviceScenes by remember(device) { device?.scenes ?: MutableStateFlow(null) }.collectAsState()
    val takenSceneNames = remember(deviceScenes) {
        buildSet {
            AppPreferences.scenes.getAll().forEach { s -> s.name?.trim()?.takeIf { it.isNotBlank() }?.let(::add) }
            deviceScenes?.forEach { add(it.name) }
        }
    }
    val paletteOptions = remember(palettesMap) {
        palettesMap.values.map { PaletteOption(it.name) }.sortedBy { it.name }
    }

    val linkedPhoneScene = remember(initial, origin, deviceId) {
        if (origin == SceneOrigin.DEVICE) initial?.id?.let { AppPreferences.scenes.findByDeviceLink(deviceId, it) } else null
    }
    var originalName by remember(initial) { mutableStateOf(initial?.name?.trim()?.takeIf { it.isNotBlank() }) }
    var originalId by remember(initial, origin) {
        mutableStateOf(if (origin == SceneOrigin.DEVICE) initial?.id else null)
    }
    var originalDeviceLinks by remember(initial, origin, linkedPhoneScene) {
        mutableStateOf(
            if (origin == SceneOrigin.GLOBAL) initial?.deviceLinks ?: emptyList()
            else linkedPhoneScene?.deviceLinks ?: emptyList(),
        )
    }
    var originalPhoneSceneName by remember(initial, origin, linkedPhoneScene) {
        mutableStateOf(if (origin == SceneOrigin.DEVICE) linkedPhoneScene?.name else originalName)
    }
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
    var isSaving by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var saveAlsoToOther by remember { mutableStateOf(false) }
    var focusNameOnSettingsOpen by remember { mutableStateOf(false) }

    val activeSceneForTracking = scene
    LaunchedEffect(activeSceneForTracking, panels.isEmpty()) {
        if (activeSceneForTracking == null || panels.isEmpty()) return@LaunchedEffect
        val initialJson = activeSceneForTracking.toSceneJson(panels)
        snapshotFlow { activeSceneForTracking.toSceneJson(panels) }
            .drop(1)
            .collect { current -> isDirty = current != initialJson }
    }

    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Local, controller-free preview: runs the shared scene engine in-process and renders its
    // packets through the same per-panel players the live mirror uses (OfflineSceneService).
    val offlineService = remember { OfflineSceneService(cleanupScope) }
    DisposableEffect(offlineService) { onDispose { offlineService.close() } }
    val offlineStates by offlineService.states.collectAsState()
    val offlineError by offlineService.error.collectAsState()

    LaunchedEffect(panels, configuration) {
        if (panels.isEmpty()) return@LaunchedEffect
        offlineService.setTopology(panels.map { it.info }, configuration?.logicalRoot ?: 0)
    }
    LaunchedEffect(palettesMap) {
        offlineService.clearPalettes()
        palettesMap.forEach { (id, palette) -> offlineService.registerPalette(id, palette.stops) }
    }

    fun stopPreview() {
        offlineService.stop()
    }

    var showPreviewModal by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneNameDraft by remember { mutableStateOf("") }
    var snapMode by remember { mutableStateOf(true) }
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
        (layer.palette ?: activeScene.palette ?: devicePalette)?.let { palettesMap[it]?.stops }

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
                    paletteOptions = paletteOptions,
                    paletteStops = stopsFor(e.layer),
                    baseColors   = baseColors,
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

    fun performSave(alsoSaveToOther: Boolean) {
        activeScene.clearUnusedStepIds()
        val sceneJson = activeScene.toSceneJson(panels)
        val renamed = originalName != null && originalName != sceneJson.name
        when (origin) {
            SceneOrigin.GLOBAL -> {
                isSaving = true
                scope.launch {
                    var links = originalDeviceLinks
                    if (alsoSaveToOther && httpClient != null) {
                        val existingLink = links.find { it.deviceId == deviceId }
                        val saved = httpClient.runCatching { saveScene(sceneJson.copy(id = existingLink?.deviceSceneId)) }
                        if (saved.isFailure) {
                            snackbar.showSnackbar("Failed to save to device; saved locally only.")
                        } else {
                            links = links.filterNot { it.deviceId == deviceId } + SceneDeviceLink(deviceId, saved.getOrThrow())
                            device?.refreshPalettes()
                            device?.refreshScenes()
                        }
                    }
                    val ok = runCatching { AppPreferences.scenes.save(sceneJson.copy(id = null, deviceLinks = links)) }.isSuccess
                    if (!ok) {
                        isSaving = false
                        snackbar.showSnackbar("Failed to save scene.")
                        return@launch
                    }
                    if (renamed) AppPreferences.scenes.delete(originalName!!)
                    isDirty = false
                    onBack()
                }
            }
            SceneOrigin.DEVICE -> {
                if (httpClient == null) {
                    scope.launch { snackbar.showSnackbar("Connect a device to save.") }
                    return
                }
                isSaving = true
                scope.launch {
                    val saved = httpClient.runCatching { saveScene(sceneJson.copy(id = originalId)) }
                    if (saved.isFailure) {
                        isSaving = false
                        snackbar.showSnackbar("Failed to save scene to device.")
                        return@launch
                    }
                    val newDeviceSceneId = saved.getOrThrow()
                    isDirty = false
                    device?.refreshPalettes()
                    device?.refreshScenes()
                    if (alsoSaveToOther) {
                        val links = originalDeviceLinks.filterNot { it.deviceId == deviceId } +
                            SceneDeviceLink(deviceId, newDeviceSceneId)
                        val ok = runCatching {
                            AppPreferences.scenes.save(sceneJson.copy(id = null, deviceLinks = links))
                        }.isSuccess
                        if (!ok) {
                            snackbar.showSnackbar("Saved to device but failed to save locally.")
                        } else if (originalPhoneSceneName != null && originalPhoneSceneName != sceneJson.name) {
                            AppPreferences.scenes.delete(originalPhoneSceneName!!)
                        }
                    }
                    onBack()
                }
            }
        }
    }

    fun requestSave() {
        activeScene.nameValidationError()?.let {
            focusNameOnSettingsOpen = true
            showOptionsSheet = true
            return
        }
        val err = activeScene.validationError()
        if (err != null) {
            scope.launch { snackbar.showSnackbar(err) }
            return
        }
        saveAlsoToOther = false
        showSaveConfirm = true
    }

    Box(Modifier.fillMaxSize()) {
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
                        cloneNameDraft = suggestCloneSceneName(
                            activeScene.name.ifBlank { "Scene" },
                            takenSceneNames,
                        )
                        showCloneDialog = true
                    }) { Text("Clone") }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(
                        onClick = {
                            val err = activeScene.validationError(requireName = false)
                            if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@IconButton }
                            val json = previewJson.encodeToString(
                                SceneJson.serializer(),
                                activeScene.toPreviewSceneJson(panels, devicePalette, baseColors),
                            )
                            offlineService.play(json)
                            showPreviewModal = true
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview", modifier = Modifier.size(32.dp))
                    }
                },
                floatingActionButton = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showOptionsSheet = true }, enabled = !isSaving) {
                            Icon(Icons.Default.Tune, contentDescription = "Scene options")
                        }
                        ExtendedFloatingActionButton(onClick = { if (!isSaving) requestSave() }) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val contentMarginPx = with(density) { TIMELINE_CONTENT_MARGIN_DP.dp.toPx() }
        Column(Modifier.fillMaxSize()) {
            val usableWidthPx = (viewportWidthPx - 2 * contentMarginPx).coerceAtLeast(0f)
            val fitPxPerMs = if (usableWidthPx > 0f) {
                (usableWidthPx / maxDurationMs(activeScene.layers)).coerceIn(MIN_PX_PER_MS_FLOOR, MAX_PX_PER_MS)
            } else {
                MIN_PX_PER_MS_FLOOR
            }
            // The slider must be able to reach the fit level, even if that's more zoomed-out
            // than the "first 10 steps fill the viewport" heuristic.
            val dynamicMinPxPerMs = minOf(minPxPerMsFor(activeScene.layers, usableWidthPx), fitPxPerMs)

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
                snapMode    = snapMode,
                onSnapModeChange = { snapMode = it },
            )

            SceneTimeline(
                scene        = activeScene,
                pxPerMs      = pxPerMs,
                hScroll      = hScroll,
                baseColors   = baseColors,
                stopsFor     = ::stopsFor,
                snapMode     = snapMode,
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
        LoadingOverlay(visible = isSaving)
    }

    if (showCloneDialog) {
        CloneSceneNameDialog(
            initialName = cloneNameDraft,
            takenNames  = takenSceneNames,
            onConfirm   = { name ->
                showCloneDialog = false
                scene = activeScene.clone(name)
                // A clone is a brand-new scene with no identity of its own — without this reset
                // it would inherit the original's device links and PATCH the original's device scene.
                originalName = null
                originalId = null
                originalDeviceLinks = emptyList()
                originalPhoneSceneName = null
                isDirty = true
            },
            onDismiss = { showCloneDialog = false },
        )
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

    if (showSaveConfirm) {
        val alsoSaveLabel = when (origin) {
            SceneOrigin.GLOBAL -> "Also save to device"
            SceneOrigin.DEVICE -> "Also save to Global"
        }
        val alsoSaveEnabled = when (origin) {
            SceneOrigin.GLOBAL -> httpClient != null
            SceneOrigin.DEVICE -> true
        }
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title            = { Text("Save scene?") },
            text             = {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = alsoSaveEnabled) { saveAlsoToOther = !saveAlsoToOther },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked         = saveAlsoToOther,
                            onCheckedChange = { saveAlsoToOther = it },
                            enabled         = alsoSaveEnabled,
                        )
                        Column {
                            Text(alsoSaveLabel, style = MaterialTheme.typography.bodyMedium)
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
            },
            confirmButton    = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    performSave(saveAlsoToOther)
                }) { Text("OK") }
            },
            dismissButton    = { TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showOptionsSheet) {
        ModalBottomSheet(onDismissRequest = { showOptionsSheet = false }) {
            SceneOptionsContent(
                scene                   = activeScene,
                paletteOptions          = paletteOptions,
                requestNameFocus        = focusNameOnSettingsOpen,
                onNameFocusHandled      = { focusNameOnSettingsOpen = false },
            )
        }
    }

    if (showPreviewModal) {
        Dialog(onDismissRequest = { stopPreview(); showPreviewModal = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    LightnetDeviceVisualizer(
                        panels         = panels,
                        modifier       = Modifier.fillMaxWidth().height(320.dp),
                        interactive    = false,
                        overrideStates = offlineStates,
                    )
                    offlineError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
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

@Composable
private fun CloneSceneNameDialog(
    initialName: String,
    takenNames: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val error = sceneCloneNameValidationError(trimmed, takenNames)

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Clone scene") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a name for the copy.")
                TextField(
                    value          = name,
                    onValueChange  = { name = sanitizeSceneName(it) },
                    label          = { Text("Name") },
                    singleLine     = true,
                    isError        = error != null,
                    supportingText = error?.let { err -> { Text(err) } },
                    modifier       = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(trimmed) },
                enabled  = error == null,
            ) { Text("Clone") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Scene-wide options: name, loop, speed, default palette, background. */
@Composable
private fun SceneOptionsContent(
    scene: EditableScene,
    paletteOptions: List<PaletteOption>,
    requestNameFocus: Boolean,
    onNameFocusHandled: () -> Unit,
) {
    val nameFocusRequester = remember { FocusRequester() }
    val nameError = scene.nameValidationError()
    LaunchedEffect(requestNameFocus) {
        if (requestNameFocus) {
            delay(350)
            nameFocusRequester.requestFocus()
            onNameFocusHandled()
        }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Scene options",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TextField(
            value         = scene.name,
            onValueChange = { scene.name = sanitizeSceneName(it) },
            label         = { Text("Name") },
            singleLine    = true,
            isError       = nameError != null,
            supportingText = nameError?.let { err -> { Text(err) } },
            modifier      = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))
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
            options  = paletteOptions,
            onSelect = { scene.palette = it },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        HorizontalDivider()
        BackgroundColorRow(
            hex      = scene.background,
            onChange = { scene.background = it },
        )
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun ZoomControls(
    pxPerMs: Float,
    minPxPerMs: Float,
    onPxPerMs: (Float) -> Unit,
    onFit: () -> Unit,
    snapMode: Boolean,
    onSnapModeChange: (Boolean) -> Unit,
) {
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
        FilledIconToggleButton(
            checked         = snapMode,
            onCheckedChange = onSnapModeChange,
        ) {
            Icon(
                Icons.Default.GridOn,
                contentDescription = if (snapMode) "Disable snapping" else "Enable snapping",
            )
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
    snapMode: Boolean,
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
    val contentMarginDp = TIMELINE_CONTENT_MARGIN_DP.dp
    val contentWidthPx = maxDurationMs * pxPerMs + CONTENT_TAIL_PX + 2 * with(density) { contentMarginDp.toPx() }
    val contentWidthDp = with(density) { contentWidthPx.toDp() }
    val snapToleranceMs = (SNAP_DISTANCE_PX / pxPerMs).roundToInt().coerceAtLeast(1)

    // startAfter connector arrows: capture each track's window position so a fixed overlay can
    // draw meandering links from a target layer/step end into the dependent layer's first step.
    val trackPos = remember { mutableStateMapOf<Long, Offset>() }
    var canvasPos by remember { mutableStateOf<Offset?>(null) }
    var trackAreaTopPx by remember { mutableStateOf(0f) }

    // Cross-layer drag session: how many rows the finger has moved from the source layer.
    // Measured from actual track positions (header + track + divider), not just TRACK_HEIGHT_DP,
    // since the header adds extra height per row.
    val fallbackRowHeightPx = with(density) { TRACK_HEIGHT_DP.dp.toPx() }
    val rowHeightPx = if (scene.layers.size >= 2) {
        val y0 = trackPos[scene.layers[0].id]?.y
        val y1 = trackPos[scene.layers[1].id]?.y
        if (y0 != null && y1 != null && y1 != y0) kotlin.math.abs(y1 - y0) else fallbackRowHeightPx
    } else fallbackRowHeightPx
    var dragSourceIndex by remember { mutableStateOf<Int?>(null) }
    var dragRowDelta by remember { mutableFloatStateOf(0f) }
    val dropTargetIndex: Int? = dragSourceIndex?.let { src ->
        (src + (dragRowDelta / rowHeightPx).roundToInt()).coerceIn(0, scene.layers.lastIndex)
    }

    // Floating "ghost" of the block currently being dragged, drawn in an unclipped overlay so it
    // stays visible above other layers' tracks (the per-track horizontalScroll clips overflow).
    var dragGhost by remember { mutableStateOf<DragGhost?>(null) }

    // Subtle line + arrow pointing at the element a block is currently snapped to.
    var snapIndicator by remember { mutableStateOf<SnapIndicator?>(null) }
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
        Column(Modifier.fillMaxSize()) {
            TimeRuler(maxDurationMs, pxPerMs, hScroll, contentWidthDp, contentMarginDp, contentWidthPx, viewportWidthPx, scrollScope)
            HorizontalDivider()

            Column(
                Modifier.onGloballyPositioned { trackAreaTopPx = it.positionInWindow().y - (canvasPos?.y ?: 0f) }
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
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
                    contentMarginDp = contentMarginDp,
                    paletteStops   = stopsFor(layer),
                    baseColors     = baseColors,
                    isDropTarget   = dropTargetIndex == i && dropTargetIndex != dragSourceIndex,
                    isDragSource   = dragSourceIndex == i,
                    canvasPos      = canvasPos,
                    snapMode       = snapMode,
                    snapTargetsFor = { step -> snapTargets(scene.layers, layerOffsets, step) },
                    snapToleranceMs = snapToleranceMs,
                    trackPos       = trackPos,
                    onGhostChange  = { dragGhost = it },
                    onSnapIndicatorChange = { snapIndicator = it },
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
                        var absoluteNew = base + sourceOffset + deltaMs
                        if (snapMode) {
                            val points = snapPoints(scene.layers, layerOffsets, step)
                            absoluteNew = snapBlockEdges(absoluteNew, step.durationMs, points, snapToleranceMs)
                        }
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
                        var newStart = (p.startMs + deltaMs).coerceIn(0, end - BLOCK_MIN_MS)
                        if (snapMode) {
                            val sourceOffset = layerOffsets[layer.id] ?: 0
                            val points = snapPoints(scene.layers, layerOffsets, step)
                            val snapped = points.minByOrNull { kotlin.math.abs(it - (newStart + sourceOffset)) }
                                ?.takeIf { kotlin.math.abs(it - (newStart + sourceOffset)) <= snapToleranceMs }
                            if (snapped != null) newStart = (snapped - sourceOffset).coerceIn(0, end - BLOCK_MIN_MS)
                        }
                        step.durationMs = end - newStart
                        layer.applyPlaced(placed.map { if (it.step === step) Placed(step, newStart) else it })
                    },
                    onBlockResizeRight = { step, dxPx ->
                        val placed = layer.placedBlocks()
                        val p = placed.firstOrNull { it.step === step } ?: return@LayerTrackRow
                        val deltaMs = (dxPx / pxPerMs).roundToInt()
                        var newDuration = (step.durationMs + deltaMs).coerceAtLeast(BLOCK_MIN_MS)
                        if (snapMode) {
                            val sourceOffset = layerOffsets[layer.id] ?: 0
                            val points = snapPoints(scene.layers, layerOffsets, step)
                            val absEnd = p.startMs + sourceOffset + newDuration
                            val snapped = points.minByOrNull { kotlin.math.abs(it - absEnd) }
                                ?.takeIf { kotlin.math.abs(it - absEnd) <= snapToleranceMs }
                            if (snapped != null) newDuration = (snapped - sourceOffset - p.startMs).coerceAtLeast(BLOCK_MIN_MS)
                        }
                        step.durationMs = newDuration
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
        }

        ConnectorArrows(
            connectors = connectors,
            trackPos   = trackPos,
            canvasPos  = canvasPos,
            pxPerMs    = pxPerMs,
            scrollX    = hScroll.value.toFloat(),
            color      = connColor,
            clipTopPx  = trackAreaTopPx,
            modifier   = Modifier.matchParentSize(),
        )

        snapIndicator?.let { indicator ->
            val color = MaterialTheme.colorScheme.primary
            Canvas(Modifier.matchParentSize()) {
                clipRect(top = trackAreaTopPx) {
                val arrow = 4.dp.toPx()
                val dir = if (indicator.toYPx >= indicator.fromYPx) 1f else -1f
                val tip = Offset(indicator.xPx, indicator.toYPx)
                drawLine(
                    color       = color.copy(alpha = 0.6f),
                    start       = Offset(indicator.xPx, indicator.fromYPx),
                    end         = tip,
                    strokeWidth = 1.5.dp.toPx(),
                    cap         = StrokeCap.Round,
                    pathEffect  = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                drawPath(
                    path = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(tip.x - arrow, tip.y - dir * arrow)
                        lineTo(tip.x + arrow, tip.y - dir * arrow)
                        close()
                    },
                    color = color.copy(alpha = 0.6f),
                )
                }
            }
        }

        dragGhost?.let { ghost ->
            Box(
                Modifier
                    .offset { IntOffset(ghost.topLeftPx.x.roundToInt(), ghost.topLeftPx.y.roundToInt()) }
                    .width(ghost.width)
                    .height(ghost.height)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
            ) {
                BlockContent(ghost.step, ghost.paletteStops, ghost.baseColors)
            }
        }
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
    clipTopPx: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clipToBounds()) {
        val origin = canvasPos ?: return@Canvas
        val rowH   = TRACK_HEIGHT_DP.dp.toPx()
        val inset  = BLOCK_VERTICAL_INSET_DP.dp.toPx()
        val arrow  = CONN_ARROW_DP.dp.toPx()
        val stroke = CONN_STROKE_DP.dp.toPx()
        val margin = TIMELINE_CONTENT_MARGIN_DP.dp.toPx()

        clipRect(top = clipTopPx) {
        connectors.forEach { c ->
            val sPos = trackPos[c.sourceId] ?: return@forEach
            val tPos = trackPos[c.targetId] ?: return@forEach
            val x = (tPos.x - origin.x) + margin + c.boundaryMs * pxPerMs - scrollX

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
}

/** Absolute-timeline edges (block starts and ends) of every step, used as snap targets for
 *  drags/resizes. Each point is paired with the id of the layer it belongs to (`null` for the
 *  timeline origin) so a snap indicator can point at the matched row. */
private fun snapTargets(layers: List<EditableLayer>, layerOffsets: Map<Long, Int>, exclude: EditableStep?): List<Pair<Int, Long?>> {
    val points = mutableListOf<Pair<Int, Long?>>(0 to null)
    layers.forEach { layer ->
        val offset = layerOffsets[layer.id] ?: 0
        layer.placedBlocks().forEach { p ->
            if (p.step === exclude) return@forEach
            points += (offset + p.startMs) to layer.id
            points += (offset + p.startMs + p.step.durationMs) to layer.id
        }
    }
    return points
}

/** Absolute-timeline edges (block starts and ends) of every step, used as snap targets for drags/resizes. */
private fun snapPoints(layers: List<EditableLayer>, layerOffsets: Map<Long, Int>, exclude: EditableStep?): List<Int> =
    snapTargets(layers, layerOffsets, exclude).map { it.first }

/** Snaps either edge of a [durationMs]-wide block at [startMs] to the nearest [points] within [toleranceMs], preferring whichever edge moves least. */
private fun snapBlockEdges(startMs: Int, durationMs: Int, points: List<Int>, toleranceMs: Int): Int {
    fun nearest(value: Int): Int? =
        points.minByOrNull { kotlin.math.abs(it - value) }?.takeIf { kotlin.math.abs(it - value) <= toleranceMs }

    val endMs = startMs + durationMs
    val startSnap = nearest(startMs)
    val endSnap = nearest(endMs)
    return when {
        startSnap != null && endSnap != null ->
            if (kotlin.math.abs(startSnap - startMs) <= kotlin.math.abs(endSnap - endMs)) startSnap else endSnap - durationMs
        startSnap != null -> startSnap
        endSnap != null -> endSnap - durationMs
        else -> startMs
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
    contentMarginDp: androidx.compose.ui.unit.Dp,
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
            Box(Modifier.width(contentWidthDp).fillMaxHeight().padding(horizontal = contentMarginDp)) {
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
    contentMarginDp: androidx.compose.ui.unit.Dp,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    isDropTarget: Boolean,
    isDragSource: Boolean,
    canvasPos: Offset?,
    snapMode: Boolean,
    snapTargetsFor: (EditableStep) -> List<Pair<Int, Long?>>,
    snapToleranceMs: Int,
    trackPos: Map<Long, Offset>,
    onGhostChange: (DragGhost?) -> Unit,
    onSnapIndicatorChange: (SnapIndicator?) -> Unit,
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
    var menuExpanded by remember { mutableStateOf(false) }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.alpha(if (layer.enabled) 1f else 0.5f),
            ) {
                LayerAsyncChip(layer = layer)
                LayerBlendChip(layer = layer)
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move layer up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move layer down")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Layer options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit layer") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditLayer() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add block") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { menuExpanded = false; onAddBlock() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove layer") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; showRemoveConfirm = true },
                    )
                }
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
            Box(Modifier.width(contentWidthDp).fillMaxHeight().padding(horizontal = contentMarginDp)) {
                layer.placedBlocks().forEach { placed ->
                    BlockView(
                        step          = placed.step,
                        startMs       = placed.startMs + offsetMs,
                        pxPerMs       = pxPerMs,
                        paletteStops  = paletteStops,
                        baseColors    = baseColors,
                        canvasPos     = canvasPos,
                        snapMode      = snapMode,
                        snapTargetsFor = snapTargetsFor,
                        snapToleranceMs = snapToleranceMs,
                        trackPos      = trackPos,
                        onGhostChange = onGhostChange,
                        onSnapIndicatorChange = onSnapIndicatorChange,
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

/** Color swatches + label shown inside a block (and its drag ghost). */
@Composable
private fun BoxScope.BlockContent(step: EditableStep, paletteStops: List<PaletteStop>?, baseColors: List<String>) {
    Row(
        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.anim.colorMode != ColorMode.None) {
            Spacer(Modifier.width(2.dp))
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
}

@Composable
private fun BlockView(
    step: EditableStep,
    startMs: Int,
    pxPerMs: Float,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    canvasPos: Offset?,
    snapMode: Boolean,
    snapTargetsFor: (EditableStep) -> List<Pair<Int, Long?>>,
    snapToleranceMs: Int,
    trackPos: Map<Long, Offset>,
    onGhostChange: (DragGhost?) -> Unit,
    onSnapIndicatorChange: (SnapIndicator?) -> Unit,
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

    // detectDragGesturesAfterLongPress/detectHorizontalDragGestures loop across multiple gesture
    // sessions within the same pointerInput launch (keyed on step.id/pxPerMs only), so values that
    // can change between sessions without relaunching the gesture must be read via
    // rememberUpdatedState — otherwise later sessions see the values captured at first launch.
    val currentStartMs by rememberUpdatedState(startMs)
    val currentCanvasPos by rememberUpdatedState(canvasPos)
    val currentTrackPos by rememberUpdatedState(trackPos)
    val currentSnapMode by rememberUpdatedState(snapMode)
    val currentSnapTargetsFor by rememberUpdatedState(snapTargetsFor)
    val currentSnapToleranceMs by rememberUpdatedState(snapToleranceMs)

    /** Result of snapping a raw drag delta: the (possibly adjusted) pixel delta, and — if a snap
     *  point matched — the absolute ms and owning layer id of the matched edge. */
    data class SnapResult(val deltaPx: Float, val matchedLayerId: Long?)

    /** Snaps [rawDeltaPx] so that the edge currently at [edgeMs] lands on the nearest snap point
     *  within tolerance, falling back to the raw delta when nothing is close enough. */
    fun snapResult(rawDeltaPx: Float, edgeMs: Int): SnapResult {
        // Only snap while actively dragging; at rest (rawDeltaPx == 0) snapping to a nearby
        // edge would visibly shift this block's width/position whenever snap mode toggles.
        if (!currentSnapMode || rawDeltaPx == 0f) return SnapResult(rawDeltaPx, null)
        val candidateMs = edgeMs + (rawDeltaPx / pxPerMs).roundToInt()
        val match = currentSnapTargetsFor(step)
            .minByOrNull { kotlin.math.abs(it.first - candidateMs) }
            ?.takeIf { kotlin.math.abs(it.first - candidateMs) <= currentSnapToleranceMs }
        return if (match != null) SnapResult((match.first - edgeMs) * pxPerMs, match.second) else SnapResult(rawDeltaPx, null)
    }

    fun snapDeltaPx(rawDeltaPx: Float, edgeMs: Int): Float = snapResult(rawDeltaPx, edgeMs).deltaPx

    /** Re-reads [step.durationMs] (mutated in place by other gestures) so gesture closures from a
     *  stale pointerInput session — which captured [baseWidthPx]/[widthPx] at launch time — don't
     *  use a width from before a resize that happened in a different session. */
    fun currentWidthPx(leftDeltaPx: Float, rightDeltaPx: Float): Float =
        ((step.durationMs * pxPerMs).coerceAtLeast(8f) -
            snapDeltaPx(leftDeltaPx, currentStartMs) + snapDeltaPx(rightDeltaPx, currentStartMs + step.durationMs)
        ).coerceAtLeast(8f)

    // Position stays static while move-dragging; only the floating ghost (in the unclipped
    // overlay) follows the finger, since this block's own track clips vertical overflow.
    val offsetPx = (baseStartPx + snapDeltaPx(leftDx, startMs)).roundToInt()
    val offsetYPx = with(density) { BLOCK_VERTICAL_INSET_DP.dp.toPx() }.roundToInt()
    val widthPx = (baseWidthPx - snapDeltaPx(leftDx, startMs) + snapDeltaPx(rightDx, startMs + step.durationMs)).coerceAtLeast(8f)
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = (TRACK_HEIGHT_DP - 2 * BLOCK_VERTICAL_INSET_DP).dp
    val heightPx = with(density) { heightDp.toPx() }
    val rowHPx = with(density) { TRACK_HEIGHT_DP.dp.toPx() }
    val insetPx = with(density) { BLOCK_VERTICAL_INSET_DP.dp.toPx() }

    var blockWindowPos by remember { mutableStateOf(Offset.Zero) }

    fun updateGhost() {
        val origin = currentCanvasPos ?: return
        onGhostChange(
            DragGhost(
                step         = step,
                paletteStops = paletteStops,
                baseColors   = baseColors,
                width        = with(density) { currentWidthPx(leftDx, rightDx).toDp() },
                height       = heightDp,
                topLeftPx    = blockWindowPos - origin + Offset(snapDeltaPx(moveDx, currentStartMs), moveDy),
            ),
        )
    }

    /** Shows a line+arrow from this block's near edge to the matching corner of the row
     *  [result] snapped against, or hides it if nothing matched. Mirrors the corner geometry
     *  used by [ConnectorArrows] for startAfter links. */
    fun updateSnapIndicator(result: SnapResult, anchorXPx: Float) {
        val origin = currentCanvasPos
        val layerId = result.matchedLayerId
        val targetTop = layerId?.let { currentTrackPos[it] }
        if (origin == null || targetTop == null) {
            onSnapIndicatorChange(null)
            return
        }
        val downward = targetTop.y > blockWindowPos.y
        val fromY = if (downward) blockWindowPos.y - origin.y + heightPx else blockWindowPos.y - origin.y
        val toY = if (downward) targetTop.y - origin.y + insetPx else targetTop.y - origin.y + rowHPx - insetPx
        onSnapIndicatorChange(
            SnapIndicator(
                xPx     = anchorXPx,
                fromYPx = fromY,
                toYPx   = toY,
            ),
        )
    }

    Box(
        Modifier
            .offset { IntOffset(offsetPx, offsetYPx) }
            .onGloballyPositioned { blockWindowPos = it.positionInWindow() }
            .height(heightDp)
            .width(widthDp)
            .alpha(if (dragging) 0.4f else 1f)
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
                    onDragStart = { dragging = true; moveDx = 0f; moveDy = 0f; updateGhost() },
                    onDragEnd   = {
                        dragging = false
                        onMoveEnd(snapDeltaPx(moveDx, currentStartMs), moveDy)
                        moveDx = 0f; moveDy = 0f
                        onGhostChange(null)
                        onSnapIndicatorChange(null)
                    },
                    onDragCancel = {
                        dragging = false; moveDx = 0f; moveDy = 0f
                        onGhostChange(null)
                        onSnapIndicatorChange(null)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        moveDx += dragAmount.x
                        moveDy += dragAmount.y
                        val result = snapResult(moveDx, currentStartMs)
                        onMove(result.deltaPx, moveDy)
                        updateGhost()
                        val origin = currentCanvasPos
                        if (origin != null) {
                            updateSnapIndicator(result, blockWindowPos.x - origin.x + result.deltaPx)
                        }
                    },
                )
            },
    ) {
        BlockContent(step, paletteStops, baseColors)

        // Left resize handle.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(14.dp)
                .pointerInput(step.id, pxPerMs) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onResizeLeft(snapDeltaPx(leftDx, currentStartMs)); leftDx = 0f; onSnapIndicatorChange(null) },
                        onDragCancel = { leftDx = 0f; onSnapIndicatorChange(null) },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            leftDx += dx
                            val result = snapResult(leftDx, currentStartMs)
                            val origin = currentCanvasPos
                            if (origin != null) updateSnapIndicator(result, blockWindowPos.x - origin.x)
                        },
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
                        onDragEnd = { onResizeRight(snapDeltaPx(rightDx, currentStartMs + step.durationMs)); rightDx = 0f; onSnapIndicatorChange(null) },
                        onDragCancel = { rightDx = 0f; onSnapIndicatorChange(null) },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            rightDx += dx
                            val result = snapResult(rightDx, currentStartMs + step.durationMs)
                            val origin = currentCanvasPos
                            if (origin != null) updateSnapIndicator(result, blockWindowPos.x - origin.x + currentWidthPx(0f, rightDx))
                        },
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
