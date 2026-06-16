package com.lightnet.animation

import animcore.scene_clear_palettes
import animcore.scene_clear_tags
import animcore.scene_create
import animcore.scene_destroy
import animcore.scene_drain
import animcore.scene_is_playing
import animcore.scene_last_error
import animcore.scene_load_and_play
import animcore.scene_reresolve_palettes
import animcore.scene_set_palette
import animcore.scene_set_speed
import animcore.scene_set_tag
import animcore.scene_set_topology
import animcore.scene_stop
import animcore.scene_tick
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

// iOS actual for NativeSceneCore — cinterop over controller_core_c.h. The C++ object code is linked
// from the `controller_core` static library (build per arch with CMake, same as `panel_core`; see
// src/iosMain/README.md). load/tick/stop return the emitted packets via scene_drain().
@OptIn(ExperimentalForeignApi::class)
actual class NativeSceneCore actual constructor() {
    private var handle = scene_create()

    actual fun setTopology(indices: ByteArray, count: Int, links: ByteArray, linkCount: Int, edgeCounts: ByteArray, logicalRoot: Int) {
        if (indices.isEmpty()) return
        indices.usePinned { pi ->
            links.usePinned { pl ->
                edgeCounts.usePinned { pe ->
                    scene_set_topology(
                        handle,
                        pi.addressOf(0).reinterpret(), count.toUByte(),
                        if (links.isEmpty()) null else pl.addressOf(0).reinterpret(), linkCount.toUByte(),
                        pe.addressOf(0).reinterpret(), logicalRoot.toUByte(),
                    )
                }
            }
        }
    }

    actual fun setPalette(name: String, stops: ByteArray, count: Int) {
        if (stops.isEmpty()) return
        memScoped {
            stops.usePinned { ps ->
                scene_set_palette(handle, name.cstr.ptr, ps.addressOf(0).reinterpret(), count.toUByte())
            }
        }
    }

    actual fun clearPalettes() { scene_clear_palettes(handle) }

    actual fun setTag(name: String, panels: ByteArray, count: Int) {
        if (panels.isEmpty()) return
        memScoped {
            panels.usePinned { pp ->
                scene_set_tag(handle, name.cstr.ptr, pp.addressOf(0).reinterpret(), count.toUByte())
            }
        }
    }

    actual fun clearTags() { scene_clear_tags(handle) }

    actual fun loadAndPlay(json: ByteArray, now: Int): ByteArray? {
        if (json.isEmpty()) return null
        val ok = json.usePinned {
            scene_load_and_play(handle, it.addressOf(0).reinterpret<ByteVar>(), json.size, now.toUInt())
        }
        return if (ok == 0) null else drain()
    }

    actual fun tick(now: Int): ByteArray {
        scene_tick(handle, now.toUInt())
        return drain()
    }

    actual fun stop(now: Int): ByteArray {
        scene_stop(handle, now.toUInt())
        return drain()
    }

    actual fun setSpeed(speed: Float) { scene_set_speed(handle, speed) }

    actual fun reresolvePalettes(palette: String?, baseColors: List<String>?) {
        memScoped {
            val palPtr = palette?.cstr?.ptr
            if (baseColors == null) {
                scene_reresolve_palettes(handle, palPtr, null)
            } else {
                val bytes = ByteArray(9).also { out ->
                    repeat(3) { slot ->
                        val rgb = baseColors.getOrNull(slot)?.removePrefix("#")?.toIntOrNull(16) ?: 0
                        out[slot * 3] = ((rgb shr 16) and 0xFF).toByte()
                        out[slot * 3 + 1] = ((rgb shr 8) and 0xFF).toByte()
                        out[slot * 3 + 2] = (rgb and 0xFF).toByte()
                    }
                }
                bytes.usePinned {
                    scene_reresolve_palettes(handle, palPtr, it.addressOf(0).reinterpret())
                }
            }
        }
    }

    actual fun isPlaying(): Boolean = scene_is_playing(handle) != 0
    actual fun lastError(): String = scene_last_error(handle)?.toKString() ?: ""

    actual fun close() {
        scene_destroy(handle)
        handle = null
    }

    // Copy the pending MIRROR_BATCH payload out of the engine (size, then fill).
    private fun drain(): ByteArray {
        val len = scene_drain(handle, null, 0)
        if (len <= 0) return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { scene_drain(handle, it.addressOf(0).reinterpret<UByteVar>(), len) }
        return out
    }
}
