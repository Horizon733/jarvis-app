#include <android/log.h>

#include <algorithm>

#include <atomic>

#include <cstring>

#include <sstream>

#include <jni.h>

#include <string>

#include <unistd.h>

#include <vector>



#include "chat.h"

#include "common.h"

#include "ggml-backend.h"

#include "llama.h"

#include "sampling.h"



#define TAG "LlamaBridge"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)



static constexpr int N_THREADS_MIN = 2;

static constexpr int N_THREADS_MAX = 4;

static constexpr int N_THREADS_HEADROOM = 2;

static constexpr int DEFAULT_CONTEXT_SIZE = 1024;

static constexpr int BATCH_SIZE = 512;



static llama_model * g_model = nullptr;

static llama_context * g_context = nullptr;

static common_chat_templates_ptr g_chat_templates;

static std::atomic_bool g_cancelled = false;

static std::string g_last_error;

static std::string g_load_log;

static int g_effective_ctx = DEFAULT_CONTEXT_SIZE;

static bool g_backend_initialized = false;

static std::string get_model_architecture() {
    if (!g_model) return {};
    char buf[256] = {};
    if (llama_model_meta_val_str(g_model, "general.architecture", buf, sizeof(buf)) > 0) {
        return buf;
    }
    return {};
}

static bool is_gemma_architecture(const std::string & arch) {
    return arch.find("gemma") != std::string::npos;
}

static std::string format_gemma4_fallback_prompt(const std::vector<common_chat_msg> & messages) {
    std::ostringstream ss;
    size_t start = 0;
    if (!messages.empty() &&
        (messages[0].role == "system" || messages[0].role == "developer")) {
        ss << "<|turn>system\n" << messages[0].content << "<turn|>\n";
        start = 1;
    }
    for (size_t i = start; i < messages.size(); ++i) {
        const auto & msg = messages[i];
        if (msg.role == "assistant") {
            ss << "<|turn>model\n" << msg.content << "<turn|>\n";
        } else {
            ss << "<|turn>user\n" << msg.content << "<turn|>\n";
        }
    }
    if (!messages.empty() && messages.back().role == "user") {
        // Prime an empty thought channel so the model answers directly.
        ss << "<|turn>model\n<|channel>thought\n<channel|>";
    }
    return ss.str();
}

static size_t partial_marker_suffix_len(const std::string & text, const char * marker) {
    const size_t marker_len = strlen(marker);
    if (marker_len <= 1 || text.empty()) return 0;
    const size_t max_len = std::min(text.size(), marker_len - 1);
    for (size_t len = max_len; len > 0; --len) {
        if (text.compare(text.size() - len, len, marker, len) == 0) {
            return len;
        }
    }
    return 0;
}

// Gemma4 streams internal planning in a "thought" channel before the user-visible reply.
static std::string strip_gemma_thinking_chunk(std::string & carry, const std::string & chunk, bool & in_thinking) {
    carry += chunk;
    std::string output;
    static const char * k_thinking_starts[] = {
        "<|channel|>thought",
        "<|channel>thought",
        nullptr,
    };
    static const char * k_thinking_end = "<channel|>";

    while (!carry.empty()) {
        if (!in_thinking) {
            size_t start_pos = std::string::npos;
            size_t start_len = 0;
            for (const char ** marker = k_thinking_starts; *marker != nullptr; ++marker) {
                const size_t pos = carry.find(*marker);
                if (pos != std::string::npos && (start_pos == std::string::npos || pos < start_pos)) {
                    start_pos = pos;
                    start_len = strlen(*marker);
                }
            }

            if (start_pos == 0) {
                carry.erase(0, start_len);
                in_thinking = true;
                continue;
            }
            if (start_pos != std::string::npos) {
                output += carry.substr(0, start_pos);
                carry.erase(0, start_pos);
                continue;
            }

            size_t keep = partial_marker_suffix_len(carry, k_thinking_end);
            for (const char ** marker = k_thinking_starts; *marker != nullptr; ++marker) {
                keep = std::max(keep, partial_marker_suffix_len(carry, *marker));
            }
            if (carry.size() > keep) {
                output += carry.substr(0, carry.size() - keep);
                carry.erase(0, carry.size() - keep);
            }
            break;
        }

        const size_t end_pos = carry.find(k_thinking_end);
        if (end_pos == std::string::npos) {
            const size_t keep = partial_marker_suffix_len(carry, k_thinking_end);
            if (carry.size() > keep) {
                carry.erase(0, carry.size() - keep);
            }
            break;
        }
        carry.erase(0, end_pos + strlen(k_thinking_end));
        in_thinking = false;
    }

    return output;
}

