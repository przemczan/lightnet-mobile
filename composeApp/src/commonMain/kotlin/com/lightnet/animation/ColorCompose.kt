package com.lightnet.animation

import com.lightnet.api.websocket.protocol.model.ColorRgbModel

/**
 * Kotlin port of the firmware lib/Lightnet/Common/ColorCompose.hpp — the panel layer
 * compositor's blend + modifier + HSV math. Integer expressions mirror the firmware exactly so
 * the mobile live-preview composites identically to the hardware.
 */

// Blend modes for SOURCE layers (firmware ComposeMode / ComposeOp).
const val COMPOSE_OPAQUE = 0
const val COMPOSE_ADD = 1
const val COMPOSE_MAX = 2
const val COMPOSE_MULTIPLY = 3
const val COMPOSE_SCREEN = 4
const val COMPOSE_DARKEN = 6
const val COMPOSE_OVERLAY = 7
const val COMPOSE_DIFFERENCE = 8
const val COMPOSE_SUBTRACT = 9

// Modifier ops (firmware ModOp), distinct from the ANIM_MOD_* animTypes.
const val MO_BRIGHTNESS = 0
const val MO_SATURATION = 1
const val MO_HUE = 2
const val MO_INVERT = 3

private fun clampAdd(a: Int, b: Int): Int = (a + b).let { if (it > 255) 255 else it }
private fun mul(a: Int, b: Int): Int = (a * b) / 255
private fun screen(a: Int, b: Int): Int = 255 - mul(255 - a, 255 - b)
private fun maxc(a: Int, b: Int): Int = if (a > b) a else b
private fun minc(a: Int, b: Int): Int = if (a < b) a else b
private fun overlay(a: Int, b: Int): Int = if (a < 128) (2 * a * b) / 255 else 255 - (2 * (255 - a) * (255 - b)) / 255
private fun diff(a: Int, b: Int): Int = if (a > b) a - b else b - a
private fun sub(a: Int, b: Int): Int = if (a > b) a - b else 0

/** Fold a source colour onto the accumulator using the given blend op. */
fun composeColor(acc: ColorRgbModel, src: ColorRgbModel, op: Int): ColorRgbModel = when (op) {
    COMPOSE_ADD -> ColorRgbModel(clampAdd(acc.r, src.r), clampAdd(acc.g, src.g), clampAdd(acc.b, src.b))
    COMPOSE_MAX -> ColorRgbModel(maxc(acc.r, src.r), maxc(acc.g, src.g), maxc(acc.b, src.b))
    COMPOSE_MULTIPLY -> ColorRgbModel(mul(acc.r, src.r), mul(acc.g, src.g), mul(acc.b, src.b))
    COMPOSE_SCREEN -> ColorRgbModel(screen(acc.r, src.r), screen(acc.g, src.g), screen(acc.b, src.b))
    COMPOSE_DARKEN -> ColorRgbModel(minc(acc.r, src.r), minc(acc.g, src.g), minc(acc.b, src.b))
    COMPOSE_OVERLAY -> ColorRgbModel(overlay(acc.r, src.r), overlay(acc.g, src.g), overlay(acc.b, src.b))
    COMPOSE_DIFFERENCE -> ColorRgbModel(diff(acc.r, src.r), diff(acc.g, src.g), diff(acc.b, src.b))
    COMPOSE_SUBTRACT -> ColorRgbModel(sub(acc.r, src.r), sub(acc.g, src.g), sub(acc.b, src.b))
    else -> src // COMPOSE_OPAQUE
}

// ── Integer HSV (H/S/V 0..255), classic 6-sector — matches ColorCompose.hpp ──

private data class Hsv(val h: Int, val s: Int, val v: Int)

