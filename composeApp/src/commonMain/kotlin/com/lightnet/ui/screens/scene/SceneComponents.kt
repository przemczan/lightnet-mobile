package com.lightnet.ui.screens.scene

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.lightnet.api.http.model.PaletteOption
import com.lightnet.api.http.model.paletteNamesEqual
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.BlendMode
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.colorToHex
import com.lightnet.ui.parseHexColor
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.colorRefToColor
import com.lightnet.ui.screens.ColorPickerSheet

internal const val BLEND_DEFAULT = "default"

private val layerOptionChipColors
    @Composable get() = FilterChipDefaults.filterChipColors()

@Composable
private fun LayerOptionChipLabel(text: String, muted: Boolean = false) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
    )
}

/** Compact async-mode chip on a timeline layer row — tap cycles Off → Loop → Free. */
@Composable
internal fun LayerAsyncChip(
    layer: EditableLayer,
    modifier: Modifier = Modifier,
) {
    val asyncLocked = !layer.startAfter.isNullOrBlank()
    FilterChip(
        selected = false,
        onClick  = { layer.cycleAsyncMode() },
        enabled  = !asyncLocked,
        label    = { LayerOptionChipLabel(layer.asyncMode.name.lowercase(), muted = asyncLocked) },
        colors   = layerOptionChipColors,
        modifier = modifier,
    )
}

/** Compact blend-mode chip on a timeline layer row — tap opens a picker. */
@Composable
internal fun LayerBlendChip(
    layer: EditableLayer,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = (layer.blend ?: BLEND_DEFAULT).lowercase()
    val options = remember { listOf(BLEND_DEFAULT) + BlendMode.all }

    Box(modifier) {
        FilterChip(
            selected = false,
            onClick  = { expanded = true },
            label    = { LayerOptionChipLabel(display) },
            colors   = layerOptionChipColors,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode) },
                    onClick = {
                        layer.blend = if (mode == BLEND_DEFAULT) null else mode
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteConfirmText: String = "This cannot be undone.",
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (onDelete != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        },
    )
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title            = { Text("Delete $title?") },
            text             = { Text(deleteConfirmText) },
            confirmButton    = { TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) { Text("Delete") } },
            dismissButton    = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

/** Foldable live-preview card shown above the scene/timeline editors. */
@Composable
internal fun VisualizerPreviewCard(
    panels: List<LightnetDevicePanel>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse preview" else "Expand preview",
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                LightnetDeviceVisualizer(
                    panels      = panels,
                    modifier    = Modifier.fillMaxWidth().height(220.dp),
                    interactive = false,
                )
            }
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
internal fun ColorSwatchRow(
    label: String,
    color: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(contentPadding),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box(
            Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
                .background(colorRefToColor(color, paletteStops, baseColors))
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        )
    }
}

