package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetApiException
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.ColorRef
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.device.LightnetDevice
import com.lightnet.device.LightnetDevicePanel
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.components.ColorRefPickerSheet
import com.lightnet.ui.components.LightnetDeviceVisualizer
import com.lightnet.ui.components.PaintMode
import com.lightnet.ui.components.colorRefToColor
import com.lightnet.ui.screens.scene.AnimId
import com.lightnet.ui.screens.scene.ColorMode
import com.lightnet.ui.screens.scene.EditableGroup
import com.lightnet.ui.screens.scene.EditableScene
import com.lightnet.ui.screens.scene.EditableStep
import com.lightnet.ui.screens.scene.sceneFromJson
import com.lightnet.ui.screens.scene.toSceneJson
import com.lightnet.ui.screens.scene.validationError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorScreen(
    device: LightnetDevice?,
    httpClient: LightnetHttpClient?,
    initial: com.lightnet.api.http.model.SceneJson?,
    onBack: () -> Unit,
) {
    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val snapshot by remember(device) { device?.snapshot ?: MutableStateFlow(null) }.collectAsState()
    val panels = snapshot?.panels ?: emptyList()

    // Reference data for colour previews / dropdowns.
    var palettesMap by remember { mutableStateOf<Map<String, PaletteJson>>(emptyMap()) }
    var baseColors  by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(httpClient) {
        palettesMap = httpClient?.runCatching { getPalettes() }?.getOrNull() ?: emptyMap()
    }
    LaunchedEffect(device) {
        baseColors = device?.loadAppearance()?.baseColors ?: device?.cachedAppearance?.baseColors ?: emptyList()
    }
    val paletteNames = remember(palettesMap) { palettesMap.keys.sorted() }

    // Build the editable model once. For an existing scene we wait until panels are
    // loaded so panel targets map correctly; a new scene starts immediately.
    var scene by remember { mutableStateOf<EditableScene?>(null) }
    LaunchedEffect(initial, panels.size) {
        if (scene != null) return@LaunchedEffect
        scene = when {
            initial == null     -> EditableScene()
            panels.isNotEmpty()  -> sceneFromJson(initial, panels)
            else                 -> null
        }
    }

    // Best-effort cleanup that survives leaving the screen.
    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun stopPreview() {
        device?.setLivePreview(false)
        val c = httpClient
        if (c != null) cleanupScope.launch(NonCancellable) { runCatching { c.stopScene() } }
    }

    // Stop any preview playback whenever the editor leaves composition for good.
    // Registered before the sub-screen early-returns so navigating into a group
    // (which swaps this screen out via return) does not trigger it.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { stopPreview() } }

    var editingGroup by remember { mutableStateOf<EditableGroup?>(null) }

    val activeScene = scene
    if (activeScene == null) {
        Scaffold(topBar = { EditorTopBar(if (initial == null) "New scene" else "Edit scene", onBack) }) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                if (device == null || httpClient == null) {
                    Text(
                        "Connect a device to edit scenes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else CircularProgressIndicator()
            }
        }
        return
    }

    // Effective palette stops for a group's colour previews (group override → scene default).
    fun stopsFor(group: EditableGroup): List<PaletteStop>? =
        (group.palette ?: activeScene.palette)?.let { palettesMap[it]?.stops }

    editingGroup?.let { group ->
        GroupEditorScreen(
            group        = group,
            index        = activeScene.groups.indexOf(group),
            panels       = panels,
            paletteNames = paletteNames,
            paletteStops = stopsFor(group),
            baseColors   = baseColors,
            onBack       = { editingGroup = null },
        )
        return
    }

    BackHandlerCompat(onBack = { stopPreview(); onBack() })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title          = { Text(activeScene.name.ifBlank { "New scene" }) },
                navigationIcon = {
                    IconButton(onClick = { stopPreview(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@TextButton }
                        scope.launch {
                            val result = httpClient?.runCatching { saveScene(activeScene.toSceneJson(panels)) }
                            if (result?.isSuccess == true) { stopPreview(); onBack() }
                            else {
                                val apiError = (result?.exceptionOrNull() as? LightnetApiException)?.error
                                snackbar.showSnackbar(apiError?.let { "Failed to save: $it" } ?: "Failed to save scene.")
                            }
                        }
                    }) { Text("Save") }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val err = activeScene.validationError()
                        if (err != null) { scope.launch { snackbar.showSnackbar(err) }; return@OutlinedButton }
                        device?.setLivePreview(true)
                        scope.launch { httpClient?.runCatching { playSceneInline(activeScene.toSceneJson(panels)) } }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Preview")
                }
                OutlinedButton(onClick = { stopPreview() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Stop")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (panels.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        LightnetDeviceVisualizer(
                            panels      = panels,
                            modifier    = Modifier.fillMaxWidth().height(220.dp),
                            interactive = false,
                        )
                    }
                }
            }
            item {
                TextField(
                    value         = activeScene.name,
                    onValueChange = { activeScene.name = it },
                    label         = { Text("NAME") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Loop", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = activeScene.loop, onCheckedChange = { activeScene.loop = it })
                        }
                        HorizontalDivider()
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text("Speed  ${activeScene.speed.oneDecimal()}×", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value         = activeScene.speed,
                                onValueChange = { activeScene.speed = (it * 10).roundToInt() / 10f },
                                valueRange    = 0.1f..10f,
                            )
                        }
                        HorizontalDivider()
                        PaletteDropdown(
                            label    = "Default palette",
                            value    = activeScene.palette,
                            options  = paletteNames,
                            onSelect = { activeScene.palette = it },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            item { SettingsSectionTitle("GROUPS") }
            activeScene.groups.forEach { group ->
                item(key = group.id) {
                    GroupCard(
                        group        = group,
                        index        = activeScene.groups.indexOf(group),
                        panelCount   = panels.size,
                        paletteStops = stopsFor(group),
                        baseColors   = baseColors,
                        onEdit       = { editingGroup = group },
                        onDelete     = { activeScene.groups.remove(group) },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick  = { activeScene.groups.add(EditableGroup()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp)); Text("Add group")
                }
            }
        }
    }
}

