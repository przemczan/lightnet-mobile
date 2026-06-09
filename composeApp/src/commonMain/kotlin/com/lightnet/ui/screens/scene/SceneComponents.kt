package com.lightnet.ui.screens.scene

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.colorRefToColor

internal const val BLEND_DEFAULT = "default"

internal fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
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
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value         = value ?: "Device default",
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Device default") }, onClick = { onSelect(null); expanded = false })
            options.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
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

@Composable
internal fun PanelPickerField(
    label: String,
    selectedPanelId: Int?,
    panels: List<LightnetDevicePanel>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
                    selectedPanelId?.let { "Panel $it" } ?: "Tap to choose",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose panel")
        }
    }
    if (show) {
        PanelPickerSheet(
            title           = label,
            panels          = panels,
            selectedPanelId = selectedPanelId,
            onPick          = { onPick(it); show = false },
            onDismiss       = { show = false },
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
