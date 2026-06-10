package com.lightnet.ui.screens.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.ColorRefPickerSheet
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StepEditorScreen(
    step: EditableStep,
    panels: List<LightnetDevicePanel>,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)
    var colorSlot by remember { mutableStateOf<Int?>(null) }

    Scaffold(topBar = { EditorTopBar("Step", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AnimTypeDropdown(step) }

            if (!step.anim.isRunner) {
                when (step.anim.colorMode) {
                    ColorMode.Single -> item {
                        ColorSlotRow("Color", step.colorA, paletteStops, baseColors) { colorSlot = 0 }
                    }
                    ColorMode.FromTo -> item {
                        Card(Modifier.fillMaxWidth()) {
                            Column {
                                ColorSwatchRow("From", step.colorA, paletteStops, baseColors, modifier = Modifier.clickable { colorSlot = 0 })
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                ColorSwatchRow("To", step.colorB, paletteStops, baseColors, modifier = Modifier.clickable { colorSlot = 1 })
                            }
                        }
                    }
                    ColorMode.None -> Unit
                }
            }

            item { DurationEditor(step) }

            if (step.anim == AnimId.WHEEL) {
                item { WheelPivotCard(step, panels) }
                item { WheelBladesCard(step) }
                item { WheelAnimatesCard(step, paletteStops, baseColors) { colorSlot = 0 } }
            } else if (step.anim.isRunner) {
                item { RunnerDirectionEditor(step, panels) }
                item {
                    RunnerAnimatesEditor(
                        step         = step,
                        paletteStops = paletteStops,
                        baseColors   = baseColors,
                        onColorClick = { colorSlot = 0 },
                    )
                }
                if (step.anim.hasWidth) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(step.anim.widthLabel ?: "Width", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column {
                                    Text("${step.width}", style = MaterialTheme.typography.bodyLarge)
                                    Slider(
                                        value         = step.width.toFloat(),
                                        onValueChange = { step.width = it.roundToInt().coerceAtLeast(1) },
                                        valueRange    = 1f..16f,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            step.anim.params.forEachIndexed { i, spec ->
                item {
                    val value = step.params.getOrElse(i) { spec.default }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("${spec.label}  $value", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value         = value.toFloat(),
                                onValueChange = { v ->
                                    step.params = step.params.toMutableList().also {
                                        while (it.size <= i) it.add(0)
                                        it[i] = v.roundToInt()
                                    }
                                },
                                valueRange = spec.min.toFloat()..spec.max.toFloat(),
                            )
                        }
                    }
                }
            }

            if (step.anim.supportsLoopFlags) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            ToggleRow("Loop", step.loop) { step.loop = it }
                            HorizontalDivider()
                            ToggleRow("Ping-pong", step.pingpong) { step.pingpong = it }
                        }
                    }
                }
            }
        }
    }

    colorSlot?.let { slot ->
        val current = if (slot == 0) step.colorA else step.colorB
        ColorRefPickerSheet(
            title        = if (step.anim.colorMode == ColorMode.FromTo) (if (slot == 0) "From colour" else "To colour") else "Colour",
            initial      = current,
            paletteStops = paletteStops,
            baseColors   = baseColors,
            onPick       = { picked: ColorRef -> if (slot == 0) step.colorA = picked else step.colorB = picked },
            onDismiss    = { colorSlot = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimTypeDropdown(step: EditableStep) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value         = step.anim.display,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Animation type") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupHeader("Local animations")
            AnimId.panelTypes.forEach { t ->
                DropdownMenuItem(
                    text     = { Text(t.display) },
                    onClick  = { step.changeAnim(t); expanded = false },
                    modifier = if (step.anim == t) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
                )
            }
            HorizontalDivider()
            GroupHeader("Runners")
            AnimId.runnerTypes.forEach { t ->
                DropdownMenuItem(
                    text     = { Text(t.display) },
                    onClick  = { step.changeAnim(t); expanded = false },
                    modifier = if (step.anim == t) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun DurationEditor(step: EditableStep) {
    var text by remember(step.id) { mutableStateOf(step.durationMs.toString()) }
    LaunchedEffect(step.durationMs) {
        if (step.durationMs.toString() != text) text = step.durationMs.toString()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value         = step.durationMs.toFloat(),
                    onValueChange = { step.durationMs = it.roundToInt() },
                    valueRange    = 0f..30000f,
                    modifier      = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value           = text,
                    onValueChange   = { v ->
                        text = v.filter(Char::isDigit).take(5)
                        text.toIntOrNull()?.let { step.durationMs = it.coerceIn(0, 30000) }
                    },
                    singleLine      = true,
                    suffix          = { Text("ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.width(110.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunnerDirectionEditor(step: EditableStep, panels: List<LightnetDevicePanel>) {
    val isRipple   = step.anim == AnimId.RIPPLE
    val showAngle  = step.geometric && !isRipple
    val showSource = !step.geometric || isRipple

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Directionality", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!step.geometric, { step.geometric = false }, { Text("Topology") })
                FilterChip(step.geometric,  { step.geometric = true  }, { Text("Geometric") })
            }

            if (showAngle) {
                // Full 360° ring covers both directions — no separate Reverse toggle needed.
                var angleText by remember { mutableStateOf(step.angle.toString()) }
                LaunchedEffect(step.angle) {
                    if (step.angle.toString() != angleText) angleText = step.angle.toString()
                }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AngleRingPicker(
                        angle         = step.angle,
                        onAngleChange = { step.angle = it },
                    )
                    OutlinedTextField(
                        value           = angleText,
                        onValueChange   = { v ->
                            angleText = v.filter(Char::isDigit).take(3)
                            angleText.toIntOrNull()?.coerceIn(0, 359)?.let { step.angle = it }
                        },
                        singleLine      = true,
                        suffix          = { Text("°") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.width(100.dp),
                    )
                }
            }

            if (showSource) {
                Text(
                    if (step.geometric) "Ripple centre" else "Source",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(step.source == RunnerSrc.Root,   { step.source = RunnerSrc.Root },   { Text("Root") })
                    FilterChip(step.source == RunnerSrc.Leaves, { step.source = RunnerSrc.Leaves }, { Text("Leaves") })
                    FilterChip(step.source == RunnerSrc.Panel,  { step.source = RunnerSrc.Panel },  { Text("Panel") })
                    if (isRipple || step.source == RunnerSrc.All)
                        FilterChip(step.source == RunnerSrc.All, { step.source = RunnerSrc.All }, { Text("All") })
                }
                if (step.source == RunnerSrc.Panel) {
                    PanelPickerField(
                        label           = "From panel",
                        selectedPanelId = step.sourcePanel,
                        panels          = panels,
                        onPick          = { step.sourcePanel = it },
                    )
                }
                HorizontalDivider()
                ToggleRow("Reverse direction", step.reverse) { step.reverse = it }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WheelPivotCard(step: EditableStep, panels: List<LightnetDevicePanel>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pivot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.source == RunnerSrc.Root,   { step.source = RunnerSrc.Root },   { Text("Root") })
                FilterChip(step.source == RunnerSrc.Leaves, { step.source = RunnerSrc.Leaves }, { Text("Leaves") })
                FilterChip(step.source == RunnerSrc.Panel,  { step.source = RunnerSrc.Panel },  { Text("Panel") })
                FilterChip(step.source == RunnerSrc.All,    { step.source = RunnerSrc.All },    { Text("All") })
            }
            if (step.source == RunnerSrc.Panel) {
                PanelPickerField(
                    label           = "Pivot panel",
                    selectedPanelId = step.sourcePanel,
                    panels          = panels,
                    onPick          = { step.sourcePanel = it },
                )
            }
            HorizontalDivider()
            ToggleRow("Spin the other way", step.reverse) { step.reverse = it }
        }
    }
}

@Composable
private fun WheelBladesCard(step: EditableStep) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blades", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text("Count  ${step.lines}", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value         = step.lines.toFloat(),
                    onValueChange = { step.lines = it.roundToInt().coerceIn(1, 6) },
                    valueRange    = 1f..6f,
                )
            }
            HorizontalDivider()
            Column {
                Text("Thickness  ${step.thickness}°", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value         = step.thickness.toFloat(),
                    onValueChange = { step.thickness = it.roundToInt().coerceIn(0, 180) },
                    valueRange    = 0f..180f,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WheelAnimatesCard(
    step: EditableStep,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onColorClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Animates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.animates == RunnerAnimates.Color,      { step.animates = RunnerAnimates.Color },      { Text("Color") })
                FilterChip(step.animates == RunnerAnimates.Dim,        { step.animates = RunnerAnimates.Dim },        { Text("Dim") })
                FilterChip(step.animates == RunnerAnimates.Brighten,   { step.animates = RunnerAnimates.Brighten },   { Text("Brighten") })
                FilterChip(step.animates == RunnerAnimates.Desaturate, { step.animates = RunnerAnimates.Desaturate }, { Text("Desaturate") })
                FilterChip(step.animates == RunnerAnimates.Saturate,   { step.animates = RunnerAnimates.Saturate },   { Text("Saturate") })
                FilterChip(step.animates == RunnerAnimates.Hue,        { step.animates = RunnerAnimates.Hue },        { Text("Hue") })
                FilterChip(step.animates == RunnerAnimates.Invert,     { step.animates = RunnerAnimates.Invert },     { Text("Invert") })
            }
            if (step.animates == RunnerAnimates.Color) {
                HorizontalDivider()
                ColorSwatchRow(
                    label          = "Color",
                    color          = step.colorA,
                    paletteStops   = paletteStops,
                    baseColors     = baseColors,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier       = Modifier.clickable(onClick = onColorClick),
                )
            } else {
                HorizontalDivider()
                Column {
                    Text("Peak amount  ${step.amount}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.amount.toFloat(),
                        onValueChange = { step.amount = it.roundToInt() },
                        valueRange    = 0f..255f,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunnerAnimatesEditor(
    step: EditableStep,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onColorClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Animates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(step.animates == RunnerAnimates.Color,      { step.animates = RunnerAnimates.Color },      { Text("Color") })
                FilterChip(step.animates == RunnerAnimates.Dim,        { step.animates = RunnerAnimates.Dim },        { Text("Dim") })
                FilterChip(step.animates == RunnerAnimates.Brighten,   { step.animates = RunnerAnimates.Brighten },   { Text("Brighten") })
                FilterChip(step.animates == RunnerAnimates.Desaturate, { step.animates = RunnerAnimates.Desaturate }, { Text("Desaturate") })
                FilterChip(step.animates == RunnerAnimates.Saturate,   { step.animates = RunnerAnimates.Saturate },   { Text("Saturate") })
                FilterChip(step.animates == RunnerAnimates.Hue,        { step.animates = RunnerAnimates.Hue },        { Text("Hue") })
                FilterChip(step.animates == RunnerAnimates.Invert,     { step.animates = RunnerAnimates.Invert },     { Text("Invert") })
            }
            if (step.animates == RunnerAnimates.Color) {
                ColorSwatchRow(
                    label          = "Color",
                    color          = step.colorA,
                    paletteStops   = paletteStops,
                    baseColors     = baseColors,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier       = Modifier.clickable(onClick = onColorClick),
                )
                HorizontalDivider()
            } else {
                Column {
                    Text("Peak amount  ${step.amount}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.amount.toFloat(),
                        onValueChange = { step.amount = it.roundToInt() },
                        valueRange    = 0f..255f,
                    )
                }
                HorizontalDivider()
                Text("Shape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(step.modShape == RunnerModShape.Fall, { step.modShape = RunnerModShape.Fall }, { Text("Fall") })
                    FilterChip(step.modShape == RunnerModShape.Rise, { step.modShape = RunnerModShape.Rise }, { Text("Rise") })
                    FilterChip(step.modShape == RunnerModShape.Bell, { step.modShape = RunnerModShape.Bell }, { Text("Bell") })
                }
                HorizontalDivider()
            }
            ToggleRow("Repeat — continuous train", step.repeat) { step.repeat = it }
            if (step.repeat) {
                HorizontalDivider()
                Column {
                    Text("Waves  ${step.repeatCount}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.repeatCount.toFloat(),
                        onValueChange = { step.repeatCount = it.roundToInt().coerceAtLeast(1) },
                        valueRange    = 1f..16f,
                    )
                }
            }
        }
    }
}
