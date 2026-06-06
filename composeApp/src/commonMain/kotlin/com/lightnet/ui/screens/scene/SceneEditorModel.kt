package com.lightnet.ui.screens.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PanelTarget
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.SceneLayer
import com.lightnet.api.http.model.SceneStep
import com.lightnet.device.LightnetDevicePanel

// ── Animation metadata ─────────────────────────────────────────────────────────
// Single source of truth that drives the dynamic step form: which colour slots to
// show, which parameters with what friendly labels/ranges, and how the step maps to
// the firmware's SceneStep (`type` vs `runner`, GAP = duration-only).

/** How many colour slots a step exposes. */
enum class ColorMode { None, Single, FromTo }

/** One type-specific parameter (firmware `params[index]`). */
data class ParamSpec(val label: String, val min: Int, val max: Int, val default: Int)

/** Runner directionality source. */
enum class RunnerSrc { Root, Leaves, All, Panel }

enum class AnimId(
    val display: String,
    val isRunner: Boolean,
    val colorMode: ColorMode,
    val params: List<ParamSpec>,
    val wireName: String?,            // value for SceneStep.type / .runner; null = GAP
    val supportsLoopFlags: Boolean = true,
    val widthLabel: String? = null,   // runner band/ring width (rings); null = no width
    val defaultWidth: Int = 0,
) {
    SOLID("Solid", false, ColorMode.Single, emptyList(), "SOLID"),
    FADE("Fade", false, ColorMode.FromTo, emptyList(), "FADE"),
    BREATHE("Breathe", false, ColorMode.FromTo, listOf(ParamSpec("Speed", 0, 255, 0)), "BREATHE"),
    PULSE(
        "Pulse", false, ColorMode.FromTo,
        // Rise/fall are proportions of the total duration, 0–255 (hold = 255 − rise − fall).
        listOf(ParamSpec("Rise", 0, 255, 64), ParamSpec("Fall", 0, 255, 64)), "PULSE",
    ),
    BLINK("Blink", false, ColorMode.FromTo, listOf(ParamSpec("Half-period (ms)", 0, 255, 100)), "BLINK"),
    HUE_CYCLE("Hue cycle", false, ColorMode.None, listOf(ParamSpec("Speed", 0, 255, 25)), "HUE_CYCLE"),
    STROBE("Strobe", false, ColorMode.Single, listOf(ParamSpec("Frequency (Hz)", 1, 30, 8)), "STROBE"),
    REACTIVE("Reactive", false, ColorMode.FromTo, listOf(ParamSpec("Decay", 0, 255, 180)), "REACTIVE"),
    GAP("Gap (hold)", false, ColorMode.None, emptyList(), null, supportsLoopFlags = false),
    WAVE("Wave", true, ColorMode.Single, emptyList(), "WAVE", supportsLoopFlags = false, widthLabel = "Width (rings)", defaultWidth = 3),
    RIPPLE("Ripple", true, ColorMode.Single, emptyList(), "RIPPLE", supportsLoopFlags = false, widthLabel = "Ring width", defaultWidth = 2),
    CHASE("Chase", true, ColorMode.Single, emptyList(), "CHASE", supportsLoopFlags = false),
    ;

    /** Param list seeded to defaults for this animation. */
    fun defaultParams(): List<Int> = params.map { it.default }

    val hasWidth: Boolean get() = widthLabel != null

    companion object {
        val panelTypes: List<AnimId> = entries.filter { !it.isRunner }
        val runnerTypes: List<AnimId> = entries.filter { it.isRunner }

        fun fromStep(step: SceneStep): AnimId {
            step.runner?.let { r -> entries.firstOrNull { it.isRunner && it.wireName == r }?.let { return it } }
            if (step.type == "TRANSITION") return FADE  // firmware alias for FADE
            step.type?.let { t -> entries.firstOrNull { !it.isRunner && it.wireName == t }?.let { return it } }
            return GAP
        }
    }
}

// ── Panel targeting (editor representation) ──────────────────────────────────────
// A flat, Compose-friendly view of a layer's `panels`. The visual picker drives
// [Specific]; role/tag presets drive [Selector]; anything the UI can't model (an
// `exclude`/`any`/`all`/`not` object) is preserved read-only as [Advanced].

enum class TargetKind { All, Specific, Selector, Advanced }

// ── Mutable editor model ────────────────────────────────────────────────────────
// Compose snapshot-state classes so edits recompose in place. Converted to the
// immutable SceneJson on Save/Preview. Each holds a stable [id] for list keys.

private var idCounter = 0L
private fun nextId() = idCounter++

