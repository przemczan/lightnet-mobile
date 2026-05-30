package com.lightnet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.websocket.MockConnector
import com.lightnet.api.websocket.SocketConnector
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.DeviceRepository
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.ServiceDiscovery
import com.lightnet.discovery.effectiveHost
import com.lightnet.ui.components.LightnetBottomNav
import com.lightnet.ui.components.RootTab
import com.lightnet.ui.screens.AddDeviceSheet
import com.lightnet.ui.screens.DEMO_DEVICE_HOST
import com.lightnet.ui.screens.DEMO_DEVICE_NAME
import com.lightnet.ui.screens.DeviceControllerScreen
import com.lightnet.ui.screens.DeviceSwitcherSheet
import com.lightnet.ui.screens.EditDeviceSheet
import com.lightnet.ui.screens.LibraryScreen
import com.lightnet.ui.screens.MyDevicesScreen
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

private val DemoSavedDevice = SavedDevice(DEMO_DEVICE_NAME, DEMO_DEVICE_HOST, 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightnetApp(
    serviceDiscovery: ServiceDiscovery,
    deviceRepository: DeviceRepository,
    httpClient: HttpClient,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val scope = rememberCoroutineScope()

        var devices by remember { mutableStateOf(deviceRepository.getAll()) }
        fun refreshDevices() { devices = deviceRepository.getAll() }

        var selectedTab  by remember { mutableStateOf(RootTab.Devices) }
        var activeDevice by remember { mutableStateOf<SavedDevice?>(null) }

        // Sheet state
        var showAddSheet  by remember { mutableStateOf(false) }
        var editTarget    by remember { mutableStateOf<SavedDevice?>(null) }
        var showSwitcher  by remember { mutableStateOf(false) }

        // Hoisted device — survives bottom-nav tab switches. Keyed on (host, port)
        // so a pure rename doesn't trigger a reconnect.
        val device = remember(activeDevice?.host, activeDevice?.port) {
            activeDevice?.let { d ->
                val connector = if (d.host == DEMO_DEVICE_HOST) {
                    MockConnector()
                } else {
                    SocketConnector(
                        overrideIP       = d.host.ifEmpty { null },
                        hostName         = d.hostName,
                        lastIP           = d.lastIP,
                        port             = d.port,
                        client           = httpClient,
                        onConnectedWith  = { connectedHost ->
                            if (connectedHost != d.hostName) {
                                scope.launch { deviceRepository.updateLastIP(d.name, connectedHost) }
                            }
                        },
                    )
                }
                LightnetDevice(connector)
            }
        }
        DisposableEffect(device) {
            device?.load()
            onDispose { device?.close() }
        }

        // Hoisted HTTP API client — keyed on effective host + port, null for demo device.
        val httpApiClient = remember(activeDevice?.effectiveHost, activeDevice?.port) {
            val d = activeDevice ?: return@remember null
            if (d.host == DEMO_DEVICE_HOST) return@remember null
            d.effectiveHost.ifEmpty { null }?.let { h ->
                LightnetHttpClient("http://$h:${d.port}")
            }
        }
        DisposableEffect(httpApiClient) { onDispose { httpApiClient?.close() } }

        val bottomBar: @Composable () -> Unit = {
            LightnetBottomNav(selected = selectedTab, onSelect = { selectedTab = it })
        }

        Box(Modifier.fillMaxSize()) {
            when (selectedTab) {
                RootTab.Devices -> MyDevicesScreen(
                    devices          = devices,
                    onOpenDevice     = { d ->
                        activeDevice = d
                        selectedTab  = RootTab.Control
                    },
                    onOpenDemoDevice = {
                        activeDevice = DemoSavedDevice
                        selectedTab  = RootTab.Control
                    },
                    onAddDevice      = { showAddSheet = true },
                    onEditDevice     = { editTarget = it },
                    bottomBar        = bottomBar,
                )
                RootTab.Library -> LibraryScreen(
                    httpClient = httpApiClient,
                    bottomBar  = bottomBar,
                )
                RootTab.Control -> DeviceControllerScreen(
                    device               = device,
                    activeDevice         = activeDevice,
                    httpClient           = httpApiClient,
                    onOpenDeviceSwitcher = { showSwitcher = true },
                    bottomBar            = bottomBar,
                )
            }
        }

        if (showAddSheet) {
            AddDeviceSheet(
                serviceDiscovery = serviceDiscovery,
                httpClient       = httpClient,
                existingNames    = devices.map { it.name }.toSet(),
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
                    if (activeDevice?.name == d.name) activeDevice = null
                },
                onDismiss = { editTarget = null },
            )
        }

        if (showSwitcher) {
            DeviceSwitcherSheet(
                devices         = devices,
                activeKey       = activeDevice?.let {
                    if (it.host == DEMO_DEVICE_HOST) DEMO_DEVICE_HOST else it.name
                },
                onSelect        = { activeDevice = it },
                onSelectDemo    = { activeDevice = DemoSavedDevice },
                onManageDevices = { selectedTab = RootTab.Devices },
                onDismiss       = { showSwitcher = false },
            )
        }
    }
}