private fun rgb2hsv(c: ColorRgbModel): Hsv {
    val mx = maxc(maxc(c.r, c.g), c.b)
    val mn = if (c.r < c.g) (if (c.r < c.b) c.r else c.b) else (if (c.g < c.b) c.g else c.b)
    if (mx == 0) return Hsv(0, 0, 0)
    val delta = mx - mn
    val s = (delta * 255) / mx
    if (delta == 0) return Hsv(0, s, mx)
    val h = when (mx) {
        c.r -> (c.g - c.b) * 43 / delta
        c.g -> 85 + (c.b - c.r) * 43 / delta
        else -> 171 + (c.r - c.g) * 43 / delta
    }
    return Hsv(h and 0xFF, s, mx)
}

private fun hsv2rgb(c: Hsv): ColorRgbModel {
    if (c.s == 0) return ColorRgbModel(c.v, c.v, c.v)
    val region = c.h / 43
    val remainder = (c.h - region * 43) * 6
    val p = (c.v * (255 - c.s)) / 255
    val q = (c.v * (255 - (c.s * remainder) / 255)) / 255
    val t = (c.v * (255 - (c.s * (255 - remainder)) / 255)) / 255
    return when (region) {
        0 -> ColorRgbModel(c.v, t, p)
        1 -> ColorRgbModel(q, c.v, p)
        2 -> ColorRgbModel(p, c.v, t)
        3 -> ColorRgbModel(p, q, c.v)
        4 -> ColorRgbModel(t, p, c.v)
        else -> ColorRgbModel(c.v, p, q)
    }
}

// ── Modifier ops (identity values bypass the approximate HSV round-trip) ──

fun modBrightness(acc: ColorRgbModel, value: Int) = ColorRgbModel(mul(acc.r, value), mul(acc.g, value), mul(acc.b, value))

fun modSaturation(acc: ColorRgbModel, value: Int): ColorRgbModel {
    if (value == 255) return acc
    val h = rgb2hsv(acc)
    return hsv2rgb(h.copy(s = mul(h.s, value)))
}

fun modHueShift(acc: ColorRgbModel, value: Int): ColorRgbModel {
    if (value == 0) return acc
    val h = rgb2hsv(acc)
    return hsv2rgb(h.copy(h = (h.h + value) and 0xFF))
}

fun modInvert(acc: ColorRgbModel, value: Int): ColorRgbModel {
    if (value == 0) return acc
    val inv = ColorRgbModel(255 - acc.r, 255 - acc.g, 255 - acc.b)
    if (value == 255) return inv
    return ColorRgbModel(
        acc.r + ((inv.r - acc.r) * value) / 255,
        acc.g + ((inv.g - acc.g) * value) / 255,
        acc.b + ((inv.b - acc.b) * value) / 255,
    )
}

// ── Layer fold (the compositor's per-tick contract) ──

/** One active layer contribution for [foldLayers]. */
data class CompositeLayer(
    val composeOrder: Int,
    val isModifier: Boolean,
    val op: Int,            // ComposeOp (source) or ModOp (modifier)
    val value: Int = 0,     // modifier scalar
    val color: ColorRgbModel = ColorRgbModel(0, 0, 0),
)

/**
 * Fold [layers] (already filtered to actually-contributing slots) onto [base] in ascending
 * composeOrder. Source layers blend via their ComposeOp; modifier layers transform the
 * accumulator. Mirrors AnimationPlayer::composite() / ColorCompose::foldLayers.
 */
fun foldLayers(layers: List<CompositeLayer>, base: ColorRgbModel): ColorRgbModel {
    var acc = base
    for (l in layers.sortedBy { it.composeOrder }) {
        acc = if (l.isModifier) {
            when (l.op) {
                MO_BRIGHTNESS -> modBrightness(acc, l.value)
                MO_SATURATION -> modSaturation(acc, l.value)
                MO_HUE -> modHueShift(acc, l.value)
                MO_INVERT -> modInvert(acc, l.value)
                else -> acc
            }
        } else {
            composeColor(acc, l.color, l.op)
        }
    }
    return acc
}