@Composable
internal fun BackgroundColorRow(
    hex: String?,
    onChange: (String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val color = remember(hex) { parseHexColor(hex ?: "#000000") ?: Color.Black }

    Row(
        Modifier.fillMaxWidth().clickable { showPicker = true }.padding(vertical = 8.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Column {
            Text("Background", style = MaterialTheme.typography.bodyLarge)
            Text(
                hex ?: "Default (black)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hex != null) {
                TextButton(onClick = { onChange(null) }) { Text("Reset") }
            }
            Box(
                Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            )
        }
    }

    if (showPicker) {
        ColorPickerSheet(
            initial        = color,
            showBaseColors = false,
            onPick         = { onChange(colorToHex(it)) },
            onDismiss      = { showPicker = false },
        )
    }
}

@Composable
internal fun ColorSlotRow(
    label: String,
    color: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        ColorSwatchRow(label, color, paletteStops, baseColors, modifier = Modifier.clickable(onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaletteDropdown(
    label: String,
    value: String?,
    options: List<PaletteOption>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = value?.let { name ->
        options.find { paletteNamesEqual(it.name, name) }?.name
    } ?: "Device default"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value         = displayValue,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Device default") }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = { onSelect(option.name); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabeledDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value         = value,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}

/**
 * "Start after" picker: lists every other layer plus, indented underneath, each of its steps
 * (as "N (no id)" or "N <id>", N = 1-based step index). Picking a layer waits for its whole
 * sequence; picking a step waits for just that step — auto-assigning it a `stepN` id first if
 * it doesn't have one yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartAfterDropdown(
    layer: EditableLayer,
    otherLayers: List<EditableLayer>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = layer.startAfter?.trim()?.takeIf(String::isNotBlank)
    val (depGroup, depStep) = current?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (null to null)
    val depLayer = depGroup?.let { name -> otherLayers.firstOrNull { it.name.trim() == name } }
    val displayValue = when {
        current == null -> "Nothing (start immediately)"
        depStep == null || depLayer == null -> current
        else -> {
            val idx = depLayer.steps.indexOfFirst { it.stepId?.trim() == depStep }
            if (idx >= 0) "$depGroup: step ${idx + 1} ($depStep)" else current
        }
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value         = displayValue,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Start after") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text    = { Text("Nothing (start immediately)") },
                onClick = { layer.startAfter = null; expanded = false },
            )
            otherLayers.forEach { l ->
                val name = l.name.trim()
                DropdownMenuItem(
                    text    = { Text(name, fontWeight = FontWeight.Bold) },
                    onClick = { layer.startAfter = name; expanded = false },
                )
                l.steps.forEachIndexed { si, s ->
                    val sid = s.stepId?.trim()?.takeIf(String::isNotBlank)
                    DropdownMenuItem(
                        text    = { Text("    ${si + 1} ${sid ?: "(no id)"}") },
                        onClick = {
                            val id = sid ?: nextStepId(l).also { s.stepId = it }
                            layer.startAfter = "$name:$id"
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PanelPickerField(
    label: String,
    selectedPanelId: Int?,
    panels: List<LightnetDevicePanel>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, shown instead of "Tap to choose" when [selectedPanelId] is null. */
    emptyLabel: String = "Tap to choose",
    /** Optional reset choice (e.g. logical root → physical root). */
    defaultOptionLabel: String? = null,
    onPickDefault: (() -> Unit)? = null,
) {
    var show by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth().clickable { show = true }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedPanelId?.let { "Panel $it" } ?: emptyLabel,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose panel")
        }
    }
    if (show) {
        PanelPickerSheet(
            title               = label,
            panels              = panels,
            selectedPanelId     = selectedPanelId,
            defaultOptionLabel  = defaultOptionLabel,
            onPickDefault       = onPickDefault?.let { pickDefault ->
                {
                    pickDefault()
                    show = false
                }
            },
            onPick              = { onPick(it); show = false },
            onDismiss           = { show = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelPickerSheet(
    title: String,
    panels: List<LightnetDevicePanel>,
    selectedPanelId: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    defaultOptionLabel: String? = null,
    onPickDefault: (() -> Unit)? = null,
) {
    val selectedIndex = remember(panels, selectedPanelId) {
        panels.indexOfFirst { it.info.id == selectedPanelId }
    }
    val selectedSet = if (selectedIndex >= 0) setOf(selectedIndex) else emptySet()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a panel to select it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (defaultOptionLabel != null && onPickDefault != null) {
                TextButton(onClick = onPickDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(defaultOptionLabel)
                }
            }
            LightnetDeviceVisualizer(
                panels            = panels,
                modifier          = Modifier.fillMaxWidth().height(340.dp),
                interactive       = false,
                selectionMode     = true,
                selectedPanels    = selectedSet,
                showPanelIds      = true,
                onSelectionChange = { newSet ->
                    val picked = (newSet - selectedSet).firstOrNull() ?: selectedIndex.takeIf { it >= 0 }
                    if (picked != null) onPick(panels[picked].info.id) else onDismiss()
                },
            )
        }
    }
}
