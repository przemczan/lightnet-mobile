package com.lightnet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.websocket.SocketConnector
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.DeviceRepository
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.ServiceDiscovery
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

        // Pool of persistent connections, one per saved device.
        val devicePool     = remember { mutableStateMapOf<String, LightnetDevice>() }
        val connectedHosts = remember { mutableStateMapOf<String, String>() }
        // Tracks which connection params were used to create each pooled device,
        // so we can detect when a device edit requires reconnecting.
        val poolSnapshots  = remember { mutableMapOf<String, SavedDevice>() }

        fun connectsToSameEndpoint(a: SavedDevice, b: SavedDevice) =
            a.host == b.host && a.hostName == b.hostName && a.port == b.port

        // Keep pool in sync with the saved-devices list.
        LaunchedEffect(devices) {
            // Remove devices no longer in the list.
            val currentIds = devices.map { it.id }.toSet()
            (devicePool.keys - currentIds).forEach { id ->
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

        // Cache panel count for the active device whenever a snapshot arrives.
        LaunchedEffect(device) {
            device?.snapshot?.collect { snap ->
                val count = snap?.panels?.size ?: return@collect
                activeDevice?.let { d ->
                    deviceRepository.updatePanelCount(d.id, count)
                    refreshDevices()
                }
            }
        }

        val connectedWsHost = activeDevice?.id?.let { connectedHosts[it] }

        val httpApiClient = remember(connectedWsHost, activeDevice?.port) {
            val host = connectedWsHost ?: return@remember null
            val port = activeDevice?.port ?: return@remember null
            LightnetHttpClient("http://$host:$port")
        }
        DisposableEffect(httpApiClient) { onDispose { httpApiClient?.close() } }

        // Wire the resolved HTTP client into the device so it handles all API calls.
        LaunchedEffect(device, httpApiClient) { device?.attachHttpClient(httpApiClient) }

        if (showGlobalSettings) {
            GlobalSettingsScreen(onBack = { showGlobalSettings = false })
        } else if (showDevice && activeDevice != null) {
            DeviceControllerScreen(
                device          = device,
                activeDevice    = activeDevice!!,
                devices         = devices,
                onBack          = { showDevice = false },
                onSwitchDevice  = { d -> activeDevice = d },
                onManageDevices = { showDevice = false },
                modifier        = Modifier.fillMaxSize(),
            )
        } else {
            MyDevicesScreen(
                devices         = devices,
                onOpenDevice    = { d ->
                    activeDevice = d
                    showDevice   = true
                },
                onAddDevice     = { showAddSheet = true },
                onEditDevice    = { editTarget = it },
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
