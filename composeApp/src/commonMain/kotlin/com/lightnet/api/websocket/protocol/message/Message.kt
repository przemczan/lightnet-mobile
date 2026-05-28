package com.lightnet.api.websocket.protocol.message

import com.lightnet.api.websocket.protocol.ByteReader
import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.Crc
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.ProtocolVersion
import kotlin.random.Random

abstract class Message(val type: MessageType) {

    protected abstract fun encodePayload(writer: ByteWriter)

    fun encode(): ByteArray {
        val payloadWriter = ByteWriter()
        encodePayload(payloadWriter)
        val payload = payloadWriter.toByteArray()

        val writer = ByteWriter()

        // Header: type(1) + protocolVersion(2) + nonce(4) = 7 bytes
        writer.writeU8(type.value)
        writer.writeU16Le(ProtocolVersion.CURRENT)
        writer.writeU32Le(Random.nextLong(0L, 0x100000000L))

        val headerCrcOffset = writer.pos
        writer.writeU16Le(0) // headerCrc placeholder
        val payloadCrcOffset = writer.pos
        writer.writeU16Le(0) // payloadCrc placeholder
        writer.writeU16Le(payload.size)

        writer.writeBytes(payload)

        val bytes = writer.toByteArray()

        // Patch header CRC over the 7-byte header
        val headerCrc = Crc.calculate(bytes, 0, HEADER_SIZE)
        bytes[headerCrcOffset] = (headerCrc and 0xFF).toByte()
        bytes[headerCrcOffset + 1] = ((headerCrc ushr 8) and 0xFF).toByte()

        // Patch payload CRC — always written; crc16 over 0 bytes == 0xFFFF (initial value),
        // which the firmware also computes for zero-length payloads.
        val payloadCrc = Crc.calculate(bytes, META_SIZE, payload.size)
        bytes[payloadCrcOffset] = (payloadCrc and 0xFF).toByte()
        bytes[payloadCrcOffset + 1] = ((payloadCrc ushr 8) and 0xFF).toByte()

        return bytes
    }

    companion object {
        const val HEADER_SIZE = 7   // type(1) + protocolVersion(2) + nonce(4)
        const val META_SIZE = 13    // HEADER_SIZE(7) + headerCrc(2) + payloadCrc(2) + payloadSize(2)
    }
}

class IncomingMessage(
    val type: MessageType,
    val protocolVersion: Int,
    val nonce: Long,
    val payload: ByteArray,
)
