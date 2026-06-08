package com.lightnet.animation

import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the Kotlin compose/modifier math (ColorCompose.kt) to the firmware
 * lib/Lightnet/Common/ColorCompose.hpp — same deterministic values asserted by the native
 * test_compositor suite, so the mobile preview composites identically to the hardware.
 */
class ColorComposeTest {

    private fun rgb(r: Int, g: Int, b: Int) = ColorRgbModel(r, g, b)
    private fun assertRgb(c: ColorRgbModel, r: Int, g: Int, b: Int) {
        assertEquals(rgb(r, g, b), c)
    }

    @Test fun blendModes() {
        assertRgb(composeColor(rgb(10, 20, 30), rgb(200, 100, 50), COMPOSE_OPAQUE), 200, 100, 50)
        assertRgb(composeColor(rgb(200, 100, 0), rgb(100, 100, 5), COMPOSE_ADD), 255, 200, 5)
        assertRgb(composeColor(rgb(200, 10, 50), rgb(100, 100, 50), COMPOSE_MAX), 200, 100, 50)
        assertRgb(composeColor(rgb(255, 128, 0), rgb(128, 255, 255), COMPOSE_MULTIPLY), 128, 128, 0)
        assertRgb(composeColor(rgb(128, 0, 255), rgb(128, 0, 0), COMPOSE_SCREEN), 192, 0, 255)
    }

    @Test fun brightnessModifierExact() {
        assertRgb(modBrightness(rgb(200, 100, 50), 255), 200, 100, 50)
        assertRgb(modBrightness(rgb(200, 100, 50), 0), 0, 0, 0)
        assertRgb(modBrightness(rgb(200, 100, 50), 128), 100, 50, 25)
    }

    @Test fun saturationAndHueIdentityNoDrift() {
        // Identity values bypass the approximate HSV round-trip exactly (matches firmware guard).
        assertRgb(modSaturation(rgb(123, 45, 200), 255), 123, 45, 200)
        assertRgb(modHueShift(rgb(123, 45, 200), 0), 123, 45, 200)
        // Zero saturation → grey at the same value.
        val grey = modSaturation(rgb(255, 0, 0), 0)
        assertEquals(grey.r, grey.g); assertEquals(grey.g, grey.b); assertEquals(255, grey.r)
    }

    @Test fun foldRunnerOverBackground() {
        val bg = rgb(0, 0, 40)
        // Off-phase (black accent, MAX) leaves the background; lit-phase brightens it.
        assertRgb(foldLayers(listOf(CompositeLayer(0, false, COMPOSE_MAX, color = rgb(0, 0, 0))), bg), 0, 0, 40)
        assertRgb(foldLayers(listOf(CompositeLayer(0, false, COMPOSE_MAX, color = rgb(200, 60, 0))), bg), 200, 60, 40)
    }

    @Test fun foldModifierDimsBelowByOrder() {
        val layers = listOf(
            CompositeLayer(0, false, COMPOSE_OPAQUE, color = rgb(200, 100, 50)),
            CompositeLayer(1, true, MO_BRIGHTNESS, value = 128),
        )
        assertRgb(foldLayers(layers, rgb(0, 0, 0)), 100, 50, 25)
    }

    @Test fun foldEmptyIsBase() {
        assertRgb(foldLayers(emptyList(), rgb(5, 10, 15)), 5, 10, 15)
        assertTrue(true)
    }
}
