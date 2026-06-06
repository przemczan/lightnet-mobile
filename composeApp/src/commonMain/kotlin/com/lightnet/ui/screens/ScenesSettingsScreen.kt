package com.lightnet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.SceneJson
import com.lightnet.device.LightnetDevice
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.BackHandlerCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesSettingsScreen(
    device: LightnetDevice?,
    httpClient: LightnetHttpClient?,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)

    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var scenes       by remember { mutableStateOf(AppPreferences.scenes.getAll()) }
    var deleteTarget by remember { mutableStateOf<SceneJson?>(null) }
    var showEditor   by remember { mutableStateOf(false) }
    var editingScene by remember { mutableStateOf<SceneJson?>(null) }

    fun reload() { scenes = AppPreferences.scenes.getAll() }

    if (showEditor) {
        SceneEditorScreen(
            device     = device,
            httpClient = httpClient,
            initial    = editingScene,
            onBack     = {
                showEditor   = false
                editingScene = null
                reload()
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Scenes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingScene = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "New scene")
            }
        },
    ) { padding ->
        when {
            scenes.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No scenes yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scenes, key = { it.name ?: "" }) { scene ->
                    SceneItem(
                        scene  = scene,
                        onPlay = {
                            scope.launch {
                                if (httpClient == null) {
                                    snackbar.showSnackbar("Connect a device to play scenes.")
                                    return@launch
                                }
                                val r = runCatching { httpClient.playSceneInline(scene) }
                                if (r.isFailure) snackbar.showSnackbar("Failed to play \"${scene.name}\".")
                            }
                        },
                        onEdit = {
                            editingScene = scene
                            showEditor   = true
                        },
                        onDelete = { deleteTarget = scene },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text("Delete scene") },
            text             = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = {
                    deleteTarget = null
                    AppPreferences.scenes.delete(target.name ?: return@TextButton)
                    reload()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SceneItem(
    scene: SceneJson,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Text(scene.name ?: "Unnamed", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
    }
}