class EditableStep(
    anim: AnimId = AnimId.SOLID,
    colorA: ColorRef = ColorRef.Hex("#FF0000"),
    colorB: ColorRef = ColorRef.Hex("#0000FF"),
    durationMs: Int = 1000,
    loop: Boolean = false,
    pingpong: Boolean = false,
    params: List<Int> = anim.defaultParams(),
    source: RunnerSrc = RunnerSrc.Root,
    sourcePanel: Int = 1,
    reverse: Boolean = false,
    width: Int = anim.defaultWidth,
) {
    val id: Long = nextId()
    var anim by mutableStateOf(anim)
    var colorA by mutableStateOf(colorA)
    var colorB by mutableStateOf(colorB)
    var durationMs by mutableStateOf(durationMs)
    var loop by mutableStateOf(loop)
    var pingpong by mutableStateOf(pingpong)
    var params by mutableStateOf(params)
    // Runner-only fields.
    var source by mutableStateOf(source)
    var sourcePanel by mutableStateOf(sourcePanel)
    var reverse by mutableStateOf(reverse)
    var width by mutableStateOf(width)

    /** Switch animation type, re-seeding params/width to the new type's defaults. */
    fun changeAnim(next: AnimId) {
        anim = next
        params = next.defaultParams()
        width = next.defaultWidth
        if (!next.supportsLoopFlags) { loop = false; pingpong = false }
    }
}

class EditableLayer(
    name: String = "",
    targetKind: TargetKind = TargetKind.All,
    selected: Set<Int> = emptySet(),     // 0-based indices into the device panel list
    selectorToken: String = "leaves",
    rawTarget: PanelTarget? = null,
    palette: String? = null,
    async: Boolean = false,
    startAfter: String? = null,
    fallback: PanelTarget? = null,
    steps: List<EditableStep> = listOf(EditableStep()),
) {
    val id: Long = nextId()
    var name by mutableStateOf(name)
    var targetKind by mutableStateOf(targetKind)
    var selected by mutableStateOf(selected)
    var selectorToken by mutableStateOf(selectorToken)
    var rawTarget by mutableStateOf(rawTarget)
    var palette by mutableStateOf(palette)
    var async by mutableStateOf(async)
    var startAfter by mutableStateOf(startAfter)
    var fallback by mutableStateOf(fallback)  // round-trip passthrough (no UI yet)
    val steps = steps.toMutableStateList()
}

class EditableScene(
    name: String = "",
    loop: Boolean = true,
    speed: Float = 1f,
    palette: String? = null,
    layers: List<EditableLayer> = listOf(EditableLayer(name = "layer1")),
) {
    var name by mutableStateOf(name)
    var loop by mutableStateOf(loop)
    var speed by mutableStateOf(speed)
    var palette by mutableStateOf(palette)
    val layers = layers.toMutableStateList()

    /** A unique auto-name for a freshly added layer. */
    fun nextLayerName(): String {
        var n = layers.size + 1
        val taken = layers.map { it.name }.toSet()
        while ("layer$n" in taken) n++
        return "layer$n"
    }
}

// ── Conversion: editor ⇄ SceneJson ──────────────────────────────────────────────
// The visualizer reports 0-based list positions; the firmware addresses panels from
// 1 via PanelInfo.id. Map through panels[idx].info.id, never by adding 1.

private fun EditableStep.runnerSourceToken(): String? = when (source) {
    RunnerSrc.Root   -> null                 // default — omit
    RunnerSrc.Leaves -> "leaves"
    RunnerSrc.All    -> "all"
    RunnerSrc.Panel  -> "panel:$sourcePanel"
}

private fun EditableStep.toSceneStep(): SceneStep {
    val a = anim
    if (a == AnimId.GAP) return SceneStep(duration = durationMs)
    if (a.isRunner) {
        return SceneStep(
            runner      = a.wireName,
            color       = colorA,
            duration    = durationMs,
            source      = runnerSourceToken(),
            reverse     = if (reverse) true else null,
            waveWidth   = if (a == AnimId.WAVE && a.hasWidth) width else null,
            rippleWidth = if (a == AnimId.RIPPLE && a.hasWidth) width else null,
        )
    }
    return SceneStep(
        type      = a.wireName,
        color     = if (a.colorMode == ColorMode.Single) colorA else null,
        colorFrom = if (a.colorMode == ColorMode.FromTo) colorA else null,
        colorTo   = if (a.colorMode == ColorMode.FromTo) colorB else null,
        duration  = durationMs,
        loop      = if (loop) true else null,
        pingpong  = if (pingpong) true else null,
        params    = a.params.indices.map { params.getOrElse(it) { a.params[it].default } }.ifEmpty { null },
    )
}

private fun EditableLayer.toPanelTarget(panels: List<LightnetDevicePanel>): PanelTarget =
    when (targetKind) {
        TargetKind.All -> PanelTarget.All
        TargetKind.Specific -> PanelTarget.Include(selected.sorted().mapNotNull { panels.getOrNull(it)?.info?.id })
        TargetKind.Selector ->
            selectorToken.trim().let { if (it.isBlank() || it == "all") PanelTarget.All else PanelTarget.Selector(it) }
        TargetKind.Advanced -> rawTarget ?: PanelTarget.All
    }

