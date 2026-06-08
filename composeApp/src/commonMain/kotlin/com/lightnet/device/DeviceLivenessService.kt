package com.lightnet.device

import com.lightnet.api.websocket.MessageApiService
import com.lightnet.api.websocket.protocol.message.PingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Confirms a connected device is actually responsive by sending a PING and awaiting PONG —
 * a WebSocket can stay in CONNECTED state even after the controller has gone silent (e.g. a
 * dropped network without a clean close), so [LightnetDevice.connectionState] alone isn't
 * a reliable "online" signal.
 */
class DeviceLivenessService(
    private val messageApiService: MessageApiService,
    connectionState: StateFlow<ConnectionState>,
    scope: CoroutineScope,
    private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val pongTimeoutMs: Long = DEFAULT_PONG_TIMEOUT_MS,
) {
    private val _isOnline = MutableStateFlow<Boolean?>(null)

    /** null = not yet checked, true = last ping was answered, false = last ping timed out or device is disconnected. */
    val isOnline: StateFlow<Boolean?> = _isOnline

    init {
        scope.launch {
            connectionState.collect { state ->
                if (state != ConnectionState.CONNECTED) _isOnline.value = false
            }
        }
        scope.launch {
            while (true) {
                connectionState.first { it == ConnectionState.CONNECTED }
                _isOnline.value = ping()
                delay(checkIntervalMs)
            }
        }
    }

    private suspend fun ping(): Boolean = withTimeoutOrNull(pongTimeoutMs) {
        // Subscribe BEFORE sending to guarantee we don't miss the response (see PanelsListService.load).
        val pongDeferred = async(start = CoroutineStart.UNDISPATCHED) { messageApiService.pong.first() }
        messageApiService.send(PingMessage())
        pongDeferred.await()
        true
    } ?: false

    companion object {
        const val DEFAULT_CHECK_INTERVAL_MS = 10_000L
        const val DEFAULT_PONG_TIMEOUT_MS = 5_000L
    }
}
