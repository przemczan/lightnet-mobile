package com.lightnet.device

import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.SceneInfo
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
import kotlinx.coroutines.yield

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

    /** True while the controller is playing a scene — mirror rendering is only used then. */
    private val _scenePlaying = MutableStateFlow(false)

    /**
     * Paint mode: panel colours are owned locally (user taps/drags). Mirror and polled controller
     * state are ignored so live preview cannot overwrite paints.
     */
    private val _paintMode = MutableStateFlow(true)
    val paintMode: StateFlow<Boolean> = _paintMode

    /**
     * Drives the visualizer when a scene is played offline (demo device or future local preview).
     * Topology and palettes are kept in sync automatically as the device loads them.
     */
    val offlineSceneService = OfflineSceneService(scope)

    // Null when not playing; set to the rendered states while offline playback is active.
    private val offlineActiveStates: Flow<List<PanelState>?> =
        offlineSceneService.states.combine(offlineSceneService.playing) { states, playing ->
            if (playing) states else null
        }

    // Source of panel render state while a scene is playing: frozen > offline (demo) > mirror > polled.
    // In paint mode, [LightnetDevicePanel] ignores this flow entirely — colours are local only.
    private val renderStates: Flow<List<PanelState>> =
        combine(
            combine(_livePreview, _scenePlaying, panelsStatesService.states) { live, scenePlaying, polled ->
                Triple(live, scenePlaying, polled)
            },
            panelMirrorService.states,
            _frozenStates,
            offlineActiveStates,
        ) { (live, scenePlaying, polled), mirror, frozen, offline ->
            when {
                frozen != null -> frozen
                offline != null && live -> offline
                scenePlaying -> if (live) mirror.ifEmpty { polled } else polled
                else -> polled
            }
        }

    private val _snapshot = MutableStateFlow<DeviceSnapshot?>(null)
    val snapshot: StateFlow<DeviceSnapshot?> = _snapshot

    /** Attached from App once the resolved WebSocket host is known (or pre-wired for demo). */
    @Volatile private var httpClient: DeviceHttpApi? = null

    /** The currently attached HTTP client (read-only access for App.kt and screens). */
    val activeHttpClient: DeviceHttpApi? get() = httpClient

    /** Last successfully fetched appearance — survives screen navigation so the UI seeds instantly. */
    @Volatile var cachedAppearance: AppearanceResponse? = null
        private set

    /** Last known power state — survives screen navigation so the UI seeds instantly. */
    @Volatile var cachedPowerState: Boolean? = null
        private set

    /** Last known controller firmware version, read from `/api/state`. */
    @Volatile var cachedControllerFirmware: String? = null
        private set

    /** Last known logical root — 0 means the physical root (panel 1). */
    @Volatile private var cachedLogicalRoot: Int = 0
        private set

    private val _palettes = MutableStateFlow<List<PaletteJson>?>(null)
    /** Device palettes — null until first loaded; survives navigation. */
    val palettes: StateFlow<List<PaletteJson>?> = _palettes

    private val _palettesLoading = MutableStateFlow(false)
    val palettesLoading: StateFlow<Boolean> = _palettesLoading

    private val _scenes = MutableStateFlow<List<SceneInfo>?>(null)
    /** Device scenes — null until first loaded; survives navigation. */
    val scenes: StateFlow<List<SceneInfo>?> = _scenes

    private val _scenesLoading = MutableStateFlow(false)
    val scenesLoading: StateFlow<Boolean> = _scenesLoading

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
                    scope.launch { refreshCachedLogicalRoot() }
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
                        resyncLiveMirror()
                    }
                }
                // Invalidate the palette cache on (re)connect so the next loadPalettes() call
                // picks up any changes made on the controller while we were disconnected.
                if (cs == ConnectorState.CONNECTED) {
                    _palettes.value = null
                    _scenes.value = null
                }
                if (cs == ConnectorState.DISCONNECTED || cs == ConnectorState.FAILED) _snapshot.value = null
            }
        }
        scope.launch {
            panelsListService.panels.collect { panels ->
                _snapshot.value = panels?.let { buildSnapshot(it) }
                if (panels != null) offlineSceneService.setTopology(panels, cachedLogicalRoot)
            }
        }
    }

    fun load() {
        scope.launch {
            connector.disconnect()
            // Yield so the state collector processes DISCONNECTED before we reconnect.
            // Without this, StateFlow conflation may swallow the CONNECTED emission when
            // the previous state was also CONNECTED (e.g. demo shuffle / panel-count change).
            yield()
            connector.connect()
        }
    }

    /** Re-requests the panel list without disconnecting. Used by demo shuffle. */
    fun reloadPanels() {
        if (connectionState.value == ConnectionState.CONNECTED) panelsListService.load()
    }

    /** Called from App when the resolved HTTP base URL becomes available (after WS connects). */
    fun attachHttpClient(client: DeviceHttpApi?) {
        httpClient = client
        if (connectionState.value == ConnectionState.CONNECTED) {
            scope.launch { refreshCachedLogicalRoot() }
        }
    }

    /** Requests a fresh PANELS_STATES update over WebSocket if currently connected. */
    fun refreshPanelStates() {
        if (connectionState.value == ConnectionState.CONNECTED) panelsStatesService.refresh()
    }

    /** Switches between scene playback (mirror-driven) and paint mode (local panel state). */
    fun setScenePlaying(playing: Boolean) {
        if (playing == _scenePlaying.value) return
        val wasPlaying = _scenePlaying.value
        _scenePlaying.value = playing
        _paintMode.value = !playing
        _frozenStates.value = null
        if (playing) {
            // Live preview may already be on (UI toggle unchanged) — still need a fresh renderer
            // and a controller snapshot replay; reset alone leaves an empty mirror with no re-sync.
            if (_livePreview.value) resyncLiveMirror()
        } else if (wasPlaying) {
            resetPanelsForPaint()
        }
    }

    private fun resetPanelsForPaint() {
        _snapshot.value?.panels?.forEach { it.resetForPaint() }
    }

    /** Clears the mirror renderer and asks the controller to replay its animation snapshot. */
    private fun resyncLiveMirror() {
        panelMirrorService.reset()
        if (connectionState.value == ConnectionState.CONNECTED) {
            messageApiService.send(SetMirrorMessage(true))
        }
    }

    /** Toggles live animation preview. Freezes the last animated frame when turning off. */
    fun setLivePreview(on: Boolean) {
        if (on == _livePreview.value) return
        if (on) {
            // A stale player can retain animation/seq state from a previous preview session,
            // which would blend leftover frames with the controller's fresh snapshot replay.
            panelMirrorService.reset()
            _frozenStates.value = null
        } else {
            // Freeze on the last rendered frame (mirror for real devices, offline for demo).
            // This prevents a "fast forward" jump when re-polling after the controller kept animating.
            val lastMirror  = panelMirrorService.states.value.ifEmpty { null }
            // Capture the last offline frame even if the scene is no longer playing — the engine
            // keeps the "stop" frame in states, so this freezes on the stopped animation state
            // rather than falling through to the (usually white/off) polled states.
            val lastOffline = offlineSceneService.states.value.ifEmpty { null }
            _frozenStates.value = lastMirror ?: lastOffline
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
        offlineSceneService.close()
        scope.cancel()
    }

    // ── HTTP operations — single point of device API access ──────────────────

    suspend fun loadAppearance(): AppearanceResponse? =
        httpClient?.runCatching { getAppearance() }?.getOrNull()?.also { cachedAppearance = it }

    suspend fun setAppearance(req: AppearanceRequest) {
        httpClient?.runCatching { setAppearance(req) }
    }

    suspend fun getPowerState(): Boolean? =
        httpClient?.runCatching { getAppState() }?.getOrNull()?.also {
            cachedPowerState = it.isOn
            cachedControllerFirmware = it.controllerFirmware
        }?.isOn

    suspend fun setPowerState(on: Boolean) {
        httpClient?.runCatching { setPowerState(on) }
    }

    suspend fun getPalettes(): List<String> =
        httpClient?.runCatching { getPalettes().keys.toList() }?.getOrNull() ?: emptyList()

    /** Loads device palettes once and caches them; pass `force = true` to reload. */
    suspend fun loadPalettes(force: Boolean = false) {
        if (!force && _palettes.value != null) return
        _palettesLoading.value = true
        try {
            val palettes = httpClient?.runCatching { getPalettes().values.toList() }?.getOrNull() ?: emptyList()
            _palettes.value = palettes
            offlineSceneService.clearPalettes()
            palettes.forEach { offlineSceneService.registerPalette(it.name, it.stops) }
        } finally {
            _palettesLoading.value = false
        }
    }

    suspend fun refreshPalettes() = loadPalettes(force = true)

    /** Loads device scenes once and caches them; pass `force = true` to reload. */
    suspend fun loadScenes(force: Boolean = false) {
        if (!force && _scenes.value != null) return
        _scenesLoading.value = true
        try {
            _scenes.value = httpClient?.runCatching { getScenes() }?.getOrNull() ?: emptyList()
        } finally {
            _scenesLoading.value = false
        }
    }

    suspend fun refreshScenes() = loadScenes(force = true)

    suspend fun getConfiguration(): ConfigurationResponse? =
        httpClient?.runCatching { getConfiguration() }?.getOrNull()

    suspend fun setConfiguration(req: ConfigurationRequest) {
        httpClient?.runCatching { setConfiguration(req) }
    }

    suspend fun getTopology(): TopologyResponse? =
        httpClient?.runCatching { getTopology() }?.getOrNull()?.also { cachedLogicalRoot = it.logicalRoot }

    /** Set the logical root panel index (0 resets to the physical root). */
    suspend fun setLogicalRoot(root: Int) {
        httpClient?.runCatching { setLogicalRoot(root) }
        cachedLogicalRoot = root
        panelsListService.panels.value?.let { offlineSceneService.setTopology(it, root) }
    }

    private suspend fun refreshCachedLogicalRoot() {
        cachedLogicalRoot = httpClient?.runCatching { getTopology() }?.getOrNull()?.logicalRoot ?: 0
        panelsListService.panels.value?.let { offlineSceneService.setTopology(it, cachedLogicalRoot) }
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
                paintMode         = _paintMode,
                scope             = scope,
                initialState      = cachedStates.find { it.panelId == info.id },
            )
        }
        return DeviceSnapshot(panels, layouts)
    }
}
