package com.lightnet.device

import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelState
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
 * Rendering is delegated to a [PanelPacketRenderer] (one [com.lightnet.animation.PanelAnimationPlayer]
 * per panel) — the same render core the offline scene preview uses, so live and offline render
 * identically. This service only owns the live transport: feeding mirror batches + the polled-state
 * baseline into the renderer and driving the ~30fps publish loop.
 *
 * All renderer access is confined to a single-threaded dispatcher so the batch handler and the
 * tick loop never mutate a player concurrently.
 */
class PanelMirrorService(
    messageApiService: MessageApiService,
    panelsListService: PanelsListService,
    panelsStatesService: PanelsStatesService,
    private val livePreview: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<List<PanelState>>(emptyList())
    val states: StateFlow<List<PanelState>> = _states

    private val renderer = PanelPacketRenderer()

    private val scope = scope

    // Single-thread confinement for all renderer/state mutation.
    private val work = Dispatchers.Default.limitedParallelism(1)
    private val clock = TimeSource.Monotonic.markNow()
    private fun nowMs(): Long = clock.elapsedNow().inWholeMilliseconds

    init {
        scope.launch(work) {
            panelsListService.panels.collect { list ->
                renderer.panelIds = list?.map { it.id } ?: emptyList()
            }
        }
        // Baseline color/on from real polled state the first time each panel is seen, so panels
        // not yet driven by an animation show their actual color rather than black.
        scope.launch(work) {
            panelsStatesService.states.collect { polled ->
                for (state in polled) {
                    if (!renderer.has(state.panelId)) renderer.setBaseline(state.panelId, state.color, state.on)
                }
            }
        }
        scope.launch(work) {
            messageApiService.mirrorBatches.collect { batch ->
                if (!livePreview.value) return@collect
                val now = nowMs()
                renderer.applyBatch(batch, now)
                renderer.resyncAll(batch.controllerMillis, now)
            }
        }
        // Driver loop: advance every player and publish the frame. Idle while live preview is
        // off — mirroring is opt-in, so the controller streams batches only while preview is on,
        // and replays a state snapshot the instant it's enabled, making the view current at once.
        scope.launch(work) {
            while (true) {
                if (livePreview.value) {
                    renderer.tickAll(nowMs())
                    _states.value = renderer.snapshot()
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
        scope.launch(work) { renderer.release() }
    }

    companion object {
        private const val FRAME_MS = 33L  // ~30fps
    }
}
