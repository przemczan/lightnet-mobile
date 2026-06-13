package com.lightnet.ui.screens.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular ring showing an arrow from the center pointing at [angle] (0° = right, clockwise).
 * Dragging anywhere on/inside the ring sets a new angle snapped to 15° increments.
 * Events are consumed so the parent LazyColumn does not scroll while dragging.
 */
@Composable
internal fun AngleRingPicker(
    angle: Int,
    onAngleChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor  = MaterialTheme.colorScheme.outlineVariant
    val tickColor  = MaterialTheme.colorScheme.outline
    val arrowColor = MaterialTheme.colorScheme.primary

    val angleRad = Math.toRadians(angle.toDouble()).toFloat()

    Canvas(
        modifier = modifier
            .size(140.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun update(pos: Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toInt()
                        if (deg < 0) deg += 360
                        onAngleChange(((deg + 7) / 15 * 15) % 360)
                    }
                    update(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position)
                    }
                }
            },
    ) {
        val cx     = size.width / 2f
        val cy     = size.height / 2f
        val center = Offset(cx, cy)
        val radius = (size.width.coerceAtMost(size.height) / 2f) - 12.dp.toPx()

        // Ring
        drawCircle(color = ringColor, radius = radius, style = Stroke(width = 4.dp.toPx()))

        // Cardinal ticks (every 45°)
        for (i in 0 until 8) {
            val t = Math.toRadians(i * 45.0).toFloat()
            drawLine(
                color       = tickColor,
                start       = Offset(cx + (radius - 8.dp.toPx()) * cos(t), cy + (radius - 8.dp.toPx()) * sin(t)),
                end         = Offset(cx + radius * cos(t), cy + radius * sin(t)),
                strokeWidth = 3.dp.toPx(),
                cap         = StrokeCap.Round,
            )
        }

        // Arrow shaft (center → base of arrowhead)
        val headLen  = 12.dp.toPx()
        val baseX    = cx + (radius - headLen) * cos(angleRad)
        val baseY    = cy + (radius - headLen) * sin(angleRad)
        drawLine(
            color       = arrowColor,
            start       = center,
            end         = Offset(baseX, baseY),
            strokeWidth = 2.5f.dp.toPx(),
            cap         = StrokeCap.Round,
        )

        // Arrowhead triangle
        val tipX   = cx + radius * cos(angleRad)
        val tipY   = cy + radius * sin(angleRad)
        val halfW  = 5.dp.toPx()
        val perpX  = sin(angleRad) * halfW
        val perpY  = -cos(angleRad) * halfW
        drawPath(
            path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(baseX + perpX, baseY + perpY)
                lineTo(baseX - perpX, baseY - perpY)
                close()
            },
            color = arrowColor,
        )

        // Center dot
        drawCircle(color = arrowColor, radius = 4.dp.toPx(), center = center)
    }
}
