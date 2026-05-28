package com.lightnet.geometry

import com.lightnet.api.websocket.model.EdgeCoords

object GeometryUtils {
    data class Point(val x: Double, val y: Double)

    fun isInsidePolygon(x: Double, y: Double, points: List<Point>): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val xi = points[i].x; val yi = points[i].y
            val xj = points[j].x; val yj = points[j].y
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun edgeCoordsToPoints(coords: Collection<EdgeCoords>): List<Point> =
        coords.map { Point(it.x1, it.y1) }
}
