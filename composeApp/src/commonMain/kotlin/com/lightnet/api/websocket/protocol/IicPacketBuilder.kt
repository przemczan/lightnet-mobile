package com.lightnet.api.websocket.protocol

import com.lightnet.api.http.model.PaletteStop

/** Builds I²C wire packets (PacketMeta + body) for the panel animation core. */
object IicPacketBuilder {
    private const val PROTOCOL_VERSION = 6

    fun buildSetPalette(stops: List<PaletteStop>): ByteArray {
        val count = stops.size.coerceIn(1, 16)
        val packet = ByteArray(IicPacketType.IIC_META_SIZE + 1 + 16 * 4)
        stampMeta(packet, IicPacketType.SET_PALETTE.value)
        packet[IicPacketType.IIC_META_SIZE] = count.toByte()
        stops.take(count).forEachIndexed { i, stop ->
            val rgb = stop.color.removePrefix("#").toInt(16)
            val off = IicPacketType.IIC_META_SIZE + 1 + i * 4
            packet[off] = stop.position.toByte()
            packet[off + 1] = ((rgb shr 16) and 0xFF).toByte()
            packet[off + 2] = ((rgb shr 8) and 0xFF).toByte()
            packet[off + 3] = (rgb and 0xFF).toByte()
        }
        return packet
    }

    fun buildSetBaseColors(hexColors: List<String>): ByteArray {
        val packet = ByteArray(IicPacketType.IIC_META_SIZE + 9)
        stampMeta(packet, IicPacketType.SET_BASE_COLORS.value)
        repeat(3) { slot ->
            val rgb = hexColors.getOrNull(slot)?.removePrefix("#")?.toIntOrNull(16) ?: 0
            val off = IicPacketType.IIC_META_SIZE + slot * 3
            packet[off] = ((rgb shr 16) and 0xFF).toByte()
            packet[off + 1] = ((rgb shr 8) and 0xFF).toByte()
            packet[off + 2] = (rgb and 0xFF).toByte()
        }
        return packet
    }

    /** 3-stop userColors gradient (primary @0, secondary @128, tertiary @255). */
    fun buildUserColorStops(baseColors: List<String>): List<PaletteStop> = listOf(
        PaletteStop(0, baseColors.getOrNull(0) ?: "#000000"),
        PaletteStop(128, baseColors.getOrNull(1) ?: "#000000"),
        PaletteStop(255, baseColors.getOrNull(2) ?: "#000000"),
    )

    private fun stampMeta(packet: ByteArray, type: Int) {
        packet[0] = type.toByte()
        packet[1] = (PROTOCOL_VERSION and 0xFF).toByte()
        packet[2] = ((PROTOCOL_VERSION shr 8) and 0xFF).toByte()
        val crc = Crc.calculate(packet, 0, 3)
        packet[3] = (crc and 0xFF).toByte()
        packet[4] = ((crc shr 8) and 0xFF).toByte()
    }
}
