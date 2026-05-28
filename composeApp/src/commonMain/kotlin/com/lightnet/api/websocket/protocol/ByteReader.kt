package com.lightnet.api.websocket.protocol

class ByteReader(private val data: ByteArray, private var pos: Int = 0) {
    fun readU8(): Int = data[pos++].toInt() and 0xFF
    fun readU16Le(): Int = readU8() or (readU8() shl 8)
    fun readU32Le(): Long = readU16Le().toLong() or (readU16Le().toLong() shl 16)
    fun readBytes(n: Int): ByteArray = data.copyOfRange(pos, pos + n).also { pos += n }
    val remaining: Int get() = data.size - pos
}
