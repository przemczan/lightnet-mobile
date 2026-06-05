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

enum class AnimId(
    val display: String,
    val isRunner: Boolean,
    val colorMode: ColorMode,
    val params: List<ParamSpec>,
    val wireName: String?,            // value for SceneStep.type / .runner; null = GAP
    val supportsLoopFlags: Boolean = true,
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
    WAVE("Wave", true, ColorMode.Single, listOf(ParamSpec("Width (panels)", 1, 16, 3)), "WAVE"),
    RIPPLE(
        "Ripple", true, ColorMode.Single,
        listOf(ParamSpec("Ring width", 1, 16, 2), ParamSpec("Origin panel", 0, 99, 0)), "RIPPLE",
    ),
    CHASE("Chase", true, ColorMode.Single, emptyList(), "CHASE"),
    ;

    /** Param list seeded to defaults for this animation. */
    fun defaultParams(): List<Int> = params.map { it.default }

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
) {
    val id: Long = nextId()
    var anim by mutableStateOf(anim)
    var colorA by mutableStateOf(colorA)
    var colorB by mutableStateOf(colorB)
    var durationMs by mutableStateOf(durationMs)
    var loop by mutableStateOf(loop)
    var pingpong by mutableStateOf(pingpong)
    var params by mutableStateOf(params)

    /** Switch animation type, re-seeding params to the new type's defaults. */
    fun changeAnim(next: AnimId) {
        anim = next
        params = next.defaultParams()
        if (!next.supportsLoopFlags) { loop = false; pingpong = false }
    }
}

class EditableGroup(
    allPanels: Boolean = true,
    selected: Set<Int> = emptySet(),   // 0-based indices into the device panel list
    palette: String? = null,
    steps: List<EditableStep> = listOf(EditableStep()),
) {
    val id: Long = nextId()
    var allPanels by mutableStateOf(allPanels)
    var selected by mutableStateOf(selected)
    var palette by mutableStateOf(palette)
    val steps = steps.toMutableStateList()
}

class EditableScene(
    name: String = "",
    loop: Boolean = true,
    speed: Float = 1f,
    palette: String? = null,
    groups: List<EditableGroup> = listOf(EditableGroup()),
) {
    var name by mutableStateOf(name)
    var loop by mutableStateOf(loop)
    var speed by mutableStateOf(speed)
    var palette by mutableStateOf(palette)
    val groups = groups.toMutableStateList()
}

// ── Conversion: editor ⇄ SceneJson ──────────────────────────────────────────────
// The visualizer reports 0-based list positions; the firmware addresses panels from
// 1 via PanelInfo.id. Map through panels[idx].info.id, never by adding 1.

private fun EditableStep.toSceneStep(): SceneStep {
    val a = anim
    if (a == AnimId.GAP) return SceneStep(duration = durationMs)
    return SceneStep(
        type      = if (a.isRunner) null else a.wireName,
        runner    = if (a.isRunner) a.wireName else null,
        color     = if (a.colorMode == ColorMode.Single) colorA else null,
        colorFrom = if (a.colorMode == ColorMode.FromTo) colorA else null,
        colorTo   = if (a.colorMode == ColorMode.FromTo) colorB else null,
        duration  = durationMs,
        loop      = if (loop) true else null,
        pingpong  = if (pingpong) true else null,
        params    = a.params.indices.map { params.getOrElse(it) { a.params[it].default } }.ifEmpty { null },
    )
}

private fun EditableGroup.toPanelTarget(panels: List<LightnetDevicePanel>): PanelTarget =
    if (allPanels) PanelTarget.All
    else PanelTarget.Include(selected.sorted().mapNotNull { panels.getOrNull(it)?.info?.id })

fun EditableScene.toSceneJson(panels: List<LightnetDevicePanel>): SceneJson = SceneJson(
    name    = name.trim().ifBlank { null },
    loop    = loop,
    speed   = speed,
    palette = palette,
    layers  = groups.mapIndexed { idx, g ->
        SceneLayer(
            group    = idx + 1,
            panels   = g.toPanelTarget(panels),
            palette  = g.palette,
            sequence = g.steps.map { it.toSceneStep() },
        )
    },
)

private fun stepFrom(step: SceneStep): EditableStep {
    val anim = AnimId.fromStep(step)
    return EditableStep(
        anim       = anim,
        colorA     = step.color ?: step.colorFrom ?: ColorRef.Hex("#FF0000"),
        colorB     = step.colorTo ?: ColorRef.Hex("#0000FF"),
        durationMs = step.duration ?: 1000,
        loop       = step.loop == true,
        pingpong   = step.pingpong == true,
        params     = step.params ?: anim.defaultParams(),
    )
}

private fun panelTargetToSelection(target: PanelTarget, panels: List<LightnetDevicePanel>): Pair<Boolean, Set<Int>> =
    when (target) {
        is PanelTarget.All -> true to emptySet()
        is PanelTarget.Include ->
            false to target.indices.mapNotNull { id -> panels.indexOfFirst { it.info.id == id }.takeIf { it >= 0 } }.toSet()
        is PanelTarget.Exclude -> {
            val excluded = target.indices.toSet()
            false to panels.indices.filter { panels[it].info.id !in excluded }.toSet()
        }
    }

fun sceneFromJson(json: SceneJson, panels: List<LightnetDevicePanel>): EditableScene = EditableScene(
    name    = json.name ?: "",
    loop    = json.loop ?: true,
    speed   = json.speed ?: 1f,
    palette = json.palette,
    groups  = json.layers.map { layer ->
        val (all, selected) = panelTargetToSelection(layer.panels, panels)
        EditableGroup(
            allPanels = all,
            selected  = selected,
            palette   = layer.palette,
            steps     = layer.sequence.map { stepFrom(it) }.ifEmpty { listOf(EditableStep()) },
        )
    }.ifEmpty { listOf(EditableGroup()) },
)

// ── Validation ──────────────────────────────────────────────────────────────────

private val nameRegex = Regex("^[A-Za-z0-9_-]{1,18}$")

/** Returns the first validation error message, or null when the scene is valid to save/preview. */
fun EditableScene.validationError(): String? {
    if (!nameRegex.matches(name.trim())) return "Name must be 1–18 chars (letters, digits, - or _)."
    if (groups.isEmpty()) return "Add at least one group."
    groups.forEachIndexed { gi, g ->
        if (g.steps.isEmpty()) return "Group ${gi + 1} needs at least one step."
        if (!g.allPanels && g.selected.isEmpty()) return "Group ${gi + 1} has no panels selected."
        g.steps.forEachIndexed { si, s ->
            val isLast = si == g.steps.lastIndex
            if (s.durationMs <= 0 && !isLast) return "Group ${gi + 1} step ${si + 1}: duration must be > 0."
        }
    }
    return null
}
