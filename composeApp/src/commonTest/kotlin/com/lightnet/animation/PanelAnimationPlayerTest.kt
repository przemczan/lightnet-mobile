package com.lightnet.animation

import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fidelity test for the AnimationPlayer Kotlin port.
 *
 * Expected values are produced by tools/anim-refgen/refgen.cpp, which runs the EXACT integer
 * expressions from the firmware (AnimationPlayer.cpp / Palette.hpp) through a host C++ compiler.
 * Regenerate with:  cd tools/anim-refgen && g++ -O2 -std=c++17 refgen.cpp -o refgen && ./refgen
 *
 * If the port's arithmetic ever diverges from the firmware's (e.g. signed shift, division
 * truncation, uint8 wrap), these assertions fail.
 */
class PanelAnimationPlayerTest {

    private fun rgb(r: Int, g: Int, b: Int) = ColorRgbModel(r, g, b)

    // Drive one animation through the real public API and read the resulting color at `elapsed`.
    private fun colorAt(
        animType: Int,
        durationMs: Int,
        from: ColorRef,
        to: ColorRef,
        elapsed: Long,
        param1: Int = 0,
        param2: Int = 0,
        flags: Int = 0,
        palette: List<GradientStop>? = null,
    ): ColorRgbModel {
        val player = PanelAnimationPlayer()
        palette?.let { player.setPalette(it) }
        player.prepare(
            AnimationState(
                animType = animType,
                groupId = 1,
                flags = flags,
                durationMs = durationMs,
                colorFrom = from,
                colorTo = to,
                param1 = param1,
                param2 = param2,
            )
        )
        player.start(seqId = 1, groupId = 1, nowMs = 0)
        player.tick(nowMs = elapsed)
        return player.currentColor
    }

    @Test
    fun lerp8_matchesFirmware() {
        // lerp8(0, 200, frac)
        assertEquals(0, PanelAnimationPlayer.lerp8(0, 200, 0))
        assertEquals(0, PanelAnimationPlayer.lerp8(0, 200, 1))
        assertEquals(50, PanelAnimationPlayer.lerp8(0, 200, 64))
        assertEquals(100, PanelAnimationPlayer.lerp8(0, 200, 128))
        assertEquals(156, PanelAnimationPlayer.lerp8(0, 200, 200))
        assertEquals(198, PanelAnimationPlayer.lerp8(0, 200, 254))
        assertEquals(200, PanelAnimationPlayer.lerp8(0, 200, 255))
        // lerp8(200, 0, frac) — the a > b branch
        assertEquals(200, PanelAnimationPlayer.lerp8(200, 0, 0))
        assertEquals(200, PanelAnimationPlayer.lerp8(200, 0, 1))
        assertEquals(150, PanelAnimationPlayer.lerp8(200, 0, 64))
        assertEquals(100, PanelAnimationPlayer.lerp8(200, 0, 128))
        assertEquals(44, PanelAnimationPlayer.lerp8(200, 0, 200))
        assertEquals(2, PanelAnimationPlayer.lerp8(200, 0, 254))
        assertEquals(0, PanelAnimationPlayer.lerp8(200, 0, 255))
    }

    @Test
    fun samplePalette_matchesFirmware() {
        // stops: 0=#000000, 128=#FF0000, 255=#00FF00. Resolved via a BLINK in its "on" phase.
        val stops = listOf(
            GradientStop(0, 0, 0, 0),
            GradientStop(128, 255, 0, 0),
            GradientStop(255, 0, 255, 0),
        )
        val expected = mapOf(
            0 to rgb(0, 0, 0),
            32 to rgb(63, 0, 0),
            64 to rgb(127, 0, 0),
            127 to rgb(253, 0, 0),
            128 to rgb(255, 0, 0),
            160 to rgb(191, 64, 0),
            200 to rgb(111, 144, 0),
            255 to rgb(0, 255, 0),
        )
        for ((pos, color) in expected) {
            val got = colorAt(
                ANIM_BLINK, durationMs = 0, from = ColorRef.rgb(0, 0, 0),
                to = ColorRef(COLORREF_PALETTE, pos, 0, 0), elapsed = 0, param1 = 100, palette = stops,
            )
            assertEquals(color, got, "palette pos=$pos")
        }
    }

