package com.lightnet.animation

/** JNI bridge to liblightnet_anim.so (jni_scene.cpp). Handle = native pointer carried as a Long. */
private object NativeSceneBridge {
    init {
        System.loadLibrary("lightnet_anim")
    }

    external fun create(): Long
    external fun destroy(h: Long)
    external fun setTopology(h: Long, indices: ByteArray, count: Int, links: ByteArray, linkCount: Int, edgeCounts: ByteArray, root: Int)
    external fun setPalette(h: Long, name: String, stops: ByteArray, count: Int)
    external fun clearPalettes(h: Long)
    external fun setTag(h: Long, name: String, panels: ByteArray, count: Int)
    external fun clearTags(h: Long)
    external fun loadAndPlay(h: Long, json: ByteArray, now: Int): ByteArray?
    external fun tick(h: Long, now: Int): ByteArray
    external fun stop(h: Long, now: Int): ByteArray
    external fun setSpeed(h: Long, speed: Float)
    external fun reresolvePalettes(h: Long, palette: String?, baseColors: ByteArray?)
    external fun isPlaying(h: Long): Boolean
    external fun lastError(h: Long): String
}

actual class NativeSceneCore actual constructor() {
    private var handle: Long = NativeSceneBridge.create()

    actual fun setTopology(indices: ByteArray, count: Int, links: ByteArray, linkCount: Int, edgeCounts: ByteArray, logicalRoot: Int) =
        NativeSceneBridge.setTopology(handle, indices, count, links, linkCount, edgeCounts, logicalRoot)

    actual fun setPalette(name: String, stops: ByteArray, count: Int) = NativeSceneBridge.setPalette(handle, name, stops, count)
    actual fun clearPalettes() = NativeSceneBridge.clearPalettes(handle)
    actual fun setTag(name: String, panels: ByteArray, count: Int) = NativeSceneBridge.setTag(handle, name, panels, count)
    actual fun clearTags() = NativeSceneBridge.clearTags(handle)

    actual fun loadAndPlay(json: ByteArray, now: Int): ByteArray? = NativeSceneBridge.loadAndPlay(handle, json, now)
    actual fun tick(now: Int): ByteArray = NativeSceneBridge.tick(handle, now)
    actual fun stop(now: Int): ByteArray = NativeSceneBridge.stop(handle, now)

    actual fun setSpeed(speed: Float) = NativeSceneBridge.setSpeed(handle, speed)

    actual fun reresolvePalettes(palette: String?, baseColors: List<String>?) {
        val bytes = baseColors?.let { colors ->
            ByteArray(9).also { out ->
                repeat(3) { slot ->
                    val rgb = colors.getOrNull(slot)?.removePrefix("#")?.toIntOrNull(16) ?: 0
                    out[slot * 3] = ((rgb shr 16) and 0xFF).toByte()
                    out[slot * 3 + 1] = ((rgb shr 8) and 0xFF).toByte()
                    out[slot * 3 + 2] = (rgb and 0xFF).toByte()
                }
            }
        }
        NativeSceneBridge.reresolvePalettes(handle, palette, bytes)
    }

    actual fun isPlaying(): Boolean = NativeSceneBridge.isPlaying(handle)
    actual fun lastError(): String = NativeSceneBridge.lastError(handle)

    actual fun close() {
        if (handle != 0L) {
            NativeSceneBridge.destroy(handle)
            handle = 0L
        }
    }
}
