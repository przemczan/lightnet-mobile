package com.lightnet.animation

import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlin.math.absoluteValue

/**
 * Previews one panel by driving the shared C++ animation core ([NativeAnimCore]) — the same code the
 * firmware panel runs, so the preview matches the hardware by construction (single source of truth).
 *
 * This class is a thin wrapper that owns only the mobile-specific concerns the firmware doesn't have:
 *
 *  - **Clock-domain translation.** The firmware uses its own `millis()`; the mobile tick loop uses a
 *    monotonic clock that differs from the controller's. We drive the core in **controller time** via
 *    a single offset `clockOffsetMs = mobileNow - controllerNow`, updated from each mirror batch.
 *    Animations start at `controllerMs` and advance at `mobileNow - offset`, so elapsed tracks the
 *    controller. This replaces the firmware-absent per-slot resync — drift is global (one mobile clock
 *    vs one controller clock), so one offset suffices.
 *  - **Colour exposure.** [currentColor] is the core's output as a [ColorRgbModel] for the visualizer.
 *
 * Packet bytes flow straight through to the core, which decodes them with the firmware struct layout
 * (no Kotlin re-implementation). Wire `now` values are the low 16 bits of the controller-domain ms,
 * matching the panel's `uint16 millis()` arithmetic.
 *
 * Not thread-safe: [PanelMirrorService] confines all access to a single dispatcher.
 */
class PanelAnimationPlayer {
    private val core = NativeAnimCore()

    var currentColor: ColorRgbModel = ColorRgbModel(0, 0, 0)
        private set

    private var clockOffsetMs = 0L      // mobileNow - controllerNow
    private var haveOffset = false

    private fun coreNow(mobileNow: Long): Int = (mobileNow - clockOffsetMs).toInt() and 0xFFFF

    private fun syncColor() {
        if (core.takeDirty()) {
            val c = core.currentColor()
            currentColor = ColorRgbModel((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
    }

    val isAnimating: Boolean get() = core.isAnimating()

    /** Direct LED colour (PACKET_SET_COLOR). Event-driven — visible immediately, like the firmware. */
    fun setColorDirect(color: ColorRgbModel) {
        core.setColorDirect(color.r, color.g, color.b)
        currentColor = color
        core.takeDirty()  // consume so the next tick's dirty reflects only subsequent changes
    }

    /** Raw PacketSetPalette / PacketSetBaseColors wire bytes (PacketMeta header included). */
    fun setPalette(packet: ByteArray) = core.setPalette(packet)
    fun setBaseColors(packet: ByteArray) = core.setBaseColors(packet)

    fun setBackground(color: ColorRgbModel) {
        core.setBackground(color.r, color.g, color.b)
        syncColor()
    }

    /** Raw PacketAnimationPrepare wire bytes (PacketMeta header included). */
    fun prepare(packet: ByteArray) = core.prepare(packet)

    fun start(seqId: Int, groupId: Int, mobileNow: Long, controllerMs: Long) {
        // Each START's (mobileNow, controllerMs) pair is an authoritative clock-sync sample —
        // re-anchor on every call, not just the first. The slot below is anchored at
        // `controllerMs` via core.start(); if clockOffsetMs were left stale from an earlier,
        // less accurate sample, tick()'s coreNow could fall *before* this slot's startMs,
        // underflowing `animElapsed` to ~65535 and snapping the animation straight to its
        // held end-state (e.g. black for ANIM_PULSE) instead of playing from the start.
        clockOffsetMs = mobileNow - controllerMs
        haveOffset = true
        // Anchor the slot at the controller's start time; tick() advances it in controller time.
        core.start(seqId, groupId, controllerMs.toInt() and 0xFFFF)
    }

    fun control(cmd: Int, groupId: Int, mobileNow: Long) = core.control(cmd, groupId, coreNow(mobileNow))

    fun updateParams(seqId: Int, groupId: Int, paramType: Int, value: Int, mobileNow: Long) =
        core.updateParams(seqId, groupId, paramType, value, 0, coreNow(mobileNow))

    /** Re-estimate the mobile↔controller clock offset from a mirror batch, clamping large jumps. */
    fun resync(controllerNow: Long, mobileNow: Long) {
        val newOffset = mobileNow - controllerNow
        if (!haveOffset) {
            clockOffsetMs = newOffset
            haveOffset = true
            return
        }
        if ((newOffset - clockOffsetMs).absoluteValue <= RESYNC_THRESHOLD_MS) {
            clockOffsetMs = newOffset
        }
    }

    fun tick(mobileNow: Long) {
        core.tick(coreNow(mobileNow))
        syncColor()
    }

    /** Releases the native handle. Call when the panel is discarded. */
    fun release() = core.close()

    companion object {
        private const val RESYNC_THRESHOLD_MS = 100L
    }
}
