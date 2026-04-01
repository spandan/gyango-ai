#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

whisper_context* g_ctx = nullptr;

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_ai_pocket_whisper_WhisperBridge_init(JNIEnv* env, jclass, jstring jModelPath, jint /*nThreads*/) {
    const char* path = env->GetStringUTFChars(jModelPath, nullptr);
    if (!path) return JNI_FALSE;

    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    g_ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(jModelPath, path);

    if (!g_ctx) {
        LOGE("whisper_init_from_file failed");
        return JNI_FALSE;
    }
    LOGI("Whisper model loaded");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_ai_pocket_whisper_WhisperBridge_release(JNIEnv* env, jclass) {
    (void)env;
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        LOGI("Whisper released");
    }
}

JNIEXPORT jstring JNICALL
Java_ai_pocket_whisper_WhisperBridge_transcribe(JNIEnv* env, jclass, jfloatArray jSamples, jint nThreads) {
    if (!g_ctx) {
        return env->NewStringUTF("");
    }

    const jsize n = env->GetArrayLength(jSamples);
    if (n <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<float> pcmf32(static_cast<size_t>(n));
    env->GetFloatArrayRegion(jSamples, 0, n, pcmf32.data());

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.n_threads = nThreads > 0 ? nThreads : 4;
    wparams.single_segment = true;
    wparams.no_context = true;

    if (whisper_full(g_ctx, wparams, pcmf32.data(), static_cast<int>(pcmf32.size())) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }

    std::string text;
    const int n_segments = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char* seg = whisper_full_get_segment_text(g_ctx, i);
        if (seg && seg[0] != '\0') {
            if (!text.empty()) text += ' ';
            text += seg;
        }
    }

    return env->NewStringUTF(text.c_str());
}

} // extern "C"
