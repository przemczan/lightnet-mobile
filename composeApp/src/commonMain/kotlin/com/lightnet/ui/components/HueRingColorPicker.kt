package com.lightnet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
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
    brightness: Float = 1f,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectedColor   = hsvToColor(hue, saturation, brightness)
    val onHueRef        = rememberUpdatedState(onHueChange)
    val onSatRef        = rememberUpdatedState(onSaturationChange)
    val onBrightnessRef = rememberUpdatedState(onBrightnessChange)

    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        HueRingCanvas(
            selectedColor = selectedColor,
            hue           = hue,
            onHueChange   = onHueRef.value,
            modifier      = Modifier.size(168.dp),
        )
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerticalHsvSlider(
                icon               = Icons.Default.Opacity,
                contentDescription = "Saturation",
                value              = saturation,
                onValueChange      = onSatRef.value,
                gradientColors     = listOf(
                    hsvToColor(hue, 0f, 1f),
                    hsvToColor(hue, 1f, 1f),
                ),
            )
            VerticalHsvSlider(
                icon               = Icons.Default.WbSunny,
                contentDescription = "Brightness",
                value              = brightness,
                onValueChange      = onBrightnessRef.value,
                gradientColors     = listOf(Color.Black, hsvToColor(hue, saturation, 1f)),
            )
        }
    }
}

@Composable
private fun HueRingCanvas(
    selectedColor: Color,
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onHueRef   = rememberUpdatedState(onHueChange)
    val currentHue = rememberUpdatedState(hue)

    BoxWithConstraints(modifier) {
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

            drawCircle(
                color  = selectedColor,
                radius = innerR - 4.dp.toPx(),
                center = Offset(cx, cy),
            )

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
}

@Composable
private fun VerticalHsvSlider(
    icon: ImageVector,
    contentDescription: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    sliderHeight: Dp = 132.dp,
    sliderWidth: Dp = 28.dp,
) {
    val onValueRef   = rememberUpdatedState(onValueChange)
    val currentValue = rememberUpdatedState(value)

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            modifier           = Modifier.size(20.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            Modifier
                .width(sliderWidth)
                .height(sliderHeight),
        ) {
            val sliderH = constraints.maxHeight.toFloat()

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(sliderH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onValueRef.value((down.position.y / sliderH).coerceIn(0f, 1f))
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                onValueRef.value((ch.position.y / sliderH).coerceIn(0f, 1f))
                                ch.consume()
                            }
                        }
                    },
            ) {
                val barW    = 14.dp.toPx()
                val barLeft = (size.width - barW) / 2
                val corner  = CornerRadius(barW / 2)
                val thumbY  = (currentValue.value * size.height).coerceIn(10.dp.toPx(), size.height - 10.dp.toPx())

                drawRoundRect(
                    brush        = Brush.verticalGradient(gradientColors),
                    topLeft      = Offset(barLeft, 0f),
                    size         = Size(barW, size.height),
                    cornerRadius = corner,
                )

                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(size.width / 2, thumbY))
                drawCircle(
                    color  = Color.Black.copy(alpha = 0.35f),
                    radius = 10.dp.toPx(),
                    center = Offset(size.width / 2, thumbY),
                    style  = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
