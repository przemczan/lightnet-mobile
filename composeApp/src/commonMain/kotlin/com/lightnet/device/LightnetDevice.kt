package com.lightnet.device

import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.TopologyResponse
import com.lightnet.api.websocket.Connector
import com.lightnet.api.websocket.ConnectorState
import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.model.PanelLayout
import com.lightnet.api.websocket.model.PanelState
import com.lightnet.api.websocket.protocol.message.SetMirrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    private val _livePreview = MutableStateFlow(false)
    /** When on, panels render from mirrored animation packets instead of polled states. */
    val livePreview: StateFlow<Boolean> = _livePreview

    private val panelMirrorService  = PanelMirrorService(messageApiService, panelsListService, panelsStatesService, _livePreview, scope)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val livenessService = DeviceLivenessService(messageApiService, connectionState, scope)
    /** null = not yet checked, true = device answered the last PING, false = it didn't (or isn't connected). */
    val isOnline: StateFlow<Boolean?> = livenessService.isOnline

    // Frozen on the last mirror frame when live preview is turned off; cleared when it's turned
    // back on. Avoids both the stale-polled-state flicker and a "fast forward" jump from re-polling.
    private val _frozenStates = MutableStateFlow<List<PanelState>?>(null)

    // Source of panel render state: mirrored packets while live preview is on, else polled.
    // Empty mirror emissions are ignored downstream, so panels keep their last frame.
    private val renderStates: Flow<List<PanelState>> =
        combine(_livePreview, panelsStatesService.states, panelMirrorService.states, _frozenStates) { live, polled, mirror, frozen ->
            when {
                frozen != null -> frozen
                live -> mirror
                else -> polled
            }
        }

    private val _snapshot = MutableStateFlow<DeviceSnapshot?>(null)
    val snapshot: StateFlow<DeviceSnapshot?> = _snapshot

    /** Updated from App once the resolved WebSocket host is known. */
    @Volatile private var httpClient: LightnetHttpClient? = null

    /** Last successfully fetched appearance — survives screen navigation so the UI seeds instantly. */
    @Volatile var cachedAppearance: AppearanceResponse? = null
        private set

    /** Last known power state — survives screen navigation so the UI seeds instantly. */
    @Volatile var cachedPowerState: Boolean? = null
        private set

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
                if (cs == ConnectorState.CONNECTED) {
                    panelsListService.load()
                    // Mirroring is per-connection and defaults off on the controller, so a
                    // reconnect while preview is on must re-enable it (and re-trigger the snapshot).
                    // A controller restart resets its animation/seq state, so drop our stale
                    // per-panel players first — otherwise dedup guards in the native core can
                    // permanently swallow the replayed snapshot.
                    if (_livePreview.value) {
                        // Wait for the panel list to (re)load before re-enabling mirroring: general-call
                        // snapshot records (SET_PALETTE/SET_BASE_COLORS/START) need panelIds populated,
                        // otherwise they're dropped and animations run with no palette → black output.
                        withTimeoutOrNull(5_000) { panelsListService.panels.first { it != null } }
                        panelMirrorService.reset()
                        messageApiService.send(SetMirrorMessage(true))
                    }
                }
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

    /** Toggles live animation preview. Freezes the last animated frame when turning off. */
    fun setLivePreview(on: Boolean) {
        if (on == _livePreview.value) return
        if (on) {
            // A stale player can retain animation/seq state from a previous preview session,
            // which would blend leftover frames with the controller's fresh snapshot replay.
            panelMirrorService.reset()
            // Drop the freeze from the previous "off" so the live mirror feed shows through.
            _frozenStates.value = null
        } else {
            // Freeze on the last rendered mirror frame and leave it there — the controller keeps
            // animating, so polling for a fresh state would jump forward by however long the
            // poll round-trip took ("fast forward"). The freeze holds until preview is re-enabled.
            _frozenStates.value = panelMirrorService.states.value.ifEmpty { null }
        }
        _livePreview.value = on
        // Opt in/out of the controller's MIRROR_BATCH stream. Enabling also makes the controller
        // replay a snapshot of the current animation state, so the preview is correct at once.
        if (connectionState.value == ConnectionState.CONNECTED) {
            messageApiService.send(SetMirrorMessage(on))
        }
    }

    fun close() {
        connector.close()
        scope.cancel()
    }

    // ── HTTP operations — single point of device API access ──────────────────

    suspend fun loadAppearance(): AppearanceResponse? =
        httpClient?.runCatching { getAppearance() }?.getOrNull()?.also { cachedAppearance = it }

    suspend fun setAppearance(req: AppearanceRequest) {
        httpClient?.runCatching { setAppearance(req) }
    }

    suspend fun getPowerState(): Boolean? =
        httpClient?.runCatching { getPowerState() }?.getOrNull()?.also { cachedPowerState = it }

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

    suspend fun getTopology(): TopologyResponse? =
        httpClient?.runCatching { getTopology() }?.getOrNull()

    /** Set the logical root panel index (0 resets to the physical root). */
    suspend fun setLogicalRoot(root: Int) {
        httpClient?.runCatching { setLogicalRoot(root) }
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
                panelsStates      = renderStates,
                scope             = scope,
                initialState      = cachedStates.find { it.panelId == info.id },
            )
        }
        return DeviceSnapshot(panels, layouts)
    }
}
