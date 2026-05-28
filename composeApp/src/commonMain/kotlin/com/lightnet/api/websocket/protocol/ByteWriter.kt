package com.lightnet.api.websocket.protocol

class ByteWriter {
    private val bytes = mutableListOf<Byte>()
    val pos: Int get() = bytes.size

    fun writeU8(v: Int) { bytes.add(v.toByte()) }
    fun writeU16Le(v: Int) { writeU8(v and 0xFF); writeU8((v ushr 8) and 0xFF) }
    fun writeU32Le(v: Long) {
        writeU16Le((v and 0xFFFFL).toInt())
        writeU16Le(((v ushr 16) and 0xFFFFL).toInt())
    }
    fun writeBytes(b: ByteArray) { b.forEach { bytes.add(it) } }
    fun toByteArray(): ByteArray = bytes.toByteArray()
}
