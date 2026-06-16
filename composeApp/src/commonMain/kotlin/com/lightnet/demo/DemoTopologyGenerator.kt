package com.lightnet.demo

import com.lightnet.api.websocket.model.EdgeInfo
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.protocol.model.PanelEdgeInfoModel
import com.lightnet.device.PanelsLayoutService
import kotlin.random.Random

internal object DemoTopologyGenerator {
    private const val EDGES_PER_PANEL = 3
    // How many recent free edges to consider when branching (≈ last 2–3 placed panels).
    private const val BRANCH_RECENT_EDGES = 6

    /**
     * Generates a random spanning-tree topology of [panelCount] panels guaranteed to have no
     * geometric overlaps. For each new panel, all valid (parentPanel, parentEdge, childEdge)
     * combinations are tried in shuffled order; the first placement that passes a SAT convex-
     * polygon overlap test is accepted. Mirrors the approach in PanelsInitializerSim.cpp.
     */
    fun generate(panelCount: Int): List<PanelEdgeInfoModel> {
        if (panelCount <= 0) return emptyList()

        val edgeModels = mutableListOf<PanelEdgeInfoModel>()

        // Free (panelId, edgeIndex) pairs — edges not yet connected to another panel.
        // Newer panels' edges are appended to the end, so takeLast() = most-recently-added edges.
        val freeEdges = mutableListOf<Pair<Int, Int>>()

        // Root panel: all edges free.
        for (e in 0 until EDGES_PER_PANEL) {
            edgeModels.add(PanelEdgeInfoModel(1, e, 0, 0))
            freeEdges.add(1 to e)
        }

        // Per-generation branch factor: 0 = uniform blob, ~0.85 = very branchy tree.
        // Randomised each run so shapes vary across calls.
        val branchFactor = Random.nextFloat() * 0.85f

        for (id in 2..panelCount) {
            // Prefer recent free edges (branch tips) with probability branchFactor.
            // Fewer than BRANCH_RECENT_EDGES available → fall back to all edges automatically.
            val useRecent = Random.nextFloat() < branchFactor && freeEdges.size > BRANCH_RECENT_EDGES
            val pool = if (useRecent) freeEdges.takeLast(BRANCH_RECENT_EDGES) else freeEdges

            var accepted = firstNonOverlapping(edgeModels, id, pool)

            // If the branch tip is blocked by overlap, retry against the full free-edge set.
            if (accepted == null && useRecent) accepted = firstNonOverlapping(edgeModels, id, freeEdges)

            // Fallback: all orientations collided (shouldn't happen with triangles).
            val (parentId, parentEdge, childEdge) = accepted
                ?: buildCandidates(freeEdges).first()

            val parentIdx = edgeModels.indexOfFirst { it.panelId == parentId && it.edgeIndex == parentEdge }
            edgeModels[parentIdx] = edgeModels[parentIdx].copy(connectedPanelId = id, connectedEdgeIndex = childEdge)

            for (e in 0 until EDGES_PER_PANEL) {
                edgeModels.add(PanelEdgeInfoModel(
                    panelId            = id,
                    edgeIndex          = e,
                    connectedPanelId   = if (e == childEdge) parentId else 0,
                    connectedEdgeIndex = if (e == childEdge) parentEdge else 0,
                ))
            }

            freeEdges.remove(parentId to parentEdge)
            for (e in 0 until EDGES_PER_PANEL) {
                if (e != childEdge) freeEdges.add(id to e)
            }
        }

        return edgeModels
    }

    private fun buildCandidates(pool: List<Pair<Int, Int>>): List<Triple<Int, Int, Int>> {
        val candidates = ArrayList<Triple<Int, Int, Int>>(pool.size * EDGES_PER_PANEL)
        for ((parentId, parentEdge) in pool) {
            for (childEdge in 0 until EDGES_PER_PANEL) {
                candidates.add(Triple(parentId, parentEdge, childEdge))
            }
        }
        candidates.shuffle()
        return candidates
    }

    private fun firstNonOverlapping(
        edgeModels: List<PanelEdgeInfoModel>,
        id: Int,
        pool: List<Pair<Int, Int>>,
    ): Triple<Int, Int, Int>? {
        for (candidate in buildCandidates(pool)) {
            if (!wouldOverlap(edgeModels, id, candidate.first, candidate.second, candidate.third))
                return candidate
        }
        return null
    }

    private fun wouldOverlap(
        current: List<PanelEdgeInfoModel>,
        newId: Int,
        parentId: Int,
        parentEdge: Int,
        childEdge: Int,
    ): Boolean {
        val tentative = buildTentativeEdges(current, newId, parentId, parentEdge, childEdge)
        val layouts = PanelsLayoutService.generateLayout(buildPanelTree(tentative))
        val newPoly = layouts.find { it.panelId == newId }?.vertices() ?: return true
        return layouts.any { other ->
            other.panelId != newId && convexPolygonsOverlap(newPoly, other.vertices())
        }
    }

