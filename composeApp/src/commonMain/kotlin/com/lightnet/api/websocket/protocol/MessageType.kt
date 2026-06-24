package com.lightnet.api.websocket.protocol

enum class MessageType(val value: Int) {
    TOGGLE(1),
    SET_COLOR(3),
    GET_EDGES_LIST(4),
    GET_PANELS_STATES(5),
    PANELS_STATES(6),
    EDGES_LIST(7),
    MIRROR_BATCH(9),
    SET_MIRROR(10),
    PING(11),
    PONG(12),
    APP_STATE(13);

    companion object {
        fun fromValue(value: Int): MessageType? = entries.find { it.value == value }
    }
}