// ── Group card (summary in the scene list) ──────────────────────────────────────

@Composable
private fun GroupCard(
    group: EditableGroup,
    index: Int,
    panelCount: Int,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Group ${index + 1}", style = MaterialTheme.typography.titleSmall)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            Text(
                if (group.allPanels) "All panels" else "${group.selected.size} of $panelCount panels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.steps.forEach { step -> StepChip(step, paletteStops, baseColors) }
            }
        }
    }
}

@Composable
private fun StepChip(step: EditableStep, paletteStops: List<PaletteStop>?, baseColors: List<String>) {
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.anim.colorMode != ColorMode.None) {
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(colorRefToColor(step.colorA, paletteStops, baseColors))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        Text(step.anim.display, style = MaterialTheme.typography.labelSmall)
    }
}

// ── Group editor (panel targeting + step sequence) ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupEditorScreen(
    group: EditableGroup,
    index: Int,
    panels: List<LightnetDevicePanel>,
    paletteNames: List<String>,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)
    var editingStep by remember { mutableStateOf<EditableStep?>(null) }

    editingStep?.let { step ->
        StepEditorScreen(
            step         = step,
            paletteStops = paletteStops,
            baseColors   = baseColors,
            onBack       = { editingStep = null },
        )
        return
    }

    Scaffold(topBar = { EditorTopBar("Group ${index + 1}", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SettingsSectionTitle("PANELS") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("All panels", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = group.allPanels, onCheckedChange = { group.allPanels = it })
                        }
                        if (!group.allPanels) {
                            HorizontalDivider()
                            Text(
                                "Tap panels to include (${group.selected.size} selected)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            LightnetDeviceVisualizer(
                                panels            = panels,
                                modifier          = Modifier.fillMaxWidth().height(260.dp),
                                interactive       = false,
                                selectionMode     = true,
                                selectedPanels    = group.selected,
                                onSelectionChange = { group.selected = it },
                                paintMode         = PaintMode.Paint,
                            )
                        }
                    }
                }
            }
            if (paletteNames.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        PaletteDropdown(
                            label    = "Palette override (optional)",
                            value    = group.palette,
                            options  = paletteNames,
                            onSelect = { group.palette = it },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            item { SettingsSectionTitle("STEPS") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        group.steps.forEachIndexed { i, step ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            StepRow(
                                step         = step,
                                paletteStops = paletteStops,
                                baseColors   = baseColors,
                                canRemove    = group.steps.size > 1,
                                canMoveUp    = i > 0,
                                canMoveDown  = i < group.steps.lastIndex,
                                onClick      = { editingStep = step },
                                onRemove     = { group.steps.remove(step) },
                                onMoveUp     = { group.steps.move(i, i - 1) },
                                onMoveDown   = { group.steps.move(i, i + 1) },
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        TextButton(
                            onClick  = { group.steps.add(EditableStep()) },
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

// ── Step editor (dynamic form driven by the animation metadata) ──────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StepEditorScreen(
    step: EditableStep,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)
    var colorSlot by remember { mutableStateOf<Int?>(null) }  // 0 = A/from/single, 1 = B/to

    Scaffold(topBar = { EditorTopBar("Step", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSectionTitle("TYPE")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimId.panelTypes.forEach { t ->
                        FilterChip(selected = step.anim == t, onClick = { step.changeAnim(t) }, label = { Text(t.display) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Runners", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimId.runnerTypes.forEach { t ->
                        FilterChip(selected = step.anim == t, onClick = { step.changeAnim(t) }, label = { Text(t.display) })
                    }
                }
            }

            when (step.anim.colorMode) {
                ColorMode.Single -> item {
                    ColorSlotRow("Color", step.colorA, paletteStops, baseColors) { colorSlot = 0 }
                }
                ColorMode.FromTo -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSlotRow("From", step.colorA, paletteStops, baseColors) { colorSlot = 0 }
                        ColorSlotRow("To", step.colorB, paletteStops, baseColors) { colorSlot = 1 }
                    }
                }
                ColorMode.None -> Unit
            }

            item {
                Column {
                    Text("Duration  ${step.durationMs} ms", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value         = step.durationMs.toFloat(),
                        onValueChange = { step.durationMs = it.roundToInt() },
                        valueRange    = 0f..30000f,
                    )
                }
            }

            step.anim.params.forEachIndexed { i, spec ->
                item {
                    val value = step.params.getOrElse(i) { spec.default }
                    Column {
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

@Composable
private fun ColorSlotRow(
    label: String,
    color: ColorRef,
    paletteStops: List<PaletteStop>?,
    baseColors: List<String>,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteDropdown(
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

private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

private fun Float.oneDecimal(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}
