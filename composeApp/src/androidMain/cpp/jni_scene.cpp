// jni_scene.cpp — JNI bindings for the portable SCENE engine (Android).
//
// Maps com.lightnet.animation.NativeSceneBridge onto the scene C ABI (controller_core_c.h). The handle is
// the native pointer carried as a jlong. The engine runs the whole controller scene orchestrator with
// no hardware; load/tick/stop emit wire packets that scene_drain() returns as a MIRROR_BATCH payload —
// the same bytes the live preview already decodes, so offline preview reuses the per-panel render path.

#include <jni.h>
#include "controller_core_c.h"

namespace {
inline scene_handle handle(jlong h) { return reinterpret_cast<scene_handle>(h); }

// Drain the pending packets into a fresh jbyteArray (MIRROR_BATCH payload).
jbyteArray drainBatch(JNIEnv *env, jlong h)
{
    int len = scene_drain(handle(h), nullptr, 0);
    jbyteArray arr = env->NewByteArray(len);
    jbyte *buf = env->GetByteArrayElements(arr, nullptr);
    scene_drain(handle(h), reinterpret_cast<uint8_t *>(buf), len);
    env->ReleaseByteArrayElements(arr, buf, 0); // 0 = commit back to the Java array
    return arr;
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_lightnet_animation_NativeSceneBridge_create(JNIEnv *, jclass)
{
    return reinterpret_cast<jlong>(scene_create());
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_destroy(JNIEnv *, jclass, jlong h)
{
    scene_destroy(handle(h));
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_setTopology(JNIEnv *env, jclass, jlong h,
        jbyteArray indices, jint count, jbyteArray links, jint linkCount, jbyteArray edgeCounts, jint root)
{
    jbyte *idx = env->GetByteArrayElements(indices, nullptr);
    jbyte *lk  = env->GetByteArrayElements(links, nullptr);
    jbyte *ec  = env->GetByteArrayElements(edgeCounts, nullptr);
    scene_set_topology(handle(h), reinterpret_cast<const uint8_t *>(idx), (uint8_t)count,
                       reinterpret_cast<const uint8_t *>(lk), (uint8_t)linkCount,
                       reinterpret_cast<const uint8_t *>(ec), (uint8_t)root);
    env->ReleaseByteArrayElements(indices, idx, JNI_ABORT);
    env->ReleaseByteArrayElements(links, lk, JNI_ABORT);
    env->ReleaseByteArrayElements(edgeCounts, ec, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_setPalette(JNIEnv *env, jclass, jlong h,
        jstring name, jbyteArray stops, jint count)
{
    const char *n = env->GetStringUTFChars(name, nullptr);
    jbyte *s = env->GetByteArrayElements(stops, nullptr);
    scene_set_palette(handle(h), n, reinterpret_cast<const uint8_t *>(s), (uint8_t)count);
    env->ReleaseByteArrayElements(stops, s, JNI_ABORT);
    env->ReleaseStringUTFChars(name, n);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_clearPalettes(JNIEnv *, jclass, jlong h)
{
    scene_clear_palettes(handle(h));
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_setTag(JNIEnv *env, jclass, jlong h,
        jstring name, jbyteArray panels, jint count)
{
    const char *n = env->GetStringUTFChars(name, nullptr);
    jbyte *p = env->GetByteArrayElements(panels, nullptr);
    scene_set_tag(handle(h), n, reinterpret_cast<const uint8_t *>(p), (uint8_t)count);
    env->ReleaseByteArrayElements(panels, p, JNI_ABORT);
    env->ReleaseStringUTFChars(name, n);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_clearTags(JNIEnv *, jclass, jlong h)
{
    scene_clear_tags(handle(h));
}

JNIEXPORT jbyteArray JNICALL
Java_com_lightnet_animation_NativeSceneBridge_loadAndPlay(JNIEnv *env, jclass, jlong h, jbyteArray json, jint now)
{
    jsize len = env->GetArrayLength(json);
    jbyte *j = env->GetByteArrayElements(json, nullptr);
    int ok = scene_load_and_play(handle(h), reinterpret_cast<const char *>(j), (int)len, (uint32_t)now);
    env->ReleaseByteArrayElements(json, j, JNI_ABORT);
    return ok ? drainBatch(env, h) : nullptr;
}

JNIEXPORT jbyteArray JNICALL
Java_com_lightnet_animation_NativeSceneBridge_tick(JNIEnv *env, jclass, jlong h, jint now)
{
    scene_tick(handle(h), (uint32_t)now);
    return drainBatch(env, h);
}

JNIEXPORT jbyteArray JNICALL
Java_com_lightnet_animation_NativeSceneBridge_stop(JNIEnv *env, jclass, jlong h, jint now)
{
    scene_stop(handle(h), (uint32_t)now);
    return drainBatch(env, h);
}

JNIEXPORT void JNICALL
Java_com_lightnet_animation_NativeSceneBridge_setSpeed(JNIEnv *, jclass, jlong h, jfloat speed)
{
    scene_set_speed(handle(h), (float)speed);
}

JNIEXPORT jboolean JNICALL
Java_com_lightnet_animation_NativeSceneBridge_isPlaying(JNIEnv *, jclass, jlong h)
{
    return scene_is_playing(handle(h)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_lightnet_animation_NativeSceneBridge_lastError(JNIEnv *env, jclass, jlong h)
{
    return env->NewStringUTF(scene_last_error(handle(h)));
}

}  // extern "C"
