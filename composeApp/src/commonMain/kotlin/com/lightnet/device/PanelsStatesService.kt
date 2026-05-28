package com.lightnet.device

import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.model.PanelState
import com.lightnet.api.websocket.protocol.message.GetPanelsStatesMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class PanelsStatesService(
    private val messageApiService: MessageApiService,
    panelsListService: PanelsListService,
    scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<List<PanelState>>(emptyList())
    val states: StateFlow<List<PanelState>> = _states

    init {
        scope.launch {
            panelsListService.panels
                .filter { it.isNotEmpty() }
                .collect { refresh() }
        }
        scope.launch {
            messageApiService.panelsStates.collect { models ->
                _states.value = models.map { PanelState(it.panelId, it.on, it.color, it.brightness) }
            }
        }
    }

    fun refresh() = messageApiService.send(GetPanelsStatesMessage())
}
