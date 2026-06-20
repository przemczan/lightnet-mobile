package com.lightnet.demo

import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.model.AnimationPlayRequest
import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.EntryIds
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.withAppearanceDefaults
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.FirmwareFlashResponse
import com.lightnet.api.http.model.FirmwareStatusResponse
import com.lightnet.api.http.model.PanelEdgeResponse
import com.lightnet.api.http.model.PanelStateResponse
import com.lightnet.api.http.model.paletteNamesEqual
import com.lightnet.api.websocket.protocol.IicPacketBuilder
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
        val loaded = stored?.let { runCatching { json.decodeFromString(AppearanceResponse.serializer(), it) }.getOrNull() }
        return loaded ?: AppearanceResponse(
            brightness = 200,
            baseColors = listOf("#FF0000", "#00FF00", "#0000FF"),
            palette = EntryIds.USER_COLORS_NAME,
        )
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
            resolvePaletteStops  = ::resolvePaletteStops,
            reresolvePalette     = !playingSceneHadOwnPalette,
            reresolveColors      = !playingSceneHadOwnColors,
            pushPalette          = request.palette != null ||
                (request.baseColors != null && EntryIds.isUserColors(appearance.palette)),
            pushBaseColors       = request.baseColors != null,
        )
    }

    // ── Palettes ──────────────────────────────────────────────────────────────

    private val palettes: MutableMap<String, PaletteJson> = loadPalettes()

    private fun loadPalettes(): MutableMap<String, PaletteJson> {
        val stored = settings.getStringOrNull(KEY_PALETTES)
        val loaded = stored?.let { runCatching { json.decodeFromString(paletteMapSerializer, it) }.getOrNull() }
        if (!loaded.isNullOrEmpty()) return loaded.toMutableMap()
        val defaults = DemoDataInitializer.defaultPalettes.associateBy { it.name }
        savePalettes(defaults)
        return defaults.toMutableMap()
    }

    private fun savePalettes(map: Map<String, PaletteJson>) {
        val stored = map.filterKeys { !EntryIds.isUserColors(it) }
        settings.putString(KEY_PALETTES, json.encodeToString(paletteMapSerializer, stored))
    }

    private fun findPaletteKey(name: String): String? =
        palettes.keys.find { paletteNamesEqual(it, name) }

    private fun userColorsPalette(): PaletteJson =
        PaletteJson(
            name = EntryIds.USER_COLORS_NAME,
            builtin = true,
            stops = IicPacketBuilder.buildUserColorStops(appearance.baseColors),
        )

    private fun resolvePaletteStops(name: String): List<PaletteStop>? = when {
        EntryIds.isUserColors(name) -> IicPacketBuilder.buildUserColorStops(appearance.baseColors)
        else -> findPaletteKey(name)?.let { palettes[it]?.stops }
    }

    override suspend fun getPalettes(): List<PaletteJson> =
        palettes.values.toList() + userColorsPalette()

    override suspend fun getPalette(name: String): PaletteJson = when {
        EntryIds.isUserColors(name) -> userColorsPalette()
        else -> findPaletteKey(name)?.let { palettes[it] }
            ?: throw NoSuchElementException("Palette not found: $name")
    }

    override suspend fun savePalette(palette: PaletteJson): String {
        if (EntryIds.isUserColors(palette.name)) {
            throw IllegalArgumentException("cannot_overwrite_builtin")
        }
        palettes[palette.name] = palette
        savePalettes(palettes)
        return palette.name
    }

    override suspend fun updatePalette(name: String, stops: List<PaletteStop>) {
        if (EntryIds.isUserColors(name)) throw IllegalArgumentException("cannot_overwrite_builtin")
        val key = findPaletteKey(name) ?: throw NoSuchElementException("Palette not found: $name")
        palettes[key] = palettes.getValue(key).copy(stops = stops)
        savePalettes(palettes)
    }

    override suspend fun deletePalette(name: String) {
        if (EntryIds.isUserColors(name)) throw IllegalArgumentException("cannot_delete_builtin")
        findPaletteKey(name)?.let { palettes.remove(it) }
        savePalettes(palettes)
    }

    // ── Scenes ────────────────────────────────────────────────────────────────

    private val scenes: MutableMap<String, SceneJson> = loadScenes()

    private fun loadScenes(): MutableMap<String, SceneJson> {
        val stored = settings.getStringOrNull(KEY_SCENES)
        val loaded = stored?.let {
            runCatching { json.decodeFromString(sceneListSerializer, it) }.getOrNull()
        }
        if (!loaded.isNullOrEmpty()) {
            return loaded.mapNotNull { scene ->
                val name = scene.name ?: return@mapNotNull null
                val id = scene.id ?: EntryIds.demoSceneId(name)
                id to scene.copy(id = id)
            }.toMap().toMutableMap()
        }
        val defaults = DemoDataInitializer.defaultScenes
            .mapNotNull { s -> s.id?.let { it to s } }
            .toMap()
            .toMutableMap()
        saveScenes(defaults)
        return defaults
    }

    private fun saveScenes(map: Map<String, SceneJson> = scenes) {
        settings.putString(KEY_SCENES, json.encodeToString(sceneListSerializer, map.values.toList()))
    }

    override suspend fun getScenes(): List<SceneInfo> =
        scenes.values.map { scene ->
            SceneInfo(
                id = scene.id!!,
                name = scene.name ?: "",
                layerCount = scene.layers.size,
                duration = 0,
            )
        }

    override suspend fun getScene(id: String): SceneJson =
        scenes[id] ?: throw NoSuchElementException("Scene not found: $id")

    override suspend fun saveScene(scene: SceneJson): String {
        val name = scene.name ?: throw IllegalArgumentException("scene_name_required")
        val id = scene.id ?: EntryIds.demoSceneId(name)
        scenes[id] = scene.copy(id = id)
        saveScenes()
        return id
    }

    override suspend fun deleteScene(id: String) {
        scenes.remove(id)
        saveScenes()
    }

    private var playingSceneId: String? = null
    private var playingSceneIsStored = true
    private var playingSceneHadOwnPalette = false
    private var playingSceneHadOwnColors = false

    private fun sceneForPlay(scene: SceneJson): SceneJson {
        playingSceneHadOwnPalette = scene.palette != null
        playingSceneHadOwnColors = scene.colors != null
        return scene.withAppearanceDefaults(appearance.palette, appearance.baseColors)
    }

    override suspend fun playSceneById(id: String) {
        val scene = scenes[id] ?: return
        val sceneJson = json.encodeToString(SceneJson.serializer(), sceneForPlay(scene))
        sceneService.play(sceneJson)
        playingSceneId = id
        playingSceneIsStored = true
    }

    override suspend fun playSceneInline(scene: SceneJson) {
        val sceneJson = json.encodeToString(SceneJson.serializer(), sceneForPlay(scene))
        sceneService.play(sceneJson)
        playingSceneId = scene.id
        playingSceneIsStored = false
    }

    override suspend fun playLastScene() {
        val id = playingSceneId ?: return
        playSceneById(id)
    }

    override suspend fun stopScene() {
        sceneService.stop()
        playingSceneId = null
        playingSceneIsStored = true
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
            isOn                    = powerState,
            controllerFirmware      = "Demo",
            playing                 = playing,
            lastPlayedSceneId       = playingSceneId ?: "",
            lastPlayedSceneIsStored = playingSceneIsStored,
        )
    }

    override suspend fun setPowerState(on: Boolean) {
        powerState = on
        settings.putBoolean(KEY_POWER, on)
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    private var logicalRoot: Int = settings.getIntOrNull(KEY_LOGICAL_ROOT) ?: 0

    override suspend fun getConfiguration(): ConfigurationResponse =
        ConfigurationResponse(powerStateOnBoot = 1, logicalRoot = logicalRoot)

    override suspend fun setConfiguration(request: ConfigurationRequest) {
        request.logicalRoot?.let {
            logicalRoot = it
            settings.putInt(KEY_LOGICAL_ROOT, it)
        }
    }

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
