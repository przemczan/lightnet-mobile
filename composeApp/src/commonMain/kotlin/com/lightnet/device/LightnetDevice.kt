package com.lightnet.device

import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
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

    private val messageApiService   = MessageApiService(connector, scope)
    private val panelsListService   = PanelsListService(messageApiService, scope)
    private val panelsStatesService = PanelsStatesService(messageApiService, panelsListService, scope)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _snapshot = MutableStateFlow<DeviceSnapshot?>(null)
    val snapshot: StateFlow<DeviceSnapshot?> = _snapshot

    /** Updated from App once the resolved WebSocket host is known. */
    @Volatile private var httpClient: LightnetHttpClient? = null

    init {
        scope.launch {
            connector.state.collect { cs ->
                _connectionState.value = when (cs) {
                    ConnectorState.IDLE          -> ConnectionState.IDLE
                    ConnectorState.CONNECTING    -> ConnectionState.CONNECTING
                    ConnectorState.CONNECTED     -> ConnectionState.CONNECTED
                    ConnectorState.DISCONNECTED,
                    ConnectorState.FAILED        -> ConnectionState.DISCONNECTED
                }
                if (cs == ConnectorState.CONNECTED) panelsListService.load()
                if (cs == ConnectorState.DISCONNECTED || cs == ConnectorState.FAILED) _snapshot.value = null
            }
        }
        scope.launch {
            panelsListService.panels.collect { panels ->
                _snapshot.value = panels?.let { buildSnapshot(it) }
            }
        }
    }

    fun load() {
        connector.disconnect()
        connector.connect()
    }

    /** Called from App when the resolved HTTP base URL becomes available (after WS connects). */
    fun attachHttpClient(client: LightnetHttpClient?) {
        httpClient = client
    }

    /** Requests a fresh PANELS_STATES update over WebSocket if currently connected. */
    fun refreshPanelStates() {
        if (connectionState.value == ConnectionState.CONNECTED) panelsStatesService.refresh()
    }

    fun close() {
        connector.close()
        scope.cancel()
    }

    // ── HTTP operations — single point of device API access ──────────────────

    suspend fun loadAppearance(): AppearanceResponse? =
        httpClient?.runCatching { getAppearance() }?.getOrNull()

    suspend fun setAppearance(req: AppearanceRequest) {
        httpClient?.runCatching { setAppearance(req) }
    }

    suspend fun getPowerState(): Boolean? =
        httpClient?.runCatching { getPowerState() }?.getOrNull()

    suspend fun setPowerState(on: Boolean) {
        httpClient?.runCatching { setPowerState(on) }
    }

    suspend fun getPalettes(): List<String> =
        httpClient?.runCatching { getPalettes().keys.toList() }?.getOrNull() ?: emptyList()

    suspend fun getConfiguration(): ConfigurationResponse? =
        httpClient?.runCatching { getConfiguration() }?.getOrNull()

    suspend fun setConfiguration(req: ConfigurationRequest) {
        httpClient?.runCatching { setConfiguration(req) }
    }

    // ────────────────────────────────────────────────────────────────────────

    private fun buildSnapshot(panelsList: List<PanelInfo>): DeviceSnapshot {
        val layouts      = PanelsLayoutService.generateLayout(panelsList, edgeLength)
        val cachedStates = panelsStatesService.states.value
        val panels = panelsList.map { info ->
            LightnetDevicePanel(
                messageApiService = messageApiService,
                info              = info,
                layout            = layouts.first { it.panelId == info.id },
                panelsStates      = panelsStatesService.states,
                scope             = scope,
                initialState      = cachedStates.find { it.panelId == info.id },
            )
        }
        return DeviceSnapshot(panels, layouts)
    }
}