    private fun buildTentativeEdges(
        current: List<PanelEdgeInfoModel>,
        newId: Int,
        parentId: Int,
        parentEdge: Int,
        childEdge: Int,
    ): List<PanelEdgeInfoModel> {
        val result = current.map { e ->
            if (e.panelId == parentId && e.edgeIndex == parentEdge)
                e.copy(connectedPanelId = newId, connectedEdgeIndex = childEdge)
            else e
        }.toMutableList()
        for (e in 0 until EDGES_PER_PANEL) {
            result.add(PanelEdgeInfoModel(
                panelId            = newId,
                edgeIndex          = e,
                connectedPanelId   = if (e == childEdge) parentId else 0,
                connectedEdgeIndex = if (e == childEdge) parentEdge else 0,
            ))
        }
        return result
    }

    /**
     * Converts a flat PanelEdgeInfoModel list to a PanelInfo tree suitable for PanelsLayoutService.
     * Mirrors PanelsListService.buildPanelTree() without requiring access to its private method.
     *
     * Root panel (lowest ID, no parent). All other panels have rootEdge set to the edge whose
     * connectedPanelId is smaller than their own ID — valid because panels are always attached to
     * already-placed (lower-ID) panels during generation.
     */
    private fun buildPanelTree(edges: List<PanelEdgeInfoModel>): List<PanelInfo> {
        val byPanel = edges.groupBy { it.panelId }
            .mapValues { (_, es) -> es.sortedBy { it.edgeIndex } }
        if (byPanel.isEmpty()) return emptyList()

        val sortedIds = byPanel.keys.sorted()
        val panelMap = sortedIds.associateWith { PanelInfo(it) }
        val edgeMap = mutableMapOf<Pair<Int, Int>, EdgeInfo>()

        for (id in sortedIds) {
            val panel = panelMap[id]!!
            for (e in byPanel[id]!!) {
                val edge = EdgeInfo(e.edgeIndex, panel)
                panel.edges.add(edge)
                edgeMap[id to e.edgeIndex] = edge
            }
        }

        for (e in edges) {
            if (e.connectedPanelId == 0) continue
            val from = edgeMap[e.panelId to e.edgeIndex] ?: continue
            val to   = edgeMap[e.connectedPanelId to e.connectedEdgeIndex] ?: continue
            if (from.connectedEdge == null) {
                from.connectedEdge = to
                to.connectedEdge   = from
            }
        }

        for (id in sortedIds.drop(1)) {
            val panel = panelMap[id]!!
            val parentEdge = byPanel[id]!!.find { it.connectedPanelId in 1 until id }
            if (parentEdge != null) {
                panel.rootEdge = panel.edges.find { it.index == parentEdge.edgeIndex }
            }
        }

        return sortedIds.mapNotNull { panelMap[it] }
    }

    /** Triangle vertices as (x, y) pairs, derived from edge start-points in edge-index order. */
    private fun com.lightnet.api.websocket.model.PanelLayout.vertices(): List<Pair<Double, Double>> =
        edgesCoords.entries.sortedBy { it.key }.map { it.value.x1 to it.value.y1 }

    /**
     * SAT convex-polygon overlap test, matching PanelGeometry.hpp convexPolygonsOverlap().
     * eps = 0.5 matches firmware: touching polygons (shared edge, projections exactly equal)
     * are considered non-overlapping, handling integer-rounded layout coordinates correctly.
     */
    private fun convexPolygonsOverlap(
        a: List<Pair<Double, Double>>,
        b: List<Pair<Double, Double>>,
        eps: Double = 0.5,
    ): Boolean = !hasSeparatingAxis(a, b, eps) && !hasSeparatingAxis(b, a, eps)

    private fun hasSeparatingAxis(
        poly1: List<Pair<Double, Double>>,
        poly2: List<Pair<Double, Double>>,
        eps: Double,
    ): Boolean {
        val n = poly1.size
        for (i in 0 until n) {
            val (x1, y1) = poly1[i]
            val (x2, y2) = poly1[(i + 1) % n]
            val nx = -(y2 - y1)
            val ny =   x2 - x1

            var aMin = Double.MAX_VALUE; var aMax = -Double.MAX_VALUE
            for ((x, y) in poly1) { val p = x * nx + y * ny; if (p < aMin) aMin = p; if (p > aMax) aMax = p }

            var bMin = Double.MAX_VALUE; var bMax = -Double.MAX_VALUE
            for ((x, y) in poly2) { val p = x * nx + y * ny; if (p < bMin) bMin = p; if (p > bMax) bMax = p }

            if (aMax <= bMin + eps || bMax <= aMin + eps) return true
        }
        return false
    }
}
