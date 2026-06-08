package com.lightnet.animation

import com.lightnet.api.websocket.protocol.model.ColorRgbModel

// Animation types — mirror of Lightnet::AnimationType (firmware AnimationTypes.hpp).
const val ANIM_SOLID = 0
const val ANIM_FADE = 1
const val ANIM_TRANSITION = 2
const val ANIM_BREATHE = 3
const val ANIM_PULSE = 4
const val ANIM_BLINK = 5
const val ANIM_HUE_CYCLE = 6
const val ANIM_STROBE = 7
const val ANIM_REACTIVE = 8

// Modifier layer types (transform the colour composited below them).
const val ANIM_MOD_BRIGHTNESS = 10
const val ANIM_MOD_SATURATION = 11
const val ANIM_MOD_HUE_SHIFT = 12

fun isModifierType(t: Int) = t in ANIM_MOD_BRIGHTNESS..ANIM_MOD_HUE_SHIFT

// Max concurrent composited layers per panel (firmware MAX_ANIM_SLOTS).
const val MAX_ANIM_SLOTS = 4

// Animation flags (bitfield).
const val FLAG_LOOP = 0x01
const val FLAG_PINGPONG = 0x02
const val FLAG_EASING = 0x04
const val FLAG_CURRENT_COLOR_FROM = 0x08
const val FLAG_CURRENT_COLOR_TO = 0x10

// Control commands.
const val ANIM_CTRL_STOP = 1
const val ANIM_CTRL_PAUSE = 2
const val ANIM_CTRL_RESUME = 3
const val ANIM_CTRL_CLEAR_QUEUE = 4

// Param update types.
const val PARAM_TRIGGER = 1
const val PARAM_BRIGHTNESS_MULT = 2
const val PARAM_SPEED_SCALE = 3

const val PALETTE_STOPS = 16
const val BASE_COLORS_COUNT = 3

// ColorRef kinds.
const val COLORREF_RGB = 0
const val COLORREF_PALETTE = 1
const val COLORREF_USE_COLOR = 2

/** 4-byte color reference: a kind byte plus three payload bytes (firmware Lightnet::ColorRef). */
data class ColorRef(val kind: Int, val a: Int, val b: Int, val c: Int) {
    companion object {
        fun rgb(r: Int, g: Int, b: Int) = ColorRef(COLORREF_RGB, r, g, b)

        /** Reads a 4-byte ColorRef from [payload] at [offset]. */
        fun fromBytes(payload: ByteArray, offset: Int) = ColorRef(
            payload[offset].toInt() and 0xFF,
            payload[offset + 1].toInt() and 0xFF,
            payload[offset + 2].toInt() and 0xFF,
            payload[offset + 3].toInt() and 0xFF,
        )
    }
}

/** Palette gradient stop (firmware Lightnet::GradientStop). */
data class GradientStop(val pos: Int, val r: Int, val g: Int, val b: Int)

/** Queued animation parameters (firmware Lightnet::AnimationState, sans playback fields). */
data class AnimationState(
    val animType: Int = ANIM_SOLID,
    val groupId: Int = 0,
    val flags: Int = 0,
    val transitionMs: Int = 0,
    val durationMs: Int = 0,
    val colorFrom: ColorRef = ColorRef.rgb(0, 0, 0),
    val colorTo: ColorRef = ColorRef.rgb(0, 0, 0),
    val param1: Int = 0,
    val param2: Int = 0,
    val composeMode: Int = COMPOSE_OPAQUE,
    val composeOrder: Int = 0,
    val startDelayMs: Int = 0,
)

/**
 * Decodes a PacketAnimationPrepare body into an [AnimationState]. [payload] is the raw I²C packet
 * (a [metaSize]-byte Protocol::PacketMeta followed by the 20-byte v6 body). Mirrors the firmware
 * struct: animType, group_id, flags, transitionMs, durationMs(u16), colorFrom(4), colorTo(4),
 * param1, param2, composeMode, composeOrder, startDelayMs(u16).
 */
