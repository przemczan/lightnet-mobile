package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.ui.parseHexColor
import kotlinx.coroutines.launch

private enum class LibraryTab { Palettes, Scenes, Animations }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    httpClient: LightnetHttpClient?,
    bottomBar: @Composable () -> Unit,
) {
    var activeTab by remember { mutableStateOf(LibraryTab.Palettes) }
    var showPaletteEditor by remember { mutableStateOf(false) }
    var editingPalette by remember { mutableStateOf<PaletteJson?>(null) }

    if (showPaletteEditor) {
        PaletteEditorScreen(
            initial    = editingPalette,
            httpClient = httpClient,
            onBack     = { showPaletteEditor = false; editingPalette = null },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (activeTab == LibraryTab.Palettes) {
                FloatingActionButton(onClick = { editingPalette = null; showPaletteEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New palette")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                LibraryTab.entries.forEachIndexed { i, tab ->
                    SegmentedButton(
                        selected = activeTab == tab,
                        onClick  = { activeTab = tab },
                        shape    = SegmentedButtonDefaults.itemShape(i, LibraryTab.entries.size),
                        label    = {
                            Text(
                                when (tab) {
                                    LibraryTab.Palettes   -> "Palettes"
                                    LibraryTab.Scenes     -> "Scenes"
                                    LibraryTab.Animations -> "Animations"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (activeTab) {
                LibraryTab.Palettes -> PalettesTab(
                    httpClient    = httpClient,
                    onEditPalette = { pal -> editingPalette = pal; showPaletteEditor = true },
                )
                LibraryTab.Scenes, LibraryTab.Animations -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "${activeTab.name} — coming soon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Palettes tab ──────────────────────────────────────────────────────────────

@Composable
private fun PalettesTab(
    httpClient: LightnetHttpClient?,
    onEditPalette: (PaletteJson) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var palettes     by remember { mutableStateOf<List<PaletteJson>>(emptyList()) }
    var activeName   by remember { mutableStateOf<String?>(null) }
    var searchQuery  by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<PaletteJson?>(null) }
    var isLoading    by remember { mutableStateOf(false) }

    suspend fun reload() {
        if (httpClient == null) return
        isLoading = true
        val names = httpClient.runCatching { getPaletteNames() }.getOrNull() ?: emptyList()
        palettes  = names.mapNotNull { name -> httpClient.runCatching { getPalette(name) }.getOrNull() }
        activeName = httpClient.runCatching { getPaletteName() }.getOrNull()
        isLoading = false
    }

    LaunchedEffect(httpClient) { reload() }

    val displayed = if (searchQuery.isBlank()) palettes
    else palettes.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Active chip + search
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            if (activeName != null) {
                FilterChip(
                    selected = true,
                    onClick  = {},
                    label    = { Text("Active: $activeName", style = MaterialTheme.typography.labelSmall) },
                )
            }
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(52.dp),
                textStyle     = MaterialTheme.typography.bodySmall,
            )
        }

        if (httpClient == null) {
            Text(
                "Connect a device to manage palettes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (displayed.isEmpty() && !isLoading) {
            Text(
                if (searchQuery.isBlank()) "No palettes found on device." else "No results for \"$searchQuery\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            contentPadding  = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(displayed, key = { it.name }) { palette ->
                PaletteCard(
                    palette      = palette,
                    isActive     = palette.name == activeName,
                    onTap        = {
                        activeName = palette.name
                        scope.launch { httpClient?.runCatching { setPaletteName(palette.name) } }
                    },
                    onEdit       = { onEditPalette(palette) },
                    onDelete     = { deleteTarget = palette },
                )
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text("Delete palette") },
            text             = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        httpClient?.runCatching { deletePalette(target.name) }
                        reload()
                    }
                }) { Text("Delete") }
            },
            dismissButton    = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PaletteCard(
    palette: PaletteJson,
    isActive: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val gradientStops = remember(palette.stops) {
        palette.stops.sortedBy { it.position }.map { stop ->
            (stop.position / 255f) to (parseHexColor(stop.color) ?: Color.White)
        }.toTypedArray()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (gradientStops.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Brush.horizontalGradient(colorStops = gradientStops))
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(palette.name, style = MaterialTheme.typography.labelMedium)
                    if (isActive) {
                        Text(
                            "active",
                            style  = MaterialTheme.typography.labelSmall,
                            color  = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit") },   onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
    }
}
