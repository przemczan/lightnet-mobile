package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.ui.colorToHex
import com.lightnet.ui.colorToHsv
import com.lightnet.ui.hsvToColor
import com.lightnet.ui.interpolatePaletteColor
import com.lightnet.ui.parseHexColor

private enum class ColorTab { RGB, Palette, Base }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initial: Color,
    httpClient: LightnetHttpClient?,
    paletteNames: List<String>,
    baseColors: List<String>,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        var activeTab by remember { mutableStateOf(ColorTab.RGB) }

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Colour", style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ColorTab.entries.forEachIndexed { i, tab ->
                    SegmentedButton(
                        selected = activeTab == tab,
                        onClick  = { activeTab = tab },
                        shape    = SegmentedButtonDefaults.itemShape(i, ColorTab.entries.size),
                        label    = {
                            Text(
                                when (tab) {
                                    ColorTab.RGB     -> "RGB"
                                    ColorTab.Palette -> "Palette"
                                    ColorTab.Base    -> "Base colour"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            when (activeTab) {
                ColorTab.RGB     -> RgbTab(initial, onPick)
                ColorTab.Palette -> PaletteTab(httpClient, paletteNames, onPick)
                ColorTab.Base    -> BaseColorTab(baseColors, onPick)
            }
        }
    }
}

// ── RGB tab ──────────────────────────────────────────────────────────────────

@Composable
private fun RgbTab(initial: Color, onPick: (Color) -> Unit) {
    val (initH, initS, initV) = remember(initial) { colorToHsv(initial) }

    var hue        by remember { mutableFloatStateOf(initH) }
    var saturation by remember { mutableFloatStateOf(initS) }
    var value      by remember { mutableFloatStateOf(initV) }
    var hexText    by remember { mutableStateOf(colorToHex(initial)) }
    var rText      by remember { mutableStateOf((initial.red   * 255).toInt().toString()) }
    var gText      by remember { mutableStateOf((initial.green * 255).toInt().toString()) }
    var bText      by remember { mutableStateOf((initial.blue  * 255).toInt().toString()) }

    fun syncFromHsv(h: Float, s: Float, v: Float) {
        val c = hsvToColor(h, s, v)
        hexText = colorToHex(c)
        rText = (c.red   * 255).toInt().toString()
        gText = (c.green * 255).toInt().toString()
        bText = (c.blue  * 255).toInt().toString()
        onPick(c)
    }

    val hueColor = hsvToColor(hue, 1f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // SV gradient picker
        SvGradientBox(
            hueColor   = hueColor,
            saturation = saturation,
            value      = value,
            onSvChange = { s, v ->
                saturation = s; value = v
                syncFromHsv(hue, s, v)
            },
        )

        // Hue slider (0-360)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Text("H", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(14.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        Brush.horizontalGradient(
                            (0..12).map { i -> hsvToColor(i * 30f, 1f, 1f) }
                        )
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            hue = (down.position.x / size.width * 360f).coerceIn(0f, 360f)
                            saturation = saturation; value = value
                            syncFromHsv(hue, saturation, value)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                syncFromHsv(hue, saturation, value)
                                change.consume()
                            }
                        }
                    }
            )
            Box(Modifier.size(width = 20.dp, height = 20.dp).background(hsvToColor(hue, saturation, value), MaterialTheme.shapes.extraSmall))
        }

        // HEX field
        OutlinedTextField(
            value         = hexText,
            onValueChange = { raw ->
                hexText = raw
                parseHexColor(raw)?.let { c ->
                    val (h, s, v) = colorToHsv(c)
                    hue = h; saturation = s; value = v
                    rText = (c.red   * 255).toInt().toString()
                    gText = (c.green * 255).toInt().toString()
                    bText = (c.blue  * 255).toInt().toString()
                    onPick(c)
                }
            },
            label         = { Text("HEX") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )

        fun applyRgb(r: Int, g: Int, b: Int) {
            val c = Color(r / 255f, g / 255f, b / 255f)
            val (h2, s2, v2) = colorToHsv(c)
            hue = h2; saturation = s2; value = v2
            hexText = colorToHex(c)
            onPick(c)
        }

        // RGB fields
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = rText,
                onValueChange = { v -> rText = v; v.toIntOrNull()?.coerceIn(0, 255)?.let { r -> applyRgb(r, gText.toIntOrNull()?.coerceIn(0, 255) ?: 0, bText.toIntOrNull()?.coerceIn(0, 255) ?: 0) } },
                label = { Text("R") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = gText,
                onValueChange = { v -> gText = v; v.toIntOrNull()?.coerceIn(0, 255)?.let { g -> applyRgb(rText.toIntOrNull()?.coerceIn(0, 255) ?: 0, g, bText.toIntOrNull()?.coerceIn(0, 255) ?: 0) } },
                label = { Text("G") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = bText,
                onValueChange = { v -> bText = v; v.toIntOrNull()?.coerceIn(0, 255)?.let { b -> applyRgb(rText.toIntOrNull()?.coerceIn(0, 255) ?: 0, gText.toIntOrNull()?.coerceIn(0, 255) ?: 0, b) } },
                label = { Text("B") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SvGradientBox(
    hueColor: Color,
    saturation: Float,
    value: Float,
    onSvChange: (saturation: Float, value: Float) -> Unit,
) {
    val onSvChangeRef = rememberUpdatedState(onSvChange)

    val density = androidx.compose.ui.platform.LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(pos: androidx.compose.ui.geometry.Offset) {
                        val s = (pos.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        onSvChangeRef.value(s, v)
                    }
                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position)
                        change.consume()
                    }
                }
            }
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.White, hueColor))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
        }

        // Thumb — positioned in pixels via offset { IntOffset }
        val widthPx  = constraints.maxWidth.toFloat()
        val heightPx = with(density) { 120.dp.toPx() }
        val thumbPx  = with(density) { 14.dp.toPx() }
        val thumbX   = (saturation * widthPx  - thumbPx / 2f).coerceIn(0f, widthPx  - thumbPx)
        val thumbY   = ((1f - value) * heightPx - thumbPx / 2f).coerceIn(0f, heightPx - thumbPx)

        Box(
            Modifier
                .size(with(density) { thumbPx.toDp() })
                .offset { IntOffset(thumbX.roundToInt(), thumbY.roundToInt()) }
                .border(2.dp, Color.White, MaterialTheme.shapes.extraLarge)
                .border(3.5.dp, Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.extraLarge)
        )
    }
}

