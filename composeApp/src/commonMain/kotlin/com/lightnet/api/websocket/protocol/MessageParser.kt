package com.lightnet.api.websocket.protocol

import com.lightnet.api.websocket.protocol.message.IncomingMessage
import com.lightnet.api.websocket.protocol.message.Message

object MessageParser {

    sealed class Result {
        data class Success(val message: IncomingMessage) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun parse(data: ByteArray): Result {
        if (data.size < Message.META_SIZE)
            return Result.Failure("Too short: ${data.size} bytes, need at least ${Message.META_SIZE}")

        val reader = ByteReader(data)
        val typeValue       = reader.readU8()
        val protocolVersion = reader.readU16Le()
        val nonce           = reader.readU32Le()
        val headerCrc       = reader.readU16Le()
        val payloadCrc      = reader.readU16Le()
        val payloadSize     = reader.readU16Le()

        val type = MessageType.fromValue(typeValue)
            ?: return Result.Failure("Unknown message type: $typeValue")

        if (data.size != Message.META_SIZE + payloadSize)
            return Result.Failure("Size mismatch: expected ${Message.META_SIZE + payloadSize}, got ${data.size}")

        val expectedHeaderCrc = Crc.calculate(data, 0, Message.HEADER_SIZE)
        if (expectedHeaderCrc != headerCrc)
            return Result.Failure("Header CRC mismatch: expected $expectedHeaderCrc, got $headerCrc")

        val payload = data.copyOfRange(Message.META_SIZE, data.size)

        val expectedPayloadCrc = Crc.calculate(payload)
        if (expectedPayloadCrc != payloadCrc)
            return Result.Failure("Payload CRC mismatch: expected $expectedPayloadCrc, got $payloadCrc")

        return Result.Success(IncomingMessage(type, protocolVersion, nonce, payload))
    }
}
