package com.lightnet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.geometry.GeometryUtils

@Composable
fun LightnetDeviceVisualizer(
    panels: List<LightnetDevicePanel>,
    modifier: Modifier = Modifier,
) {
    // Collect each panel's state individually
    val states = panels.map { it.state.collectAsState() }

    BoxWithConstraints(modifier) {
        if (panels.isEmpty()) return@BoxWithConstraints

        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        // Bounding box over all edge coordinates
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

        // Polygon vertex lists in layout space for hit-testing
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

        val visitedInStroke = remember { mutableSetOf<Int>() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(panels, scale, offsetX, offsetY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var hasMoved = false
                        visitedInStroke.clear()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            if ((change.position - down.position).getDistance() > 4f) hasMoved = true

                            if (hasMoved) {
                                val idx = hitTest(change.position.x, change.position.y)
                                if (idx != null && visitedInStroke.add(idx)) panels[idx].toggle()
                                change.consume()
                            }
                        }

                        // Tap: released without significant movement
                        if (!hasMoved) hitTest(down.position.x, down.position.y)?.let { panels[it].toggle() }
                    }
                }
        ) {
            panels.forEachIndexed { i, panel ->
                val state = states[i].value

                val points = panel.layout.edgesCoords.entries
                    .sortedBy { it.key }
                    .map { (_, c) ->
                        Offset(
                            x = (c.x1.toFloat() + offsetX) * scale,
                            y = (c.y1.toFloat() + offsetY) * scale,
                        )
                    }

                if (points.size < 3) return@forEachIndexed

                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (j in 1 until points.size) lineTo(points[j].x, points[j].y)
                    close()
                }

                // Background: black fill + dark outline
                drawPath(path, color = Color.Black, style = Fill)
                drawPath(path, color = Color(0xFF444444), style = Stroke(width = 1.5f))

                // Coloured overlay when on
                if (state.on) {
                    drawPath(
                        path = path,
                        color = Color(
                            red   = state.color.r / 255f,
                            green = state.color.g / 255f,
                            blue  = state.color.b / 255f,
                            alpha = state.brightness / 255f,
                        ),
                        style = Fill,
                    )
                }
            }
        }
    }
}
