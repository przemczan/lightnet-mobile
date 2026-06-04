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
)

/**
 * Decodes a PacketAnimationPrepare body into an [AnimationState]. [payload] is the raw I²C packet
 * (a [metaSize]-byte Protocol::PacketMeta followed by the 16-byte body). Mirrors the firmware
 * struct layout: animType, group_id, flags, transitionMs, durationMs(u16), colorFrom(4), colorTo(4),
 * param1, param2.
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
    )
}

/**
 * Faithful Kotlin port of the firmware Lightnet::AnimationPlayer (lib/Lightnet/Panel/AnimationPlayer.cpp).
 * One instance drives one panel locally so the app can preview panel-local animations
 * (FADE/BREATHE/PULSE/…) without per-frame network traffic.
 *
 * Integer math mirrors the firmware exactly (lerp8, q8 fractions, parabolic breathe) so the
 * preview tracks the hardware. Time is supplied by the caller as monotonic milliseconds; elapsed
 * is computed in 16-bit modular arithmetic to match the panel's uint16 millis() wrap.
 *
 * Differences from firmware, all behaviourally inert for color output:
 *  - no 16 ms internal frame gate (the driver loop sets cadence; output depends only on elapsed),
 *  - [currentColor] stands in for the LED controller, including FLAG_CURRENT_* resolution.
 */
class PanelAnimationPlayer {
    // Output — the resolved color for the current frame. Also the "live LED color".
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

    init {
        palette[0] = GradientStop(0, 255, 255, 255)
        palette[1] = GradientStop(255, 255, 255, 255)
    }

    // 4-deep animation queue.
    private val queue = Array(4) { AnimationState() }
    private var queueHead = 0
    private var queueCount = 0

    // Playback state.
    private var animType = ANIM_SOLID
    private var groupId = 0
    private var flags = 0
    private var transitionMs = 0
    private var durationMs = 0
    private var startMs = 0L
    private var startControllerMs = 0L
    private var paused = false
    private var pausedElapsedMs = 0
    private var lastStartSeqId = 0xFF
    private var lastParamsSeqId = 0xFF

    // Reactive state.
    private var reactiveLevel = 0
    private var reactiveDecayRate = 0
    private var reactiveTriggerMs = 0L

    val isAnimating: Boolean get() = animType != ANIM_SOLID || reactiveLevel > 0

    // ── External color writes (SET_COLOR) and baseline seeding ──────────────────

    /** Directly sets the LED color, as the firmware's SET_COLOR handler does (independent of the queue). */
    fun setColorDirect(color: ColorRgbModel) {
        currentColor = color
    }

    // ── Palette / base colors ────────────────────────────────────────────────────

    fun setPalette(stops: List<GradientStop>) {
        if (stops.isEmpty()) return
        val count = stops.size.coerceAtMost(PALETTE_STOPS)
        for (i in 0 until count) palette[i] = stops[i]
        paletteCount = count
    }

    fun setBaseColors(colors: List<ColorRgbModel>) {
        for (i in 0 until BASE_COLORS_COUNT) {
            if (i < colors.size) baseColors[i] = colors[i]
        }
    }

    // ── ColorRef resolution ────────────────────────────────────────────────────

    private fun resolveColorRef(ref: ColorRef): ColorRgbModel = when (ref.kind) {
        COLORREF_RGB -> ColorRgbModel(ref.a, ref.b, ref.c)
        COLORREF_PALETTE -> samplePalette(ref.a)
        COLORREF_USE_COLOR -> baseColors[if (ref.a >= BASE_COLORS_COUNT) 0 else ref.a]
        else -> ColorRgbModel(255, 255, 255)
    }

    private fun resolveCurrentColors(): Pair<ColorRgbModel, ColorRgbModel> {
        val anim = queue[queueHead]
        return resolveColorRef(anim.colorFrom) to resolveColorRef(anim.colorTo)
    }

    // ── Packet handlers ────────────────────────────────────────────────────────

    fun prepare(state: AnimationState) {
        if (queueCount >= 4) return
        val idx = (queueHead + queueCount) % 4
        queue[idx] = state
        queueCount++
    }

