package com.lightnet.api.http.model

import kotlinx.serialization.Serializable

@Serializable
data class FirmwareFlashResponse(
    val status: String,
    val panels: Int? = null,
    val error: String? = null,
)

@Serializable
data class FirmwareStatusResponse(
    val state: String,
    val panel: Int? = null,
    val total: Int? = null,
    val progress: Int? = null,
    val error: String? = null,
) {
    object State {
        const val IDLE = "idle"
        const val CONNECTING = "connecting"
        const val FLASHING = "flashing"
        const val VERIFYING = "verifying"
        const val DONE = "done"
        const val ERROR = "error"
    }
}
