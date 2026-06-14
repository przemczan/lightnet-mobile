package com.lightnet.device

import com.lightnet.animation.PanelAnimationPlayer
import com.lightnet.api.websocket.model.PanelState
import com.lightnet.api.websocket.protocol.IicPacketType
import com.lightnet.api.websocket.protocol.IicPacketType.Companion.IIC_META_SIZE
import com.lightnet.api.websocket.protocol.message.MirrorBatch
import com.lightnet.api.websocket.protocol.message.MirrorRecord
import com.lightnet.api.websocket.protocol.model.ColorRgbModel

/**
 * Per-panel render core shared by the live mirror ([PanelMirrorService]) and the offline scene
 * preview ([OfflineSceneService]). Owns one [PanelAnimationPlayer] per panel and reconstructs each
 * panel's colour by applying mirrored I²C packets exactly as the firmware panels would — so both
 * sources render through the identical path. Pure rendering: no transport, no coroutines.
 *
 * NOT thread-safe; the owner confines all access to a single thread (the batch handler + tick loop).
 */
class PanelPacketRenderer {
    private class Panel(val player: PanelAnimationPlayer, var on: Boolean)

    private val panels = LinkedHashMap<Int, Panel>()

    /** Panel ids a General Call (address 0) targets. Owner keeps this in sync with the panel list. */
    var panelIds: List<Int> = emptyList()

    /** Apply every record in a batch. `now` is the render clock; `controllerMs` the batch timestamp. */
    fun applyBatch(batch: MirrorBatch, now: Long) {
        for (record in batch.records) applyRecord(record, now, batch.controllerMillis)
    }

    fun resyncAll(controllerMs: Long, now: Long) {
        for (panel in panels.values) panel.player.resync(controllerMs, now)
    }

    fun tickAll(now: Long) {
        for (panel in panels.values) panel.player.tick(now)
    }

    fun snapshot(): List<PanelState> =
        panels.entries.map { (id, p) -> PanelState(panelId = id, on = p.on, color = p.player.currentColor) }

    fun has(id: Int): Boolean = panels.containsKey(id)

    /** Seed a panel's colour/on from real (polled) state, the first time it's seen. */
    fun setBaseline(id: Int, color: ColorRgbModel, on: Boolean) {
        val panel = getOrCreate(id)
        panel.player.setColorDirect(color)
        panel.on = on
    }

    /** Drop all players and their native cores (e.g. before a fresh snapshot replay). */
    fun release() {
        for (panel in panels.values) panel.player.release()
        panels.clear()
    }

    private fun getOrCreate(id: Int): Panel =
        panels.getOrPut(id) { Panel(PanelAnimationPlayer(), on = false) }

    // Address 0 = General Call (all panels); else a single panel index. Falls back to known panels
    // when panelIds hasn't been populated yet (mirrors the live service's reconnect safeguard).
    private fun targets(address: Int): List<Int> =
        if (address == GENERAL_CALL) panelIds.ifEmpty { panels.keys.toList() } else listOf(address)

    private fun forEachTarget(address: Int, action: (Panel) -> Unit) {
        for (id in targets(address)) action(getOrCreate(id))
    }

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

    companion object {
        private const val GENERAL_CALL = 0
        private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    }
}