// ── Palette tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteTab(
    httpClient: LightnetHttpClient?,
    paletteNames: List<String>,
    onPick: (Color) -> Unit,
) {
    var selectedName by remember(paletteNames) { mutableStateOf(paletteNames.firstOrNull() ?: "") }
    var loadedPalette by remember { mutableStateOf<PaletteJson?>(null) }
    var position by remember { mutableIntStateOf(128) }
    var showDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(httpClient, selectedName) {
        if (selectedName.isNotEmpty() && httpClient != null) {
            loadedPalette = httpClient.runCatching { getPalette(selectedName) }.getOrNull()
        }
    }

    LaunchedEffect(loadedPalette, position) {
        loadedPalette?.let { pal ->
            onPick(interpolatePaletteColor(pal.stops, position))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (paletteNames.isEmpty()) {
            Text("No palettes available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // Palette dropdown
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showDropdown = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(selectedName.ifEmpty { "Select palette" }, style = MaterialTheme.typography.bodyMedium)
                    Text("▾", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                paletteNames.forEach { name ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { selectedName = name; showDropdown = false },
                    )
                }
            }
        }

        // Gradient preview
        val stops = loadedPalette?.stops
        if (!stops.isNullOrEmpty()) {
            val colorStops = remember(stops) {
                stops.sortedBy { it.position }.map { stop ->
                    (stop.position / 255f) to (parseHexColor(stop.color) ?: Color.White)
                }.toTypedArray()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Brush.horizontalGradient(colorStops = colorStops))
            )
        }

        // Position slider
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Text("Position", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value            = position / 255f,
                onValueChange    = { position = (it * 255).toInt() },
                modifier         = Modifier.weight(1f),
            )
            Text("$position / 255", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Base colour tab ───────────────────────────────────────────────────────────

@Composable
private fun BaseColorTab(baseColors: List<String>, onPick: (Color) -> Unit) {
    val labels    = listOf("Primary", "Secondary", "Tertiary")
    val fallbacks = listOf(Color(0xFFCF5B3C), Color(0xFF2F6DB0), Color(0xFF3C9A5F))
    val colors = labels.indices.map { i ->
        baseColors.getOrNull(i)?.let { parseHexColor(it) } ?: fallbacks[i]
    }
    var selected by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected) { onPick(colors[selected]) }

    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
        labels.forEachIndexed { i, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { selected = i; onPick(colors[i]) },
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(colors[i], MaterialTheme.shapes.small)
                        .then(
                            if (i == selected) Modifier.border(3.dp, Color.White, MaterialTheme.shapes.small)
                            else Modifier
                        )
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
