package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteReader
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import com.lightnet.api.websocket.protocol.model.PanelStateModel

// Inbound only. Payload: length(u16) + PanelStateModel[length]
// Each PanelStateModel: panelId(u16) + on(u8) + r(u8) + g(u8) + b(u8) + brightness(u8) = 7 bytes
fun decodePanelsStates(payload: ByteArray): List<PanelStateModel> {
    val reader = ByteReader(payload)
    val count = reader.readU16Le()
    return List(count) {
        PanelStateModel(
            panelId    = reader.readU16Le(),
            on         = reader.readU8() != 0,
            color      = ColorRgbModel(reader.readU8(), reader.readU8(), reader.readU8()),
            brightness = reader.readU8(),
        )
    }
}
