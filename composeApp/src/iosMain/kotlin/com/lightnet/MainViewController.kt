package com.lightnet

import androidx.compose.ui.window.ComposeUIViewController
import com.lightnet.discovery.DeviceRepository
import com.lightnet.discovery.StubServiceDiscovery
import com.lightnet.settings.AppPreferences
import com.russhwolf.settings.NSUserDefaultsSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import platform.Foundation.NSUserDefaults

fun MainViewController() = ComposeUIViewController {
    AppPreferences.init(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults))
    LightnetApp(
        serviceDiscovery = StubServiceDiscovery(),
        deviceRepository = DeviceRepository(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)),
        httpClient       = HttpClient(Darwin) { install(WebSockets) },
    )
}
