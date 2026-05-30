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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.ui.colorToHsv
import com.lightnet.ui.hsvToColor
import com.lightnet.ui.components.HueRingColorPicker
import com.lightnet.ui.parseHexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initial: Color,
    baseColors: List<String> = emptyList(),
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val (initH, initS, _) = remember(initial) { colorToHsv(initial) }
    var hue        by remember { mutableFloatStateOf(initH) }
    var saturation by remember { mutableFloatStateOf(initS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Color", style = MaterialTheme.typography.titleMedium)

            if (baseColors.isNotEmpty()) {
                val labels    = listOf("Primary", "Secondary", "Tertiary")
                val fallbacks = listOf(Color(0xFFCF5B3C), Color(0xFF2F6DB0), Color(0xFF3C9A5F))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    baseColors.take(3).forEachIndexed { i, hex ->
                        val color = parseHexColor(hex) ?: fallbacks.getOrElse(i) { Color.White }
                        Column(
                            Modifier.clickable {
                                val (h, s, _) = colorToHsv(color)
                                hue = h; saturation = s
                                onPick(hsvToColor(hue, saturation, 1f))
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(color, MaterialTheme.shapes.small)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                            )
                            Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HueRingColorPicker(
                hue                = hue,
                saturation         = saturation,
                onHueChange        = { hue = it; onPick(hsvToColor(it, saturation, 1f)) },
                onSaturationChange = { saturation = it; onPick(hsvToColor(hue, it, 1f)) },
                modifier           = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("OK")
            }
        }
    }
}
