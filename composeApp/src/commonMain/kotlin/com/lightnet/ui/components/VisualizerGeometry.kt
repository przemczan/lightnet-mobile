package com.lightnet.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.sqrt

/** Moves every vertex toward the centroid by [padding] px, creating a gap between adjacent panels. */
internal fun shrinkPolygon(points: List<Offset>, padding: Float): List<Offset> {
    val (cx, cy) = centroid(points)
    return points.map { p ->
        val dx = p.x - cx
        val dy = p.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= padding) Offset(cx, cy)
        else Offset(cx + dx * (dist - padding) / dist, cy + dy * (dist - padding) / dist)
    }
}

/** Moves every vertex away from the centroid by [amount] px — the inverse of [shrinkPolygon]. */
internal fun expandPolygon(points: List<Offset>, amount: Float): List<Offset> {
    val (cx, cy) = centroid(points)
    return points.map { p ->
        val dx = p.x - cx
        val dy = p.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) p
        else Offset(cx + dx * (dist + amount) / dist, cy + dy * (dist + amount) / dist)
    }
}

/**
 * Builds the panel outline. When [radius] is positive each vertex is rounded with a quadratic bezier
 * whose control point is the original vertex; otherwise a plain straight-edged polygon is returned.
 */
internal fun buildPanelPath(points: List<Offset>, radius: Float): Path {
    if (radius <= 0f) {
        return Path().apply {
            moveTo(points[0].x, points[0].y)
            for (j in 1 until points.size) lineTo(points[j].x, points[j].y)
            close()
        }
    }

    val n = points.size
    val entries = ArrayList<Offset>(n) // arrival point on the edge from the previous vertex
    val exits = ArrayList<Offset>(n)   // departure point on the edge toward the next vertex

    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val curr = points[i]
        val next = points[(i + 1) % n]

        val toPrev = prev - curr
        val toNext = next - curr
        val lenPrev = toPrev.getDistance()
        val lenNext = toNext.getDistance()

        if (lenPrev == 0f || lenNext == 0f) {
            entries.add(curr)
            exits.add(curr)
            continue
        }

        val r = radius.coerceAtMost(lenPrev / 2f).coerceAtMost(lenNext / 2f)
        entries.add(curr + toPrev / lenPrev * r)
        exits.add(curr + toNext / lenNext * r)
    }

    return Path().apply {
        moveTo(exits[0].x, exits[0].y)
        for (i in 1 until n) {
            lineTo(entries[i].x, entries[i].y)
            quadraticTo(points[i].x, points[i].y, exits[i].x, exits[i].y)
        }
        lineTo(entries[0].x, entries[0].y)
        quadraticTo(points[0].x, points[0].y, exits[0].x, exits[0].y)
        close()
    }
}

private fun centroid(points: List<Offset>): Pair<Float, Float> {
    val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
    val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
    return cx to cy
}
