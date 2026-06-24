package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.http.model.AppStateBody
import com.lightnet.api.websocket.protocol.ByteReader

const val APP_STATE_PAYLOAD_SIZE = 51

// Inbound only. Mirrors WebsocketApi::Rsp::AppState on the controller.
fun decodeAppState(payload: ByteArray): AppStateBody {
    require(payload.size >= APP_STATE_PAYLOAD_SIZE) {
        "APP_STATE payload too short: ${payload.size}, need $APP_STATE_PAYLOAD_SIZE"
    }
    val reader = ByteReader(payload)
    val isOn = reader.readU8() != 0
    val lastPlayedSceneIsStored = reader.readU8() != 0
    val playing = reader.readU8() != 0
    reader.readU8() // reserved
    val speed = reader.readFloatLe()
    val lastPlayedSceneId = reader.readFixedCString(11)
    val controllerFirmware = reader.readFixedCString(32)
    return AppStateBody(
        isOn = isOn,
        lastPlayedSceneId = lastPlayedSceneId,
        lastPlayedSceneIsStored = lastPlayedSceneIsStored,
        playing = playing,
        speed = speed,
        controllerFirmware = controllerFirmware,
    )
}
