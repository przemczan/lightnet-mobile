package com.lightnet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// Logarithmic speed mapping: slider position p∈[0,1] ↔ speed∈[0.1,10] with 1.0× at p=0.5.
internal fun sliderPosToSpeed(p: Float): Float {
    val s = 10f.pow(2f * p - 1f)
    return (s * 10f).roundToInt() / 10f
}

internal fun speedToSliderPos(speed: Float): Float =
    ((log10(speed.coerceIn(0.1f, 10f)) + 1f) / 2f).coerceIn(0f, 1f)

internal fun Float.oneDecimal(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}

@Composable
fun SpeedSlider(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Speed, contentDescription = null)
        Slider(
            value         = speedToSliderPos(speed),
            valueRange    = 0f..1f,
            onValueChange = { onSpeedChange(sliderPosToSpeed(it)) },
            modifier      = Modifier.weight(1f),
        )
        Text(
            "${speed.oneDecimal()}×",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        IconButton(onClick = { onSpeedChange(1f) }, enabled = speed != 1f) {
            Icon(Icons.Default.RestartAlt, contentDescription = "Reset speed to 1.0×")
        }
    }
}
