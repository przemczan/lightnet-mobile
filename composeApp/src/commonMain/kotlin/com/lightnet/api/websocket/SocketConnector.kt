package com.lightnet.api.websocket

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// HttpClient must have WebSockets plugin installed. Engine is platform-specific:
//   androidMain: HttpClient(OkHttp) { install(WebSockets) }
//   iosMain:     HttpClient(Darwin) { install(WebSockets) }
//
// Hosts are tried in order each reconnect cycle:
//   1. overrideIP  — manually configured by the user
//   2. lastIP      — last known IP from discovery or prior override connection
//   3. hostName    — mDNS hostname from discovery (e.g. lightnet-3F2A.local); slowest, last resort
//
// When the override IP connects successfully, onConnectedWith is called so the
// caller can persist it as the new lastIP.
class SocketConnector(
    private val overrideIP: String?,
    private val hostName: String?,
    private val lastIP: String?,
    private val port: Int,
    private val client: HttpClient,
    private val onConnectedWith: ((host: String) -> Unit)? = null,
) : Connector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ConnectorState.IDLE)
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    private val sendQueue = Channel<ByteArray>(Channel.BUFFERED)
    private var connectionJob: Job? = null

    override val state: StateFlow<ConnectorState> = _state
    override val incoming: Flow<ByteArray> = _incoming

    private fun hostsToTry(): List<String> = buildList {
        overrideIP?.takeIf { it.isNotEmpty() }?.let { add(it) }
        lastIP?.takeIf { it.isNotEmpty() && it != overrideIP }?.let { add(it) }
        hostName?.takeIf { it.isNotEmpty() && it != overrideIP && it != lastIP }?.let { add(it) }
    }

    override fun connect() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            var delayMs = INITIAL_DELAY_MS
            while (true) {
                var connected = false
                for (host in hostsToTry()) {
                    _state.value = ConnectorState.CONNECTING
                    try {
                        client.webSocket("ws://$host:$port/ws") {
                            delayMs = INITIAL_DELAY_MS
                            _state.value = ConnectorState.CONNECTED
                            onConnectedWith?.invoke(host)
                            connected = true
                            val sender = launch {
                                for (data in sendQueue) outgoing.send(Frame.Binary(true, data))
                            }
                            for (frame in incoming) {
                                if (frame is Frame.Binary) _incoming.emit(frame.readBytes())
                            }
                            sender.cancel()
                        }
                        break  // host worked; restart outer loop on disconnect
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // this host failed; try the next one
                    }
                }
                if (connected) delayMs = INITIAL_DELAY_MS
                _state.value = ConnectorState.DISCONNECTED
                delay(delayMs)
                delayMs = minOf(delayMs * 2, MAX_DELAY_MS)
            }
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        _state.value = ConnectorState.DISCONNECTED
    }

    override fun send(data: ByteArray) {
        scope.launch { sendQueue.send(data) }
    }

    override fun close() {
        connectionJob?.cancel()
        connectionJob = null
        scope.cancel()
    }

    companion object {
        private const val INITIAL_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
    }
}
