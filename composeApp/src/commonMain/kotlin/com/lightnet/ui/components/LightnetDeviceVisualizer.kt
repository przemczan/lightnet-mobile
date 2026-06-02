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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.geometry.GeometryUtils
import com.lightnet.ui.toColorRgb
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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
    borderWidth: Float = 6f,
    panelPadding: Float = 12f,
    borderColor: Color = Color(0xFF444444),
    backgroundColor: Color = Color.Black,
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

        val polygons = remember(panels) {
            panels.map { panel ->
                panel.layout.edgesCoords.entries
                    .sortedBy { it.key }
                    .map { GeometryUtils.Point(it.value.x1, it.value.y1) }
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


        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            panels.forEachIndexed { i, panel ->
                val state = states[i].value

                val rawPoints = panel.layout.edgesCoords.entries
                    .sortedBy { it.key }
                    .map { (_, c) ->
                        Offset(
                            x = (c.x1.toFloat() + offsetX) * scale,
                            y = (c.y1.toFloat() + offsetY) * scale,
                        )
                    }

                if (rawPoints.size < 3) return@forEachIndexed

                val points = if (panelPadding > 0f) shrinkPolygon(rawPoints, panelPadding) else rawPoints

                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (j in 1 until points.size) lineTo(points[j].x, points[j].y)
                    close()
                }

                drawPath(path, color = backgroundColor, style = Fill)
                if (borderWidth > 0f) {
                    drawPath(path, color = borderColor, style = Stroke(width = borderWidth))
                }

                if (state.on && powerOn) {
                    drawPath(
                        path = path,
                        color = Color(
                            red   = state.color.r / 255f,
                            green = state.color.g / 255f,
                            blue  = state.color.b / 255f,
                        ),
                        style = Fill,
                    )
                }

                if (selectionMode) {
                    if (i in selectedPanels) {
                        drawPath(path, color = Color.White.copy(alpha = 0.25f), style = Fill)
                        drawPath(path, color = Color.White, style = Stroke(width = 2.5f))
                    } else {
                        drawPath(path, color = Color.Black.copy(alpha = 0.55f), style = Fill)
                    }
                }

                if (showPanelIds) {
                    val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
                    val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
                    val label = panel.info.id.toString()
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
            }
        }
    }
}

private fun shrinkPolygon(points: List<Offset>, padding: Float): List<Offset> {
    val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
    val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
    return points.map { p ->
        val dx = p.x - cx
        val dy = p.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= padding) Offset(cx, cy)
        else Offset(cx + dx * (dist - padding) / dist, cy + dy * (dist - padding) / dist)
    }
}
