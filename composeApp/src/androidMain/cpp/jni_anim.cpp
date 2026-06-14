// jni_anim.cpp — JNI bindings for the portable animation core (Android).
//
// Maps com.lightnet.animation.NativeAnimBridge (a JVM object of `external` functions) onto the C
// ABI (panel_core_c.h). The handle is the native pointer carried as a jlong. Packet entry points
// take the raw wire bytes (PacketMeta header included) — same bytes the firmware parses.

#include <jni.h>
#include "panel_core_c.h"

namespace {
inline anim_handle handle(jlong h) { return reinterpret_cast<anim_handle>(h); }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_lightnet_animation_NativeAnimBridge_create(JNIEnv *, jclass)
{
    return reinterpret_cast<jlong>(anim_create());
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_destroy(JNIEnv *, jclass, jlong h)
{
    anim_destroy(handle(h));
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_prepare(JNIEnv *env, jclass, jlong h, jbyteArray bytes)
{
    jsize len = env->GetArrayLength(bytes);
    jbyte *buf = env->GetByteArrayElements(bytes, nullptr);
    anim_prepare(handle(h), reinterpret_cast<const uint8_t *>(buf), (int)len);
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_setPalette(JNIEnv *env, jclass, jlong h, jbyteArray bytes)
{
    jsize len = env->GetArrayLength(bytes);
    jbyte *buf = env->GetByteArrayElements(bytes, nullptr);
    anim_set_palette(handle(h), reinterpret_cast<const uint8_t *>(buf), (int)len);
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_setBaseColors(JNIEnv *env, jclass, jlong h, jbyteArray bytes)
{
    jsize len = env->GetArrayLength(bytes);
    jbyte *buf = env->GetByteArrayElements(bytes, nullptr);
    anim_set_base_colors(handle(h), reinterpret_cast<const uint8_t *>(buf), (int)len);
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_start(JNIEnv *, jclass, jlong h, jint seq, jint group, jint now)
{
    anim_start(handle(h), (uint8_t)seq, (uint8_t)group, (uint16_t)now);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_control(JNIEnv *, jclass, jlong h, jint cmd, jint group, jint now)
{
    anim_control(handle(h), (uint8_t)cmd, (uint8_t)group, (uint16_t)now);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_updateParams(JNIEnv *, jclass, jlong h,
        jint seq, jint group, jint paramType, jint value, jint transitionMs, jint now)
{
    anim_update_params(handle(h), (uint8_t)seq, (uint8_t)group, (uint8_t)paramType,
                       (uint8_t)value, (uint8_t)transitionMs, (uint16_t)now);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_setBackground(JNIEnv *, jclass, jlong h, jint r, jint g, jint b)
{
    anim_set_background(handle(h), (uint8_t)r, (uint8_t)g, (uint8_t)b);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_setColorDirect(JNIEnv *, jclass, jlong h, jint r, jint g, jint b)
{
    anim_set_color_direct(handle(h), (uint8_t)r, (uint8_t)g, (uint8_t)b);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeAnimBridge_tick(JNIEnv *, jclass, jlong h, jint now)
{
    anim_tick(handle(h), (uint16_t)now);
}

JNIEXPORT jint JNICALL
Java_com_lightnet_animation_NativeAnimBridge_currentColor(JNIEnv *, jclass, jlong h)
{
    uint8_t r = 0, g = 0, b = 0;
    anim_get_color(handle(h), &r, &g, &b);
    return (jint)((r << 16) | (g << 8) | b);
}

JNIEXPORT jboolean JNICALL
Java_com_lightnet_animation_NativeAnimBridge_takeDirty(JNIEnv *, jclass, jlong h)
{
    return anim_take_dirty(handle(h)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_lightnet_animation_NativeAnimBridge_isAnimating(JNIEnv *, jclass, jlong h)
{
    return anim_is_animating(handle(h)) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