fun EditableScene.toSceneJson(panels: List<LightnetDevicePanel>): SceneJson = SceneJson(
    name    = name.trim().ifBlank { null },
    loop    = loop,
    speed   = speed,
    palette = palette,
    layers  = layers.map { l ->
        SceneLayer(
            group      = l.name.trim(),
            panels     = l.toPanelTarget(panels),
            palette    = l.palette,
            sequence   = l.steps.map { it.toSceneStep() },
            startAfter = l.startAfter?.trim()?.ifBlank { null },
            async      = if (l.async) true else null,
            fallback   = l.fallback,
        )
    },
)

private fun parseRunnerSource(token: String?): Pair<RunnerSrc, Int> = when {
    token == null || token == "root" -> RunnerSrc.Root to 1
    token == "leaves"                -> RunnerSrc.Leaves to 1
    token == "all"                   -> RunnerSrc.All to 1
    token.startsWith("panel:")       -> RunnerSrc.Panel to (token.removePrefix("panel:").toIntOrNull() ?: 1)
    else                             -> RunnerSrc.Root to 1
}

private fun stepFrom(step: SceneStep): EditableStep {
    val anim = AnimId.fromStep(step)
    val (src, srcPanel) = parseRunnerSource(step.source)
    return EditableStep(
        anim        = anim,
        colorA      = step.color ?: step.colorFrom ?: ColorRef.Hex("#FF0000"),
        colorB      = step.colorTo ?: ColorRef.Hex("#0000FF"),
        durationMs  = step.duration ?: 1000,
        loop        = step.loop == true,
        pingpong    = step.pingpong == true,
        params      = step.params ?: anim.defaultParams(),
        source      = src,
        sourcePanel = srcPanel,
        reverse     = step.reverse == true,
        width       = step.waveWidth ?: step.rippleWidth ?: step.params?.getOrNull(0) ?: anim.defaultWidth,
    )
}

private fun layerFromTarget(target: PanelTarget, panels: List<LightnetDevicePanel>): EditableLayer.() -> Unit = {
    when (target) {
        is PanelTarget.All -> targetKind = TargetKind.All
        is PanelTarget.Include -> {
            targetKind = TargetKind.Specific
            selected = target.indices.mapNotNull { id -> panels.indexOfFirst { it.info.id == id }.takeIf { it >= 0 } }.toSet()
        }
        is PanelTarget.Selector -> {
            targetKind = TargetKind.Selector
            selectorToken = target.token
        }
        is PanelTarget.Exclude, is PanelTarget.Raw -> {
            targetKind = TargetKind.Advanced
            rawTarget = target
        }
    }
}

fun sceneFromJson(json: SceneJson, panels: List<LightnetDevicePanel>): EditableScene = EditableScene(
    name    = json.name ?: "",
    loop    = json.loop ?: true,
    speed   = json.speed ?: 1f,
    palette = json.palette,
    layers  = json.layers.mapIndexed { idx, layer ->
        EditableLayer(
            name       = layer.group.ifBlank { "layer${idx + 1}" },
            palette    = layer.palette,
            async      = layer.async == true,
            startAfter = layer.startAfter,
            fallback   = layer.fallback,
            steps      = layer.sequence.map { stepFrom(it) }.ifEmpty { listOf(EditableStep()) },
        ).apply(layerFromTarget(layer.panels, panels))
    }.ifEmpty { listOf(EditableLayer(name = "layer1")) },
)

// ── Validation ──────────────────────────────────────────────────────────────────

private val sceneNameRegex = Regex("^[A-Za-z0-9_-]{1,18}$")
private val groupNameRegex = Regex("^[A-Za-z0-9_-]{1,15}$")

/** Returns the first validation error message, or null when the scene is valid to save/preview. */
fun EditableScene.validationError(): String? {
    if (!sceneNameRegex.matches(name.trim())) return "Name must be 1–18 chars (letters, digits, - or _)."
    if (layers.isEmpty()) return "Add at least one layer."
    val names = layers.map { it.name.trim() }
    if (names.toSet().size != names.size) return "Layer names must be unique."
    layers.forEachIndexed { li, l ->
        val label = l.name.ifBlank { "Layer ${li + 1}" }
        if (!groupNameRegex.matches(l.name.trim())) return "$label: name must be 1–15 chars (letters, digits, - or _)."
        if (l.steps.isEmpty()) return "$label needs at least one step."
        if (l.targetKind == TargetKind.Specific && l.selected.isEmpty()) return "$label has no panels selected."
        l.startAfter?.trim()?.takeIf { it.isNotBlank() }?.let { dep ->
            if (dep == l.name.trim()) return "$label cannot start after itself."
            if (dep !in names) return "$label: \"$dep\" is not a layer name."
        }
        l.steps.forEachIndexed { si, s ->
            val isLast = si == l.steps.lastIndex
            if (s.durationMs <= 0 && !isLast) return "$label step ${si + 1}: duration must be > 0."
        }
    }
    return null
}
