package com.lightnet.ui.screens.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.PaletteOption
import com.lightnet.api.http.model.BlendMode
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.SectionHeader
import com.lightnet.ui.components.colorRefToColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayerEditorScreen(
    layer: EditableLayer,
    index: Int,
    panels: List<LightnetDevicePanel>,
    paletteOptions: List<PaletteOption>,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    otherLayers: List<EditableLayer>,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    BackHandlerCompat(onBack = onBack)
    var editingStep by remember { mutableStateOf<EditableStep?>(null) }

    editingStep?.let { step ->
        StepEditorScreen(
            step         = step,
            panels       = panels,
            paletteStops = paletteStops,
            baseColors   = baseColors,
            onBack       = { editingStep = null },
            onDelete     = if (layer.steps.size > 1) {
                { layer.steps.remove(step); editingStep = null }
            } else null,
        )
        return
    }

    Scaffold(topBar = { EditorTopBar(layer.name.ifBlank { "Layer ${index + 1}" }, onBack, onDelete, "This layer and all its steps will be removed.") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextField(
                    value         = layer.name,
                    onValueChange = { layer.name = sanitizeLayerName(it) },
                    label         = { Text("LAYER NAME") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }

            item { SectionHeader("Panels") }
            item { PanelTargetEditor(layer, panels) }

            item { SectionHeader("Playback") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        @OptIn(ExperimentalLayoutApi::class)
                        Column {
                            val asyncEnabled = layer.startAfter.isNullOrBlank()
                            Text("Async", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(layer.asyncMode == AsyncMode.Off,  { if (asyncEnabled) layer.asyncMode = AsyncMode.Off },  { Text("Off") },  enabled = asyncEnabled || layer.asyncMode == AsyncMode.Off)
                                FilterChip(layer.asyncMode == AsyncMode.Loop, { if (asyncEnabled) layer.asyncMode = AsyncMode.Loop }, { Text("Loop") }, enabled = asyncEnabled || layer.asyncMode == AsyncMode.Loop)
                                FilterChip(layer.asyncMode == AsyncMode.Free, { if (asyncEnabled) layer.asyncMode = AsyncMode.Free }, { Text("Free") }, enabled = asyncEnabled || layer.asyncMode == AsyncMode.Free)
                            }
                        }
                        HorizontalDivider()
                        StartAfterDropdown(
                            layer       = layer,
                            otherLayers = otherLayers,
                            modifier    = Modifier.padding(vertical = 8.dp),
                        )
                        HorizontalDivider()
                        LabeledDropdown(
                            label    = "Blend (how this layer composites)",
                            value    = layer.blend ?: BLEND_DEFAULT,
                            options  = listOf(BLEND_DEFAULT) + BlendMode.all,
                            onSelect = { layer.blend = if (it == BLEND_DEFAULT) null else it },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            if (paletteOptions.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        PaletteDropdown(
                            label    = "Palette override (optional)",
                            value    = layer.palette,
                            options  = paletteOptions,
                            onSelect = { layer.palette = it },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            item { SectionHeader("Steps") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        layer.steps.forEachIndexed { i, step ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            StepRow(
                                step         = step,
                                paletteStops = paletteStops,
                                baseColors   = baseColors,
                                canRemove    = layer.steps.size > 1,
                                canMoveUp    = i > 0,
                                canMoveDown  = i < layer.steps.lastIndex,
                                onClick      = { editingStep = step },
                                onRemove     = { layer.steps.remove(step) },
                                onMoveUp     = { layer.steps.move(i, i - 1) },
                                onMoveDown   = { layer.steps.move(i, i + 1) },
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        TextButton(
                            onClick  = { layer.steps.add(EditableStep()) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text("+ Add step") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: EditableStep,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    canRemove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.anim.colorMode != ColorMode.None) {
            Box(
                Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(colorRefToColor(step.colorA, paletteStops, baseColors))
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(step.anim.display, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${step.durationMs} ms" + if (step.loop) " · loop" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelTargetEditor(
    layer: EditableLayer,
    panels: List<LightnetDevicePanel>,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(layer.targetKind == TargetKind.All,      { layer.targetKind = TargetKind.All },      { Text("All") })
                FilterChip(layer.targetKind == TargetKind.Specific, { layer.targetKind = TargetKind.Specific }, { Text("Specific") })
                FilterChip(layer.targetKind == TargetKind.Selector, { layer.targetKind = TargetKind.Selector }, { Text("By role") })
                if (layer.targetKind == TargetKind.Advanced) {
                    FilterChip(true, {}, { Text("Advanced") })
                }
            }

            when (layer.targetKind) {
                TargetKind.All -> Unit
                TargetKind.Specific -> {
                    Text(
                        "Tap panels to include (${layer.selected.size} selected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LightnetDeviceVisualizer(
                        panels            = panels,
                        modifier          = Modifier.fillMaxWidth().height(260.dp),
                        interactive       = false,
                        selectionMode     = true,
                        selectedPanels    = layer.selected,
                        onSelectionChange = { layer.selected = it },
                        paintMode         = PaintMode.Paint,
                    )
                }
                TargetKind.Selector  -> SelectorEditor(layer, panels)
                TargetKind.Advanced  -> Text(
                    "This layer uses an advanced selector that can't be edited here. " +
                        "Pick All, Specific, or By role to replace it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class SelectorKind(val key: String, val label: String, val arg: ArgKind)
private enum class ArgKind { None, DepthBand, Panel, Count, Fraction }

private val SELECTOR_KINDS = listOf(
    SelectorKind("root",      "Root",                  ArgKind.None),
    SelectorKind("leaves",    "Leaves (tips)",         ArgKind.None),
    SelectorKind("branches",  "Branches (forks)",      ArgKind.None),
    SelectorKind("even",      "Even",                  ArgKind.None),
    SelectorKind("odd",       "Odd",                   ArgKind.None),
    SelectorKind("depth",     "Depth ring",            ArgKind.DepthBand),
    SelectorKind("subtree",   "Subtree of panel",      ArgKind.Panel),
    SelectorKind("neighbors", "Neighbors of panel",    ArgKind.Panel),
    SelectorKind("first",     "First N",               ArgKind.Count),
    SelectorKind("last",      "Last N",                ArgKind.Count),
    SelectorKind("fraction",  "Fraction (front..back)", ArgKind.Fraction),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorEditor(
    layer: EditableLayer,
    panels: List<LightnetDevicePanel>,
) {
    val token   = layer.selectorToken
    val kindKey = token.substringBefore(':').ifBlank { "leaves" }
    val kind    = SELECTOR_KINDS.firstOrNull { it.key == kindKey } ?: SELECTOR_KINDS[1]
    val arg     = token.substringAfter(':', "")

    fun defaultToken(k: SelectorKind): String = when (k.arg) {
        ArgKind.None      -> k.key
        ArgKind.DepthBand -> "${k.key}:1"
        ArgKind.Panel     -> "${k.key}:${panels.firstOrNull()?.info?.id ?: 1}"
        ArgKind.Count     -> "${k.key}:1"
        ArgKind.Fraction  -> "${k.key}:0-0.5"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledDropdown(
            label    = "Role",
            value    = kind.label,
            options  = SELECTOR_KINDS.map { it.label },
            onSelect = { sel -> SELECTOR_KINDS.firstOrNull { it.label == sel }?.let { layer.selectorToken = defaultToken(it) } },
        )

        when (kind.arg) {
            ArgKind.None     -> Unit
            ArgKind.DepthBand -> OutlinedTextField(
                value         = arg,
                onValueChange = { layer.selectorToken = "${kind.key}:$it" },
                label         = { Text("Depth (e.g. 1 or 1-2)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            ArgKind.Count -> OutlinedTextField(
                value           = arg,
                onValueChange   = { v -> layer.selectorToken = "${kind.key}:${v.filter { it.isDigit() }}" },
                label           = { Text("Count") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier        = Modifier.fillMaxWidth(),
            )
            ArgKind.Fraction -> OutlinedTextField(
                value         = arg,
                onValueChange = { layer.selectorToken = "${kind.key}:$it" },
                label         = { Text("Range 0-1 (e.g. 0-0.5)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            ArgKind.Panel -> PanelPickerField(
                label           = "Panel",
                selectedPanelId = arg.toIntOrNull(),
                panels          = panels,
                onPick          = { layer.selectorToken = "${kind.key}:$it" },
            )
        }
    }
}
