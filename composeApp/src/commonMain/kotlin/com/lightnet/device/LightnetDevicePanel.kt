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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LightnetDevicePanel(
    private val messageApiService: MessageApiService,
    val info: PanelInfo,
    val layout: PanelLayout,
    panelsStates: kotlinx.coroutines.flow.Flow<List<PanelState>>,
    private val paintMode: StateFlow<Boolean>,
    scope: CoroutineScope,
    initialState: PanelState? = null,
) {
    private val _state = MutableStateFlow(
        initialState ?: PanelState(panelId = info.id, on = false, color = ColorRgbModel(255, 255, 255))
    )
    val state: StateFlow<PanelState> = _state

    init {
        scope.launch {
            combine(panelsStates, paintMode) { states, painting -> states to painting }
                .collect { (states, painting) ->
                    if (painting || states.isEmpty()) return@collect
                    states.find { it.panelId == info.id }?.let { _state.value = it }
                }
        }
    }

    fun setLocalState(on: Boolean, color: ColorRgbModel = ColorRgbModel(0, 0, 0)) {
        _state.value = PanelState(panelId = info.id, on = on, color = color)
    }

    /** Clears the panel for paint mode — local state only until the user paints. */
    fun resetForPaint() {
        setLocalState(on = false)
        messageApiService.send(ToggleMessage(info.id, false))
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
