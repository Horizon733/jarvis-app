#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.cpp/llama.h"
#include "llama.cpp/common/common.h"

#define TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_ai_1agent_native_LlamaEngine_nativeLoad(JNIEnv* env, jobject thiz, jstring model_path, jint n_ctx, jint n_threads) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);

    llama_backend_init();

    auto mparams = llama_model_default_params();
    model = llama_load_model_from_file(path, mparams);

    if (!model) {
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    ctx = llama_new_context_with_model(model, cparams);

    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_ai_1agent_native_LlamaEngine_nativeUnload(JNIEnv* env, jobject thiz) {
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_free_model(model);
        model = nullptr;
    }
    llama_backend_free();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_ai_1agent_native_LlamaEngine_nativeGenerate(
    JNIEnv* env,
    jobject thiz,
    jstring prompt,
    jfloat temp,
    jint n_predict,
    jobject callback
) {
    if (!ctx) return;

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

    // Simple generation loop for demonstration
    // In a production app, you'd use common.h's sampling and batching

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    // Tokenize
    std::vector<llama_token> tokens = llama_tokenize(model, prompt_str, true, true);

    int n_ctx = llama_n_ctx(ctx);
    int n_kv_req = tokens.size() + (n_predict > 0 ? n_predict : 256);

    if (n_kv_req > n_ctx) {
        LOGI("Context size exceeded");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return;
    }

    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, { 0 }, i == tokens.size() - 1);
    }

    if (llama_decode(ctx, batch) != 0) {
        LOGI("llama_decode failed");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return;
    }

    int n_cur = batch.n_tokens;
    int n_gen = 0;

    while (n_gen < n_predict || n_predict < 0) {
        auto logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
        auto n_vocab = llama_n_vocab(model);

        std::vector<llama_token_data> candidates;
        candidates.reserve(n_vocab);
        for (llama_token token_id = 0; token_id < n_vocab; token_id++) {
            candidates.push_back({token_id, logits[token_id], 0.0f});
        }

        llama_token_data_array candidates_p = { candidates.data(), candidates.size(), false };

        // Basic sampling
        llama_sample_temp(ctx, &candidates_p, temp);
        const llama_token id = llama_sample_token(ctx, &candidates_p);

        if (id == llama_token_eos(model)) {
            break;
        }

        // Convert token to string
        char buf[128];
        int n = llama_token_to_piece(model, id, buf, sizeof(buf));
        if (n > 0) {
            std::string piece(buf, n);
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        n_gen++;

        llama_batch_clear(batch);
        llama_batch_add(batch, id, n_cur, { 0 }, true);
        n_cur++;

        if (llama_decode(ctx, batch) != 0) {
            break;
        }
    }

    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_ai_1agent_native_LlamaEngine_nativeApplyTemplate(JNIEnv* env, jobject thiz, jstring template_name, jstring prompt) {
    // Simplified for Phase 1
    return prompt;
}