static int android_log_prio_from_ggml(enum ggml_log_level level) {

    switch (level) {

        case GGML_LOG_LEVEL_ERROR: return ANDROID_LOG_ERROR;

        case GGML_LOG_LEVEL_WARN:  return ANDROID_LOG_WARN;

        case GGML_LOG_LEVEL_INFO:  return ANDROID_LOG_INFO;

        case GGML_LOG_LEVEL_DEBUG: return ANDROID_LOG_DEBUG;

        default:                   return ANDROID_LOG_DEFAULT;

    }

}



static void android_log_callback(enum ggml_log_level level, const char * text, void *) {

    __android_log_write(android_log_prio_from_ggml(level), TAG, text);

}



static void load_log_callback(enum ggml_log_level level, const char * text, void *) {

    android_log_callback(level, text, nullptr);

    if ((level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN) && g_load_log.size() < 4096) {

        g_load_log += text;

    }

}



static int resolve_thread_count() {

    const long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);

    if (cpu_count <= 0) return N_THREADS_MIN;

    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, static_cast<int>(cpu_count) - N_THREADS_HEADROOM));

}



extern "C"

JNIEXPORT void JNICALL

Java_com_example_llama_LlamaEngine_nativeInit(JNIEnv * env, jobject, jstring jnative_library_dir) {

    llama_log_set(android_log_callback, nullptr);



    const char * native_library_dir = env->GetStringUTFChars(jnative_library_dir, nullptr);

    LOGI("Loading GGML backends from %s", native_library_dir);

    ggml_backend_load_all_from_path(native_library_dir);

    env->ReleaseStringUTFChars(jnative_library_dir, native_library_dir);



    if (!g_backend_initialized) {

        llama_backend_init();

        g_backend_initialized = true;

    }



    LOGI("llama backend initialized, backend_count=%zu", ggml_backend_reg_count());

}



static void release_model() {

    g_chat_templates.reset();

    if (g_context) {

        llama_free(g_context);

        g_context = nullptr;

    }

    if (g_model) {

        llama_model_free(g_model);

        g_model = nullptr;

    }

}



extern "C"

JNIEXPORT jlong JNICALL

