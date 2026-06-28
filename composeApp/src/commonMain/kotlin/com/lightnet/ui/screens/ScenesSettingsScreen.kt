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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.components.groupedListItemShape
import com.lightnet.ui.components.LoadingOverlay
import com.lightnet.ui.screens.scene.SceneOrigin
import com.lightnet.ui.screens.scene.TimelineSceneEditorScreen
import com.lightnet.ui.BackHandlerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesSettingsScreen(
    device: LightnetDevice?,
    deviceId: String,
    httpClient: DeviceHttpApi?,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)

    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Device-scene management needs a live connection. httpClient stays non-null after a
    // prior connection, so gate the Device tab on the actual connection state instead.
    val connectionState by remember(device) {
        device?.connectionState ?: MutableStateFlow(ConnectionState.DISCONNECTED)
    }.collectAsState()
    val deviceConnected = connectionState == ConnectionState.CONNECTED && httpClient != null

    var tab by remember { mutableIntStateOf(0) }

    var globalScenes        by remember { mutableStateOf(AppPreferences.scenes.getAll()) }
    val deviceScenes by remember(device) {
        device?.scenes ?: MutableStateFlow<List<SceneInfo>?>(null)
    }.collectAsState()
    val loadingDevice by remember(device) {
        device?.scenesLoading ?: MutableStateFlow(false)
    }.collectAsState()
    var deleteGlobalTarget  by remember { mutableStateOf<SceneJson?>(null) }
    var deleteDeviceTarget  by remember { mutableStateOf<SceneInfo?>(null) }
    var deletingGlobalName  by remember { mutableStateOf<String?>(null) }
    var deletingDeviceId    by remember { mutableStateOf<String?>(null) }
    var loadingSceneId      by remember { mutableStateOf<String?>(null) }
    var showEditor          by remember { mutableStateOf(false) }
    var editingScene        by remember { mutableStateOf<SceneJson?>(null) }
    var editingOrigin       by remember { mutableStateOf(SceneOrigin.GLOBAL) }

    fun reloadGlobal() { globalScenes = AppPreferences.scenes.getAll() }

    suspend fun reloadDevice() {
        device?.refreshScenes()
    }

    LaunchedEffect(device, deviceConnected) { if (deviceConnected) device?.loadScenes() }

    fun openEditor(scene: SceneJson?, origin: SceneOrigin) {
        editingScene  = scene
        editingOrigin = origin
        showEditor    = true
    }

    if (showEditor) {
        TimelineSceneEditorScreen(
            device     = device,
            deviceId   = deviceId,
            httpClient = httpClient,
            initial    = editingScene,
            origin     = editingOrigin,
            onBack     = {
                showEditor   = false
                editingScene = null
                reloadGlobal()
                scope.launch { reloadDevice() }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
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
            // Device scenes can only be created while connected; the Global tab always allows it.
            if (tab == 0 || deviceConnected) {
                FloatingActionButton(onClick = {
                    openEditor(null, if (tab == 0) SceneOrigin.GLOBAL else SceneOrigin.DEVICE)
                }) {
                    Icon(Icons.Default.Add, contentDescription = "New scene")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Global") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Device") })
            }

            val listModifier = Modifier.fillMaxSize()
            val listPadding  = PaddingValues(
                top    = 8.dp,
                bottom = padding.calculateBottomPadding() + 80.dp,
                start  = 16.dp,
                end    = 16.dp,
            )

            if (tab == 0) {
                when {
                    globalScenes.isEmpty() -> Box(
                        listModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No scenes yet. Tap + to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(
                        modifier            = listModifier,
                        contentPadding      = listPadding,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(globalScenes, key = { _, it -> it.name ?: "" }) { index, scene ->
                            SceneSettingsItem(
                                name     = scene.name ?: "Unnamed",
                                subtitle = if (scene.deviceLinks.isNotEmpty()) {
                                    "Linked to ${scene.deviceLinks.size} device${if (scene.deviceLinks.size == 1) "" else "s"}"
                                } else null,
                                shape    = groupedListItemShape(index, globalScenes.size),
                                deleting = deletingGlobalName == scene.name,
                                onEdit   = { openEditor(scene, SceneOrigin.GLOBAL) },
                                onDelete = { deleteGlobalTarget = scene },
                            )
                        }
                    }
                }
            } else {
                when {
                    !deviceConnected -> Box(
                        listModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Connect a device to manage its scenes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    loadingDevice && deviceScenes.isNullOrEmpty() -> Box(
                        listModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    deviceScenes.isNullOrEmpty() -> Box(
                        listModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No scenes on device. Tap + to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(
                        modifier            = listModifier,
                        contentPadding      = listPadding,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(deviceScenes!!, key = { _, it -> it.id }) { index, info ->
                            SceneSettingsItem(
                                name     = info.name,
                                shape    = groupedListItemShape(index, deviceScenes!!.size),
                                deleting = deletingDeviceId == info.id,
                                onEdit   = {
                                    if (loadingSceneId != null) return@SceneSettingsItem
                                    loadingSceneId = info.id
                                    scope.launch {
                                        val full = httpClient.runCatching { getScene(info.id) }.getOrNull()
                                        loadingSceneId = null
                                        if (full != null) openEditor(full, SceneOrigin.DEVICE)
                                        else snackbar.showSnackbar("Failed to load \"${info.name}\".")
                                    }
                                },
                                onDelete = { deleteDeviceTarget = info },
                            )
                        }
                    }
                }
            }
        }
    }
        LoadingOverlay(visible = loadingSceneId != null)
    }

    deleteGlobalTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteGlobalTarget = null },
            title            = { Text("Delete scene") },
            text             = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = {
                    deleteGlobalTarget = null
                    deletingGlobalName = target.name
                    AppPreferences.scenes.delete(target.name ?: return@TextButton)
                    reloadGlobal()
                    deletingGlobalName = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteGlobalTarget = null }) { Text("Cancel") } },
        )
    }

    deleteDeviceTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteDeviceTarget = null },
            title            = { Text("Delete scene") },
            text             = { Text("Delete \"${target.name}\" from device? This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = {
                    deleteDeviceTarget = null
                    deletingDeviceId = target.id
                    scope.launch {
                        httpClient?.runCatching { deleteScene(target.id) }
                        AppPreferences.scenes.removeDeviceLinks(deviceId, target.id)
                        reloadGlobal()
                        reloadDevice()
                        deletingDeviceId = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDeviceTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SceneSettingsItem(
    name: String,
    shape: Shape,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    subtitle: String? = null,
    deleting: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(shape = shape, modifier = Modifier.fillMaxWidth().clickable(enabled = !deleting, onClick = onEdit)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Column {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (deleting) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
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
