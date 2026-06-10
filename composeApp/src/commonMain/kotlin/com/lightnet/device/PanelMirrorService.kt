package com.lightnet.device

import com.lightnet.animation.PanelAnimationPlayer
import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelState
import com.lightnet.api.websocket.protocol.IicPacketType
import com.lightnet.api.websocket.protocol.IicPacketType.Companion.IIC_META_SIZE
import com.lightnet.api.websocket.protocol.message.MirrorRecord
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Reconstructs live per-panel state from mirrored I²C packets (MIRROR_BATCH frames) so the
 * visualizer can show what the panels are doing in real time without polling GET_PANELS_STATES.
 *
 * Each panel is driven by a [PanelAnimationPlayer] (a faithful port of the firmware player), so
 * both direct SET_COLOR / runner frames and panel-local animations (FADE/BREATHE/PULSE/…) render.
 * A driver loop ticks all players at ~30fps and emits a snapshot whenever a color changes.
 *
 * All player access is confined to a single-threaded dispatcher so the batch handler and the
 * tick loop never mutate a player concurrently.
 */
class PanelMirrorService(
    messageApiService: MessageApiService,
    panelsListService: PanelsListService,
    panelsStatesService: PanelsStatesService,
    private val livePreview: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    private class Panel(val player: PanelAnimationPlayer, var on: Boolean)

    private val _states = MutableStateFlow<List<PanelState>>(emptyList())
    val states: StateFlow<List<PanelState>> = _states

    private val panels = LinkedHashMap<Int, Panel>()
    private var panelIds: List<Int> = emptyList()

    private val scope = scope

    // Single-thread confinement for all player/state mutation.
    private val work = Dispatchers.Default.limitedParallelism(1)
    private val clock = TimeSource.Monotonic.markNow()
    private fun nowMs(): Long = clock.elapsedNow().inWholeMilliseconds

    init {
        scope.launch(work) {
            panelsListService.panels.collect { list ->
                panelIds = list?.map { it.id } ?: emptyList()
            }
        }
        // Baseline color/on from real polled state the first time each panel is seen, so panels
        // not yet driven by an animation show their actual color rather than black.
        scope.launch(work) {
            panelsStatesService.states.collect { polled ->
                for (state in polled) {
                    val existed = panels.containsKey(state.panelId)
                    val panel = getOrCreate(state.panelId)
                    if (!existed) {
                        panel.player.setColorDirect(state.color)
                        panel.on = state.on
                    }
                }
            }
        }
        scope.launch(work) {
            messageApiService.mirrorBatches.collect { batch ->
                val now = nowMs()
                for (record in batch.records) applyRecord(record, now, batch.controllerMillis)
                for (panel in panels.values) panel.player.resync(batch.controllerMillis, now)
            }
        }
        // Driver loop: advance every player and publish the frame. Idle while live preview is
        // off — mirroring is opt-in, so the controller streams batches only while preview is on,
        // and replays a state snapshot the instant it's enabled, making the view current at once.
        scope.launch(work) {
            while (true) {
                if (livePreview.value) {
                    val now = nowMs()
                    for (panel in panels.values) panel.player.tick(now)
                    _states.value = panels.entries.map { (id, p) ->
                        PanelState(panelId = id, on = p.on, color = p.player.currentColor)
                    }
                }
                delay(FRAME_MS)
            }
        }
    }

    /**
     * Drops all per-panel animation players and their native cores.
     *
     * The native [PanelAnimationPlayer] retains dedup/slot state (`lastStartSeqId`,
     * `lastParamsSeqId`, occupied slots) for the life of the instance. A controller restart resets
     * its own `nextSeqId`/group state to its initial values, so a stale mobile-side player can
     * permanently dedupe-away the replayed PREPARE/START — the panel then never animates again
     * (only unicast SET_COLOR / wheel still works). Call this whenever the controller is about to
     * (re)send its mirror snapshot, so playback starts from fresh native state.
     */
    fun reset() {
        scope.launch(work) {
            for (panel in panels.values) panel.player.release()
            panels.clear()
        }
    }

    private fun getOrCreate(id: Int): Panel =
        panels.getOrPut(id) { Panel(PanelAnimationPlayer(), on = false) }

    /**
     * Address 0 = General Call (all panels); otherwise a single panel index.
     *
     * On reconnect, `panelsListService.load()` clears [panelIds] while the controller may already
     * be replaying its mirror snapshot (PREPARE arrives unicast and creates panels; general-call
     * START would otherwise resolve to an empty target list and be silently dropped). Fall back to
     * the panels already known to this service so general-call records still apply.
     */
    private fun targets(address: Int): List<Int> =
        if (address == GENERAL_CALL) panelIds.ifEmpty { panels.keys.toList() } else listOf(address)

    private fun applyRecord(record: MirrorRecord, now: Long, controllerMs: Long) {
        val p = record.payload
        when (record.type) {
            IicPacketType.SET_COLOR.value -> {
                if (p.size < IIC_META_SIZE + 3) return
                val color = ColorRgbModel(u8(p, IIC_META_SIZE), u8(p, IIC_META_SIZE + 1), u8(p, IIC_META_SIZE + 2))
                forEachTarget(record.address) { it.player.setColorDirect(color) }
            }
            IicPacketType.TURN_ON_OFF.value -> {
                if (p.size < IIC_META_SIZE + 1) return
                val on = u8(p, IIC_META_SIZE) != 0
                forEachTarget(record.address) { it.on = on }
            }
            IicPacketType.ANIMATION_PREPARE.value -> {
                if (p.size < IIC_META_SIZE + 20) return
                forEachTarget(record.address) { it.player.prepare(p) }
            }
            IicPacketType.ANIMATION_START.value -> {
                if (p.size < IIC_META_SIZE + 2) return
                val seqId = u8(p, IIC_META_SIZE)
                val groupId = u8(p, IIC_META_SIZE + 1)
                forEachTarget(record.address) { it.player.start(seqId, groupId, now, controllerMs) }
            }
            IicPacketType.ANIMATION_CONTROL.value -> {
                if (p.size < IIC_META_SIZE + 2) return
                val cmd = u8(p, IIC_META_SIZE)
                val groupId = u8(p, IIC_META_SIZE + 1)
                forEachTarget(record.address) { it.player.control(cmd, groupId, now) }
            }
            IicPacketType.ANIMATION_UPDATE_PARAMS.value -> {
                if (p.size < IIC_META_SIZE + 4) return
                val seqId = u8(p, IIC_META_SIZE)
                val groupId = u8(p, IIC_META_SIZE + 1)
                val paramType = u8(p, IIC_META_SIZE + 2)
                val value = u8(p, IIC_META_SIZE + 3)
                forEachTarget(record.address) { it.player.updateParams(seqId, groupId, paramType, value, now) }
            }
            IicPacketType.SET_PALETTE.value -> {
                if (p.size < IIC_META_SIZE + 1) return
                forEachTarget(record.address) { it.player.setPalette(p) }
            }
            IicPacketType.SET_BASE_COLORS.value -> {
                if (p.size < IIC_META_SIZE + 9) return
                forEachTarget(record.address) { it.player.setBaseColors(p) }
            }
            IicPacketType.SET_BACKGROUND.value -> {
                if (p.size < IIC_META_SIZE + 3) return
                val color = ColorRgbModel(u8(p, IIC_META_SIZE), u8(p, IIC_META_SIZE + 1), u8(p, IIC_META_SIZE + 2))
                forEachTarget(record.address) { it.player.setBackground(color) }
            }
            // SET_GLOBAL_BRIGHTNESS is applied at render time via the appearance brightness slider.
        }
    }

    private fun forEachTarget(address: Int, action: (Panel) -> Unit) {
        for (id in targets(address)) action(getOrCreate(id))
    }

    companion object {
        private const val GENERAL_CALL = 0
        private const val FRAME_MS = 33L  // ~30fps
        private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    }
}
