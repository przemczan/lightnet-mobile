package com.lightnet.api.websocket.protocol

internal object Crc {
    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001
                      else crc ushr 1
            }
        }
        return crc
    }
}
