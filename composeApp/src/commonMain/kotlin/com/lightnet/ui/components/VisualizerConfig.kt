package com.lightnet.ui.components

import androidx.compose.ui.graphics.Color

/** Entrance animation played when panels first appear (and on every reconnect). */
enum class PanelAnimationStyle {
    /** Each panel flies in from a random direction outside the viewport. */
    FromDirections,

    /** Panels drop from the top, staggered left-to-right like rain. */
    Rain,

    /** Panels grow from zero size to full size with a slight bounce, staggered randomly. */
    PopUp,

    /** Pick one of the concrete styles at random on each appearance. */
    Random,
}

/**
 * All visual + animation tuning for [LightnetDeviceVisualizer], grouped in one place so a single
 * panel layer can be adjusted in isolation. Every field defaults to the original look (effects off,
 * sharp corners) so existing call sites render unchanged.
 */
data class PanelVisualConfig(
    // ── Geometry ──────────────────────────────────────────────────────────────
    // All sizes below are in *panel-layout units* (edge length ≈ 100), multiplied by the
    // canvas `scale` at draw time so they stay proportional regardless of panel count.
    val borderWidth: Float = 4f,
    val panelPadding: Float = 5f,
    /** Corner rounding radius in layout units. 0 = sharp corners. */
    val cornerRadius: Float = 6f,

    // ── Colors ──────────────────────────────────────────────────────────────
    val borderColor: Color = Color(0xFF444444),
    val backgroundColor: Color = Color.Black,

    // ── Drop shadow ─────────────────────────────────────────────────────────
    val shadow: ShadowConfig = ShadowConfig(),

    // ── Inner shadow ────────────────────────────────────────────────────────
    val innerShadow: InnerShadowConfig = InnerShadowConfig(),

    // ── Entrance animation ──────────────────────────────────────────────────
    val animationStyle: PanelAnimationStyle = PanelAnimationStyle.Random,
    /** Per-panel animation duration in ms. */
    val animationSpeedMs: Int = 400,

    // ── Rotation ──────────────────────────────────────────────────────────────
    /** Degrees of view rotation per px of horizontal swipe while in rotate mode. */
    val rotateSensitivity: Float = 0.35f,
)

/** Shadow rendering technique, shared by [ShadowConfig] (drop) and [InnerShadowConfig] (inner). */
enum class ShadowImplementation {
    /** A few hard steps. Cheapest; sharp, banded edge. */
    Layered,

    /** Many faint steps whose alpha accumulates — a soft edge on every platform. */
    Feathered,

    /**
     * A real Gaussian blur on a separate layer via `Modifier.blur` (Android 31+ / iOS; degrades to a
     * sharp silhouette on Android 24–30). Drop shadows only — inner shadows fall back to [Feathered],
     * since a per-panel clipped Gaussian blur isn't expressible in commonMain.
     */
    NativeBlur,
}

/**
 * Drop-shadow configuration. The fields above the divider are shared by every implementation; the
 * nested param objects below hold the knobs specific to one [ShadowImplementation].
 */
data class ShadowConfig(
    val enabled: Boolean = true,
    val implementation: ShadowImplementation = ShadowImplementation.NativeBlur,

    // ── Common to all implementations ─────────────────────────────────────────
    // offsets in panel-layout units, scaled by canvas `scale` at draw time.
    val color: Color = Color.Black.copy(alpha = 1f),
    val offsetX: Float = 4f,
    val offsetY: Float = 6f,

    // ── Implementation-specific ────────────────────────────────────────────────
    val layered: LayeredShadow = LayeredShadow(),
    val feathered: FeatheredShadow = FeatheredShadow(),
    val nativeBlur: NativeBlurShadow = NativeBlurShadow(),
)

/** Params for [ShadowImplementation.Layered]. */
data class LayeredShadow(
    /** Number of stacked offset copies (1–3). More = softer but more banded. */
    val layers: Int = 8,
)

/** Params for [ShadowImplementation.Feathered]. */
data class FeatheredShadow(
    /** How far the soft edge feathers outward from the panel, in layout units (scaled at draw time). */
    val blur: Float = 6f,
    /** Feather resolution: number of expanding rings. Higher = smoother, more fills per frame. */
    val steps: Int = 12,
)

/** Params for [ShadowImplementation.NativeBlur]. */
data class NativeBlurShadow(
    /** Gaussian blur radius in layout units (multiplied by canvas `scale` → dp at draw time). */
    val radius: Float = 6f,
)

/**
 * Inner-shadow configuration — a dark rim clipped to the panel interior, giving a recessed/bevelled
 * feel. Mirrors [ShadowConfig]: shared fields above the divider, per-implementation knobs below.
 * Both techniques are clipped strokes that differ only in step count; [ShadowImplementation.NativeBlur]
 * falls back to [ShadowImplementation.Feathered] here.
 */
data class InnerShadowConfig(
    val enabled: Boolean = false,
    val implementation: ShadowImplementation = ShadowImplementation.Feathered,

    // ── Common to all implementations ─────────────────────────────────────────
    val color: Color = Color.Black.copy(alpha = 0.25f),
    /** Rim thickness in layout units (scaled at draw time). */
    val width: Float = 2.5f,

    // ── Implementation-specific ────────────────────────────────────────────────
    val layered: LayeredInnerShadow = LayeredInnerShadow(),
    val feathered: FeatheredInnerShadow = FeatheredInnerShadow(),
)

/** Params for the [ShadowImplementation.Layered] inner shadow. */
data class LayeredInnerShadow(
    /** Number of discrete bands across the rim. Few = banded. */
    val layers: Int = 5,
)

/** Params for the [ShadowImplementation.Feathered] inner shadow. */
data class FeatheredInnerShadow(
    /** Number of steps across the rim. Higher = smoother, more strokes per frame. */
    val steps: Int = 8,
)