fun decodeAnimationPrepare(payload: ByteArray, metaSize: Int = 5): AnimationState {
    fun u8(i: Int) = payload[metaSize + i].toInt() and 0xFF
    fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
    return AnimationState(
        animType = u8(0),
        groupId = u8(1),
        flags = u8(2),
        transitionMs = u8(3),
        durationMs = u16(4),
        colorFrom = ColorRef.fromBytes(payload, metaSize + 6),
        colorTo = ColorRef.fromBytes(payload, metaSize + 10),
        param1 = u8(14),
        param2 = u8(15),
        composeMode = u8(16),
        composeOrder = u8(17),
        startDelayMs = u16(18),
    )
}

/**
 * Faithful Kotlin port of the firmware Lightnet::AnimationPlayer (lib/Lightnet/Panel/AnimationPlayer.cpp)
 * as of protocol v6: a per-panel **layer compositor**. Up to [MAX_ANIM_SLOTS] layers (groups) run
 * concurrently; each tick resolves every started slot to one contribution and folds them in
 * composeOrder onto the background base (see [ColorCompose]). One instance previews one panel.
 *
 * Integer math mirrors the firmware exactly so the preview tracks the hardware. Time is supplied as
 * monotonic milliseconds; elapsed uses 16-bit modular arithmetic to match the panel's uint16 millis().
 */
class PanelAnimationPlayer {
    var currentColor: ColorRgbModel = ColorRgbModel(0, 0, 0)
        private set

    // Palette + base colors (per panel), resolved at frame time.
    private val palette = Array(PALETTE_STOPS) { GradientStop(0, 255, 255, 255) }
    private var paletteCount = 2
    private val baseColors = arrayOf(
        ColorRgbModel(255, 255, 255),
        ColorRgbModel(0, 0, 0),
        ColorRgbModel(0, 0, 0),
    )
    private var backgroundColor = ColorRgbModel(0, 0, 0)

    init {
        palette[0] = GradientStop(0, 255, 255, 255)
        palette[1] = GradientStop(255, 255, 255, 255)
    }

    /** One composited layer (firmware AnimationPlayer::Slot). */
    private class Slot {
        var occupied = false
        var started = false
        var holding = false
        var paused = false
        var pausedElapsedMs = 0
        var groupId = 0
        var cur = AnimationState()
        var hasPending = false
        var pending = AnimationState()
        var reactiveLevel = 0
        var reactiveDecayRate = 0
        var reactiveTriggerMs = 0L
        var startMs = 0L
        var controllerStartMs = 0L
        var outColor = ColorRgbModel(0, 0, 0)

        fun clear() {
            occupied = false; started = false; holding = false; paused = false
            pausedElapsedMs = 0; groupId = 0; hasPending = false
            reactiveLevel = 0; reactiveDecayRate = 0; reactiveTriggerMs = 0L
            startMs = 0L; controllerStartMs = 0L
        }
    }

    private val slots = Array(MAX_ANIM_SLOTS) { Slot() }
    private var lastStartSeqId = 0xFF
    private var lastParamsSeqId = 0xFF
    private var reactiveNowMs = 0L

    val isAnimating: Boolean
        get() = slots.any { it.occupied && it.started && (it.cur.animType != ANIM_SOLID || it.reactiveLevel > 0) }

    // ── External color writes / baseline seeding ──

    /** Directly sets the LED color, as the firmware's SET_COLOR handler does. */
    fun setColorDirect(color: ColorRgbModel) {
        currentColor = color
    }

    fun setPalette(stops: List<GradientStop>) {
        if (stops.isEmpty()) return
        val count = stops.size.coerceAtMost(PALETTE_STOPS)
        for (i in 0 until count) palette[i] = stops[i]
        paletteCount = count
    }

    fun setBaseColors(colors: List<ColorRgbModel>) {
        for (i in 0 until BASE_COLORS_COUNT) if (i < colors.size) baseColors[i] = colors[i]
    }

    /** Compositor base colour (PACKET_SET_BACKGROUND); idle panels display it. */
    fun setBackground(color: ColorRgbModel) {
        backgroundColor = color
        if (slots.none { it.occupied }) currentColor = color
    }

    // ── ColorRef resolution ──

    private fun resolveColorRef(ref: ColorRef): ColorRgbModel = when (ref.kind) {
        COLORREF_RGB -> ColorRgbModel(ref.a, ref.b, ref.c)
        COLORREF_PALETTE -> samplePalette(ref.a)
        COLORREF_USE_COLOR -> baseColors[if (ref.a >= BASE_COLORS_COUNT) 0 else ref.a]
        else -> ColorRgbModel(255, 255, 255)
    }

