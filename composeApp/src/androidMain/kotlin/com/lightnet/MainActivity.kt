package com.lightnet

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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

        // Double back-press to exit. Compose's BackHandler (sheets, etc.) has higher priority
        // because it is added after this callback — so it handles those cases first.
        var backPressedAt = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - backPressedAt < 2_000) {
                    finish()
                } else {
                    backPressedAt = now
                    Toast.makeText(applicationContext, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        })

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
