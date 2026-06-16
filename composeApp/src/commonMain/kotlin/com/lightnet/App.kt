package com.lightnet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.websocket.SocketConnector
import com.lightnet.demo.DEMO_DEVICE_ID
import com.lightnet.demo.DemoConnector
import com.lightnet.demo.createDemoDevice
import com.lightnet.demo.demoSavedDevice
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.DeviceRepository
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.ServiceDiscovery
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.screens.AddDeviceSheet
import com.lightnet.ui.screens.DeviceControllerScreen
import com.lightnet.ui.screens.EditDeviceSheet
import com.lightnet.ui.screens.GlobalSettingsScreen
import com.lightnet.ui.screens.MyDevicesScreen
import com.lightnet.ui.theme.LightnetTheme
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

@Composable
fun LightnetApp(
    serviceDiscovery: ServiceDiscovery,
    deviceRepository: DeviceRepository,
    httpClient: HttpClient,
) {
    LightnetTheme {
        val scope = rememberCoroutineScope()

        var devices by remember { mutableStateOf(deviceRepository.getAll()) }
        fun refreshDevices() { devices = deviceRepository.getAll() }

        var showDevice         by remember { mutableStateOf(false) }
        var showGlobalSettings by remember { mutableStateOf(false) }
        var activeDevice       by remember { mutableStateOf<SavedDevice?>(null) }

        var showAddSheet by remember { mutableStateOf(false) }
        var editTarget   by remember { mutableStateOf<SavedDevice?>(null) }

        val demo = AppPreferences.demo
        val demoEnabled    by demo.demoEnabled.collectAsState()
        val demoPanelCount by demo.demoPanelCount.collectAsState()

        // Pool of persistent connections, one per saved device + optionally the demo device.
        val devicePool     = remember { mutableStateMapOf<String, LightnetDevice>() }
        val connectedHosts = remember { mutableStateMapOf<String, String>() }
        // Tracks which connection params were used to create each pooled device.
        val poolSnapshots  = remember { mutableMapOf<String, SavedDevice>() }

        fun connectsToSameEndpoint(a: SavedDevice, b: SavedDevice) =
            a.host == b.host && a.hostName == b.hostName && a.port == b.port

        // ── Demo device management (separate from the real-device sync loop) ──────
        // When the panel count changes while the demo is already running, reset the layout
        // in place (clears stored topology so next connect regenerates) without tearing down
        // the whole LightnetDevice. Only disable/re-enable triggers a full recreate.

        LaunchedEffect(demoEnabled, demoPanelCount) {
            val existing = devicePool[DEMO_DEVICE_ID]

            if (!demoEnabled) {
                existing?.close()
                devicePool.remove(DEMO_DEVICE_ID)
                if (activeDevice?.id == DEMO_DEVICE_ID) {
                    activeDevice = null
                    showDevice   = false
                }
                return@LaunchedEffect
            }

            if (existing != null) {
                // Demo already running — reset layout with new count and reload via disconnect/reconnect.
                // load() yields between disconnect and connect so the state collector reliably fires.
                (existing.connector as? DemoConnector)?.resetLayout(demoPanelCount)
                existing.load()
            } else {
                val d = createDemoDevice(demoPanelCount, AppPreferences.settings)
                devicePool[DEMO_DEVICE_ID] = d
                d.load()
            }
        }

        // ── Real device pool sync ─────────────────────────────────────────────────

        LaunchedEffect(devices) {
            // Remove stale devices; never touch the demo entry.
            val currentIds = devices.map { it.id }.toSet()
            (devicePool.keys - currentIds - DEMO_DEVICE_ID).forEach { id ->
                devicePool.remove(id)?.close()
                connectedHosts.remove(id)
                poolSnapshots.remove(id)
            }

            // Add new devices or recreate ones whose connection params changed.
            devices.forEach { saved ->
                val prev = poolSnapshots[saved.id]
                if (prev == null || !connectsToSameEndpoint(prev, saved)) {
                    devicePool.remove(saved.id)?.close()
                    val d = LightnetDevice(
                        SocketConnector(
                            overrideIP      = saved.host.ifEmpty { null },
                            hostName        = saved.hostName,
                            lastIP          = saved.lastIP,
                            port            = saved.port,
                            client          = httpClient,
                            onConnectedWith = { connectedHost ->
                                connectedHosts[saved.id] = connectedHost
                                scope.launch { deviceRepository.updateLastIP(saved.id, connectedHost) }
                            },
                        )
                    )
                    poolSnapshots[saved.id] = saved
                    devicePool[saved.id] = d
                    d.load()
                }
            }
        }

        // Close all connections when the app is disposed.
        DisposableEffect(Unit) {
            onDispose { devicePool.values.forEach { it.close() } }
        }

        val device = activeDevice?.let { devicePool[it.id] }

        // Cache panel count for real devices whenever a snapshot arrives.
        LaunchedEffect(device) {
            device?.snapshot?.collect { snap ->
                val count = snap?.panels?.size ?: return@collect
                activeDevice?.takeIf { it.id != DEMO_DEVICE_ID }?.let { d ->
                    deviceRepository.updatePanelCount(d.id, count)
                    refreshDevices()
                }
            }
        }

        // ── HTTP client for the active device ────────────────────────────────────
        // Demo: pre-wired inside createDemoDevice, read back via device.activeHttpClient.
        // Real: created from the resolved WebSocket host after connection.

        val connectedWsHost = activeDevice?.id
            ?.takeIf { it != DEMO_DEVICE_ID }
            ?.let { connectedHosts[it] }

        val realHttpClient: DeviceHttpApi? = remember(connectedWsHost, activeDevice?.port) {
            val host = connectedWsHost ?: return@remember null
            val port = activeDevice?.port ?: return@remember null
            LightnetHttpClient("http://$host:$port")
        }
        DisposableEffect(realHttpClient) { onDispose { (realHttpClient as? LightnetHttpClient)?.close() } }

        // Wire the resolved HTTP client into real devices as the host becomes known.
        LaunchedEffect(device, realHttpClient) {
            if (activeDevice?.id != DEMO_DEVICE_ID) device?.attachHttpClient(realHttpClient)
        }

        // The HTTP client passed to screens (demo or real).
        val httpApiClient: DeviceHttpApi? =
            if (activeDevice?.id == DEMO_DEVICE_ID) device?.activeHttpClient else realHttpClient

        // Regenerate demo layout: clear topology then reload panels (no disconnect/reconnect).
        val onRegenerateLayout: (() -> Unit)? = if (activeDevice?.id == DEMO_DEVICE_ID) {
            {
                val d = devicePool[DEMO_DEVICE_ID]
                (d?.connector as? DemoConnector)?.resetLayout(demoPanelCount)
                d?.reloadPanels()
            }
        } else null

        // ── Merged device list: demo always at the top when enabled ──────────────

        val allDevices = remember(devices, demoEnabled, demoPanelCount) {
            if (demoEnabled) listOf(demoSavedDevice(demoPanelCount)) + devices else devices
        }

        if (showGlobalSettings) {
            GlobalSettingsScreen(onBack = { showGlobalSettings = false })
        } else if (showDevice && activeDevice != null) {
            DeviceControllerScreen(
                device              = device,
                activeDevice        = activeDevice!!,
                devices             = allDevices,
                devicePool          = devicePool,
                httpClient          = httpApiClient,
                onBack              = { showDevice = false },
                onSwitchDevice      = { d -> activeDevice = d },
                onManageDevices     = { showDevice = false },
                onRegenerateLayout  = onRegenerateLayout,
                modifier            = Modifier.fillMaxSize(),
            )
        } else {
            MyDevicesScreen(
                devices         = allDevices,
                devicePool      = devicePool,
                onOpenDevice    = { d ->
                    activeDevice = d
                    showDevice   = true
                },
                onAddDevice     = { showAddSheet = true },
                onEditDevice    = { it.takeIf { d -> d.id != DEMO_DEVICE_ID }?.let { d -> editTarget = d } },
                onOpenSettings  = { showGlobalSettings = true },
                modifier        = Modifier.fillMaxSize(),
            )
        }

        if (showAddSheet) {
            AddDeviceSheet(
                serviceDiscovery = serviceDiscovery,
                httpClient       = httpClient,
                existingDevices  = devices,
                onAdd            = { saved ->
                    deviceRepository.add(saved)
                    refreshDevices()
                },
                onDismiss        = { showAddSheet = false },
            )
        }

        editTarget?.let { target ->
            EditDeviceSheet(
                device    = target,
                onSave    = { original, updated ->
                    deviceRepository.update(original.id, updated)
                    refreshDevices()
                    if (activeDevice?.id == original.id) activeDevice = updated
                },
                onDelete  = { d ->
                    deviceRepository.remove(d.id)
                    refreshDevices()
                    if (activeDevice?.id == d.id) {
                        activeDevice = null
                        showDevice   = false
                    }
                },
                onDismiss = { editTarget = null },
            )
        }
    }
}
