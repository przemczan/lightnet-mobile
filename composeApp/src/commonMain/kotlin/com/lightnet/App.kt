package com.lightnet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.lightnet.discovery.effectiveHost
import com.lightnet.ui.screens.AddDeviceSheet
import com.lightnet.ui.screens.DeviceControllerScreen
import com.lightnet.ui.screens.EditDeviceSheet
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

        var showDevice   by remember { mutableStateOf(false) }
        var activeDevice by remember { mutableStateOf<SavedDevice?>(null) }

        var showAddSheet by remember { mutableStateOf(false) }
        var editTarget   by remember { mutableStateOf<SavedDevice?>(null) }

        var connectedWsHost by remember(activeDevice) {
            mutableStateOf(activeDevice?.effectiveHost?.ifEmpty { null })
        }

        val device = remember(activeDevice?.host, activeDevice?.port) {
            activeDevice?.let { d ->
                LightnetDevice(
                    SocketConnector(
                        overrideIP      = d.host.ifEmpty { null },
                        hostName        = d.hostName,
                        lastIP          = d.lastIP,
                        port            = d.port,
                        client          = httpClient,
                        onConnectedWith = { connectedHost ->
                            connectedWsHost = connectedHost
                            scope.launch { deviceRepository.updateLastIP(d.name, connectedHost) }
                        },
                    )
                )
            }
        }
        DisposableEffect(device) {
            device?.load()
            onDispose { device?.close() }
        }

        // Cache panel count whenever the snapshot first becomes available
        LaunchedEffect(device) {
            device?.snapshot?.collect { snap ->
                val count = snap?.panels?.size ?: return@collect
                activeDevice?.let { d ->
                    deviceRepository.updatePanelCount(d.name, count)
                    refreshDevices()
                }
            }
        }

        val httpApiClient = remember(connectedWsHost, activeDevice?.port) {
            val host = connectedWsHost ?: return@remember null
            val port = activeDevice?.port ?: return@remember null
            LightnetHttpClient("http://$host:$port")
        }
        DisposableEffect(httpApiClient) { onDispose { httpApiClient?.close() } }

        // Wire the resolved HTTP client into the device so it handles all API calls.
        LaunchedEffect(device, httpApiClient) { device?.attachHttpClient(httpApiClient) }

        if (showDevice && activeDevice != null) {
            DeviceControllerScreen(
                device       = device,
                activeDevice = activeDevice!!,
                onBack       = { showDevice = false },
                modifier     = Modifier.fillMaxSize(),
            )
        } else {
            MyDevicesScreen(
                devices      = devices,
                onOpenDevice = { d ->
                    activeDevice = d
                    showDevice   = true
                },
                onAddDevice  = { showAddSheet = true },
                onEditDevice = { editTarget = it },
                modifier     = Modifier.fillMaxSize(),
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
                    deviceRepository.update(original.name, updated)
                    refreshDevices()
                    if (activeDevice?.name == original.name) activeDevice = updated
                },
                onDelete  = { d ->
                    deviceRepository.remove(d.name)
                    refreshDevices()
                    if (activeDevice?.name == d.name) {
                        activeDevice = null
                        showDevice   = false
                    }
                },
                onDismiss = { editTarget = null },
            )
        }
    }
}
