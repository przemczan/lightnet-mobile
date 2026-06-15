package com.lightnet.ui.screens.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.lightnet.api.http.model.AnimateTarget
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PanelTarget
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.SceneLayer
import com.lightnet.api.http.model.SceneStep
import com.lightnet.device.LightnetDevicePanel

enum class SceneOrigin { GLOBAL, DEVICE }

// ── Animation metadata ─────────────────────────────────────────────────────────
// Single source of truth that drives the dynamic step form: which colour slots to
// show, which parameters with what friendly labels/ranges, and how the step maps to
// the firmware's SceneStep (`type` vs `runner`, GAP = duration-only).

/** How many colour slots a step exposes. */
enum class ColorMode { None, Single, FromTo }

/** One type-specific parameter (firmware `params[index]`). */
data class ParamSpec(val label: String, val min: Int, val max: Int, val default: Int)

/** Where a runner emanates from (independent of directionality mode). */
enum class RunnerSrc { Root, Leaves, All, Panel }

/** What an animation step modulates (its own colour, or a scalar modifier property below it). */
enum class Animates { Color, Dim, Brighten, Desaturate, Saturate, Hue, Invert }

/** Layer async mode: Off = sync, Loop = loops independently (holds scene), Free = loops independently (scene ignores it). */
enum class AsyncMode { Off, Loop, Free }

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
    // Always loops, always geometric — pivots about `source`; supports non-color `animates` (blade modifier).
    WHEEL("Wheel", true, ColorMode.Single, emptyList(), "WHEEL", supportsLoopFlags = false),
    // Single band, bounces back and forth forever; `repeat`/`repeatCount` ignored.
    BOUNCE("Bounce", true, ColorMode.Single, emptyList(), "BOUNCE", supportsLoopFlags = false, widthLabel = "Width (rings)", defaultWidth = 3),
    // Comet train of drops; always loops. `repeatCount` (wire: `waves`) = drops in flight.
    // Default a few rings of tail so a fresh drop reads as a comet, not a tailless blip.
    RAIN("Rain", true, ColorMode.Single, emptyList(), "RAIN", supportsLoopFlags = false, widthLabel = "Tail length (rings)", defaultWidth = 3),
    // Per-panel random flicker; no directionality. `repeatCount` (wire: `waves`) = flicker density.
    // Default a clearly-visible fade (~0.31 of the period) so "instant-on, fade-out" shows by default.
    SPARKLE("Sparkle", true, ColorMode.Single, emptyList(), "SPARKLE", supportsLoopFlags = false, widthLabel = "Fade-out duration", defaultWidth = 80),
    // Like RAIN but straight, constant-speed columns (digital-rain). Geometric-only.
    MATRIX("Matrix", true, ColorMode.Single, emptyList(), "MATRIX", supportsLoopFlags = false, widthLabel = "Tail length (rings)", defaultWidth = 3),
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
    geometric: Boolean = false,
    source: RunnerSrc = RunnerSrc.Root,
    sourcePanel: Int = 1,
    angle: Int = 0,
    reverse: Boolean = false,
    width: Int = anim.defaultWidth,
    animates: Animates = Animates.Color,
    amount: Int = 128,
    valueFrom: Int = 255,
    valueTo: Int = 64,
    density: Int = 0,
    repeatCount: Int = 1,
    lines: Int = 1,
    thickness: Int = 18,
    speedMs: Int = 0,
    stepId: String? = null,
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
    var geometric by mutableStateOf(geometric) // true = geometric sweep, false = topology
    var source by mutableStateOf(source)        // emanation origin (independent of directionality)
    var sourcePanel by mutableStateOf(sourcePanel)
    var angle by mutableStateOf(angle)          // geometric sweep direction, degrees [0,360)
    var reverse by mutableStateOf(reverse)
    var width by mutableStateOf(width)
    var animates by mutableStateOf(animates)    // what this animation modulates
    var amount by mutableStateOf(amount)        // runner-only: peak intensity for non-Color targets, 0-255
    var valueFrom by mutableStateOf(valueFrom)  // panel-local non-Color: scalar ramp start, 0-255
    var valueTo by mutableStateOf(valueTo)      // panel-local non-Color: scalar ramp end, 0-255
    // WAVE/RIPPLE/CHASE: spawn density (0-255) of the continuous sweep train; 0 = one sweep in
    // flight at a time, gapless; 255 = max concurrent sweeps.
    var density by mutableStateOf(density)
    var repeatCount by mutableStateOf(repeatCount) // RAIN/SPARKLE/MATRIX: drops/flashes per second
    // WHEEL-only: number of rotating blades (1-6) and blade thickness in degrees.
    var lines by mutableStateOf(lines)
    var thickness by mutableStateOf(thickness)
    // RAIN/SPARKLE-only: drop-fall / flash period in ms. >0 decouples rate from `durationMs`
    // (which then means the play window). 0 = legacy (rate derived from duration).
    var speedMs by mutableStateOf(speedMs)
    // Optional label, unique within the layer's sequence. Lets other layers target this step
    // via `startAfter: "group:stepId"` (schemaVersion 8+).
    var stepId by mutableStateOf(stepId)

    /** Switch animation type, re-seeding params/width to the new type's defaults. */
    fun changeAnim(next: AnimId) {
        anim = next
        params = next.defaultParams()
        width = next.defaultWidth
        if (!next.supportsLoopFlags) { loop = false; pingpong = false }
        // HUE_CYCLE is colour-only — it has no scalar output to drive a modifier.
        if (next == AnimId.HUE_CYCLE) animates = Animates.Color
        // RAIN/MATRIX are particle spawners: `duration` is the play window and `speed` is the
        // (constant) drop fall-time. Seed a sensible fall-time the first time one is picked. SPARKLE
        // has no fall (its flashes don't move) — it uses `width` for the fade, so it needs no speed.
        if ((next == AnimId.RAIN || next == AnimId.MATRIX) && speedMs <= 0) speedMs = 800
        // For all spawners `waves` is a spawn RATE (drops/sec); 1 looks broken, seed a livelier default.
        if ((next == AnimId.RAIN || next == AnimId.SPARKLE || next == AnimId.MATRIX) && repeatCount < 2) repeatCount = 4
        // MATRIX's signature is geometric straight lines, so default to geometric when first picked
        // (the user can still toggle to topology — that mode gives a constant-speed tree path).
        if (next == AnimId.MATRIX) geometric = true
    }
}

