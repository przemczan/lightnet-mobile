package com.lightnet.animation

/** JNI bridge to liblightnet_anim.so (jni_anim.cpp). Handle = native pointer carried as a Long. */
private object NativeAnimBridge {
    init {
        System.loadLibrary("lightnet_anim")
    }

    external fun create(): Long
    external fun destroy(h: Long)
    external fun prepare(h: Long, bytes: ByteArray)
    external fun setPalette(h: Long, bytes: ByteArray)
    external fun setBaseColors(h: Long, bytes: ByteArray)
    external fun start(h: Long, seq: Int, group: Int, now: Int)
    external fun control(h: Long, cmd: Int, group: Int, now: Int)
    external fun updateParams(h: Long, seq: Int, group: Int, paramType: Int, value: Int, transitionMs: Int, now: Int)
    external fun setBackground(h: Long, r: Int, g: Int, b: Int)
    external fun setColorDirect(h: Long, r: Int, g: Int, b: Int)
    external fun tick(h: Long, now: Int)
    external fun currentColor(h: Long): Int
    external fun takeDirty(h: Long): Boolean
    external fun isAnimating(h: Long): Boolean
}

actual class NativeAnimCore actual constructor() {
    private var handle: Long = NativeAnimBridge.create()

    actual fun prepare(packet: ByteArray) = NativeAnimBridge.prepare(handle, packet)
    actual fun start(seqId: Int, groupId: Int, now: Int) = NativeAnimBridge.start(handle, seqId, groupId, now)
    actual fun control(cmd: Int, groupId: Int, now: Int) = NativeAnimBridge.control(handle, cmd, groupId, now)
    actual fun updateParams(seqId: Int, groupId: Int, paramType: Int, value: Int, transitionMs: Int, now: Int) =
        NativeAnimBridge.updateParams(handle, seqId, groupId, paramType, value, transitionMs, now)

    actual fun setPalette(packet: ByteArray) = NativeAnimBridge.setPalette(handle, packet)
    actual fun setBaseColors(packet: ByteArray) = NativeAnimBridge.setBaseColors(handle, packet)
    actual fun setBackground(r: Int, g: Int, b: Int) = NativeAnimBridge.setBackground(handle, r, g, b)
    actual fun setColorDirect(r: Int, g: Int, b: Int) = NativeAnimBridge.setColorDirect(handle, r, g, b)

    actual fun tick(now: Int) = NativeAnimBridge.tick(handle, now)
    actual fun currentColor(): Int = NativeAnimBridge.currentColor(handle)
    actual fun takeDirty(): Boolean = NativeAnimBridge.takeDirty(handle)
    actual fun isAnimating(): Boolean = NativeAnimBridge.isAnimating(handle)

    actual fun close() {
        if (handle != 0L) {
            NativeAnimBridge.destroy(handle)
            handle = 0L
        }
    }
}