    @Test
    fun fade_matchesFirmware() {
        val to = ColorRef.rgb(200, 100, 50)
        val from = ColorRef.rgb(0, 0, 0)
        val cases = mapOf(
            0L to rgb(0, 0, 0),
            100L to rgb(19, 9, 4),
            250L to rgb(50, 25, 12),
            500L to rgb(100, 50, 25),
            750L to rgb(150, 75, 37),
            900L to rgb(179, 89, 44),
            1000L to rgb(200, 100, 50),
            2000L to rgb(200, 100, 50),
        )
        for ((elapsed, color) in cases) {
            assertEquals(color, colorAt(ANIM_FADE, 1000, from, to, elapsed), "fade e=$elapsed")
        }
    }

    @Test
    fun breathe_matchesFirmware() {
        val from = ColorRef.rgb(0, 0, 0)
        val to = ColorRef.rgb(255, 255, 255)
        val cases = mapOf(
            0L to rgb(0, 0, 0),
            250L to rgb(110, 110, 110),
            500L to rgb(190, 190, 190),
            1000L to rgb(255, 255, 255),
            1500L to rgb(190, 190, 190),
            1750L to rgb(110, 110, 110),
            1999L to rgb(0, 0, 0),
        )
        for ((elapsed, color) in cases) {
            assertEquals(color, colorAt(ANIM_BREATHE, 2000, from, to, elapsed, flags = FLAG_LOOP), "breathe e=$elapsed")
        }
    }

    @Test
    fun pulse_matchesFirmware() {
        val from = ColorRef.rgb(0, 0, 0)
        val to = ColorRef.rgb(255, 255, 255)
        val cases = mapOf(
            0L to rgb(0, 0, 0),
            50L to rgb(49, 49, 49),
            124L to rgb(125, 125, 125),
            200L to rgb(202, 202, 202),
            300L to rgb(255, 255, 255),
            400L to rgb(255, 255, 255),
            600L to rgb(255, 255, 255),
            800L to rgb(200, 200, 200),
            999L to rgb(1, 1, 1),
        )
        for ((elapsed, color) in cases) {
            assertEquals(color, colorAt(ANIM_PULSE, 1000, from, to, elapsed, param1 = 64, param2 = 64), "pulse e=$elapsed")
        }
    }

    @Test
    fun hueCycle_matchesFirmware() {
        val cases = mapOf(
            0L to rgb(255, 0, 0),
            50L to rgb(255, 250, 0),
            100L to rgb(10, 255, 0),
            200L to rgb(0, 20, 255),
            306L to rgb(255, 0, 0),
            500L to rgb(0, 50, 255),
            800L to rgb(0, 80, 255),
            1000L to rgb(100, 255, 0),
        )
        // durationMs=0 so the animation never "finishes"; param1 = speed.
        for ((elapsed, color) in cases) {
            assertEquals(
                color,
                colorAt(ANIM_HUE_CYCLE, 0, ColorRef.rgb(0, 0, 0), ColorRef.rgb(0, 0, 0), elapsed, param1 = 50),
                "hue e=$elapsed",
            )
        }
    }

    @Test
    fun blink_matchesFirmware() {
        val from = ColorRef.rgb(0, 0, 0)
        val to = ColorRef.rgb(255, 255, 255)
        val cases = mapOf(
            0L to rgb(255, 255, 255),
            50L to rgb(255, 255, 255),
            99L to rgb(255, 255, 255),
            100L to rgb(0, 0, 0),
            150L to rgb(0, 0, 0),
            199L to rgb(0, 0, 0),
            200L to rgb(255, 255, 255),
        )
        for ((elapsed, color) in cases) {
            assertEquals(color, colorAt(ANIM_BLINK, 0, from, to, elapsed, param1 = 100), "blink e=$elapsed")
        }
    }

