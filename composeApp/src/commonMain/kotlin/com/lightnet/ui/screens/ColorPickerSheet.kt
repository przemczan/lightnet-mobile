package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.ui.colorToHex
import com.lightnet.ui.colorToHsv
import com.lightnet.ui.components.HueRingColorPicker
import com.lightnet.ui.hsvToColor
import com.lightnet.ui.parseHexColor

private val baseColorFallbacks = listOf(Color(0xFFCF5B3C), Color(0xFF2F6DB0), Color(0xFF3C9A5F))
private val baseColorLabels = listOf("Primary", "Secondary", "Tertiary")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initial: Color?,
    baseColors: List<String> = emptyList(),
    showBaseColors: Boolean = true,
    onPick: (Color) -> Unit,
    onUpdateBaseColor: (index: Int, color: Color) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    // No color specified yet → start at full saturation and full brightness.
    val (initH, initS, initV) = remember(initial) {
        if (initial == null) Triple(0f, 1f, 1f)
        else colorToHsv(initial)
    }
    var hue        by remember { mutableFloatStateOf(initH) }
    var saturation by remember { mutableFloatStateOf(initS) }
    var brightness by remember { mutableFloatStateOf(initV) }

    // Local copy so an updated swatch repaints immediately.
    var localBaseColors by remember(baseColors) { mutableStateOf(baseColors) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Color", style = MaterialTheme.typography.titleMedium)

            if (showBaseColors && localBaseColors.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    localBaseColors.take(3).forEachIndexed { i, hex ->
                        val swatchColor = parseHexColor(hex) ?: baseColorFallbacks.getOrElse(i) { Color.White }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            BaseColorSwatch(
                                color   = swatchColor,
                                label   = baseColorLabels.getOrElse(i) { "" },
                                onClick = {
                                    val (h, s, v) = colorToHsv(swatchColor)
                                    hue = h; saturation = s; brightness = v
                                    onPick(hsvToColor(hue, saturation, brightness))
                                },
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    val updated = hsvToColor(hue, saturation, brightness)
                                    localBaseColors = localBaseColors.toMutableList().also { list ->
                                        while (list.size <= i) list.add("#FFFFFF")
                                        list[i] = colorToHex(updated)
                                    }
                                    onUpdateBaseColor(i, updated)
                                },
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Set as ${baseColorLabels.getOrElse(i) { "" }}",
                                )
                            }
                        }
                    }
                }
            }

            HueRingColorPicker(
                hue                = hue,
                saturation         = saturation,
                brightness         = brightness,
                onHueChange        = { hue = it; onPick(hsvToColor(it, saturation, brightness)) },
                onSaturationChange = { saturation = it; onPick(hsvToColor(hue, it, brightness)) },
                onBrightnessChange = { brightness = it; onPick(hsvToColor(hue, saturation, it)) },
                modifier           = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

@Composable
private fun BaseColorSwatch(
    color: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
