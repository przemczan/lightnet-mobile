package com.lightnet.device

import com.lightnet.animation.NativeSceneCore
import com.lightnet.api.http.model.EntryIds
import com.lightnet.api.http.model.PaletteStop
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.model.PanelState
import com.lightnet.api.websocket.protocol.IicPacketBuilder
import com.lightnet.api.websocket.protocol.message.MirrorBatch
import com.lightnet.api.websocket.protocol.message.MirrorRecord
import com.lightnet.api.websocket.protocol.message.decodeMirrorBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Plays a whole scene **with no controller connected** by running the shared C++ scene engine
 * locally ([NativeSceneCore], firmware lib/Lightnet/Core/Controller/Scene via controller_core_c.h) and rendering its
 * emitted packets through the same [PanelPacketRenderer] the live mirror uses. Offline preview is
 * therefore byte-identical to live preview — same engine, same packets, same per-panel players.
 *
 * Flow: supply the panel tree (cached from a controller, or a user-authored virtual tree) + any
 * named palettes/tags the scene uses, then [play] a scene JSON. The engine returns the packets it
 * emits as a MIRROR_BATCH payload; we decode + render them and tick ~30fps.
 *
 * All engine/renderer access is confined to a single-threaded dispatcher.
 */
class OfflineSceneService(private val scope: CoroutineScope) {
    private val core = NativeSceneCore()
    private val renderer = PanelPacketRenderer()

    private val _states = MutableStateFlow<List<PanelState>>(emptyList())
    val states: StateFlow<List<PanelState>> = _states

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    /** Set to the parser message when a [play] is rejected (invalid scene JSON), else null. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val work = Dispatchers.Default.limitedParallelism(1)
    private val clock = TimeSource.Monotonic.markNow()
    private fun nowMs(): Int = clock.elapsedNow().inWholeMilliseconds.toInt()
    private var tickJob: Job? = null

    /** Supply the panel tree (cached or virtual). `logicalRoot` is the 1-based rooting panel. */
    fun setTopology(panels: List<PanelInfo>, logicalRoot: Int = 1) {
        scope.launch(work) {
            val t = buildTopology(panels)
            core.setTopology(t.indices, t.count, t.links, t.linkCount, t.edgeCounts, logicalRoot)
            renderer.panelIds = panels.map { it.id }
        }
    }

    /** Register a palette the scene references by name. `stops` = `count`*4 bytes {pos, r, g, b}. */
    fun registerPalette(name: String, stops: ByteArray, count: Int) =
        run { scope.launch(work) { core.setPalette(name, stops, count) } }

