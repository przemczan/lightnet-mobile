package com.lightnet.device

import com.lightnet.api.websocket.model.EdgeCoords
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.model.PanelLayout
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object PanelsLayoutService {

    fun generateLayout(panelsList: List<PanelInfo>, edgeLength: Double = 100.0): List<PanelLayout> {
        val layouts = mutableMapOf<Int, PanelLayout>()
        for (panel in panelsList) {
            layouts[panel.id] = buildLayout(panel, layouts, edgeLength)
        }
        return layouts.values.toList()
    }

    private fun buildLayout(panel: PanelInfo, layouts: Map<Int, PanelLayout>, edgeLength: Double): PanelLayout {
        val layout = PanelLayout(panelId = panel.id)

        panel.edges.forEachIndexed { position, edge ->
            layout.edgesCoords[edge.index] = generateEdgeCoords(layout, panel, position, edgeLength)
        }

        val parentCoords = getParentEdgeCoords(panel, layouts)
        if (parentCoords != null && panel.rootEdge != null) {
            val rootCoords = layout.edgesCoords[panel.rootEdge!!.index]!!
            movePanel(layout, parentCoords.x2 - rootCoords.x1, parentCoords.y2 - rootCoords.y1)
            rotatePanel(
                layout,
                parentCoords.x2,
                parentCoords.y2,
                angleBetween(
                    parentCoords.x1 - parentCoords.x2, parentCoords.y1 - parentCoords.y2,
                    rootCoords.x2 - rootCoords.x1,     rootCoords.y2 - rootCoords.y1,
                ),
            )
        }

        return layout
    }

    private fun generateEdgeCoords(
        layout: PanelLayout,
        panel: PanelInfo,
        position: Int,
        edgeLength: Double,
    ): EdgeCoords {
        val angleStep = 360.0 / panel.edges.size
        val radians = angleStep * position * (PI / 180.0)
        val x1 = if (position > 0) layout.edgesCoords[position - 1]!!.x2 else 0.0
        val y1 = if (position > 0) layout.edgesCoords[position - 1]!!.y2 else 0.0
        return EdgeCoords(
            x1 = x1,
            y1 = y1,
            x2 = (x1 + edgeLength * cos(radians)).roundToInt().toDouble(),
            y2 = (y1 + edgeLength * sin(radians)).roundToInt().toDouble(),
        )
    }

    private fun getParentEdgeCoords(panel: PanelInfo, layouts: Map<Int, PanelLayout>): EdgeCoords? {
        val rootEdge = panel.rootEdge ?: return null
        val parentLayout = layouts[rootEdge.connectedEdge!!.panel.id] ?: return null
        return parentLayout.edgesCoords[rootEdge.connectedEdge!!.index]
    }

    private fun movePanel(layout: PanelLayout, dx: Double, dy: Double) {
        layout.edgesCoords.replaceAll { _, c ->
            c.copy(x1 = c.x1 + dx, y1 = c.y1 + dy, x2 = c.x2 + dx, y2 = c.y2 + dy)
        }
    }

    private fun rotatePanel(layout: PanelLayout, cx: Double, cy: Double, degrees: Double) {
        layout.edgesCoords.replaceAll { _, c ->
            val (nx1, ny1) = rotatePoint(cx, cy, c.x1, c.y1, degrees)
            val (nx2, ny2) = rotatePoint(cx, cy, c.x2, c.y2, degrees)
            EdgeCoords(nx1, ny1, nx2, ny2)
        }
    }

    private fun rotatePoint(cx: Double, cy: Double, x: Double, y: Double, degrees: Double): Pair<Double, Double> {
        val radians = PI / 180.0 * degrees
        val cosA = cos(radians)
        val sinA = sin(radians)
        val nx = cosA * (x - cx) + sinA * (y - cy) + cx
        val ny = cosA * (y - cy) - sinA * (x - cx) + cy
        return Pair(nx.roundToInt().toDouble(), ny.roundToInt().toDouble())
    }

    private fun angleBetween(x1: Double, y1: Double, x2: Double, y2: Double): Double =
        ((atan2(x1 * y2 - y1 * x2, x1 * x2 + y1 * y2) * 180.0) / PI).roundToInt().toDouble()
}
