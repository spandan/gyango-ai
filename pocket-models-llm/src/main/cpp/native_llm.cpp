#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include "llama.h"

#define TAG "NativeLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define UNUSED(x) (void)(x)

/** Full detokenization for one id; buffer must fit the whole piece or llama returns negative required size. */
static std::string token_to_piece_utf8(const llama_vocab* vocab, llama_token id) {
    std::vector<char> buf(256);
    for (int guard = 0; guard < 16; ++guard) {
        const int32_t n = llama_token_to_piece(vocab, id, buf.data(), (int32_t)buf.size(), 0, true);
        if (n >= 0) {
            if (n == 0) return {};
            return std::string(buf.data(), (size_t)n);
        }
        buf.resize((size_t)(-n));
    }
    LOGE("token_to_piece_utf8: buffer resize loop exceeded");
    return {};
}

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static bool g_backend_initialized = false;
static bool g_recurrent = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_ai_pocket_native_NativeLlmBridge_init(
        JNIEnv* env,
        jobject /* this */,
        jstring jModelPath,
        jint nThreads,
        jint contextLength
) {
    LOGI("Initializing native LLM: threads=%d, ctx=%d", nThreads, contextLength);
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    if (!modelPath) return JNI_FALSE;

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }

    auto mparams = llama_model_default_params();
    mparams.use_mmap = true;

    g_model = llama_model_load_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (!g_model) {
        LOGE("Failed to load model weights");
        return JNI_FALSE;
    }

    g_recurrent = llama_model_is_recurrent(g_model);
    LOGI("Model loaded. Recurrent: %s", g_recurrent ? "yes" : "no");

    auto cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) contextLength;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    cparams.n_batch = 512;
    cparams.n_seq_max = 1;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to init context");
        return JNI_FALSE;
    }

    LOGI("Native LLM ready");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_ai_pocket_native_NativeLlmBridge_release(JNIEnv* env, jobject thiz) {
    UNUSED(env); UNUSED(thiz);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    LOGI("Native LLM released");
}

JNIEXPORT void JNICALL
Java_ai_pocket_native_NativeLlmBridge_generateStreaming(
        JNIEnv* env,
        jobject /* this */,
        jstring jPrompt,
        jfloat temperature,
        jfloat topP,
        jint maxTokens,
        jobject callback
) {
    if (!g_ctx || !g_model || !callback) return;

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) return;

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    LOGI("Starting generation. Temp: %.2f, Max Tokens: %d", temperature, maxTokens);

    // Reset KV cache for a fresh conversation turn
    llama_memory_clear(llama_get_memory(g_ctx), true);

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    const bool add_special = !g_recurrent;
    int32_t prompt_len = (int32_t)strlen(prompt);
    std::vector<llama_token> tokens(prompt_len + 16);
    int32_t n_tokens = llama_tokenize(vocab, prompt, prompt_len, tokens.data(), (int32_t)tokens.size(), add_special, false);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt, prompt_len, tokens.data(), (int32_t)tokens.size(), add_special, false);
    }
    env->ReleaseStringUTFChars(jPrompt, prompt);

    if (n_tokens <= 0) return;
    tokens.resize(n_tokens);

    // Process prompt (prefill)
    const int32_t n_batch_max = (int32_t)llama_n_batch(g_ctx);
    for (int32_t i = 0; i < n_tokens; ) {
        int32_t n_eval = n_tokens - i;
        if (n_eval > n_batch_max) n_eval = n_batch_max;
        llama_batch batch = llama_batch_get_one(tokens.data() + i, n_eval);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Decode failed at prefill");
            return;
        }
        i += n_eval;
    }

    // Initialize sampler for this request
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    if (maxTokens <= 0) {
        llama_sampler_free(sampler);
        LOGI("Generation skipped: maxTokens <= 0");
        return;
    }

    int n_gen = 0;
    uint32_t n_ctx = llama_n_ctx(g_ctx);
    // One fixed-size context window: prefill (prompt) and new tokens share n_ctx slots.
    // Effective new-token ceiling is min(maxTokens, n_ctx - n_tokens), until the model emits EOG.
    LOGI(
        "Gen budget: max_new_tokens=%d n_prompt_tokens=%d n_ctx=%u seq_headroom=%d",
        maxTokens,
        n_tokens,
        n_ctx,
        (int)n_ctx - n_tokens
    );

    while (n_gen < maxTokens) {
        llama_token id = llama_sampler_sample(sampler, g_ctx, -1);
        llama_sampler_accept(sampler, id);

        if (llama_vocab_is_eog(vocab, id)) {
            LOGI("EOG detected");
            break;
        }

        std::string piece = token_to_piece_utf8(vocab, id);
        if (!piece.empty()) {
            jstring jPiece = env->NewStringUTF(piece.c_str());
            if (jPiece) {
                env->CallVoidMethod(callback, onToken, jPiece);
                env->DeleteLocalRef(jPiece);
            } else {
                LOGE("NewStringUTF failed for token piece (invalid UTF-8? len=%zu)", piece.size());
            }
        }

        n_gen++;
        if (n_gen >= maxTokens || (n_tokens + n_gen) >= (int)n_ctx) {
            if ((n_tokens + n_gen) >= (int)n_ctx) {
                LOGI("Stopped: context full (n_prompt=%d n_new=%d n_ctx=%u)", n_tokens, n_gen, n_ctx);
            }
            break;
        }

        llama_batch gen_batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, gen_batch) != 0) break;
    }

    llama_sampler_free(sampler);
    LOGI("Generation finished. New tokens emitted: %d (cap was %d)", n_gen, maxTokens);
}

}
