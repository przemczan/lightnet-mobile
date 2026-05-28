package com.lightnet.api.websocket

import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel
import kotlin.random.Random

object PanelsGenerator {
    fun generateEdges(panelCount: Int, minEdges: Int, maxEdges: Int): List<PanelEdgeInfoModel> {
        val edges = mutableListOf<PanelEdgeInfoModel>()
        val takenEdges = mutableListOf<PanelEdgeInfoModel>()
        for (id in 1..panelCount) {
            generatePanelEdges(id, minEdges, maxEdges, edges, takenEdges)
        }
        return edges
    }

    private fun generatePanelEdges(
        id: Int,
        minEdges: Int,
        maxEdges: Int,
        edges: MutableList<PanelEdgeInfoModel>,
        takenEdges: MutableList<PanelEdgeInfoModel>,
    ) {
        var edgeCount = randomInt(minEdges, maxEdges)
        val rootEdgeIndex = if (edges.isNotEmpty()) randomInt(0, edgeCount - 1) else -1

        if (edges.isNotEmpty()) {
            var parentEdge: PanelEdgeInfoModel
            do {
                parentEdge = edges[randomInt(0, edges.size - 1)]
            } while (takenEdges.contains(parentEdge))

            takenEdges.add(parentEdge)
            val idx = edges.indexOf(parentEdge)
            edges[idx] = parentEdge.copy(connectedPanelId = id, connectedEdgeIndex = rootEdgeIndex)
        }

        while (edgeCount-- > 0) {
            val edge = PanelEdgeInfoModel(
                panelId = id,
                edgeIndex = edgeCount,
                connectedPanelId = 0,
                connectedEdgeIndex = 0,
            )
            edges.add(edge)
            if (edge.edgeIndex == rootEdgeIndex) takenEdges.add(edge)
        }
    }

    private fun randomInt(min: Int, max: Int): Int =
        if (min >= max) min else Random.nextInt(min, max + 1)
}
