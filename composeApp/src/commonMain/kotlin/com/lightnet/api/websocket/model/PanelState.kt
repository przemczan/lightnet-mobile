package com.lightnet.api.websocket.model

import com.lightnet.api.websocket.protocol.model.ColorRgbModel

data class PanelState(
    val panelId: Int,
    val on: Boolean,
    val color: ColorRgbModel,
    val brightness: Int,
)
