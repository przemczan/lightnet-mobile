package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightnet.api.websocket.MockConnector
import com.lightnet.api.websocket.SocketConnector
import com.lightnet.device.ConnectionState
import com.lightnet.device.LightnetDevice
import com.lightnet.ui.components.LightnetDeviceVisualizer
import io.ktor.client.HttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControllerScreen(
    host: String,
    port: Int,
    httpClient: HttpClient,
    onNavigateBack: () -> Unit,
) {
    val device = remember(host, port) {
        val connector = if (host == "mock") MockConnector()
                        else SocketConnector(host, port, httpClient)
        LightnetDevice(connector)
    }

    DisposableEffect(device) {
        device.load()
        onDispose { device.close() }
    }

    val connectionState by device.connectionState.collectAsState()
    val snapshot by device.snapshot.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (host == "mock") "Demo Device" else host) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Coloured dot: green = connected, amber = connecting, red = disconnected
                    val dotColor = when (connectionState) {
                        ConnectionState.CONNECTED    -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING   -> Color(0xFFFFC107)
                        ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                        else                         -> Color.Gray
                    }
                    Box(
                        Modifier
                            .padding(end = 16.dp)
                            .size(10.dp)
                            .background(dotColor, CircleShape)
                    )
                },
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                snapshot != null -> LightnetDeviceVisualizer(
                    panels = snapshot!!.panels,
                    modifier = Modifier.fillMaxSize(),
                )
                connectionState == ConnectionState.DISCONNECTED -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Disconnected", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { device.load() }) { Text("Retry") }
                    }
                }
                else -> CircularProgressIndicator()
            }
        }
    }
}
