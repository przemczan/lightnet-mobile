package com.lightnet.api.websocket

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Connector {
    val state: StateFlow<ConnectorState>
    val incoming: Flow<ByteArray>
    fun connect()
    fun disconnect()
    fun send(data: ByteArray)
    fun close()
}