    private fun resolveColors(a: AnimationState) = resolveColorRef(a.colorFrom) to resolveColorRef(a.colorTo)

    // ── Slot management ──

    private fun findSlot(groupId: Int): Slot? = slots.firstOrNull { it.occupied && it.groupId == groupId }

    private fun allocSlot(groupId: Int): Slot? {
        findSlot(groupId)?.let { return it }
        val free = slots.firstOrNull { !it.occupied } ?: return null
        free.clear(); free.occupied = true; free.groupId = groupId
        return free
    }

    // ── Packet handlers ──

    fun prepare(state: AnimationState) {
        val s = allocSlot(state.groupId) ?: return
        s.pending = state
        s.hasPending = true
    }

    fun start(seqId: Int, groupId: Int, nowMs: Long, controllerMs: Long = nowMs) {
        if (seqId == lastStartSeqId) return
        lastStartSeqId = seqId
        val s = findSlot(groupId) ?: return
        if (!s.hasPending) return

        s.cur = s.pending
        s.hasPending = false
        s.started = true
        s.holding = false
        s.paused = false
        s.pausedElapsedMs = 0
        s.startMs = nowMs
        s.controllerStartMs = controllerMs

        // Resolve FLAG_CURRENT_* against the live composited colour.
        if (s.cur.flags and FLAG_CURRENT_COLOR_FROM != 0) {
            s.cur = s.cur.copy(colorFrom = ColorRef.rgb(currentColor.r, currentColor.g, currentColor.b))
        }
        if (s.cur.flags and FLAG_CURRENT_COLOR_TO != 0) {
            s.cur = s.cur.copy(colorTo = ColorRef.rgb(currentColor.r, currentColor.g, currentColor.b))
        }

        if (s.cur.animType == ANIM_REACTIVE) {
            s.reactiveDecayRate = s.cur.param1
            s.reactiveTriggerMs = nowMs
            s.reactiveLevel = 0
        }
    }

    fun control(cmd: Int, groupId: Int, nowMs: Long) {
        for (s in slots) {
            if (!s.occupied) continue
            if (groupId != 0 && s.groupId != groupId) continue
            when (cmd) {
                ANIM_CTRL_STOP -> s.clear()
                ANIM_CTRL_PAUSE -> if (s.started && !s.paused) {
                    s.paused = true; s.pausedElapsedMs = u16(nowMs - s.startMs)
                }
                ANIM_CTRL_RESUME -> if (s.paused) {
                    s.paused = false; s.startMs = nowMs - s.pausedElapsedMs
                }
                ANIM_CTRL_CLEAR_QUEUE -> s.hasPending = false
            }
        }
    }

    fun updateParams(seqId: Int, groupId: Int, paramType: Int, value: Int, nowMs: Long) {
        if (seqId == lastParamsSeqId) return
        lastParamsSeqId = seqId
        if (paramType != PARAM_TRIGGER) return
        for (s in slots) {
            if (!s.occupied || !s.started) continue
            if (groupId != 0 && s.groupId != groupId) continue
            if (s.cur.animType == ANIM_REACTIVE) {
                s.reactiveLevel = value; s.reactiveTriggerMs = nowMs
            }
        }
    }

    /** Aligns each started slot's phase to the controller clock, correcting accumulated drift. */
    fun resync(controllerNow: Long, mobileNow: Long) {
        for (s in slots) {
            if (s.started && s.controllerStartMs > 0L) s.startMs = mobileNow - (controllerNow - s.controllerStartMs)
        }
    }

    // ── Tick / compositor ──

