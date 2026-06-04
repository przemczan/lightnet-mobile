package com.lightnet.api.websocket.protocol

/**
 * Inner I²C packet types carried by a MIRROR_BATCH record (firmware Protocol::packetType_t).
 *
 * Distinct from [MessageType], which is the outer WebSocket frame type. A mirror record's
 * `type` byte is one of these values; its payload is the raw I²C packet (a 5-byte
 * Protocol::PacketMeta followed by the packet body — see [IIC_META_SIZE]).
 */
enum class IicPacketType(val value: Int) {
    TURN_ON_OFF(4),
    SET_COLOR(5),
    ANIMATION_PREPARE(12),
    ANIMATION_START(13),
    ANIMATION_CONTROL(14),
    ANIMATION_UPDATE_PARAMS(16),
    SET_PALETTE(17),
    SET_BASE_COLORS(18),
    SET_GLOBAL_BRIGHTNESS(19);

    companion object {
        fun fromValue(value: Int): IicPacketType? = entries.find { it.value == value }

        /** Size of the Protocol::PacketMeta prefix on every I²C packet body (type + version + headerCrc). */
        const val IIC_META_SIZE = 5
    }
}