Java_com_example_llama_LlamaEngine_nativeLoad(

        JNIEnv * env, jobject, jstring jmodel_path, jint n_ctx, jint n_threads) {

    release_model();

    g_last_error.clear();

    g_load_log.clear();



    if (ggml_backend_reg_count() == 0) {

        g_last_error =

                "Inference backend not loaded. Rebuild and reinstall the app, then try again.";

        LOGE("%s", g_last_error.c_str());

        return -1;

    }



    const char * model_path = env->GetStringUTFChars(jmodel_path, nullptr);

    LOGI("Loading model from %s", model_path);



    llama_log_set(load_log_callback, nullptr);

    llama_model_params model_params = llama_model_default_params();

    g_model = llama_model_load_from_file(model_path, model_params);

    llama_log_set(android_log_callback, nullptr);

    env->ReleaseStringUTFChars(jmodel_path, model_path);



    if (!g_model) {

        if (!g_load_log.empty()) {

            g_last_error = g_load_log;

        } else {

            g_last_error =

                    "Could not load GGUF weights. If metadata looks fine, the download may be "

                    "incomplete — delete the file, re-download, and import again.";

        }

        LOGE("%s", g_last_error.c_str());

        return -1;

    }



    const int trained_context_size = llama_model_n_ctx_train(g_model);

    const int requested_ctx = n_ctx > 0 ? n_ctx : DEFAULT_CONTEXT_SIZE;

    const int effective_ctx = trained_context_size > 0

            ? std::min(requested_ctx, trained_context_size)

            : requested_ctx;

    const int effective_threads = n_threads > 0 ? n_threads : resolve_thread_count();

    LOGI("Model loaded. trained_ctx=%d effective_ctx=%d threads=%d",

         trained_context_size, effective_ctx, effective_threads);



    auto params = llama_context_default_params();

    params.n_ctx = effective_ctx;

    params.n_batch = std::min(BATCH_SIZE, effective_ctx);

    params.n_ubatch = params.n_batch;

    params.n_threads = effective_threads;

    params.n_threads_batch = effective_threads;



    g_context = llama_init_from_model(g_model, params);
    if (!g_context) {
        g_last_error =
                "Model weights loaded but context creation failed (usually out of memory). "
                "Try Gemma 3 1B first, or a smaller quantization.";
        LOGE("%s", g_last_error.c_str());
        release_model();
        return -2;
    }

    g_effective_ctx = effective_ctx;

    try {
        g_chat_templates = common_chat_templates_init(g_model, "");
        if (g_chat_templates) {
            const bool explicit_template =
                    common_chat_templates_was_explicit(g_chat_templates.get());
            const std::string arch = get_model_architecture();
            LOGI("Chat template ready (explicit=%d, arch=%s)", explicit_template, arch.c_str());
            if (!explicit_template && is_gemma_architecture(arch)) {
                LOGW("Gemma model has no embedded chat template; generation will use a Gemma4 fallback format.");
            }
        }
    } catch (const std::exception & e) {
        LOGW("Failed to initialize chat templates: %s", e.what());
    }

    LOGI("Model ready (ctx=%d)", g_effective_ctx);
    return 1;
}



extern "C"

JNIEXPORT jint JNICALL

Java_com_example_llama_LlamaEngine_nativeGetLoadedContextSize(JNIEnv *, jobject) {
    return g_effective_ctx;
}

extern "C"

JNIEXPORT jstring JNICALL

Java_com_example_llama_LlamaEngine_nativeGetLastError(JNIEnv * env, jobject) {

    if (g_last_error.empty()) return nullptr;

    return env->NewStringUTF(g_last_error.c_str());

}



extern "C"

JNIEXPORT void JNICALL

Java_com_example_llama_LlamaEngine_nativeUnload(JNIEnv *, jobject) {

    g_cancelled = true;

    release_model();

}



extern "C"

JNIEXPORT void JNICALL

Java_com_example_llama_LlamaEngine_nativeCancel(JNIEnv *, jobject) {

    g_cancelled = true;

}



static std::string java_string(JNIEnv * env, jstring value) {

    const char * chars = env->GetStringUTFChars(value, nullptr);

    std::string result(chars);

    env->ReleaseStringUTFChars(value, chars);

    return result;

}



static void call_generation_finished(JNIEnv * env, jobject callback, const std::string & error) {
    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_finished = env->GetMethodID(callback_class, "onFinished", "(Ljava/lang/String;)V");
    jstring jerr = error.empty() ? nullptr : env->NewStringUTF(error.c_str());
    env->CallVoidMethod(callback, on_finished, jerr);
    if (jerr) env->DeleteLocalRef(jerr);
    env->DeleteLocalRef(callback_class);
}

static bool is_valid_utf8(const std::string & value) {

    for (size_t i = 0; i < value.size();) {

        const auto byte = static_cast<unsigned char>(value[i]);

        size_t width = byte < 0x80 ? 1 : (byte & 0xE0) == 0xC0 ? 2 :

                       (byte & 0xF0) == 0xE0 ? 3 : (byte & 0xF8) == 0xF0 ? 4 : 0;

        if (width == 0 || i + width > value.size()) return false;

        for (size_t j = 1; j < width; ++j) {

            if ((static_cast<unsigned char>(value[i + j]) & 0xC0) != 0x80) return false;

        }

        i += width;

    }

    return true;

}