    fun start(seqId: Int, groupId: Int, nowMs: Long, controllerMs: Long = nowMs) {
        if (seqId == lastStartSeqId) return
        lastStartSeqId = seqId

        for (i in 0 until queueCount) {
            val idx = (queueHead + i) % 4
            if (queue[idx].groupId != groupId) continue

            // Already running — let it finish; advanceQueue() picks up the next prepared step.
            if (i == 0 && this.groupId != 0) return

            if (i > 0) {
                // Rotate the matching animation to the head.
                val temp = queue[idx]
                var j = idx
                while (j != queueHead) {
                    val prev = if (j > 0) j - 1 else 3
                    queue[j] = queue[prev]
                    j = prev
                }
                queue[queueHead] = temp

                // Drop the displaced running animation (now at position 1) so it doesn't restart.
                var k = 1
                while (k < queueCount - 1) {
                    queue[(queueHead + k) % 4] = queue[(queueHead + k + 1) % 4]
                    k++
                }
                queueCount--
            }

            val anim = queue[queueHead]
            animType = anim.animType
            this.groupId = anim.groupId
            flags = anim.flags
            transitionMs = anim.transitionMs
            durationMs = anim.durationMs
            startMs = nowMs
            startControllerMs = controllerMs
            paused = false
            pausedElapsedMs = 0

            // Resolve FLAG_CURRENT_* against the live color (our currentColor shadow).
            var head = queue[queueHead]
            if (flags and FLAG_CURRENT_COLOR_FROM != 0) {
                head = head.copy(colorFrom = ColorRef.rgb(currentColor.r, currentColor.g, currentColor.b))
            }
            if (flags and FLAG_CURRENT_COLOR_TO != 0) {
                head = head.copy(colorTo = ColorRef.rgb(currentColor.r, currentColor.g, currentColor.b))
            }
            queue[queueHead] = head

            if (animType == ANIM_REACTIVE) {
                reactiveDecayRate = head.param1
                reactiveTriggerMs = startMs
                reactiveLevel = 0
            }
            return
        }
    }

    fun control(cmd: Int, nowMs: Long) {
        when (cmd) {
            ANIM_CTRL_STOP -> {
                queueCount = 0
                queueHead = 0
                animType = ANIM_SOLID
                groupId = 0
                reactiveLevel = 0
                startControllerMs = 0L
            }
            ANIM_CTRL_PAUSE -> if (animType != ANIM_SOLID) {
                paused = true
                pausedElapsedMs = u16(nowMs - startMs)
            }
            ANIM_CTRL_RESUME -> if (paused && animType != ANIM_SOLID) {
                paused = false
                startMs = nowMs - pausedElapsedMs
            }
            ANIM_CTRL_CLEAR_QUEUE -> if (queueCount > 1) queueCount = 1
        }
    }

    fun updateParams(seqId: Int, groupId: Int, paramType: Int, value: Int, nowMs: Long) {
        if (seqId == lastParamsSeqId) return
        lastParamsSeqId = seqId
        if (groupId != 0 && groupId != this.groupId) return

        when (paramType) {
            PARAM_TRIGGER -> if (animType == ANIM_REACTIVE) {
                reactiveLevel = value
                reactiveTriggerMs = nowMs
            }
            // PARAM_BRIGHTNESS_MULT / PARAM_SPEED_SCALE are TODOs in the firmware.
        }
    }

    /**
     * Adjusts [startMs] so elapsed time matches the controller's clock, correcting accumulated drift.
     * Called once per MIRROR_BATCH so the mobile's animation phase tracks the hardware exactly.
     */
    fun resync(controllerNow: Long, mobileNow: Long) {
        if (startControllerMs <= 0L) return
        startMs = mobileNow - (controllerNow - startControllerMs)
    }

    // ── Tick ────────────────────────────────────────────────────────────────────

    fun tick(nowMs: Long) {
        if (queueCount == 0 && reactiveLevel == 0) return
        if (paused) return

        reactiveNowMs = nowMs
        val elapsed = u16(nowMs - startMs)

        if (durationMs > 0 && elapsed >= durationMs && (flags and FLAG_LOOP) == 0 && animType != ANIM_REACTIVE) {
            computeFrame(durationMs)
            advanceQueue(nowMs)
            return
        }

        computeFrame(elapsed)
    }

    private fun computeFrame(elapsed: Int) {
        when (animType) {
            ANIM_SOLID -> currentColor = resolveColorRef(queue[queueHead].colorTo)
            ANIM_FADE -> tickLerpOverDuration(elapsed)
            ANIM_TRANSITION -> tickLerpOverDuration(elapsed)
            ANIM_BREATHE -> tickBreathe(elapsed)
            ANIM_PULSE -> tickPulse(elapsed)
            ANIM_BLINK -> tickBlink(elapsed)
            ANIM_HUE_CYCLE -> tickHueCycle(elapsed)
            ANIM_STROBE -> tickStrobe(elapsed)
            ANIM_REACTIVE -> tickReactive(elapsed)
        }
    }

    // ── Animation type handlers (faithful integer math) ──────────────────────────

    // FADE and TRANSITION share identical math in the firmware.
    private fun tickLerpOverDuration(elapsed: Int) {
        val progressQ8 = if (durationMs > 0) {
            val prog = elapsed * 256 / durationMs
            if (prog > 255) 255 else prog
        } else 255
        val (cFrom, cTo) = resolveCurrentColors()
        currentColor = rgbLerp(cFrom, cTo, progressQ8)
    }

