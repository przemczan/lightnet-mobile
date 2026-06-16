package com.lightnet.demo

import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.model.AnimationPlayRequest
import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.withAppearanceDefaults
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.FirmwareFlashResponse
import com.lightnet.api.http.model.FirmwareStatusResponse
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PanelEdgeResponse
import com.lightnet.api.http.model.PanelStateResponse
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.TopologyResponse
import com.lightnet.device.OfflineSceneService
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class DemoHttpClient(
    private val settings: Settings,
    private val sceneService: OfflineSceneService,
) : DeviceHttpApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val paletteMapSerializer = MapSerializer(String.serializer(), PaletteJson.serializer())
    private val sceneListSerializer  = ListSerializer(SceneJson.serializer())

    // ── Appearance ────────────────────────────────────────────────────────────

    private var appearance: AppearanceResponse = loadAppearance()

    private fun loadAppearance(): AppearanceResponse {
        val stored = settings.getStringOrNull(KEY_APPEARANCE)
        return stored?.let { runCatching { json.decodeFromString(AppearanceResponse.serializer(), it) }.getOrNull() }
            ?: AppearanceResponse(brightness = 200, baseColors = listOf("#FF0000", "#00FF00", "#0000FF"), palette = "userColors")
    }

    override suspend fun getAppearance(): AppearanceResponse = appearance

    override suspend fun setAppearance(request: AppearanceRequest) {
        appearance = appearance.copy(
            brightness = request.brightness ?: appearance.brightness,
            baseColors = request.baseColors ?: appearance.baseColors,
            palette    = request.palette    ?: appearance.palette,
        )
        settings.putString(KEY_APPEARANCE, json.encodeToString(AppearanceResponse.serializer(), appearance))

        if (!sceneService.playing.value) return

        sceneService.onAppearanceChanged(
            palette            = appearance.palette,
            baseColors           = appearance.baseColors,
            resolvePaletteStops  = { palettes[it]?.stops },
            reresolvePalette     = !playingSceneHadOwnPalette,
            reresolveColors      = !playingSceneHadOwnColors,
            pushPalette          = request.palette != null ||
                (request.baseColors != null && appearance.palette == "userColors"),
            pushBaseColors       = request.baseColors != null,
        )
    }

    // ── Palettes ──────────────────────────────────────────────────────────────

    private val palettes: MutableMap<String, PaletteJson> = loadPalettes()

    private fun loadPalettes(): MutableMap<String, PaletteJson> {
        val stored = settings.getStringOrNull(KEY_PALETTES)
        val loaded = stored?.let { runCatching { json.decodeFromString(paletteMapSerializer, it) }.getOrNull() }
        if (!loaded.isNullOrEmpty()) return loaded.toMutableMap()
        // First use: seed with default palettes
        val defaults = DemoDataInitializer.defaultPalettes.associateBy { it.name }
        savePalettes(defaults)
        return defaults.toMutableMap()
    }

    private fun savePalettes(map: Map<String, PaletteJson>) {
        settings.putString(KEY_PALETTES, json.encodeToString(paletteMapSerializer, map))
    }

    override suspend fun getPalettes(): Map<String, PaletteJson> = palettes.toMap()

    override suspend fun getPalette(name: String): PaletteJson =
        palettes[name] ?: throw NoSuchElementException("Palette not found: $name")

    override suspend fun savePalette(palette: PaletteJson) {
        palettes[palette.name] = palette
        savePalettes(palettes)
    }

    override suspend fun deletePalette(name: String) {
        palettes.remove(name)
        savePalettes(palettes)
    }

    // ── Scenes ────────────────────────────────────────────────────────────────

    private val scenes: MutableMap<String, SceneJson> = loadScenes()

    private fun loadScenes(): MutableMap<String, SceneJson> {
        val stored = settings.getStringOrNull(KEY_SCENES)
        val loaded = stored?.let {
            runCatching { json.decodeFromString(sceneListSerializer, it) }.getOrNull()
                ?.mapNotNull { s -> s.name?.let { name -> name to s } }
                ?.toMap()?.toMutableMap()
        }
        if (!loaded.isNullOrEmpty()) {
            // Seed: add missing defaults and update existing defaults to their latest version.
            val defaults = DemoDataInitializer.defaultScenes.mapNotNull { s -> s.name?.let { it to s } }.toMap()
            val toUpdate = defaults.filter { (k, v) -> loaded[k] != v }
            if (toUpdate.isNotEmpty()) {
                loaded.putAll(toUpdate)
                settings.putString(KEY_SCENES, json.encodeToString(sceneListSerializer, loaded.values.toList()))
            }
            return loaded
        }
        // First use: seed with default scenes.
        val defaults = DemoDataInitializer.defaultScenes
            .mapNotNull { s -> s.name?.let { it to s } }
            .toMap()
            .toMutableMap()
        settings.putString(KEY_SCENES, json.encodeToString(sceneListSerializer, defaults.values.toList()))
        return defaults
    }

    private fun saveScenes() {
        settings.putString(KEY_SCENES, json.encodeToString(sceneListSerializer, scenes.values.toList()))
    }

    override suspend fun getScenes(): List<SceneInfo> =
        scenes.values.map { SceneInfo(it.name ?: "", json.encodeToString(SceneJson.serializer(), it).length) }

    override suspend fun getScene(name: String): SceneJson =
        scenes[name] ?: throw NoSuchElementException("Scene not found: $name")

    override suspend fun saveScene(scene: SceneJson) {
        val name = scene.name ?: return
        scenes[name] = scene
        saveScenes()
    }

    override suspend fun deleteScene(name: String) {
        scenes.remove(name)
        saveScenes()
    }

    private var playingSceneName: String? = null
    private var playingSceneHadOwnPalette = false
    private var playingSceneHadOwnColors = false

    private fun sceneForPlay(scene: SceneJson): SceneJson {
        playingSceneHadOwnPalette = scene.palette != null
        playingSceneHadOwnColors = scene.colors != null
        return scene.withAppearanceDefaults(appearance.palette, appearance.baseColors)
    }

    override suspend fun playSceneByName(name: String) {
        val scene = scenes[name] ?: return
        val sceneJson = json.encodeToString(SceneJson.serializer(), sceneForPlay(scene))
        sceneService.play(sceneJson)
        playingSceneName = name
    }

    override suspend fun playSceneInline(scene: SceneJson) {
        val sceneJson = json.encodeToString(SceneJson.serializer(), sceneForPlay(scene))
        sceneService.play(sceneJson)
        playingSceneName = scene.name
    }

    override suspend fun playLastScene() {
        val name = playingSceneName ?: return
        playSceneByName(name)
    }

    override suspend fun stopScene() {
        sceneService.stop()
        playingSceneName = null
        playingSceneHadOwnPalette = false
        playingSceneHadOwnColors = false
    }

    override suspend fun setSceneSpeed(speed: Float) = sceneService.setSpeed(speed)

    // ── Animations (no-op — demo panels receive these via WS) ────────────────

    override suspend fun playAnimation(request: AnimationPlayRequest) = Unit
    override suspend fun triggerAnimation(group: Int, value: Int) = Unit

    // ── Panels (handled via WebSocket in DemoConnector) ──────────────────────

    override suspend fun getPanels(): List<PanelStateResponse> = emptyList()
    override suspend fun getPanelEdges(): List<PanelEdgeResponse> = emptyList()
    override suspend fun setPanelOn(address: Int, on: Boolean) = Unit
    override suspend fun setPanelColor(address: Int, color: String) = Unit

    // ── App state ─────────────────────────────────────────────────────────────

    private var powerState: Boolean = settings.getBoolean(KEY_POWER, true)

    override suspend fun getAppState(): AppStateBody {
        val playing = sceneService.playing.value
        return AppStateBody(
            isOn                  = powerState,
            controllerFirmware    = "Demo",
            playing               = playing,
            lastPlayedScene       = playingSceneName ?: "",
            lastPlayedSceneIsStored = true,
        )
    }

    override suspend fun setPowerState(on: Boolean) {
        powerState = on
        settings.putBoolean(KEY_POWER, on)
    }

    // ── Topology ──────────────────────────────────────────────────────────────

    private var logicalRoot: Int = settings.getIntOrNull(KEY_LOGICAL_ROOT) ?: 0

    override suspend fun getTopology(): TopologyResponse = TopologyResponse(logicalRoot = logicalRoot)

    override suspend fun setLogicalRoot(root: Int) {
        logicalRoot = root
        settings.putInt(KEY_LOGICAL_ROOT, root)
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    override suspend fun getConfiguration(): ConfigurationResponse = ConfigurationResponse(powerStateOnBoot = 1)
    override suspend fun setConfiguration(request: ConfigurationRequest) = Unit

    // ── Firmware (not applicable for demo) ───────────────────────────────────

    override suspend fun uploadPanelFirmware(data: ByteArray): FirmwareFlashResponse =
        FirmwareFlashResponse(status = "error", error = "Firmware upload is not supported on demo devices.")

    override suspend fun getFirmwareStatus(): FirmwareStatusResponse =
        FirmwareStatusResponse(state = FirmwareStatusResponse.State.IDLE)

    override fun close() = Unit

    companion object {
        private const val KEY_APPEARANCE = "demo_http_appearance"
        private const val KEY_PALETTES   = "demo_http_palettes"
        private const val KEY_SCENES     = "demo_http_scenes"
        private const val KEY_POWER      = "demo_http_power"
        private const val KEY_LOGICAL_ROOT = "demo_http_logical_root"
    }
}
