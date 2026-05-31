package com.lightnet.api.websocket.protocol.model

data class PanelStateModel(
    val panelId: Int,
    val on: Boolean,
    val color: ColorRgbModel,
)