    private fun tickBreathe(elapsed: Int) {
        if (durationMs == 0) return
        val t = elapsed % durationMs
        val half = durationMs / 2
        if (half == 0) return

        val phaseQ8 = if (t <= half) (t * 255 / half) else ((durationMs - t) * 255 / half)
        val inv = (255 - phaseQ8) and 0xFF
        val invSq = ((inv * inv) ushr 8) and 0xFF
        val ease = (255 - invSq) and 0xFF

        val (cFrom, cTo) = resolveCurrentColors()
        currentColor = rgbLerp(cFrom, cTo, ease)
    }

    private fun tickPulse(elapsed: Int) {
        val anim = queue[queueHead]
        var risePct = anim.param1
        var fallPct = anim.param2
        val sum = risePct + fallPct
        if (sum > 255) {
            risePct = 255 * risePct / sum
            fallPct = 255 - risePct
        }
        val holdPct = 255 - risePct - fallPct

        val riseMs = durationMs * risePct / 256
        val holdMs = durationMs * holdPct / 256

        val progressQ8 = when {
            elapsed < riseMs -> (elapsed * 256 / (riseMs + 1)) and 0xFF
            elapsed < riseMs + holdMs -> 255
            else -> {
                val fallElapsed = elapsed - riseMs - holdMs
                val fallDuration = durationMs - riseMs - holdMs
                (255 - ((fallElapsed * 256 / (fallDuration + 1)) and 0xFF)) and 0xFF
            }
        }

        val (cFrom, cTo) = resolveCurrentColors()
        currentColor = rgbLerp(cFrom, cTo, progressQ8)
    }

    private fun tickBlink(elapsed: Int) {
        var periodMs = queue[queueHead].param1
        if (periodMs == 0) periodMs = 1
        val phase = elapsed % (periodMs * 2)
        val on = phase < periodMs
        val (cFrom, cTo) = resolveCurrentColors()
        currentColor = if (on) cTo else cFrom
    }

    private fun tickHueCycle(elapsed: Int) {
        var speed = queue[queueHead].param1
        if (speed == 0) speed = 1
        val hueStep = (elapsed * speed / 10) % 1530  // 1530 = 6 * 255
        val segment = hueStep / 255
        val remainder = hueStep % 255

        currentColor = when (segment) {
            0 -> ColorRgbModel(255, remainder, 0)
            1 -> ColorRgbModel(255 - remainder, 255, 0)
            2 -> ColorRgbModel(0, 255, remainder)
            3 -> ColorRgbModel(0, 255 - remainder, 255)
            4 -> ColorRgbModel(remainder, 0, 255)
            else -> ColorRgbModel(255, 0, 255 - remainder)
        }
    }

    private fun tickStrobe(elapsed: Int) {
        var hz = queue[queueHead].param1
        if (hz == 0) hz = 1
        val periodMs = 1000 / hz
        val on = (elapsed % periodMs) < (periodMs / 2)
        currentColor = if (on) resolveColorRef(queue[queueHead].colorTo) else ColorRgbModel(0, 0, 0)
    }

    private fun tickReactive(elapsed: Int) {
        if (reactiveLevel > 0) {
            val sinceTrigger = u16(reactiveNowMs - reactiveTriggerMs)
            val decayAmount = reactiveDecayRate * sinceTrigger / 1000
            reactiveLevel = if (decayAmount >= reactiveLevel) 0 else reactiveLevel - decayAmount
        }
        val (cFrom, cTo) = resolveCurrentColors()
        currentColor = rgbLerp(cFrom, cTo, reactiveLevel)
    }

    // tickReactive needs "now" for decay; capture it from the active tick call.
    private var reactiveNowMs = 0L

    // ── Queue management ──────────────────────────────────────────────────────────

    private fun advanceQueue(nowMs: Long) {
        if (queueCount > 0) {
            queueHead = (queueHead + 1) % 4
            queueCount--
        }
        if (queueCount > 0) {
            val next = queue[queueHead]
            animType = next.animType
            groupId = next.groupId
            flags = next.flags
            transitionMs = next.transitionMs
            durationMs = next.durationMs
            startMs = nowMs
            startControllerMs = 0L
            paused = false
            pausedElapsedMs = 0
            if (animType == ANIM_REACTIVE) {
                reactiveDecayRate = next.param1
                reactiveTriggerMs = startMs
                reactiveLevel = 0
            }
        } else {
            animType = ANIM_SOLID
            groupId = 0
            reactiveLevel = 0
        }
    }

    // ── Palette sampling + lerp (firmware Palette.hpp / lerp8) ────────────────────

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
