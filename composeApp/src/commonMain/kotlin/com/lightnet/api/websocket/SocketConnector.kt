package com.lightnet.api.websocket

import com.lightnet.debug.ConnectStatus
import com.lightnet.debug.DebugLog
import com.lightnet.network.resolveHostToIp
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// HttpClient must have WebSockets plugin installed and a short connect timeout set.
// Engine is platform-specific:
//   androidMain: HttpClient(OkHttp) { engine { config { connectTimeout(5, SECONDS) } }; install(WebSockets) }
//   iosMain:     HttpClient(Darwin) { install(WebSockets) }
//
// Hosts are tried in order each connection cycle:
//   1. overrideIP  — manually configured by the user
//   2. lastIP      — last known IP from discovery or prior connection
//   3. hostName    — mDNS hostname (e.g. lightnet-3F2A.local); slowest, last resort
//
// Each host is attempted attemptsPerHost times before moving to the next.
// If all hosts exhaust their attempts the state becomes FAILED and the loop stops —
// the caller must invoke connect() again (e.g. the user pressing Retry).
// After a successful connection that later drops, the cycle restarts automatically.
// onConnectedWith is called on every successful connection so the caller can persist
// the working host as lastIP.
class SocketConnector(
    private val overrideIP: String?,
    private val hostName: String?,
    private val lastIP: String?,
    private val port: Int,
    private val client: HttpClient,
    private val attemptsPerHost: Int = 2,
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
            cycle@ while (true) {
                val hosts = hostsToTry()
                if (hosts.isEmpty()) {
                    _state.value = ConnectorState.FAILED
                    return@launch
                }
                for (host in hosts) {
                    val resolvedIp = resolveHostToIp(host) ?: host
                    for (attempt in 1..attemptsPerHost) {
                        _state.value = ConnectorState.CONNECTING
                        DebugLog.logWsConnect(host, port, ConnectStatus.ATTEMPT)
                        try {
                            client.webSocket("ws://$resolvedIp:$port/ws") {
                                _state.value = ConnectorState.CONNECTED
                                DebugLog.logWsConnect(resolvedIp, port, ConnectStatus.CONNECTED)
                                onConnectedWith?.invoke(resolvedIp)
                                val sender = launch {
                                    for (data in sendQueue) outgoing.send(Frame.Binary(true, data))
                                }
                                for (frame in incoming) {
                                    if (frame is Frame.Binary) _incoming.emit(frame.readBytes())
                                }
                                sender.cancel()
                            }
                            // Clean drop — restart the cycle to reconnect
                            DebugLog.logWsConnect(resolvedIp, port, ConnectStatus.DISCONNECTED)
                            _state.value = ConnectorState.DISCONNECTED
                            continue@cycle
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DebugLog.logWsConnect(resolvedIp, port, ConnectStatus.FAILED, e.message ?: e::class.simpleName)
                        }
                    }
                }
                // All hosts and all attempts exhausted — give up until user retries
                _state.value = ConnectorState.FAILED
                return@launch
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

}
