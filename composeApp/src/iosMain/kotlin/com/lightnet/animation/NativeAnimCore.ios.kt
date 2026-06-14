package com.lightnet.animation

import animcore.anim_control
import animcore.anim_create
import animcore.anim_destroy
import animcore.anim_get_color
import animcore.anim_is_animating
import animcore.anim_prepare
import animcore.anim_set_background
import animcore.anim_set_base_colors
import animcore.anim_set_color_direct
import animcore.anim_set_palette
import animcore.anim_start
import animcore.anim_take_dirty
import animcore.anim_tick
import animcore.anim_update_params
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

// iOS actual for NativeAnimCore — cinterop over panel_core_c.h. The C++ object code is linked from
// the `panel_core` static library (build per arch with CMake; see src/iosMain/README.md).
@OptIn(ExperimentalForeignApi::class)
actual class NativeAnimCore actual constructor() {
    private var handle = anim_create()

    private inline fun withBytes(b: ByteArray, block: (CPointer<UByteVar>, Int) -> Unit) {
        if (b.isEmpty()) return
        b.usePinned { pinned -> block(pinned.addressOf(0).reinterpret(), b.size) }
    }

    actual fun prepare(packet: ByteArray) = withBytes(packet) { p, n -> anim_prepare(handle, p, n) }
    actual fun setPalette(packet: ByteArray) = withBytes(packet) { p, n -> anim_set_palette(handle, p, n) }
    actual fun setBaseColors(packet: ByteArray) = withBytes(packet) { p, n -> anim_set_base_colors(handle, p, n) }

    actual fun start(seqId: Int, groupId: Int, now: Int) =
        anim_start(handle, seqId.toUByte(), groupId.toUByte(), now.toUShort())

    actual fun control(cmd: Int, groupId: Int, now: Int) =
        anim_control(handle, cmd.toUByte(), groupId.toUByte(), now.toUShort())

    actual fun updateParams(seqId: Int, groupId: Int, paramType: Int, value: Int, transitionMs: Int, now: Int) =
        anim_update_params(handle, seqId.toUByte(), groupId.toUByte(), paramType.toUByte(),
            value.toUByte(), transitionMs.toUByte(), now.toUShort())

    actual fun setBackground(r: Int, g: Int, b: Int) =
        anim_set_background(handle, r.toUByte(), g.toUByte(), b.toUByte())

    actual fun setColorDirect(r: Int, g: Int, b: Int) =
        anim_set_color_direct(handle, r.toUByte(), g.toUByte(), b.toUByte())

    actual fun tick(now: Int) = anim_tick(handle, now.toUShort())

    actual fun currentColor(): Int = memScoped {
        val r = alloc<UByteVar>()
        val g = alloc<UByteVar>()
        val b = alloc<UByteVar>()
        anim_get_color(handle, r.ptr, g.ptr, b.ptr)
        (r.value.toInt() shl 16) or (g.value.toInt() shl 8) or b.value.toInt()
    }

    actual fun takeDirty(): Boolean = anim_take_dirty(handle) != 0
    actual fun isAnimating(): Boolean = anim_is_animating(handle) != 0

    actual fun close() {
        anim_destroy(handle)
        handle = null
    }
}
