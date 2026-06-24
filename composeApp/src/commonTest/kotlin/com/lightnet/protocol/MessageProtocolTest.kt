package com.lightnet.protocol

import com.lightnet.api.websocket.protocol.ByteWriter
import com.lightnet.api.websocket.protocol.Crc
import com.lightnet.api.websocket.protocol.MessageParser
import com.lightnet.api.websocket.protocol.MessageType
import com.lightnet.api.websocket.protocol.ProtocolVersion
import com.lightnet.api.websocket.protocol.message.GetEdgesListMessage
import com.lightnet.api.websocket.protocol.message.GetPanelsStatesMessage
import com.lightnet.api.websocket.protocol.message.SetColorMessage
import com.lightnet.api.websocket.protocol.message.ToggleMessage
import com.lightnet.api.websocket.protocol.message.decodeAppState
import com.lightnet.api.websocket.protocol.message.decodeEdgesList
import com.lightnet.api.websocket.protocol.message.decodePanelsStates
import com.lightnet.api.websocket.protocol.model.ColorRgbModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageProtocolTest {

    // --- CRC ---

    @Test
    fun crcEmptyIsInitialValue() {
        assertEquals(0xFFFF, Crc.calculate(ByteArray(0)))
    }

    @Test
    fun crcIsStable() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertEquals(Crc.calculate(data), Crc.calculate(data))
    }

    @Test
    fun crcDifferentDataDifferentResult() {
        val a = Crc.calculate(byteArrayOf(0x01, 0x02, 0x03))
        val b = Crc.calculate(byteArrayOf(0x01, 0x02, 0x04))
        assertTrue(a != b)
    }

    // --- Encoding sizes ---

    @Test
    fun crcEmptyEqualsInitialValue() {
        // Firmware's crc16(ptr, 0) returns 0xFFFF; empty-payload messages must match.
        assertEquals(0xFFFF, Crc.calculate(ByteArray(0)))
    }

    @Test
    fun getEdgesListPayloadCrcIsInitialValue() {
        val bytes = GetEdgesListMessage().encode()
        // payloadCrc is at bytes[9..10] (little-endian)
        val payloadCrc = (bytes[9].toInt() and 0xFF) or ((bytes[10].toInt() and 0xFF) shl 8)
        assertEquals(0xFFFF, payloadCrc)
    }

    @Test
    fun getPanelsStatesMessageIs13Bytes() {
        assertEquals(13, GetPanelsStatesMessage().encode().size)
    }

    @Test
    fun getEdgesListMessageIs13Bytes() {
        assertEquals(13, GetEdgesListMessage().encode().size)
    }

    @Test
    fun toggleMessageIs15Bytes() {
        assertEquals(15, ToggleMessage(panelId = 1, on = true).encode().size)
    }

    @Test
    fun setColorMessageIs17Bytes() {
        assertEquals(17, SetColorMessage(panelId = 1, color = ColorRgbModel(255, 128, 0)).encode().size)
    }

    // --- Encoding field values ---

    @Test
    fun toggleMessageEncodesFields() {
        val bytes = ToggleMessage(panelId = 7, on = true).encode()
        assertEquals(MessageType.TOGGLE.value, bytes[0].toInt() and 0xFF)
        assertEquals(ProtocolVersion.CURRENT, (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8))
        assertEquals(7, bytes[13].toInt() and 0xFF) // panelId at payload offset 0
        assertEquals(1, bytes[14].toInt() and 0xFF) // on=true at payload offset 1
    }

    @Test
    fun setColorMessageEncodesFields() {
        val bytes = SetColorMessage(panelId = 3, color = ColorRgbModel(10, 20, 30)).encode()
        assertEquals(MessageType.SET_COLOR.value, bytes[0].toInt() and 0xFF)
        assertEquals(3,  bytes[13].toInt() and 0xFF) // panelId
        assertEquals(10, bytes[14].toInt() and 0xFF) // r
        assertEquals(20, bytes[15].toInt() and 0xFF) // g
        assertEquals(30, bytes[16].toInt() and 0xFF) // b
    }

    // --- Round-trip through parser ---

    @Test
    fun toggleRoundTrip() {
        val bytes = ToggleMessage(panelId = 5, on = false).encode()
        val result = MessageParser.parse(bytes)
        assertIs<MessageParser.Result.Success>(result)
        assertEquals(MessageType.TOGGLE, result.message.type)
        assertEquals(ProtocolVersion.CURRENT, result.message.protocolVersion)
        assertEquals(2, result.message.payload.size)
        assertEquals(5, result.message.payload[0].toInt() and 0xFF)
        assertEquals(0, result.message.payload[1].toInt() and 0xFF)
    }

    @Test
    fun getPanelsStatesRoundTrip() {
        val bytes = GetPanelsStatesMessage().encode()
        val result = MessageParser.parse(bytes)
        assertIs<MessageParser.Result.Success>(result)
        assertEquals(MessageType.GET_PANELS_STATES, result.message.type)
        assertTrue(result.message.payload.isEmpty())
    }

    @Test
    fun setColorRoundTrip() {
        val bytes = SetColorMessage(panelId = 2, color = ColorRgbModel(100, 150, 200)).encode()
        val result = MessageParser.parse(bytes)
        assertIs<MessageParser.Result.Success>(result)
        assertEquals(MessageType.SET_COLOR, result.message.type)
        assertEquals(4, result.message.payload.size)
    }

    // --- Parser rejects corrupted data ---

    @Test
    fun parserRejectsTooShortBuffer() {
        val result = MessageParser.parse(ByteArray(5))
        assertIs<MessageParser.Result.Failure>(result)
    }

    @Test
    fun parserRejectsBadHeaderCrc() {
        val bytes = ToggleMessage(panelId = 1, on = true).encode().copyOf()
        bytes[7] = (bytes[7] + 1).toByte() // corrupt headerCrc
        val result = MessageParser.parse(bytes)
        assertIs<MessageParser.Result.Failure>(result)
    }

    @Test
    fun parserRejectsBadPayloadCrc() {
        val bytes = ToggleMessage(panelId = 1, on = true).encode().copyOf()
        bytes[13] = (bytes[13] + 1).toByte() // corrupt payload byte
        val result = MessageParser.parse(bytes)
        assertIs<MessageParser.Result.Failure>(result)
    }

    // --- Inbound decoders ---

    @Test
    fun decodePanelsStatesEmpty() {
        val payload = byteArrayOf(0x00, 0x00) // length = 0
        val states = decodePanelsStates(payload)
        assertTrue(states.isEmpty())
    }

    @Test
    fun decodePanelsStatesSinglePanel() {
        val payload = buildPayload {
            writeU16Le(1)          // count = 1
            writeU16Le(3)          // panelId = 3
            writeU8(1)             // on = true
            writeU8(255)           // r
            writeU8(128)           // g
            writeU8(0)             // b
        }
        val states = decodePanelsStates(payload)
        assertEquals(1, states.size)
        assertEquals(3, states[0].panelId)
        assertEquals(true, states[0].on)
        assertEquals(255, states[0].color.r)
        assertEquals(128, states[0].color.g)
        assertEquals(0, states[0].color.b)
    }

    @Test
    fun decodeEdgesListSingleEdge() {
        val payload = buildPayload {
            writeU16Le(1)  // count = 1
            writeU16Le(2)  // panelId
            writeU16Le(3)  // edgeIndex
            writeU16Le(4)  // connectedPanelId
            writeU16Le(5)  // connectedEdgeIndex
        }
        val edges = decodeEdgesList(payload)
        assertEquals(1, edges.size)
        assertEquals(2, edges[0].panelId)
        assertEquals(3, edges[0].edgeIndex)
        assertEquals(4, edges[0].connectedPanelId)
        assertEquals(5, edges[0].connectedEdgeIndex)
    }

    @Test
    fun decodeAppStatePayload() {
        val payload = buildPayload {
            writeU8(1)
            writeU8(0)
            writeU8(1)
            writeU8(0)
            writeU32Le(2.5f.toRawBits().toLong() and 0xFFFF_FFFFL)
            writeBytes(fixedCString("abcd1234", 11))
            writeBytes(fixedCString("1.2.3", 32))
        }
        val state = decodeAppState(payload)
        assertEquals(true, state.isOn)
        assertEquals(false, state.lastPlayedSceneIsStored)
        assertEquals(true, state.playing)
        assertEquals(2.5f, state.speed)
        assertEquals("abcd1234", state.lastPlayedSceneId)
        assertEquals("1.2.3", state.controllerFirmware)
    }

    // Helper for building raw payloads in tests
    private fun buildPayload(block: ByteWriter.() -> Unit): ByteArray = ByteWriter().apply(block).toByteArray()

    private fun fixedCString(value: String, length: Int): ByteArray =
        ByteArray(length) { index -> value.getOrNull(index)?.code?.toByte() ?: 0 }
}
