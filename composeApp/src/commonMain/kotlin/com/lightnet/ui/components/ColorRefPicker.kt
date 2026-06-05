package com.lightnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.ui.colorToHsv
import com.lightnet.ui.hsvToColor
import com.lightnet.ui.interpolatePaletteColor
import com.lightnet.ui.parseHexColor
import com.lightnet.ui.toColorRgb

private enum class RefMode { Custom, Palette, Base }

private val baseFallbacks = listOf(Color(0xFFCF5B3C), Color(0xFF2F6DB0), Color(0xFF3C9A5F))
private val baseLabels = listOf("Primary", "Secondary", "Tertiary")

/** Resolve a [ColorRef] to a display colour for previews/swatches. */
fun colorRefToColor(
    ref: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
): Color = when (ref) {
    is ColorRef.Hex -> parseHexColor(ref.value) ?: Color.White
    is ColorRef.Rgb -> Color(ref.r / 255f, ref.g / 255f, ref.b / 255f)
    is ColorRef.PalettePosition ->
        if (!paletteStops.isNullOrEmpty()) interpolatePaletteColor(paletteStops, ref.position) else Color.Gray
    is ColorRef.BaseColorSlot ->
        baseColors.getOrNull(ref.slot)?.let { parseHexColor(it) } ?: baseFallbacks.getOrElse(ref.slot) { Color.White }
}

/**
 * Unified colour-reference editor used for every scene step colour slot.
 * Segmented control switches between a custom RGB colour, a position sampled from
 * the active palette gradient, and one of the three device base-colour slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorRefPickerSheet(
    title: String,
    initial: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onPick: (ColorRef) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            when (initial) {
                is ColorRef.PalettePosition -> RefMode.Palette
                is ColorRef.BaseColorSlot   -> RefMode.Base
                else                        -> RefMode.Custom
            }
        )
    }

    val initColor = remember(initial) { colorRefToColor(initial, paletteStops, baseColors) }
    val (initH, initS, initV) = remember(initColor) { colorToHsv(initColor) }
    var hue        by remember { mutableFloatStateOf(initH) }
    var saturation by remember { mutableFloatStateOf(initS) }
    var brightness by remember { mutableFloatStateOf(initV) }

    var palettePos by remember {
        mutableIntStateOf((initial as? ColorRef.PalettePosition)?.position ?: 128)
    }
    var baseSlot by remember {
        mutableIntStateOf((initial as? ColorRef.BaseColorSlot)?.slot ?: 0)
    }

    fun emitCustom() {
        val rgb = hsvToColor(hue, saturation, brightness).toColorRgb()
        onPick(ColorRef.Rgb(rgb.r, rgb.g, rgb.b))
    }

    fun emit(forMode: RefMode) = when (forMode) {
        RefMode.Custom  -> emitCustom()
        RefMode.Palette -> onPick(ColorRef.PalettePosition(palettePos))
        RefMode.Base    -> onPick(ColorRef.BaseColorSlot(baseSlot))
    }

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
            Text(title, style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RefMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick  = { mode = m; emit(m) },
                        shape    = SegmentedButtonDefaults.itemShape(i, RefMode.entries.size),
                    ) { Text(m.name) }
                }
            }

            when (mode) {
                RefMode.Custom -> HueRingColorPicker(
                    hue                = hue,
                    saturation         = saturation,
                    brightness         = brightness,
                    onHueChange        = { hue = it; emitCustom() },
                    onSaturationChange = { saturation = it; emitCustom() },
                    onBrightnessChange = { brightness = it; emitCustom() },
                    modifier           = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )

                RefMode.Palette -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val gradient = remember(paletteStops) {
                        if (paletteStops.isNullOrEmpty()) null
                        else paletteStops.sortedBy { it.position }
                            .map { (it.position / 255f) to (parseHexColor(it.color) ?: Color.White) }
                            .toTypedArray()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (gradient != null) Brush.horizontalGradient(colorStops = gradient)
                                else Brush.horizontalGradient(rainbow)
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    )
                    Slider(
                        value         = palettePos.toFloat(),
                        onValueChange = { palettePos = it.toInt(); onPick(ColorRef.PalettePosition(palettePos)) },
                        valueRange    = 0f..255f,
                    )
                    Text(
                        "Position $palettePos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RefMode.Base -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    (0..2).forEach { i ->
                        val swatch = baseColors.getOrNull(i)?.let { parseHexColor(it) }
                            ?: baseFallbacks.getOrElse(i) { Color.White }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { baseSlot = i; onPick(ColorRef.BaseColorSlot(i)) },
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        if (baseSlot == i) 3.dp else 1.dp,
                                        if (baseSlot == i) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    ),
                            )
                            Text(baseLabels[i], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

private val rainbow = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)