class EditableLayer(
    name: String = "",
    targetKind: TargetKind = TargetKind.All,
    selected: Set<Int> = emptySet(),     // 0-based indices into the device panel list
    selectorToken: String = "leaves",
    rawTarget: PanelTarget? = null,
    palette: String? = null,
    asyncMode: AsyncMode = AsyncMode.Off,
    startAfter: String? = null,
    blend: String? = null,
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
    var asyncMode by mutableStateOf(asyncMode)
    var startAfter by mutableStateOf(startAfter)
    var blend by mutableStateOf(blend)        // null = default (opaque; runners use max)
    var fallback by mutableStateOf(fallback)  // round-trip passthrough (no UI yet)
    val steps = steps.toMutableStateList()

    // Persisted: false = layer is disabled (skipped during playback) and hidden from the live preview.
    var enabled by mutableStateOf(true)
}

class EditableScene(
    name: String = "",
    loop: Boolean = true,
    speed: Float = 1f,
    palette: String? = null,
    background: String? = null,
    layers: List<EditableLayer> = listOf(EditableLayer(name = "layer1")),
) {
    var name by mutableStateOf(name)
    var loop by mutableStateOf(loop)
    var speed by mutableStateOf(speed)
    var palette by mutableStateOf(palette)
    var background by mutableStateOf(background)  // compositor base #RRGGBB; null = black
    val layers = layers.toMutableStateList()

    /** A unique auto-name for a freshly added layer. */
    fun nextLayerName(): String {
        var n = layers.size + 1
        val taken = layers.map { it.name }.toSet()
        while ("layer$n" in taken) n++
        return "layer$n"
    }

    /** A unique name for a clone of [name] — "<name>_copy", then "<name>_copy2", … */
    fun cloneLayerName(name: String): String {
        val base = name.trim().ifBlank { "layer" }
        val taken = layers.map { it.name }.toSet()
        var n = 1
        while (true) {
            val suffix = if (n == 1) "_copy" else "_copy$n"
            val candidate = base.take((GROUP_NAME_MAX_LEN - suffix.length).coerceAtLeast(1)) + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }
}

/** Deep copy with fresh ids for the step itself — used when cloning a layer. */
private fun EditableStep.clone(): EditableStep = EditableStep(
    anim        = anim,
    colorA      = colorA,
    colorB      = colorB,
    durationMs  = durationMs,
    loop        = loop,
    pingpong    = pingpong,
    params      = params,
    geometric   = geometric,
    source      = source,
    sourcePanel = sourcePanel,
    angle       = angle,
    reverse     = reverse,
    width       = width,
    animates    = animates,
    amount      = amount,
    valueFrom   = valueFrom,
    valueTo     = valueTo,
    density     = density,
    repeatCount = repeatCount,
    lines       = lines,
    thickness   = thickness,
    speedMs     = speedMs,
    stepId      = stepId,
)

/** Deep copy of the whole scene, with fresh ids for every layer and step — backs the scene "Clone" action. */
fun EditableScene.clone(name: String): EditableScene = EditableScene(
    name       = name,
    loop       = loop,
    speed      = speed,
    palette    = palette,
    background = background,
    layers     = layers.map { it.clone(it.name) },
)

/** Deep copy with a fresh id (and fresh ids for its steps) — backs the layer-row "Clone" action. */
fun EditableLayer.clone(name: String): EditableLayer = EditableLayer(
    name          = name,
    targetKind    = targetKind,
    selected      = selected,
    selectorToken = selectorToken,
    rawTarget     = rawTarget,
    palette       = palette,
    asyncMode     = asyncMode,
    startAfter    = startAfter,
    blend         = blend,
    fallback      = fallback,
    steps         = steps.map { it.clone() },
).also { it.enabled = enabled }

// ── Conversion: editor ⇄ SceneJson ──────────────────────────────────────────────
// The visualizer reports 0-based list positions; the firmware addresses panels from
// 1 via PanelInfo.id. Map through panels[idx].info.id, never by adding 1.

private fun EditableStep.runnerSourceToken(): String? = when (source) {
    RunnerSrc.Root   -> null       // default — omit
    RunnerSrc.Leaves -> "leaves"
    RunnerSrc.All    -> "all"
    RunnerSrc.Panel  -> "panel:$sourcePanel"
}

private fun Animates.toToken(): String? = when (this) {
    Animates.Color      -> null   // default — omit
    Animates.Dim        -> AnimateTarget.DIM
    Animates.Brighten   -> AnimateTarget.BRIGHTEN
    Animates.Desaturate -> AnimateTarget.DESATURATE
    Animates.Saturate   -> AnimateTarget.SATURATE
    Animates.Hue        -> AnimateTarget.HUE
    Animates.Invert     -> AnimateTarget.INVERT
}

private fun parseAnimates(animates: String?): Animates = when (animates) {
    AnimateTarget.DIM        -> Animates.Dim
    AnimateTarget.BRIGHTEN   -> Animates.Brighten
    AnimateTarget.DESATURATE -> Animates.Desaturate
    AnimateTarget.SATURATE   -> Animates.Saturate
    AnimateTarget.HUE        -> Animates.Hue
    AnimateTarget.INVERT     -> Animates.Invert
    else                     -> Animates.Color
}

private fun EditableStep.toSceneStep(): SceneStep =
    toSceneStepBody().copy(id = stepId?.trim()?.ifBlank { null })

private fun EditableStep.toSceneStepBody(): SceneStep {
    val a = anim
    if (a == AnimId.GAP) return SceneStep(duration = durationMs)
    if (a == AnimId.WHEEL) {
        // Always geometric, always looping — pivots about `source`/`reverse`.
        val animatesColor = animates == Animates.Color
        return SceneStep(
            runner    = a.wireName,
            color     = if (animatesColor) colorA else null,
            duration  = durationMs,
            source    = runnerSourceToken(),
            reverse   = if (reverse) true else null,
            lines     = lines,
            thickness = thickness,
            animates  = animates.toToken(),
            amount    = if (animatesColor) null else amount,
        )
    }
    if (a.isRunner) {
        val isRipple = a == AnimId.RIPPLE
        val isSparkle = a == AnimId.SPARKLE
        val isMatrix = a == AnimId.MATRIX
        val isRainOrSparkle = a == AnimId.RAIN || isSparkle
        val isSpawner = isRainOrSparkle || isMatrix
        // RAIN and MATRIX are directional like the other runners: geometric uses `angle` (no
        // source), topology uses `source` (no angle). MATRIX supports both (geometric = straight
        // constant-speed lines, topology = constant-speed tree path). SPARKLE has no directionality.
        val usesSource = !isSparkle && (!geometric || isRipple)
        val animatesColor = animates == Animates.Color
        return SceneStep(
            runner         = a.wireName,
            color          = if (animatesColor) colorA else null,
            duration       = durationMs,
            source         = if (usesSource) runnerSourceToken() else null,
            directionality = if (geometric && !isSparkle) "geometric" else null,
            angle          = if (geometric && !isRipple && !isSparkle) angle else null,
            reverse        = if (reverse && !isSparkle) true else null,
            waveWidth      = if (a == AnimId.WAVE && a.hasWidth) width else null,
            rippleWidth    = if (a == AnimId.RIPPLE && a.hasWidth) width else null,
            width          = if ((a == AnimId.BOUNCE || isSpawner) && a.hasWidth) width else null,
            density        = if (!isSpawner && a != AnimId.BOUNCE && density > 0) density else null,
            waves          = if (isSpawner && repeatCount > 1) repeatCount else null,
            speed          = if ((a == AnimId.RAIN || isMatrix) && speedMs > 0) speedMs else null, // SPARKLE has no fall-time
            animates       = animates.toToken(),
            amount         = if (animatesColor) null else amount,
        )
    }
    val animatesColor = animates == Animates.Color
    val isInvert = animates == Animates.Invert
    return SceneStep(
        type      = a.wireName,
        color     = if (animatesColor && a.colorMode == ColorMode.Single) colorA else null,
        colorFrom = if (animatesColor && a.colorMode == ColorMode.FromTo) colorA else null,
        colorTo   = if (animatesColor && a.colorMode == ColorMode.FromTo) colorB else null,
        duration  = durationMs,
        loop      = if (loop) true else null,
        pingpong  = if (pingpong) true else null,
        params    = a.params.indices.map { params.getOrElse(it) { a.params[it].default } }.ifEmpty { null },
        animates  = if (animatesColor) null else animates.toToken(),
        from      = if (animatesColor || isInvert) null else valueFrom,
        to        = if (animatesColor || isInvert || a == AnimId.SOLID) null else valueTo,
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

fun EditableScene.toSceneJson(panels: List<LightnetDevicePanel>, devicePalette: String? = null): SceneJson {
    val usesCompositing = background != null ||
        layers.any { l -> l.blend != null || l.steps.any { it.animates != Animates.Color } }
    val usesGeometric = layers.any { l -> l.steps.any { it.geometric } }
    val usesWheel = layers.any { l -> l.steps.any { it.anim == AnimId.WHEEL } }
    val usesV7 = layers.any { l -> l.steps.any { it.anim == AnimId.BOUNCE || it.anim == AnimId.RAIN || it.anim == AnimId.SPARKLE || it.anim == AnimId.MATRIX } }
    val usesV8 = layers.any { l ->
        l.steps.any { !it.stepId.isNullOrBlank() } || l.startAfter?.contains(":") == true
    }

    return SceneJson(
        // Pick the lowest schema that still expresses the features used, so scenes keep loading
        // on older controllers: v8 = step `id` / `startAfter: "group:stepId"`, v7 = BOUNCE/RAIN/SPARKLE/MATRIX,
        // v5 = WHEEL, v4 = blend/modifier/background, v3 = geometric directionality.
        schemaVersion = when {
            usesV8          -> 8
            usesV7          -> 7
            usesWheel       -> 5
            usesCompositing -> 4
            usesGeometric   -> 3
            else            -> 2
        },
        name       = name.trim().ifBlank { null },
        loop       = loop,
        speed      = speed,
        background = background,
        palette    = palette ?: devicePalette,
        layers     = layers.map { l ->
            SceneLayer(
                group      = l.name.trim(),
                panels     = l.toPanelTarget(panels),
                palette    = l.palette ?: palette ?: devicePalette,
                sequence   = l.steps.map { it.toSceneStep() },
                startAfter = l.startAfter?.trim()?.ifBlank { null },
                async      = when (l.asyncMode) { AsyncMode.Loop -> "loop"; AsyncMode.Free -> "free"; else -> null },
                blend      = l.blend,
                fallback   = l.fallback,
                disabled   = !l.enabled,
            )
        },
    )
}

/**
 * Same as [toSceneJson] but drops disabled layers (via [EditableLayer.enabled]) from the
 * live preview. The controller also skips disabled layers during playback, but filtering
 * them here avoids sending them at all for the preview.
 */
fun EditableScene.toPreviewSceneJson(panels: List<LightnetDevicePanel>, devicePalette: String? = null): SceneJson {
    val full = toSceneJson(panels, devicePalette)
    if (layers.all { it.enabled }) return full
    val keep = layers.indices.filter { layers[it].enabled }.toSet()
    return full.copy(layers = full.layers.filterIndexed { i, _ -> i in keep })
}

// Returns (isGeometric, origin, panelIndex). Accepts both the new `directionality` field
// and the legacy `source: "geometric"` encoding for back-compat with older scenes.
private fun parseRunnerSource(source: String?, directionality: String?): Triple<Boolean, RunnerSrc, Int> {
    val isGeometric = directionality == "geometric" || source == "geometric"
    val (origin, panel) = when {
        source == null || source == "root" || source == "geometric" -> RunnerSrc.Root to 1
        source == "leaves"             -> RunnerSrc.Leaves to 1
        source == "all"                -> RunnerSrc.All to 1
        source.startsWith("panel:")    -> RunnerSrc.Panel to (source.removePrefix("panel:").toIntOrNull() ?: 1)
        else                           -> RunnerSrc.Root to 1
    }
    return Triple(isGeometric, origin, panel)
}

private fun stepFrom(step: SceneStep): EditableStep {
    val anim = AnimId.fromStep(step)
    val (isGeometric, src, srcPanel) = parseRunnerSource(step.source, step.directionality)
    val params = step.params ?: anim.defaultParams()
    return EditableStep(
        anim        = anim,
        colorA      = step.color ?: step.colorFrom ?: ColorRef.Hex("#FF0000"),
        colorB      = step.colorTo ?: ColorRef.Hex("#0000FF"),
        durationMs  = step.duration ?: 1000,
        loop        = step.loop == true,
        pingpong    = step.pingpong == true,
        params      = params,
        geometric   = isGeometric,
        source      = src,
        sourcePanel = srcPanel,
        angle       = step.angle ?: 0,
        reverse     = step.reverse == true,
        width       = step.waveWidth ?: step.rippleWidth ?: step.width ?: step.params?.getOrNull(0) ?: anim.defaultWidth,
        animates    = parseAnimates(step.animates),
        amount      = step.amount ?: 128,
        valueFrom   = step.from ?: 255,
        valueTo     = step.to ?: 64,
        density     = (step.density ?: 0).coerceIn(0, 255),
        repeatCount = (step.waves ?: 1).coerceAtLeast(1),
        lines       = step.lines ?: 1,
        thickness   = step.thickness ?: 18,
        speedMs     = step.speed ?: 0,
        stepId      = step.id,
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
    name       = json.name ?: "",
    loop       = json.loop ?: true,
    speed      = json.speed ?: 1f,
    palette    = json.palette,
    background = json.background,
    layers     = json.layers.mapIndexed { idx, layer ->
        EditableLayer(
            name       = layer.group.ifBlank { "layer${idx + 1}" },
            palette    = layer.palette,
            asyncMode  = when (layer.async) { "free" -> AsyncMode.Free; null -> AsyncMode.Off; else -> AsyncMode.Loop },
            startAfter = layer.startAfter,
            blend      = layer.blend,
            fallback   = layer.fallback,
            steps      = layer.sequence.map { stepFrom(it) }.ifEmpty { listOf(EditableStep()) },
        ).apply(layerFromTarget(layer.panels, panels)).also { it.enabled = !layer.disabled }
    }.ifEmpty { listOf(EditableLayer(name = "layer1")) },
)

// ── Validation ──────────────────────────────────────────────────────────────────

const val GROUP_NAME_MAX_LEN = 15

private val sceneNameRegex = Regex("^[A-Za-z0-9_-]{1,18}$")
private val groupNameRegex = Regex("^[A-Za-z0-9_-]{1,$GROUP_NAME_MAX_LEN}$")
private val groupNameCharRegex = Regex("[A-Za-z0-9_-]")
private val stepIdRegex = Regex("^[A-Za-z0-9_-]+$")

/** Strips characters not allowed in a layer name and enforces the max length — for use in input fields. */
fun sanitizeLayerName(input: String): String =
    input.filter { groupNameCharRegex.matches(it.toString()) }.take(GROUP_NAME_MAX_LEN)

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
        val stepIds = l.steps.mapNotNull { it.stepId?.trim()?.takeIf(String::isNotBlank) }
        if (stepIds.toSet().size != stepIds.size) return "$label: step ids must be unique."
        l.startAfter?.trim()?.takeIf { it.isNotBlank() }?.let { dep ->
            val (depGroup, depStep) = dep.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
            if (depGroup == l.name.trim()) return "$label cannot start after itself."
            val target = layers.firstOrNull { it.name.trim() == depGroup }
                ?: return "$label: \"$depGroup\" is not a layer name."
            if (depStep != null && depStep !in target.steps.mapNotNull { it.stepId?.trim()?.takeIf(String::isNotBlank) }) {
                return "$label: \"$depStep\" is not a step id in \"$depGroup\"."
            }
        }
        l.steps.forEachIndexed { si, s ->
            val isLast = si == l.steps.lastIndex
            if (s.durationMs <= 0 && !isLast) return "$label step ${si + 1}: duration must be > 0."
            val sid = s.stepId?.trim()
            if (!sid.isNullOrBlank() && !stepIdRegex.matches(sid)) return "$label step ${si + 1}: id must be letters, digits, - or _."
        }
    }
    return null
}

// ── Step ids ─────────────────────────────────────────────────────────────────────

/** First unused "stepN" (N = 1..255) within [layer]'s sequence — for auto-assigning a `startAfter` target. */
internal fun nextStepId(layer: EditableLayer): String {
    val used = layer.steps.mapNotNull { it.stepId?.trim()?.takeIf(String::isNotBlank) }.toSet()
    for (n in 1..255) {
        val candidate = "step$n"
        if (candidate !in used) return candidate
    }
    return "step255"
}

/** Clears `stepId` on any step that's no longer the target of another layer's `startAfter`. */
fun EditableScene.clearUnusedStepIds() {
    val referenced: Set<Pair<String, String>> = layers.mapNotNull { l ->
        val sa = l.startAfter?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val (depGroup, depStep) = sa.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
        if (depStep == null) null else depGroup to depStep
    }.toSet()
    layers.forEach { l ->
        val name = l.name.trim()
        l.steps.forEach { s ->
            val sid = s.stepId?.trim()
            if (!sid.isNullOrBlank() && (name to sid) !in referenced) s.stepId = null
        }
    }
}
