package com.lightnet.api.websocket.protocol

class ByteReader(private val data: ByteArray, private var pos: Int = 0) {
    fun readU8(): Int = data[pos++].toInt() and 0xFF
    fun readU16Le(): Int = readU8() or (readU8() shl 8)
    fun readU32Le(): Long = readU16Le().toLong() or (readU16Le().toLong() shl 16)
    fun readBytes(n: Int): ByteArray = data.copyOfRange(pos, pos + n).also { pos += n }
    fun readFloatLe(): Float = Float.fromBits(readU32Le().toInt())
    fun readFixedCString(length: Int): String {
        val bytes = readBytes(length)
        val end = bytes.indexOf(0).let { if (it < 0) length else it }
        return bytes.decodeToString(0, end)
    }
    val remaining: Int get() = data.size - pos
}
