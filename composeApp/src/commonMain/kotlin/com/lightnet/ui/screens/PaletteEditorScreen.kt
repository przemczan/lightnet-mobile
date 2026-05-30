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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.colorToHex
import com.lightnet.ui.parseHexColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteEditorScreen(
    initial: PaletteJson?,
    httpClient: LightnetHttpClient?,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)

    val scope       = rememberCoroutineScope()
    val snackbar    = remember { SnackbarHostState() }

    var name by remember {
        mutableStateOf(initial?.name ?: "")
    }
    var stops by remember {
        mutableStateOf(
            initial?.stops?.sortedBy { it.position }?.toMutableList()
                ?: mutableListOf(
                    PaletteStop(0,   "#FFFFFF"),
                    PaletteStop(255, "#FFFFFF"),
                )
        )
    }

    var colorPickerTarget by remember { mutableStateOf<Int?>(null) }  // index into stops

    // Validation
    val sortedStops  = stops.sortedBy { it.position }
    val hasFirst     = sortedStops.firstOrNull()?.position == 0
    val hasLast      = sortedStops.lastOrNull()?.position == 255
    val isIncreasing = sortedStops.zipWithNext().all { (a, b) -> a.position < b.position }
    val isValid      = hasFirst && hasLast && isIncreasing

    val gradientStops = remember(stops) {
        sortedStops.map { stop ->
            (stop.position / 255f) to (parseHexColor(stop.color) ?: Color.White)
        }.toTypedArray()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(name.ifEmpty { "New palette" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = isValid && name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val result = httpClient?.runCatching {
                                    savePalette(PaletteJson(name = name, stops = sortedStops))
                                }
                                if (result?.isSuccess == true) {
                                    onBack()
                                } else {
                                    snackbar.showSnackbar("Failed to save palette.")
                                }
                            }
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start  = 16.dp,
                end    = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("NAME") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }

            item {
                GradientBar(
                    stops    = sortedStops,
                    onMove   = { idx, newPos ->
                        val mutable = stops.toMutableList()
                        mutable[idx] = mutable[idx].copy(position = newPos)
                        stops = mutable
                    },
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        sortedStops.forEachIndexed { i, stop ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            StopRow(
                                stop        = stop,
                                canRemove   = stop.position != 0 && stop.position != 255,
                                onPositionChange = { raw ->
                                    raw.toIntOrNull()?.coerceIn(0, 255)?.let { pos ->
                                        val mutable = stops.toMutableList()
                                        val realIdx = stops.indexOf(stop)
                                        if (realIdx >= 0) mutable[realIdx] = stop.copy(position = pos)
                                        stops = mutable
                                    }
                                },
                                onColorClick = {
                                    colorPickerTarget = stops.indexOf(stop)
                                },
                                onRemove = {
                                    stops = stops.toMutableList().also { it.remove(stop) }
                                },
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        TextButton(
                            onClick  = {
                                val usedPositions = stops.map { it.position }.toSet()
                                val mid = (0..255).firstOrNull { it !in usedPositions && it > 0 && it < 255 } ?: return@TextButton
                                stops = stops.toMutableList().also { it.add(PaletteStop(mid, "#FFFFFF")) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text("+ Add stop") }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValidationBadge(label = "first @0, last @255", valid = hasFirst && hasLast)
                    ValidationBadge(label = "increasing", valid = isIncreasing)
                    Text(
                        "${stops.size} / 16 stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }

    // Color picker for a stop
    colorPickerTarget?.let { targetIdx ->
        val stop = stops.getOrNull(targetIdx)
        if (stop != null) {
            ColorPickerSheet(
                initial      = parseHexColor(stop.color) ?: Color.White,
                httpClient   = httpClient,
                paletteNames = emptyList(),
                baseColors   = emptyList(),
                onPick       = { color ->
                    val mutable = stops.toMutableList()
                    mutable[targetIdx] = stop.copy(color = colorToHex(color))
                    stops = mutable
                },
                onDismiss    = { colorPickerTarget = null },
            )
        }
    }
}

// ── Gradient bar with draggable handles ───────────────────────────────────────

@Composable
private fun GradientBar(
    stops: List<PaletteStop>,
    onMove: (index: Int, newPosition: Int) -> Unit,
) {
    val barHeight = 36.dp

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val widthPx  = constraints.maxWidth.toFloat()
        val density  = LocalDensity.current

        val gradientStops = remember(stops) {
            stops.map { stop ->
                (stop.position / 255f) to (parseHexColor(stop.color) ?: Color.White)
            }.toTypedArray()
        }

        // Gradient background
        if (gradientStops.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(Brush.horizontalGradient(colorStops = gradientStops))
            )
        }

        // Draggable handles
        stops.forEachIndexed { idx, stop ->
            val currentPos = rememberUpdatedState(stop.position)
            val handleWidthDp  = 10.dp
            val handleWidthPx  = with(density) { handleWidthDp.toPx() }
            val xPx = stop.position / 255f * widthPx
            val xDp = with(density) { (xPx - handleWidthPx / 2f).coerceIn(0f, widthPx - handleWidthPx).toDp() }

            Box(
                Modifier
                    .offset { IntOffset(with(density) { xDp.toPx() }.roundToInt(), 0) }
                    .width(handleWidthDp)
                    .height(barHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color.White)
                    .border(1.5.dp, Color.DarkGray, MaterialTheme.shapes.extraSmall)
                    .pointerInput(idx) {
                        var accX = currentPos.value / 255f * widthPx
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            accX = currentPos.value / 255f * widthPx
                            while (true) {
                                val event  = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                accX += change.position.x - change.previousPosition.x
                                val newPos = ((accX / widthPx) * 255).roundToInt().coerceIn(0, 255)
                                onMove(idx, newPos)
                                change.consume()
                            }
                        }
                    }
            )
        }
    }

    // Position labels below handles
    if (stops.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), Arrangement.SpaceBetween) {
            stops.forEach { stop ->
                Text(
                    stop.position.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Stop row ──────────────────────────────────────────────────────────────────

@Composable
private fun StopRow(
    stop: PaletteStop,
    canRemove: Boolean,
    onPositionChange: (String) -> Unit,
    onColorClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("pos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value           = stop.position.toString(),
                onValueChange   = onPositionChange,
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier        = Modifier
                    .width(64.dp)
                    .height(48.dp),
                textStyle       = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 24.dp, height = 22.dp)
                    .background(parseHexColor(stop.color) ?: Color.White, MaterialTheme.shapes.extraSmall)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    .clickable { onColorClick() }
            )
            Text(
                "−",
                style    = MaterialTheme.typography.titleLarge,
                color    = if (canRemove) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                modifier = if (canRemove) Modifier.clickable { onRemove() } else Modifier,
            )
        }
    }
}

// ── Validation badge ──────────────────────────────────────────────────────────

@Composable
private fun ValidationBadge(label: String, valid: Boolean) {
    val color = if (valid) Color(0xFF2F7A4A) else MaterialTheme.colorScheme.error
    Text(
        if (valid) "✓ $label" else "✗ $label",
        style    = MaterialTheme.typography.labelSmall,
        color    = color,
        modifier = Modifier
            .border(1.dp, color, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
