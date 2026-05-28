package com.lightnet.api.websocket.model

data class PanelLayout(
    val panelId: Int,
    val edgesCoords: MutableMap<Int, EdgeCoords> = mutableMapOf(),
)
