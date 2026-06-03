package com.lightnet.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Drop-shadow rendering, kept apart from the panel-body layers so each technique lives in one place.
 *
 * [Layered] and [Feathered] draw inside the main panel canvas via [drawInCanvasShadow]. [NativeBlur]
 * is rendered by the composable on a separate `Modifier.blur` layer using [drawNativeBlurShape] — it
 * is a no-op here.
 */

/**
 * Draws the in-canvas shadow techniques. No-op for [ShadowImplementation.NativeBlur].
 * [cornerRadius] arrives already in canvas px; [scale] converts the layout-unit offsets/blur to px.
 */
internal fun DrawScope.drawInCanvasShadow(
    points: List<Offset>,
    path: Path,
    shadow: ShadowConfig,
    cornerRadius: Float,
    scale: Float,
) {
    if (!shadow.enabled) return
    when (shadow.implementation) {
        ShadowImplementation.Layered -> drawLayeredShadow(path, shadow, scale)
        ShadowImplementation.Feathered -> drawFeatheredShadow(points, shadow, cornerRadius, scale)
        ShadowImplementation.NativeBlur -> Unit // drawn on a separate blurred layer
    }
}

/** A few hard offset copies, fading toward the panel. Cheapest, sharpest. */
private fun DrawScope.drawLayeredShadow(path: Path, shadow: ShadowConfig, scale: Float) {
    val n = shadow.layered.layers.coerceAtLeast(1)
    for (i in n downTo 1) {
        val f = i.toFloat() / n
        translate(shadow.offsetX * scale * f, shadow.offsetY * scale * f) {
            drawPath(path, shadow.color.copy(alpha = shadow.color.alpha * f * 0.7f), style = Fill)
        }
    }
}

/**
 * Many faint copies of the outline, each expanded a little more than the last. Where the rings
 * overlap (near the panel) alpha accumulates; out at [FeatheredShadow.blur] only the largest ring
 * remains — a roughly linear falloff that reads as a soft, blurred edge on every platform.
 */
private fun DrawScope.drawFeatheredShadow(points: List<Offset>, shadow: ShadowConfig, cornerRadius: Float, scale: Float) {
    val steps = shadow.feathered.steps.coerceAtLeast(1)
    val ringAlpha = shadow.color.alpha / steps
    translate(shadow.offsetX * scale, shadow.offsetY * scale) {
        for (s in steps downTo 1) {
            val grow = shadow.feathered.blur * scale * (s.toFloat() / steps)
            val ringPath = buildPanelPath(expandPolygon(points, grow), cornerRadius + grow)
            drawPath(ringPath, shadow.color.copy(alpha = ringAlpha), style = Fill)
        }
    }
}

/**
 * Draws the sharp shadow silhouette for the [ShadowImplementation.NativeBlur] layer. The caller wraps
 * the layer in `Modifier.blur`, which turns this into a real Gaussian blur (Android 31+ / iOS).
 */
internal fun DrawScope.drawNativeBlurShape(path: Path, shadow: ShadowConfig, scale: Float) {
    translate(shadow.offsetX * scale, shadow.offsetY * scale) {
        drawPath(path, shadow.color, style = Fill)
    }
}

/**
 * Inner shadow — a dark rim clipped to the panel interior. Both techniques stack clipped strokes of
 * growing width (a stroke of width `w` paints a rim `w/2` deep): near the edge every stroke overlaps
 * so alpha accumulates, deeper in only the widest remain — a falloff from dark edge to clear centre.
 * [ShadowImplementation.Layered] uses few strokes (banded), the others use many (smooth).
 */
internal fun DrawScope.drawInnerShadow(path: Path, inner: InnerShadowConfig, scale: Float) {
    if (!inner.enabled) return
    val steps = when (inner.implementation) {
        ShadowImplementation.Layered -> inner.layered.layers
        ShadowImplementation.Feathered, ShadowImplementation.NativeBlur -> inner.feathered.steps
    }.coerceAtLeast(1)
    val ringAlpha = inner.color.alpha / steps
    clipPath(path) {
        for (s in 1..steps) {
            val width = inner.width * scale * 2f * (s.toFloat() / steps)
            drawPath(path, inner.color.copy(alpha = ringAlpha), style = Stroke(width = width))
        }
    }
}
