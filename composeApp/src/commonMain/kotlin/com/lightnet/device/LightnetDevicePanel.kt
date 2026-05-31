package com.lightnet.device

import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelInfo
import com.lightnet.api.websocket.model.PanelLayout
import com.lightnet.api.websocket.model.PanelState
import com.lightnet.api.websocket.protocol.message.SetColorMessage
import com.lightnet.api.websocket.protocol.message.ToggleMessage
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class LightnetDevicePanel(
    private val messageApiService: MessageApiService,
    val info: PanelInfo,
    val layout: PanelLayout,
    panelsStates: kotlinx.coroutines.flow.Flow<List<PanelState>>,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        PanelState(panelId = info.id, on = false, color = ColorRgbModel(255, 255, 255))
    )
    val state: StateFlow<PanelState> = _state

    init {
        scope.launch {
            panelsStates
                .filter { it.isNotEmpty() }
                .collect { states ->
                    states.find { it.panelId == info.id }?.let { _state.value = it }
                }
        }
    }

    fun toggle(on: Boolean? = null) {
        val newOn = on ?: !_state.value.on
        _state.value = _state.value.copy(on = newOn)
        messageApiService.send(ToggleMessage(info.id, newOn))
    }

    fun setColor(color: ColorRgbModel) {
        _state.value = _state.value.copy(color = color)
        messageApiService.send(SetColorMessage(info.id, color))
    }
}
