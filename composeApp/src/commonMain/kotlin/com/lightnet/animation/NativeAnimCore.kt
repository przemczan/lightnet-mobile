package com.lightnet.animation

/**
 * Thin Kotlin façade over the shared C++ animation core (firmware lib/Lightnet/Core/Anim) via its
 * C ABI. Owns one native AnimationPlayer handle (one previewed panel).
 *
 * This is **only the animation math** — deterministic in `now` (a 16-bit ms counter, low 16 bits).
 * Clock-domain translation / resync / mirror plumbing live above it (see PanelAnimationPlayer).
 *
 * Packet entry points take the raw wire bytes (PacketMeta header included) — the same bytes the
 * firmware parses. Colours are passed/returned as packed 0xRRGGBB ints to avoid per-call allocation.
 *
 * Lifecycle: construct, then [close] to release the native handle. Not thread-safe — drive one
 * instance from one thread (the preview tick loop).
 *
 * actual impls: androidMain (JNI → liblightnet_anim.so), iosMain (cinterop → anim_core_c.h).
 */
expect class NativeAnimCore() {
    /** PacketAnimationPrepare wire bytes (meta header included). */
    fun prepare(packet: ByteArray)
    fun start(seqId: Int, groupId: Int, now: Int)
    fun control(cmd: Int, groupId: Int, now: Int)
    fun updateParams(seqId: Int, groupId: Int, paramType: Int, value: Int, transitionMs: Int, now: Int)

    /** PacketSetPalette / PacketSetBaseColors wire bytes (meta header included). */
    fun setPalette(packet: ByteArray)
    fun setBaseColors(packet: ByteArray)
    fun setBackground(r: Int, g: Int, b: Int)
    fun setColorDirect(r: Int, g: Int, b: Int)

    fun tick(now: Int)

    /** Current composited colour as 0xRRGGBB. */
    fun currentColor(): Int
    /** True if the colour changed since the last call. */
    fun takeDirty(): Boolean
    fun isAnimating(): Boolean

    fun close()
}