extern "C"
JNIEXPORT void JNICALL
Java_com_example_llama_LlamaEngine_nativeGenerateChat(
        JNIEnv * env, jobject, jobjectArray jroles, jobjectArray jcontents,
        jfloat temperature, jint top_k, jfloat top_p, jfloat repeat_penalty,
        jint n_predict, jobject callback) {
    if (!g_context || !g_model) {
        g_last_error = "Model is not loaded.";
        LOGE("%s", g_last_error.c_str());
        call_generation_finished(env, callback, g_last_error);
        return;
    }
    if (env->GetArrayLength(jroles) != env->GetArrayLength(jcontents)) {
        g_last_error = "Internal error: chat roles and contents do not match.";
        LOGE("%s", g_last_error.c_str());
        call_generation_finished(env, callback, g_last_error);
        return;
    }

    try {
        g_cancelled = false;
        g_last_error.clear();
        llama_memory_clear(llama_get_memory(g_context), false);

        std::vector<common_chat_msg> messages;
        std::string formatted_prompt;
        const auto message_count = env->GetArrayLength(jroles);
        for (jsize i = 0; i < message_count; ++i) {
            auto role = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
            auto content = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
            messages.push_back({ java_string(env, role), java_string(env, content) });
            env->DeleteLocalRef(role);
            env->DeleteLocalRef(content);
        }

        const bool model_has_builtin_template =
                g_chat_templates && common_chat_templates_was_explicit(g_chat_templates.get());
        const std::string arch = get_model_architecture();
        const bool strip_gemma_thinking = is_gemma_architecture(arch);
        bool used_template_apply = false;
        bool used_gemma_fallback = false;

        if (g_chat_templates && (model_has_builtin_template || !is_gemma_architecture(arch))) {
            common_chat_templates_inputs inputs;
            inputs.messages = messages;
            inputs.use_jinja = true;
            inputs.enable_thinking = false;
            inputs.add_generation_prompt =
                    !messages.empty() && messages.back().role == "user";
            try {
                formatted_prompt = common_chat_templates_apply(g_chat_templates.get(), inputs).prompt;
                used_template_apply = true;
            } catch (const std::exception & e) {
                LOGW("Chat template apply failed: %s", e.what());
            }
        }

        if (formatted_prompt.empty() && is_gemma_architecture(arch)) {
            formatted_prompt = format_gemma4_fallback_prompt(messages);
            used_gemma_fallback = true;
            LOGI("Using Gemma4 fallback prompt format");
        }

        if (formatted_prompt.empty()) {
            for (const auto & message : messages) {
                formatted_prompt += message.role + ": " + message.content + "\n";
            }
            if (!messages.empty()) formatted_prompt += "assistant: ";
        }

        if (formatted_prompt.empty()) {
            g_last_error = used_template_apply
                ? "Chat template produced an empty prompt. This model may not support the current message format."
                : "Prompt formatting produced empty text.";
            LOGE("%s", g_last_error.c_str());
            call_generation_finished(env, callback, g_last_error);
            return;
        }

        const bool tokenize_with_special = used_template_apply || used_gemma_fallback;
        auto tokens = common_tokenize(
                g_context, formatted_prompt, tokenize_with_special, tokenize_with_special);
        const int context_size = static_cast<int>(llama_n_ctx(g_context));
        const int max_prompt_tokens = context_size - 1;
        if (tokens.empty()) {
            g_last_error = "Prompt tokenization failed (0 tokens).";
            LOGE("%s", g_last_error.c_str());
            call_generation_finished(env, callback, g_last_error);
            return;
        }
        if (static_cast<int>(tokens.size()) > max_prompt_tokens) {
            g_last_error = "Prompt is too long: " + std::to_string(tokens.size()) +
                " tokens, but the loaded context window is " + std::to_string(context_size) +
                ". Start a new chat or send a shorter message.";
            LOGE("%s", g_last_error.c_str());
            call_generation_finished(env, callback, g_last_error);
            return;
        }

        llama_batch batch = llama_batch_init(std::min(BATCH_SIZE, max_prompt_tokens), 0, 1);
        int n_past = 0;
        for (size_t offset = 0; offset < tokens.size(); offset += BATCH_SIZE) {
            common_batch_clear(batch);
            const int count = std::min<int>(BATCH_SIZE, tokens.size() - offset);
            for (int i = 0; i < count; ++i) {
                common_batch_add(batch, tokens[offset + i], n_past + i, {0},
                                 offset + i + 1 == tokens.size());
            }
            if (llama_decode(g_context, batch) != 0) {
                llama_batch_free(batch);
                g_last_error = "Failed to process prompt (decode error).";
                LOGE("%s", g_last_error.c_str());
                call_generation_finished(env, callback, g_last_error);
                return;
            }
            n_past += count;
        }

        common_params_sampling sampling_params;
        sampling_params.temp = temperature;
        sampling_params.top_k = top_k;
        sampling_params.top_p = top_p;
        sampling_params.penalty_repeat = repeat_penalty;
        common_sampler * sampler = common_sampler_init(g_model, sampling_params);
        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");

        int tokens_emitted = 0;
        bool stopped_for_context = false;
        bool stopped_for_decode = false;
        std::string utf8_pending;
        std::string thinking_carry;
        bool in_thinking_channel = false;
        for (int generated = 0; !g_cancelled && (n_predict < 0 || generated < n_predict); ++generated) {
            const llama_token token = common_sampler_sample(sampler, g_context, -1);
            common_sampler_accept(sampler, token, true);
            if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) break;

            utf8_pending += common_token_to_piece(g_context, token);
            if (!utf8_pending.empty() && is_valid_utf8(utf8_pending)) {
                const std::string visible = strip_gemma_thinking
                        ? strip_gemma_thinking_chunk(thinking_carry, utf8_pending, in_thinking_channel)
                        : utf8_pending;
                utf8_pending.clear();
                if (!visible.empty()) {
                    jstring piece = env->NewStringUTF(visible.c_str());
                    if (piece) {
                        env->CallVoidMethod(callback, on_token, piece);
                        env->DeleteLocalRef(piece);
                        tokens_emitted++;
                    }
                }
            }

            common_batch_clear(batch);
            common_batch_add(batch, token, n_past++, {0}, true);
            if (n_past >= llama_n_ctx(g_context)) {
                stopped_for_context = true;
                break;
            }
            if (llama_decode(g_context, batch) != 0) {
                stopped_for_decode = true;
                break;
            }
        }

        if (strip_gemma_thinking && !in_thinking_channel && !thinking_carry.empty()) {
            jstring piece = env->NewStringUTF(thinking_carry.c_str());
            if (piece) {
                env->CallVoidMethod(callback, on_token, piece);
                env->DeleteLocalRef(piece);
                tokens_emitted++;
            }
            thinking_carry.clear();
        }

        env->DeleteLocalRef(callback_class);
        common_sampler_free(sampler);
        llama_batch_free(batch);

        if (g_cancelled) {
            call_generation_finished(env, callback, "");
            return;
        }
        if (tokens_emitted == 0) {
            g_last_error = (used_template_apply || used_gemma_fallback)
                ? "Model produced no text. The chat template may not match this GGUF model."
                : "Model produced no text (immediate end-of-generation).";
            LOGE("%s", g_last_error.c_str());
            call_generation_finished(env, callback, g_last_error);
            return;
        }
        if (stopped_for_context) {
            g_last_error = "Context window filled during generation (" +
                std::to_string(context_size) + " tokens). Response may be truncated.";
            LOGW("%s", g_last_error.c_str());
            call_generation_finished(env, callback, "");
            return;
        }
        if (stopped_for_decode) {
            g_last_error = "Generation stopped due to a decode error.";
            LOGE("%s", g_last_error.c_str());
            call_generation_finished(env, callback, g_last_error);
            return;
        }

        call_generation_finished(env, callback, "");
    } catch (const std::exception & e) {
        g_last_error = std::string("Native generation exception: ") + e.what();
        LOGE("%s", g_last_error.c_str());
        call_generation_finished(env, callback, g_last_error);
    } catch (...) {
        g_last_error = "Unknown native generation exception.";
        LOGE("%s", g_last_error.c_str());
        call_generation_finished(env, callback, g_last_error);
    }
}


