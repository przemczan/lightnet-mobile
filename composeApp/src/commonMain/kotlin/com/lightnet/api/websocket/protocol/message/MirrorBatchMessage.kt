package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteReader

/**
 * One mirrored outbound I²C packet.
 *
 * @param address I²C target — panel index, or 0 for General Call (all panels).
 * @param type    inner packet type (see IicPacketType).
 * @param payload raw I²C packet bytes (5-byte Protocol::PacketMeta + body).
 */
data class MirrorRecord(
    val address: Int,
    val type: Int,
    val payload: ByteArray,
)

/** A MIRROR_BATCH payload: a controller timestamp plus the packets sent since the last flush. */
data class MirrorBatch(
    val controllerMillis: Long,
    val records: List<MirrorRecord>,
)

// Inbound only. Payload: controllerMillis(u32) + count(u16) + count x record,
// each record: address(u8) + type(u8) + size(u8) + payload[size].
fun decodeMirrorBatch(payload: ByteArray): MirrorBatch {
    val reader = ByteReader(payload)
    val controllerMillis = reader.readU32Le()
    val count = reader.readU16Le()

    val records = ArrayList<MirrorRecord>(count)
    repeat(count) {
        // Defensive: stop if a record header or body would read past the buffer.
        if (reader.remaining < 3) return@repeat
        val address = reader.readU8()
        val type    = reader.readU8()
        val size    = reader.readU8()
        if (reader.remaining < size) return@repeat
        records.add(MirrorRecord(address, type, reader.readBytes(size)))
    }
    return MirrorBatch(controllerMillis, records)
}
