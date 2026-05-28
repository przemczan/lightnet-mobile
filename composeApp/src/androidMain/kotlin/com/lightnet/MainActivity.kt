package com.lightnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lightnet.discovery.DeviceRepository
import com.lightnet.discovery.JmDnsServiceDiscovery
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

class MainActivity : ComponentActivity() {

    private val httpClient by lazy {
        HttpClient(OkHttp) { install(WebSockets) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lightnet_devices", MODE_PRIVATE)
        val deviceRepository = DeviceRepository(SharedPreferencesSettings(prefs))
        val serviceDiscovery = JmDnsServiceDiscovery(applicationContext)

        setContent {
            LightnetApp(
                serviceDiscovery = serviceDiscovery,
                deviceRepository = deviceRepository,
                httpClient       = httpClient,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }
}