    /** Register a named palette from its `#RRGGBB` stops (as returned by `GET /api/palettes`). */
    fun registerPalette(name: String, stops: List<PaletteStop>) {
        if (stops.isEmpty()) return
        val bytes = ByteArray(stops.size * 4)
        stops.forEachIndexed { i, stop ->
            val rgb = stop.color.removePrefix("#").toInt(16)
            bytes[i * 4] = stop.position.toByte()
            bytes[i * 4 + 1] = ((rgb shr 16) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((rgb shr 8) and 0xFF).toByte()
            bytes[i * 4 + 3] = (rgb and 0xFF).toByte()
        }
        registerPalette(name, bytes, stops.size)
    }

    fun clearPalettes() = run { scope.launch(work) { core.clearPalettes() } }

    /** Register a device tag → its 1-based panel indices. */
    fun registerTag(name: String, panels: List<Int>) = run {
        scope.launch(work) { core.setTag(name, ByteArray(panels.size) { panels[it].toByte() }, panels.size) }
    }

    fun clearTags() = run { scope.launch(work) { core.clearTags() } }

    /** Parse + play a scene JSON. On success, renders the scene; on failure, sets [error]. */
    fun play(sceneJson: String) {
        scope.launch(work) {
            tickJob?.cancel()
            renderer.release()
            _error.value = null

            val batch = core.loadAndPlay(sceneJson.encodeToByteArray(), nowMs())
            if (batch == null) {
                _error.value = core.lastError()
                _playing.value = false
                return@launch
            }
            _playing.value = true
            applyAndPublish(decodeMirrorBatch(batch))

            tickJob = scope.launch(work) {
                while (true) {
                    delay(FRAME_MS)
                    if (!core.isPlaying()) { _playing.value = false; break }
                    applyAndPublish(decodeMirrorBatch(core.tick(nowMs())))
                }
            }
        }
    }

    fun stop() {
        scope.launch(work) {
            tickJob?.cancel()
            applyAndPublish(decodeMirrorBatch(core.stop(nowMs())))
            _playing.value = false
        }
    }

    fun setSpeed(speed: Float) {
        scope.launch(work) { core.setSpeed(speed) }
    }

    /**
     * Mirror firmware appearance change while a scene plays: push palette/base colors to panel
     * renderers and re-resolve the scene engine's default palette for layers that inherit it.
     */
    fun onAppearanceChanged(
        palette: String,
        baseColors: List<String>,
        resolvePaletteStops: (String) -> List<PaletteStop>?,
        reresolvePalette: Boolean,
        reresolveColors: Boolean,
        pushPalette: Boolean,
        pushBaseColors: Boolean,
    ) {
        scope.launch(work) {
            if (!_playing.value) return@launch

            val now = nowMs().toLong()
            if (pushBaseColors) {
                broadcastPacket(IicPacketBuilder.buildSetBaseColors(baseColors), now)
            }
            if (pushPalette) {
                val stops = resolveAppearancePaletteStops(palette, baseColors, resolvePaletteStops)
                if (stops.isNotEmpty()) {
                    broadcastPacket(IicPacketBuilder.buildSetPalette(stops), now)
                }
            }

            core.reresolvePalettes(
                palette = if (reresolvePalette) palette else null,
                baseColors = if (reresolveColors) baseColors else null,
            )
            renderer.tickAll(now)
            _states.value = renderer.snapshot()
        }
    }

    fun close() {
        scope.launch(work) {
            tickJob?.cancel()
            core.close()
        }
    }

    // Render the engine's emitted packets. The batch timestamp IS the engine clock we passed in, so
    // it's used as the render `now` too → the per-panel players' clock offset is 0 (no translation).
    private fun applyAndPublish(batch: MirrorBatch) {
        val now = batch.controllerMillis
        renderer.applyBatch(batch, now)
        renderer.tickAll(now)
        _states.value = renderer.snapshot()
    }

    private fun broadcastPacket(payload: ByteArray, now: Long) {
        renderer.applyBatch(
            MirrorBatch(
                controllerMillis = now,
                records = listOf(MirrorRecord(address = 0, type = payload[0].toInt() and 0xFF, payload = payload)),
            ),
            now,
        )
    }

    private fun resolveAppearancePaletteStops(
        palette: String,
        baseColors: List<String>,
        resolvePaletteStops: (String) -> List<PaletteStop>?,
    ): List<PaletteStop> = when {
        EntryIds.isUserColors(palette) -> IicPacketBuilder.buildUserColorStops(baseColors)
        else -> resolvePaletteStops(palette) ?: IicPacketBuilder.buildUserColorStops(baseColors)
    }

    private class Topology(
        val indices: ByteArray, val count: Int,
        val links: ByteArray, val linkCount: Int,
        val edgeCounts: ByteArray,
    )

    // Flatten the PanelInfo tree into the engine's topology arrays (mirrors the firmware's
    // PanelsTopologyProvider): each connected edge becomes one link, deduped per undirected panel pair.
    private fun buildTopology(panels: List<PanelInfo>): Topology {
        val indices = ByteArray(panels.size)
        val edgeCounts = ByteArray(panels.size)
        val linkBytes = ArrayList<Byte>()
        var linkCount = 0
        val seen = HashSet<Int>()

        panels.forEachIndexed { i, panel ->
            indices[i] = panel.id.toByte()
            edgeCounts[i] = panel.edges.size.toByte()
            for (edge in panel.edges) {
                val ce = edge.connectedEdge ?: continue
                val a = panel.id
                val b = ce.panel.id
                val key = if (a < b) (a shl 8) or b else (b shl 8) or a
                if (!seen.add(key)) continue
                linkBytes.add(a.toByte()); linkBytes.add(edge.index.toByte())
                linkBytes.add(b.toByte()); linkBytes.add(ce.index.toByte())
                linkCount++
            }
        }
        return Topology(indices, panels.size, linkBytes.toByteArray(), linkCount, edgeCounts)
    }

    companion object {
        private const val FRAME_MS = 33L  // ~30fps
    }
}
