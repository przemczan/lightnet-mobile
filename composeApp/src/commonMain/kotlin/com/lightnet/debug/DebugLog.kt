package com.lightnet.debug

import com.lightnet.api.websocket.protocol.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeSource

sealed interface DebugLogEntry {
    val id: Long
    val offsetMs: Long

    data class WsSent(
        override val id: Long,
        override val offsetMs: Long,
        val type: MessageType,
        val bytes: Int,
        val payload: ByteArray?,
    ) : DebugLogEntry

    data class WsReceived(
        override val id: Long,
        override val offsetMs: Long,
        val type: MessageType,
        val bytes: Int,
        val durationMs: Long?,
        val payload: ByteArray?,
    ) : DebugLogEntry

    data class Http(
        override val id: Long,
        override val offsetMs: Long,
        val host: String,
        val method: String,
        val path: String,
        val statusCode: Int,
        val durationMs: Long,
        val body: String? = null,
    ) : DebugLogEntry

    data class WsConnect(
        override val id: Long,
        override val offsetMs: Long,
        val host: String,
        val port: Int,
        val status: ConnectStatus,
        val detail: String?,
    ) : DebugLogEntry
}

enum class ConnectStatus { ATTEMPT, CONNECTED, FAILED, DISCONNECTED }

object DebugLog {
    private const val MAX_ENTRIES = 300

    private val source = TimeSource.Monotonic
    private val start = source.markNow()

    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries

    private var nextId = 0L

    private val inFlight = mutableMapOf<MessageType, kotlin.time.TimeMark>()
    private val requestToResponse = mapOf(
        MessageType.GET_EDGES_LIST    to MessageType.EDGES_LIST,
        MessageType.GET_PANELS_STATES to MessageType.PANELS_STATES,
    )
    private val responseToRequest = requestToResponse.entries.associate { (k, v) -> v to k }

    fun logWsSent(type: MessageType, bytes: Int, payload: ByteArray? = null) {
        if (type in requestToResponse) inFlight[type] = source.markNow()
        append(DebugLogEntry.WsSent(nextId++, now(), type, bytes, payload))
    }

    fun logWsReceived(type: MessageType, bytes: Int, payload: ByteArray? = null) {
        val durationMs = responseToRequest[type]
            ?.let { inFlight.remove(it)?.elapsedNow()?.inWholeMilliseconds }
        append(DebugLogEntry.WsReceived(nextId++, now(), type, bytes, durationMs, payload))
    }

    fun logHttp(host: String, method: String, path: String, statusCode: Int, durationMs: Long, body: String? = null) {
        append(DebugLogEntry.Http(nextId++, now(), host, method, path, statusCode, durationMs, body))
    }

    fun logWsConnect(host: String, port: Int, status: ConnectStatus, detail: String? = null) {
        append(DebugLogEntry.WsConnect(nextId++, now(), host, port, status, detail))
    }

    fun clear() {
        _entries.value = emptyList()
        inFlight.clear()
    }

    private fun now() = start.elapsedNow().inWholeMilliseconds

    private fun append(entry: DebugLogEntry) {
        val current = _entries.value
        _entries.value = if (current.size >= MAX_ENTRIES) {
            current.drop(current.size - MAX_ENTRIES + 1) + entry
        } else {
            current + entry
        }
    }
}
