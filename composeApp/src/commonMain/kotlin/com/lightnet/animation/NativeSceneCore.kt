package com.lightnet.animation

/**
 * Thin Kotlin façade over the shared C++ SCENE engine (firmware lib/Lightnet/Core/Controller/Scene)
 * via its C ABI (controller_core_c.h). Owns one native scene-engine handle — a whole virtual controller running
 * SceneParser → ScenePlayer → AnimationScheduler → runners with no hardware.
 *
 * [loadAndPlay] / [tick] / [stop] return the packets the engine emitted as a **MIRROR_BATCH payload**
 * (the exact bytes the live preview already decodes via decodeMirrorBatch), so an offline preview
 * feeds them into the same per-panel [PanelAnimationPlayer] render path. `now` is a 32-bit ms counter.
 *
 * Lifecycle: construct, then [close]. Not thread-safe — drive one instance from one thread.
 *
 * actual impls: androidMain (JNI → liblightnet_anim.so), iosMain (cinterop → controller_core_c.h).
 */
expect class NativeSceneCore() {
    /** The panel tree to resolve selectors against. `links` is `linkCount`*4 bytes
     * {panelA, edgeA, panelB, edgeB}; `indices`/`edgeCounts` are `count` bytes each. */
    fun setTopology(indices: ByteArray, count: Int, links: ByteArray, linkCount: Int, edgeCounts: ByteArray, logicalRoot: Int)

    /** Register a named palette. `stops` is `count`*4 bytes {pos, r, g, b}. */
    fun setPalette(name: String, stops: ByteArray, count: Int)
    fun clearPalettes()

    /** Register a device tag → its 1-based panel indices. */
    fun setTag(name: String, panels: ByteArray, count: Int)
    fun clearTags()

    /** Parse scene JSON and start; returns the emitted packets (MIRROR_BATCH payload), or null if invalid. */
    fun loadAndPlay(json: ByteArray, now: Int): ByteArray?
    /** Advance one frame; returns any packets emitted this frame (MIRROR_BATCH payload). */
    fun tick(now: Int): ByteArray
    /** Stop playback; returns the STOP broadcast (MIRROR_BATCH payload). */
    fun stop(now: Int): ByteArray

    fun setSpeed(speed: Float)
    fun isPlaying(): Boolean
    fun lastError(): String

    fun close()
}
