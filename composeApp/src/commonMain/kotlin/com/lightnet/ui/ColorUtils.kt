package com.lightnet.ui

import androidx.compose.ui.graphics.Color
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trimStart('#')
    if (cleaned.length != 6 && cleaned.length != 8) return null
    val long = cleaned.toLongOrNull(16) ?: return null
    return if (cleaned.length == 6) {
        Color(
            red   = ((long shr 16) and 0xFF).toInt() / 255f,
            green = ((long shr  8) and 0xFF).toInt() / 255f,
            blue  = ( long         and 0xFF).toInt() / 255f,
        )
    } else {
        Color(
            alpha = ((long shr 24) and 0xFF).toInt() / 255f,
            red   = ((long shr 16) and 0xFF).toInt() / 255f,
            green = ((long shr  8) and 0xFF).toInt() / 255f,
            blue  = ( long         and 0xFF).toInt() / 255f,
        )
    }
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}".uppercase()
}

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    if (saturation == 0f) return Color(value, value, value)
    val h = (hue / 60f) % 6f
    val i = h.toInt()
    val f = h - i
    val p = value * (1f - saturation)
    val q = value * (1f - saturation * f)
    val t = value * (1f - saturation * (1f - f))
    return when (i) {
        0 -> Color(value, t, p)
        1 -> Color(q, value, p)
        2 -> Color(p, value, t)
        3 -> Color(p, q, value)
        4 -> Color(t, p, value)
        else -> Color(value, p, q)
    }
}

fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red; val g = color.green; val b = color.blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val v = maxC
    val s = if (maxC == 0f) 0f else delta / maxC
    val rawH = when {
        delta == 0f -> 0f
        maxC == r   -> 60f * ((g - b) / delta % 6f)
        maxC == g   -> 60f * ((b - r) / delta + 2f)
        else        -> 60f * ((r - g) / delta + 4f)
    }
    val h = if (rawH < 0f) rawH + 360f else rawH
    return Triple(h, s, v)
}

fun interpolatePaletteColor(stops: List<PaletteStop>, position: Int): Color {
    if (stops.isEmpty()) return Color.White
    val sorted = stops.sortedBy { it.position }
    if (position <= sorted.first().position) return parseHexColor(sorted.first().color) ?: Color.White
    if (position >= sorted.last().position) return parseHexColor(sorted.last().color) ?: Color.White
    val lower = sorted.last { it.position <= position }
    val upper = sorted.first { it.position > position }
    val t = (position - lower.position).toFloat() / (upper.position - lower.position)
    val c1 = parseHexColor(lower.color) ?: Color.White
    val c2 = parseHexColor(upper.color) ?: Color.White
    return Color(
        red   = c1.red   + (c2.red   - c1.red)   * t,
        green = c1.green + (c2.green - c1.green) * t,
        blue  = c1.blue  + (c2.blue  - c1.blue)  * t,
    )
}

fun Color.toColorRgb() = ColorRgbModel(
    r = (red * 255).roundToInt().coerceIn(0, 255),
    g = (green * 255).roundToInt().coerceIn(0, 255),
    b = (blue * 255).roundToInt().coerceIn(0, 255),
)
