package com.lightnet.device

import com.lightnet.api.websocket.Connector
import com.lightnet.api.websocket.ConnectorState
import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.model.PanelLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

data class DeviceSnapshot(
    val panels: List<LightnetDevicePanel>,
    val layouts: List<PanelLayout>,
)

class LightnetDevice(
    val connector: Connector,
    private val edgeLength: Double = 100.0,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val messageApiService  = MessageApiService(connector, scope)
    private val panelsListService  = PanelsListService(messageApiService, scope)
    private val panelsStatesService = PanelsStatesService(messageApiService, panelsListService, scope)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _snapshot = MutableStateFlow<DeviceSnapshot?>(null)
    val snapshot: StateFlow<DeviceSnapshot?> = _snapshot

    init {
        scope.launch {
            connector.state.collect { cs ->
                _connectionState.value = when (cs) {
                    ConnectorState.IDLE          -> ConnectionState.IDLE
                    ConnectorState.CONNECTING    -> ConnectionState.CONNECTING
                    ConnectorState.CONNECTED     -> ConnectionState.CONNECTED
                    ConnectorState.DISCONNECTED  -> ConnectionState.DISCONNECTED
                }
                // Reload panels on every (re)connection — covers both first connect and
                // automatic reconnects after a drop.
                if (cs == ConnectorState.CONNECTED) panelsListService.load()
                // Clear the snapshot so the UI shows the loading indicator during reconnect.
                if (cs == ConnectorState.DISCONNECTED) _snapshot.value = null
            }
        }
        scope.launch {
            panelsListService.panels
                .map { buildSnapshot(it) }
                .collect { _snapshot.value = it }
        }
    }

    fun load() {
        connector.disconnect()
        connector.connect()
        // panelsListService.load() is now triggered by the CONNECTED state transition above
    }

    fun close() {
        connector.close()
        scope.cancel()
    }

    private fun buildSnapshot(panelsList: List<PanelInfo>): DeviceSnapshot {
        val layouts = PanelsLayoutService.generateLayout(panelsList, edgeLength)
        val panels = panelsList.map { info ->
            LightnetDevicePanel(
                messageApiService = messageApiService,
                info              = info,
                layout            = layouts.first { it.panelId == info.id },
                panelsStates      = panelsStatesService.states,
                scope             = scope,
            )
        }
        return DeviceSnapshot(panels, layouts)
    }
}
