package com.lightnet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lightnet.ui.hsvToColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HueRingColorPicker(
    hue: Float,
    saturation: Float,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor  = hsvToColor(hue, saturation, 1f)
    val onHueRef       = rememberUpdatedState(onHueChange)
    val onSatRef       = rememberUpdatedState(onSaturationChange)
    val currentHue     = rememberUpdatedState(hue)
    val currentSat     = rememberUpdatedState(saturation)

    Column(modifier) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val sizePx        = constraints.maxWidth.toFloat()
            val outerFraction = 0.94f
            val innerFraction = 0.62f

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(sizePx) {
                        val cx     = sizePx / 2
                        val cy     = sizePx / 2
                        val outerR = sizePx / 2 * outerFraction
                        val innerR = sizePx / 2 * innerFraction
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun handle(x: Float, y: Float) {
                                val dx   = x - cx
                                val dy   = y - cy
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist >= innerR * 0.85f && dist <= outerR * 1.1f) {
                                    val angle = (atan2(dy, dx) * 180f / PI.toFloat() + 90f + 360f) % 360f
                                    onHueRef.value(angle)
                                }
                            }
                            handle(down.position.x, down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                handle(ch.position.x, ch.position.y)
                                ch.consume()
                            }
                        }
                    },
            ) {
                val cx      = size.width / 2
                val cy      = size.height / 2
                val outerR  = size.minDimension / 2 * outerFraction
                val innerR  = size.minDimension / 2 * innerFraction
                val arcR    = (outerR + innerR) / 2
                val strokeW = outerR - innerR

                // Hue ring — 2° arc segments with slight overlap to prevent gaps
                val step = 2f
                var a = 0f
                while (a < 360f) {
                    drawArc(
                        color      = hsvToColor(a, 1f, 1f),
                        startAngle = a - 90f,
                        sweepAngle = step + 0.5f,
                        useCenter  = false,
                        style      = Stroke(width = strokeW),
                        topLeft    = Offset(cx - arcR, cy - arcR),
                        size       = Size(arcR * 2, arcR * 2),
                    )
                    a += step
                }

                // Inner circle showing the currently selected color
                drawCircle(
                    color  = selectedColor,
                    radius = innerR - 4.dp.toPx(),
                    center = Offset(cx, cy),
                )

                // Thumb on the ring at current hue angle
                val thumbRad = (currentHue.value - 90f) * PI.toFloat() / 180f
                val tx = cx + arcR * cos(thumbRad)
                val ty = cy + arcR * sin(thumbRad)
                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(tx, ty))
                drawCircle(
                    color  = Color.Black.copy(alpha = 0.45f),
                    radius = 10.dp.toPx(),
                    center = Offset(tx, ty),
                    style  = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Saturation slider with gradient background
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(28.dp),
        ) {
            val sliderW = constraints.maxWidth.toFloat()

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(sliderW) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onSatRef.value((down.position.x / sliderW).coerceIn(0f, 1f))
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                onSatRef.value((ch.position.x / sliderW).coerceIn(0f, 1f))
                                ch.consume()
                            }
                        }
                    },
            ) {
                val barH   = 14.dp.toPx()
                val barTop = (size.height - barH) / 2
                val corner = CornerRadius(barH / 2)
                val thumbX = (currentSat.value * size.width).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx())

                drawRoundRect(
                    brush        = Brush.horizontalGradient(
                        listOf(hsvToColor(currentHue.value, 0f, 1f), hsvToColor(currentHue.value, 1f, 1f)),
                    ),
                    topLeft      = Offset(0f, barTop),
                    size         = Size(size.width, barH),
                    cornerRadius = corner,
                )

                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(thumbX, size.height / 2))
                drawCircle(
                    color  = Color.Black.copy(alpha = 0.35f),
                    radius = 10.dp.toPx(),
                    center = Offset(thumbX, size.height / 2),
                    style  = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
