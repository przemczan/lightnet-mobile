package com.lightnet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.geometry.GeometryUtils
import com.lightnet.ui.toColorRgb
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PaintMode { Paint, Erase, Stamp }

@Composable
fun LightnetDeviceVisualizer(
    panels: List<LightnetDevicePanel>,
    modifier: Modifier = Modifier,
    powerOn: Boolean = true,
    paintMode: PaintMode = PaintMode.Paint,
    paintColor: Color = Color.White,
    interactive: Boolean = true,
    showPanelIds: Boolean = false,
    selectionMode: Boolean = false,
    selectedPanels: Set<Int> = emptySet(),
    onSelectionChange: (Set<Int>) -> Unit = {},
    onEnterSelectionMode: (firstPanelIndex: Int) -> Unit = {},
    config: PanelVisualConfig = PanelVisualConfig(),
) {
    val states = panels.map { it.state.collectAsState() }
    val textMeasurer = rememberTextMeasurer()

    // Stable refs for gesture handler — changes don't restart the gesture block.
    val currentPaintMode         = rememberUpdatedState(paintMode)
    val currentPaintColor        = rememberUpdatedState(paintColor)
    val currentInteractive       = rememberUpdatedState(interactive)
    val currentSelectionMode     = rememberUpdatedState(selectionMode)
    val currentSelectedPanels    = rememberUpdatedState(selectedPanels)
    val currentOnSelectionChange = rememberUpdatedState(onSelectionChange)
    val currentOnEnterSelection  = rememberUpdatedState(onEnterSelectionMode)

    BoxWithConstraints(modifier) {
        if (panels.isEmpty()) return@BoxWithConstraints

        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        val allCoords = panels.flatMap { it.layout.edgesCoords.values }
        val minX = allCoords.minOf { minOf(it.x1, it.x2) }.toFloat()
        val minY = allCoords.minOf { minOf(it.y1, it.y2) }.toFloat()
        val maxX = allCoords.maxOf { maxOf(it.x1, it.x2) }.toFloat()
        val maxY = allCoords.maxOf { maxOf(it.y1, it.y2) }.toFloat()

        val pad = 32f
        val scale = minOf(
            (viewW - pad * 2) / (maxX - minX).coerceAtLeast(1f),
            (viewH - pad * 2) / (maxY - minY).coerceAtLeast(1f),
        )
        val offsetX = -minX + pad / scale
        val offsetY = -minY + pad / scale

        // Per-panel polygon vertices in layout coordinates (sorted by edge index).
        val polygons = remember(panels) {
            panels.map { panel ->
                panel.layout.edgesCoords.entries
                    .sortedBy { it.key }
                    .map { GeometryUtils.Point(it.value.x1, it.value.y1) }
            }
        }

        // Entrance animation: per-panel displacement that decays to zero on appearance.
        val screenXCenters = remember(panels) {
            polygons.map { poly -> if (poly.isEmpty()) 0f else (poly.sumOf { it.x } / poly.size).toFloat() }
        }
        val entrancePlan = rememberEntrancePlan(panels, config, viewW, viewH, screenXCenters)
        val animOffsets = panels.indices.map { i ->
            entrancePlan.startOffsets[i] * entrancePlan.animatables[i].value
        }

        // Per-panel screen-space geometry, shared by the shadow layer and the main canvas.
        // Rebuilt only when layout/scale or shape params change — never per animation frame.
        val rendered = remember(panels, scale, offsetX, offsetY, config.panelPadding, config.cornerRadius) {
            panels.indices.mapNotNull { i ->
                val rawPoints = panels[i].layout.edgesCoords.entries
                    .sortedBy { it.key }
                    .map { (_, c) ->
                        Offset(
                            x = (c.x1.toFloat() + offsetX) * scale,
                            y = (c.y1.toFloat() + offsetY) * scale,
                        )
                    }
                if (rawPoints.size < 3) return@mapNotNull null
                val points = if (config.panelPadding > 0f) shrinkPolygon(rawPoints, config.panelPadding) else rawPoints
                PanelRender(i, points, buildPanelPath(points, config.cornerRadius))
            }
        }

        fun hitTest(sx: Float, sy: Float): Int? {
            val lx = (sx / scale - offsetX).toDouble()
            val ly = (sy / scale - offsetY).toDouble()
            return panels.indices.firstOrNull { i -> GeometryUtils.isInsidePolygon(lx, ly, polygons[i]) }
        }

        fun applyPaint(idx: Int) {
            when (currentPaintMode.value) {
                PaintMode.Paint, PaintMode.Stamp -> {
                    panels[idx].setColor(currentPaintColor.value.toColorRgb())
                    panels[idx].toggle(on = true)
                }
                PaintMode.Erase -> panels[idx].toggle(on = false)
            }
        }

        val gestureModifier = if (powerOn && (interactive || selectionMode)) {
            Modifier.pointerInput(panels, scale, offsetX, offsetY) {
                val visitedInStroke = mutableSetOf<Int>()

                coroutineScope {
                val cs = this
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var hasMoved = false
                    var longPressTriggered = false
                    visitedInStroke.clear()

                    val longPressJob = if (!currentSelectionMode.value && currentInteractive.value) {
                        cs.launch {
                            delay(500L)
                            if (!hasMoved) {
                                longPressTriggered = true
                                hitTest(down.position.x, down.position.y)?.let { idx ->
                                    currentOnEnterSelection.value(idx)
                                }
                            }
                        }
                    } else null

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        if ((change.position - down.position).getDistance() > 4f) {
                            hasMoved = true
                            longPressJob?.cancel()
                        }

                        if (hasMoved && currentInteractive.value && !currentSelectionMode.value) {
                            val idx = hitTest(change.position.x, change.position.y)
                            if (idx != null && visitedInStroke.add(idx)) applyPaint(idx)
                            change.consume()
                        }
                    }

                    longPressJob?.cancel()

                    if (!hasMoved && !longPressTriggered) {
                        val idx = hitTest(down.position.x, down.position.y)
                        when {
                            currentSelectionMode.value && idx != null -> {
                                val cur = currentSelectedPanels.value
                                currentOnSelectionChange.value(if (idx in cur) cur - idx else cur + idx)
                            }
                            currentInteractive.value && idx != null -> applyPaint(idx)
                        }
                    }
                }
                } // coroutineScope
            }
        } else Modifier


        val shadow = config.shadow

        // NativeBlur shadows live on their own layer so `Modifier.blur` can turn the sharp
        // silhouettes into a real Gaussian blur (Android 31+ / iOS; a no-op on older Android).
        if (shadow.enabled && shadow.implementation == ShadowImplementation.NativeBlur) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(shadow.nativeBlur.radius.dp, BlurredEdgeTreatment.Unbounded)
            ) {
                rendered.forEach { r ->
                    translate(animOffsets[r.index].x, animOffsets[r.index].y) {
                        drawNativeBlurShape(r.path, shadow)
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            // Pass 1 — in-canvas drop shadows beneath every panel body, so a panel never casts onto
            // a neighbour. (Layered / Feathered only; NativeBlur is handled by the layer above.)
            rendered.forEach { r ->
                translate(animOffsets[r.index].x, animOffsets[r.index].y) {
                    drawInCanvasShadow(r.points, r.path, shadow, config.cornerRadius)
                }
            }

            // Pass 2 — panel bodies and overlays.
            rendered.forEach { r ->
                val panel = panels[r.index]
                val state = states[r.index].value
                translate(animOffsets[r.index].x, animOffsets[r.index].y) {
                    drawPanelBackground(r.path, config)

                    if (powerOn) {
                        drawPanelActiveColor(
                            r.path,
                            Color(
                                red   = state.color.r / 255f,
                                green = state.color.g / 255f,
                                blue  = state.color.b / 255f,
                            ),
                        )
                    }

                    drawPanelBorder(r.path, config)

                    // After the fill so the rim darkens the lit colour, giving a recessed look.
                    drawInnerShadow(r.path, config.innerShadow)

                    if (selectionMode) drawPanelSelection(r.path, isSelected = r.index in selectedPanels)

                    if (showPanelIds) drawPanelLabel(panel.info.id.toString(), r.points, textMeasurer)
                }
            }
        }
    }
}