    @Test
    fun decodePrepare_roundTrip() {
        // Hand-build the raw I²C PacketAnimationPrepare bytes (5-byte meta + 20-byte v6 body) for a
        // FADE black→amber over 1000ms with add blend at composeOrder 2 and a 250ms onset, then
        // decode and drive a player — covering the byte offsets including the v6 compositor fields.
        val bytes = byteArrayOf(
            12, 1, 0, 0, 0,          // meta: type=12, version=1, headerCrc=0 (ignored by decode)
            ANIM_FADE.toByte(),      // animType
            1,                       // group_id
            0,                       // flags
            0,                       // transitionMs
            0xE8.toByte(), 0x03,     // durationMs = 1000 (LE)
            0, 0, 0, 0,              // colorFrom: kind=RGB, 0,0,0
            0, 200.toByte(), 100, 50,// colorTo:   kind=RGB, 200,100,50
            0,                       // param1
            0,                       // param2
            COMPOSE_ADD.toByte(),    // composeMode
            2,                       // composeOrder
            0xFA.toByte(), 0x00,     // startDelayMs = 250 (LE)
        )
        val state = decodeAnimationPrepare(bytes)
        assertEquals(ANIM_FADE, state.animType)
        assertEquals(1, state.groupId)
        assertEquals(1000, state.durationMs)
        assertEquals(ColorRef.rgb(0, 0, 0), state.colorFrom)
        assertEquals(ColorRef.rgb(200, 100, 50), state.colorTo)
        assertEquals(COMPOSE_ADD, state.composeMode)
        assertEquals(2, state.composeOrder)
        assertEquals(250, state.startDelayMs)

        // Drive it: with a 250ms onset, elapsed 750 → animElapsed 500 → fade midpoint.
        // Sole layer over a black background with ADD blend → just the source colour.
        val player = PanelAnimationPlayer()
        player.prepare(state)
        player.start(seqId = 1, groupId = 1, nowMs = 0)
        player.tick(nowMs = 750)
        assertEquals(rgb(100, 50, 25), player.currentColor)  // fade(500) from refgen
    }

    @Test
    fun compositor_layersBlendNotOverwrite() {
        // Two layers on one panel: a solid blue base (order 0) and an additive red accent
        // (order 1). The compositor adds them instead of the accent overwriting the base.
        val player = PanelAnimationPlayer()
        player.prepare(
            AnimationState(animType = ANIM_SOLID, groupId = 1, durationMs = 0,
                colorTo = ColorRef.rgb(0, 0, 200), composeMode = COMPOSE_NORMAL, composeOrder = 0)
        )
        player.start(seqId = 1, groupId = 1, nowMs = 0)
        player.prepare(
            AnimationState(animType = ANIM_SOLID, groupId = 2, durationMs = 0,
                colorTo = ColorRef.rgb(200, 0, 0), composeMode = COMPOSE_ADD, composeOrder = 1)
        )
        player.start(seqId = 2, groupId = 2, nowMs = 0)
        player.tick(nowMs = 10)
        assertEquals(rgb(200, 0, 200), player.currentColor)
    }

    @Test
    fun compositor_modifierDimsBelow() {
        // A MOD_BRIGHTNESS layer (order 1) at 50% halves the solid base (order 0).
        val player = PanelAnimationPlayer()
        player.prepare(
            AnimationState(animType = ANIM_SOLID, groupId = 1, durationMs = 0,
                colorTo = ColorRef.rgb(200, 100, 50), composeOrder = 0)
        )
        player.start(seqId = 1, groupId = 1, nowMs = 0)
        player.prepare(
            AnimationState(animType = ANIM_MOD_BRIGHTNESS, groupId = 2, durationMs = 0,
                param1 = 128, param2 = 128, composeOrder = 1)
        )
        player.start(seqId = 2, groupId = 2, nowMs = 0)
        player.tick(nowMs = 10)
        assertEquals(rgb(100, 50, 25), player.currentColor)
    }

    @Test
    fun strobe_matchesFirmware() {
        val to = ColorRef.rgb(255, 0, 0)
        val cases = mapOf(
            0L to rgb(255, 0, 0),
            25L to rgb(255, 0, 0),
            49L to rgb(255, 0, 0),
            50L to rgb(0, 0, 0),
            75L to rgb(0, 0, 0),
            99L to rgb(0, 0, 0),
            100L to rgb(255, 0, 0),
        )
        // param1 = hz; durationMs=0 so it never finishes.
        for ((elapsed, color) in cases) {
            assertEquals(color, colorAt(ANIM_STROBE, 0, ColorRef.rgb(0, 0, 0), to, elapsed, param1 = 10), "strobe e=$elapsed")
        }
    }
}
