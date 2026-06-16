package com.lightnet.demo

import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.russhwolf.settings.Settings

const val DEMO_DEVICE_ID = "__demo__"
const val DEMO_DEVICE_NAME = "Demo"

fun demoSavedDevice(panelCount: Int) = SavedDevice(
    id         = DEMO_DEVICE_ID,
    name       = DEMO_DEVICE_NAME,
    host       = "",
    port       = 0,
    panelCount = panelCount,
)

fun createDemoDevice(panelCount: Int, settings: Settings): LightnetDevice {
    val connector = DemoConnector(panelCount, settings)
    val device    = LightnetDevice(connector)
    val client    = DemoHttpClient(settings, device.offlineSceneService)
    device.attachHttpClient(client)
    return device
}
