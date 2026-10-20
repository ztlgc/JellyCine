#include <jni.h>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>

extern "C" {
#include "client.h"
}

// libmpv 1.0.4's pinned MPVInstance begins with mpv_handle *mpv.
// build.py refuses any other AAR checksum; do not use this with another wrapper.
static mpv_handle *handle(JNIEnv *env, jobject owner) {
    if (!owner || (mpv_client_api_version() >> 16) != 2) return nullptr;
    jclass cls = env->GetObjectClass(owner);
    jfieldID field = env->GetFieldID(cls, "nativeInstance", "J");
    env->DeleteLocalRef(cls);
    if (!field) {
        env->ExceptionClear();
        return nullptr;
    }
    const auto pointer = static_cast<uintptr_t>(env->GetLongField(owner, field));
    if (pointer < 4096 || pointer % alignof(void *)) return nullptr;
    auto *value = *reinterpret_cast<mpv_handle **>(pointer);
    return value && mpv_client_name(value) ? value : nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_jellycine_app_player_mpv_MpvAudioNative_nativeCommand(
        JNIEnv *env, jobject, jobject owner, jobjectArray arguments) {
    auto *mpv = handle(env, owner);
    if (!mpv || !arguments) return MPV_ERROR_UNINITIALIZED;
    const jsize size = env->GetArrayLength(arguments);
    if (size < 1 || size > 32) return MPV_ERROR_INVALID_PARAMETER;
    jstring refs[32]{};
    const char *strings[33]{};
    auto release = [&] {
        for (jsize i = 0; i < size; ++i) {
            if (strings[i]) env->ReleaseStringUTFChars(refs[i], strings[i]);
            if (refs[i]) env->DeleteLocalRef(refs[i]);
        }
    };
    for (jsize i = 0; i < size; ++i) {
        refs[i] = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        if (!refs[i]) {
            release();
            return MPV_ERROR_INVALID_PARAMETER;
        }
        strings[i] = env->GetStringUTFChars(refs[i], nullptr);
        if (!strings[i]) {
            release();
            return MPV_ERROR_NOMEM;
        }
    }
    const int result = mpv_command(mpv, strings);
    release();
    return result;
}

static double value(const mpv_node &node) {
    if (node.format == MPV_FORMAT_DOUBLE) return node.u.double_;
    if (node.format == MPV_FORMAT_INT64) return static_cast<double>(node.u.int64);
    if (node.format == MPV_FORMAT_STRING && node.u.string) {
        char *end = nullptr;
        const double number = std::strtod(node.u.string, &end);
        if (end && end != node.u.string && *end == '\0') return number;
    }
    return NAN;
}

static bool stats(mpv_handle *mpv, const char *property, double *output) {
    mpv_node node{};
    if (mpv_get_property(mpv, property, MPV_FORMAT_NODE, &node) < 0) return false;
    bool measured = false;
    if (node.format == MPV_FORMAT_NODE_MAP && node.u.list) {
        auto *list = node.u.list;
        for (int i = 0; i < list->num; ++i) {
            const char *key = list->keys[i];
            if (!key) continue;
            if (std::strcmp(key, "lavfi.astats.Overall.RMS_level") == 0) {
                output[0] = value(list->values[i]);
            } else if (std::strcmp(key, "lavfi.astats.Overall.Peak_level") == 0) {
                output[1] = value(list->values[i]);
            } else if (std::strcmp(key, "lavfi.astats.Overall.Number_of_samples") == 0) {
                output[2] = value(list->values[i]);
                measured = output[2] > 0;
            }
        }
    }
    mpv_free_node_contents(&node);
    return measured;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jellycine_app_player_mpv_MpvAudioNative_nativePcmStats(
        JNIEnv *env, jobject, jobject owner) {
    auto *mpv = handle(env, owner);
    if (!mpv) return nullptr;
    double input[3] = {NAN, NAN, NAN};
    double output[3] = {NAN, NAN, NAN};
    if (!stats(mpv, "af-metadata/jellycine_gain_input", input) ||
        !stats(mpv, "af-metadata/jellycine_gain_output", output)) return nullptr;
    const double values[6] = {input[0], input[1], output[0], output[1], input[2], output[2]};
    auto result = env->NewDoubleArray(6);
    if (result) env->SetDoubleArrayRegion(result, 0, 6, values);
    return result;
}