    fun tick(nowMs: Long) {
        reactiveNowMs = nowMs
        val contrib = ArrayList<CompositeLayer>(MAX_ANIM_SLOTS)

        for (s in slots) {
            if (!s.occupied || !s.started) continue

            val elapsed = if (s.paused) s.pausedElapsedMs else u16(nowMs - s.startMs)

            // Transparent before onset.
            if (!s.holding && elapsed < s.cur.startDelayMs) continue

            var animElapsed = if (elapsed >= s.cur.startDelayMs) elapsed - s.cur.startDelayMs else 0

            if (!s.paused && !s.holding && s.cur.durationMs > 0 &&
                animElapsed >= s.cur.durationMs &&
                (s.cur.flags and FLAG_LOOP) == 0 && s.cur.animType != ANIM_REACTIVE
            ) {
                s.holding = true
            }

            animElapsed = when {
                s.holding && s.cur.durationMs > 0 -> s.cur.durationMs
                (s.cur.flags and FLAG_LOOP) != 0 && s.cur.durationMs > 0 -> animElapsed % s.cur.durationMs
                else -> animElapsed
            }

            if (isModifierType(s.cur.animType)) {
                val v = modifierValue(s.cur, animElapsed)
                val op = when (s.cur.animType) {
                    ANIM_MOD_SATURATION -> MO_SATURATION
                    ANIM_MOD_HUE_SHIFT -> MO_HUE
                    else -> MO_BRIGHTNESS
                }
                contrib.add(CompositeLayer(s.cur.composeOrder, isModifier = true, op = op, value = v))
            } else {
                s.outColor = computeSlotColor(s, animElapsed)
                contrib.add(CompositeLayer(s.cur.composeOrder, isModifier = false, op = s.cur.composeMode, color = s.outColor))
            }
        }

        if (contrib.isEmpty()) return // idle → leave LED (background / direct SET_COLOR)
        currentColor = foldLayers(contrib, backgroundColor)
    }

    private fun computeSlotColor(s: Slot, elapsed: Int): ColorRgbModel = when (s.cur.animType) {
        ANIM_SOLID -> resolveColorRef(s.cur.colorTo)
        ANIM_FADE, ANIM_TRANSITION -> tickLerpOverDuration(s.cur, elapsed)
        ANIM_BREATHE -> tickBreathe(s.cur, elapsed)
        ANIM_PULSE -> tickPulse(s.cur, elapsed)
        ANIM_BLINK -> tickBlink(s.cur, elapsed)
        ANIM_HUE_CYCLE -> tickHueCycle(s.cur, elapsed)
        ANIM_STROBE -> tickStrobe(s.cur, elapsed)
        ANIM_REACTIVE -> tickReactive(s)
        else -> s.outColor
    }

    private fun modifierValue(a: AnimationState, elapsed: Int): Int {
        if (a.durationMs == 0) return a.param2
        val prog = elapsed * 256 / a.durationMs
        val q8 = if (prog > 255) 255 else prog
        return lerp8(a.param1, a.param2, q8)
    }

    // ── Animation type handlers (faithful integer math) ──

    private fun tickLerpOverDuration(a: AnimationState, elapsed: Int): ColorRgbModel {
        val progressQ8 = if (a.durationMs > 0) {
            val prog = elapsed * 256 / a.durationMs
            if (prog > 255) 255 else prog
        } else 255
        val (cFrom, cTo) = resolveColors(a)
        return rgbLerp(cFrom, cTo, progressQ8)
    }

    private fun tickBreathe(a: AnimationState, elapsed: Int): ColorRgbModel {
        if (a.durationMs == 0) return currentColor
        val t = elapsed % a.durationMs
        val half = a.durationMs / 2
        if (half == 0) return currentColor
        val phaseQ8 = if (t <= half) (t * 255 / half) else ((a.durationMs - t) * 255 / half)
        val inv = (255 - phaseQ8) and 0xFF
        val invSq = ((inv * inv) ushr 8) and 0xFF
        val ease = (255 - invSq) and 0xFF
        val (cFrom, cTo) = resolveColors(a)
        return rgbLerp(cFrom, cTo, ease)
    }

    private fun tickPulse(a: AnimationState, elapsed: Int): ColorRgbModel {
        var risePct = a.param1
        var fallPct = a.param2
        val sum = risePct + fallPct
        if (sum > 255) {
            risePct = 255 * risePct / sum
            fallPct = 255 - risePct
        }
        val holdPct = 255 - risePct - fallPct
        val riseMs = a.durationMs * risePct / 256
        val holdMs = a.durationMs * holdPct / 256
        val progressQ8 = when {
            elapsed < riseMs -> (elapsed * 256 / (riseMs + 1)) and 0xFF
            elapsed < riseMs + holdMs -> 255
            else -> {
                val fallElapsed = elapsed - riseMs - holdMs
                val fallDuration = a.durationMs - riseMs - holdMs
                (255 - ((fallElapsed * 256 / (fallDuration + 1)) and 0xFF)) and 0xFF
            }
        }
        val (cFrom, cTo) = resolveColors(a)
        return rgbLerp(cFrom, cTo, progressQ8)
    }

