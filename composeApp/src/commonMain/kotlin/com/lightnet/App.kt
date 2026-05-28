package com.lightnet

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lightnet.discovery.DeviceRepository
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.ServiceDiscovery
import com.lightnet.ui.screens.DeviceControllerScreen
import com.lightnet.ui.screens.DeviceDiscoveryScreen
import com.lightnet.ui.screens.MyDevicesScreen
import io.ktor.client.HttpClient

sealed class AppScreen {
    object MyDevices : AppScreen()
    object DeviceDiscovery : AppScreen()
    data class DeviceController(val host: String, val port: Int) : AppScreen()
}

@Composable
fun LightnetApp(
    serviceDiscovery: ServiceDiscovery,
    deviceRepository: DeviceRepository,
    httpClient: HttpClient,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.MyDevices) }

        // Device list lives here so any screen change refreshes it
        var devices by remember { mutableStateOf(deviceRepository.getAll()) }
        fun refreshDevices() { devices = deviceRepository.getAll() }

        fun navigateTo(screen: AppScreen) = backStack.add(screen)
        fun navigateBack() { if (backStack.size > 1) backStack.removeLast() }

        when (val screen = backStack.last()) {
            is AppScreen.MyDevices -> MyDevicesScreen(
                devices          = devices,
                onDelete         = { name -> deviceRepository.remove(name); refreshDevices() },
                onOpenDevice     = { d -> navigateTo(AppScreen.DeviceController(d.host, d.port)) },
                onOpenDiscovery  = { navigateTo(AppScreen.DeviceDiscovery) },
            )
            is AppScreen.DeviceDiscovery -> DeviceDiscoveryScreen(
                serviceDiscovery = serviceDiscovery,
                savedDevices     = devices,
                onAdd            = { d -> deviceRepository.add(SavedDevice(d.name, d.host, d.port)); refreshDevices() },
                onOpenDevice     = { d -> navigateTo(AppScreen.DeviceController(d.host, d.port)) },
                onNavigateBack   = ::navigateBack,
            )
            is AppScreen.DeviceController -> DeviceControllerScreen(
                host           = screen.host,
                port           = screen.port,
                httpClient     = httpClient,
                onNavigateBack = ::navigateBack,
            )
        }
    }
}
