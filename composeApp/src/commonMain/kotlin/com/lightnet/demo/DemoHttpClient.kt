package com.lightnet.demo

import com.lightnet.api.http.DeviceHttpApi
import com.lightnet.api.http.model.AnimationPlayRequest
import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.api.http.model.AppearanceResponse
import com.lightnet.api.http.model.EntryIds
import com.lightnet.api.http.model.PaletteJson
import com.lightnet.api.http.model.PaletteMeta
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.http.model.SceneInfo
import com.lightnet.api.http.model.SceneJson
import com.lightnet.api.http.model.TopologyResponse
import com.lightnet.api.http.model.withAppearanceDefaults
import com.lightnet.api.http.model.ConfigurationRequest
import com.lightnet.api.http.model.ConfigurationResponse
import com.lightnet.api.http.model.FirmwareFlashResponse
import com.lightnet.api.http.model.FirmwareStatusResponse
import com.lightnet.api.http.model.PanelEdgeResponse
import com.lightnet.api.http.model.PanelStateResponse
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
        return loaded?.let { migrateAppearance(it) }
            ?: AppearanceResponse(
                brightness = 200,
                baseColors = listOf("#FF0000", "#00FF00", "#0000FF"),
                palette = EntryIds.USER_COLORS,
            )
    }

    private fun migrateAppearance(app: AppearanceResponse): AppearanceResponse =
        if (app.palette == LEGACY_USER_COLORS) app.copy(palette = EntryIds.USER_COLORS) else app

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
        if (!loaded.isNullOrEmpty()) {
            val migrated = migratePaletteMap(loaded)
            if (migrated != loaded) savePalettes(migrated)
            return migrated.toMutableMap()
        }
        val defaults = DemoDataInitializer.defaultPalettes.associateBy { it.id!! }
        savePalettes(defaults)
        return defaults.toMutableMap()
    }

    private fun migratePaletteMap(loaded: Map<String, PaletteJson>): Map<String, PaletteJson> {
        val looksLikeIds = loaded.keys.all { it.length in 8..10 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } }
        return if (looksLikeIds) {
            loaded.filterKeys { !EntryIds.isUserColors(it) && it != LEGACY_USER_COLORS }
        } else {
            loaded
                .filterKeys { it != LEGACY_USER_COLORS && !EntryIds.isUserColors(it) }
                .map { (name, pal) ->
                    val id = pal.id ?: EntryIds.demoPaletteId(pal.name.ifBlank { name })
                    id to pal.copy(id = id, name = pal.name.ifBlank { name })
                }
                .toMap()
        }
    }

    private fun savePalettes(map: Map<String, PaletteJson>) {
        val stored = map.filterKeys { !EntryIds.isUserColors(it) }
        settings.putString(KEY_PALETTES, json.encodeToString(paletteMapSerializer, stored))
    }

    private fun userColorsPalette(): PaletteJson =
        PaletteJson(
            id = EntryIds.USER_COLORS,
            name = "Base colors",
            stops = IicPacketBuilder.buildUserColorStops(appearance.baseColors),
        )

    private fun resolvePaletteStops(id: String): List<PaletteStop>? = when {
        EntryIds.isUserColors(id) -> IicPacketBuilder.buildUserColorStops(appearance.baseColors)
        else -> palettes[id]?.stops
    }

    override suspend fun getPaletteMetas(): List<PaletteMeta> =
        palettes.values.map { PaletteMeta(id = it.id!!, name = it.name) } +
            PaletteMeta(id = EntryIds.USER_COLORS, name = "Base colors", builtin = true)

    override suspend fun getPalette(id: String): PaletteJson = when {
        EntryIds.isUserColors(id) -> userColorsPalette()
        else -> palettes[id] ?: throw NoSuchElementException("Palette not found: $id")
    }

    override suspend fun savePalette(palette: PaletteJson): String {
        if (palette.id != null && EntryIds.isUserColors(palette.id)) {
            throw IllegalArgumentException("cannot_overwrite_builtin")
        }
        val id = palette.id ?: EntryIds.demoPaletteId(palette.name)
        palettes[id] = palette.copy(id = id)
        savePalettes(palettes)
        return id
    }

    override suspend fun deletePalette(id: String) {
        if (EntryIds.isUserColors(id)) throw IllegalArgumentException("cannot_delete_builtin")
        palettes.remove(id)
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
            val map = migrateScenes(loaded)
            val defaults = DemoDataInitializer.defaultScenes.mapNotNull { s ->
                s.id?.let { it to s }
            }.toMap()
            val toUpdate = defaults.filter { (k, v) -> map[k] != v }
            if (toUpdate.isNotEmpty()) {
                map.putAll(toUpdate)
                saveScenes(map)
            }
            return map
        }
        val defaults = DemoDataInitializer.defaultScenes
            .mapNotNull { s -> s.id?.let { it to s } }
            .toMap()
            .toMutableMap()
        saveScenes(defaults)
        return defaults
    }

    private fun migrateScenes(loaded: List<SceneJson>): MutableMap<String, SceneJson> {
        val byId = loaded.mapNotNull { scene ->
            val name = scene.name ?: return@mapNotNull null
            val id = scene.id ?: EntryIds.demoSceneId(name)
            id to scene.copy(
                id = id,
                palette = scene.palette?.let { migratePaletteRef(it) },
            )
        }.toMap().toMutableMap()
        return byId
    }

    private fun migratePaletteRef(ref: String): String =
        if (ref == LEGACY_USER_COLORS || EntryIds.isUserColors(ref)) EntryIds.USER_COLORS
        else palettes.values.find { it.name == ref }?.id
            ?: palettes[ref]?.id
            ?: EntryIds.demoPaletteId(ref)

    private fun saveScenes(map: Map<String, SceneJson> = scenes) {
        settings.putString(KEY_SCENES, json.encodeToString(sceneListSerializer, map.values.toList()))
    }

    override suspend fun getScenes(): List<SceneInfo> =
        scenes.values.map { scene ->
            SceneInfo(
                id = scene.id!!,
                name = scene.name ?: "",
                layersNum = scene.layers.size,
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
        private const val LEGACY_USER_COLORS = "userColors"
        private const val KEY_APPEARANCE = "demo_http_appearance"
        private const val KEY_PALETTES   = "demo_http_palettes"
        private const val KEY_SCENES     = "demo_http_scenes"
        private const val KEY_POWER      = "demo_http_power"
        private const val KEY_LOGICAL_ROOT = "demo_http_logical_root"
    }
}