    private fun tickBlink(a: AnimationState, elapsed: Int): ColorRgbModel {
        var periodMs = a.param1
        if (periodMs == 0) periodMs = 1
        val on = elapsed % (periodMs * 2) < periodMs
        val (cFrom, cTo) = resolveColors(a)
        return if (on) cTo else cFrom
    }

    private fun tickHueCycle(a: AnimationState, elapsed: Int): ColorRgbModel {
        var speed = a.param1
        if (speed == 0) speed = 1
        val hueStep = (elapsed * speed / 10) % 1530 // 1530 = 6 * 255
        val segment = hueStep / 255
        val remainder = hueStep % 255
        return when (segment) {
            0 -> ColorRgbModel(255, remainder, 0)
            1 -> ColorRgbModel(255 - remainder, 255, 0)
            2 -> ColorRgbModel(0, 255, remainder)
            3 -> ColorRgbModel(0, 255 - remainder, 255)
            4 -> ColorRgbModel(remainder, 0, 255)
            else -> ColorRgbModel(255, 0, 255 - remainder)
        }
    }

    private fun tickStrobe(a: AnimationState, elapsed: Int): ColorRgbModel {
        var hz = a.param1
        if (hz == 0) hz = 1
        val periodMs = 1000 / hz
        val on = (elapsed % periodMs) < (periodMs / 2)
        return if (on) resolveColorRef(a.colorTo) else ColorRgbModel(0, 0, 0)
    }

    private fun tickReactive(s: Slot): ColorRgbModel {
        if (s.reactiveLevel > 0) {
            val sinceTrigger = u16(reactiveNowMs - s.reactiveTriggerMs)
            val decayAmount = s.reactiveDecayRate * sinceTrigger / 1000
            s.reactiveLevel = if (decayAmount >= s.reactiveLevel) 0 else s.reactiveLevel - decayAmount
        }
        val (cFrom, cTo) = resolveColors(s.cur)
        return rgbLerp(cFrom, cTo, s.reactiveLevel)
    }

    // ── Palette sampling + lerp (firmware Palette.hpp / lerp8) ──

    private fun samplePalette(pos: Int): ColorRgbModel {
        val count = paletteCount
        if (count == 0) return ColorRgbModel(255, 255, 255)
        if (count == 1 || pos <= palette[0].pos) {
            val s = palette[0]; return ColorRgbModel(s.r, s.g, s.b)
        }
        if (pos >= palette[count - 1].pos) {
            val s = palette[count - 1]; return ColorRgbModel(s.r, s.g, s.b)
        }
        var i = 0
        while (i + 1 < count && palette[i + 1].pos <= pos) i++
        val a = palette[i]
        val b = palette[i + 1]
        val span = (b.pos - a.pos) and 0xFF
        if (span == 0) return ColorRgbModel(a.r, a.g, a.b)
        val fracQ8 = ((pos - a.pos) * 255 / span) and 0xFF
        return ColorRgbModel(
            (a.r + (b.r - a.r) * fracQ8 / 255) and 0xFF,
            (a.g + (b.g - a.g) * fracQ8 / 255) and 0xFF,
            (a.b + (b.b - a.b) * fracQ8 / 255) and 0xFF,
        )
    }

    private fun rgbLerp(a: ColorRgbModel, b: ColorRgbModel, fracQ8: Int) = ColorRgbModel(
        lerp8(a.r, b.r, fracQ8),
        lerp8(a.g, b.g, fracQ8),
        lerp8(a.b, b.b, fracQ8),
    )

    companion object {
        /** 16-bit modular elapsed, matching the panel's uint16 millis() arithmetic. */
        private fun u16(delta: Long): Int = (delta.toInt() and 0xFFFF)

        fun lerp8(a: Int, b: Int, fracQ8: Int): Int {
            if (a == b || fracQ8 == 0) return a
            if (fracQ8 == 255) return b
            return if (a < b) a + (((b - a) * fracQ8) ushr 8)
            else a - (((a - b) * fracQ8) ushr 8)
        }
    }
}