/** Resolved per-panel render geometry, built once per layout. */
private data class PanelRender(val index: Int, val points: List<Offset>, val path: Path)

// ── Per-layer draw helpers ────────────────────────────────────────────────────
// One function per panel-body layer so a single layer can be tweaked in isolation.
// (Drop-shadow techniques live in VisualizerShadows.kt; geometry in VisualizerGeometry.kt.)

private fun DrawScope.drawPanelBackground(path: Path, config: PanelVisualConfig) {
    drawPath(path, color = config.backgroundColor, style = Fill)
}

private fun DrawScope.drawPanelBorder(path: Path, config: PanelVisualConfig) {
    if (config.borderWidth > 0f) {
        drawPath(path, color = config.borderColor, style = Stroke(width = config.borderWidth))
    }
}

private fun DrawScope.drawPanelActiveColor(path: Path, color: Color) {
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawPanelSelection(path: Path, isSelected: Boolean) {
    if (isSelected) {
        drawPath(path, color = Color.White.copy(alpha = 0.25f), style = Fill)
        drawPath(path, color = Color.White, style = Stroke(width = 2.5f))
    } else {
        drawPath(path, color = Color.Black.copy(alpha = 0.55f), style = Fill)
    }
}

private fun DrawScope.drawPanelLabel(label: String, points: List<Offset>, textMeasurer: TextMeasurer) {
    val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
    val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
    val measured = textMeasurer.measure(
        label,
        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
    val shadowMeasured = textMeasurer.measure(
        label,
        style = TextStyle(color = Color.Black.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
    val tx = cx - measured.size.width / 2f
    val ty = cy - measured.size.height / 2f
    drawText(shadowMeasured, topLeft = Offset(tx + 1f, ty + 1f))
    drawText(measured, topLeft = Offset(tx, ty))
}
