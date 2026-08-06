// JNI wrapper minimale su llama.cpp per SurvivalWiki AI.
//
// Design "token-pull": Kotlin guida il ciclo di generazione (start → nextToken* → stop),
// così lo streaming verso un Flow e la cancellazione restano lato Kotlin, senza callback JNI.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "survivalllm"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaState {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    llama_sampler* sampler = nullptr;
    int n_past = 0;
    bool eog = false;
};

std::string jstringToStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* utf = env->GetStringUTFChars(s, nullptr);
    std::string out(utf ? utf : "");
    if (utf) env->ReleaseStringUTFChars(s, utf);
    return out;
}

std::vector<llama_token> tokenize(const llama_vocab* vocab, const std::string& text, bool add_special) {
    int n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                            nullptr, 0, add_special, /*parse_special*/ true);
    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                   tokens.data(), n, add_special, true);
    return tokens;
}

std::string tokenToPiece(const llama_vocab* vocab, llama_token id) {
    char buf[256];
    int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, /*special*/ false);
    if (n < 0) return {};
    return std::string(buf, n);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_survivalwiki_core_llm_LlamaCppEngine_nativeInit(
        JNIEnv* env, jobject, jstring modelPath, jint nCtx,
        jfloat temperature, jfloat topP, jfloat repeatPenalty) {
    static bool backend_ready = false;
    if (!backend_ready) { llama_backend_init(); backend_ready = true; }

    const std::string path = jstringToStd(env, modelPath);

    llama_model_params mparams = llama_model_default_params();
    llama_model* model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("Caricamento modello fallito: %s", path.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    llama_context* ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("Creazione contesto fallita");
        llama_model_free(model);
        return 0;
    }

    const llama_vocab* vocab = llama_model_get_vocab(model);

    // Catena di campionamento: penalità di ripetizione → top-p → temperatura → estrazione.
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            /*n_vocab*/ llama_vocab_n_tokens(vocab),
            /*penalty_last_n*/ 64, /*penalty_repeat*/ repeatPenalty,
            /*penalty_freq*/ 0.0f, /*penalty_present*/ 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    auto* state = new LlamaState();
    state->model = model;
    state->ctx = ctx;
    state->vocab = vocab;
    state->sampler = smpl;
    state->n_past = 0;
    state->eog = false;
    LOGI("Modello inizializzato (n_ctx=%d)", nCtx);
    return reinterpret_cast<jlong>(state);
}

// Prepara la generazione: valuta il prompt. Ritorna false se il prompt eccede il contesto.
JNIEXPORT jboolean JNICALL
Java_com_survivalwiki_core_llm_LlamaCppEngine_nativeStart(
        JNIEnv* env, jobject, jlong handle, jstring prompt) {
    auto* state = reinterpret_cast<LlamaState*>(handle);
    if (state == nullptr) return JNI_FALSE;

    // Reset della cache KV per una nuova generazione.
    llama_memory_clear(llama_get_memory(state->ctx), true);
    state->n_past = 0;
    state->eog = false;

    const std::string text = jstringToStd(env, prompt);
    std::vector<llama_token> tokens = tokenize(state->vocab, text, /*add_special*/ true);
    if ((int) tokens.size() >= (int) llama_n_ctx(state->ctx)) {
        LOGE("Prompt troppo lungo: %zu token", tokens.size());
        return JNI_FALSE;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(state->ctx, batch) != 0) {
        LOGE("llama_decode del prompt fallito");
        return JNI_FALSE;
    }
    state->n_past += (int) tokens.size();
    return JNI_TRUE;
}

// Estrae il prossimo token. Ritorna il pezzo di testo, o stringa vuota a fine generazione (EOG).
JNIEXPORT jstring JNICALL
Java_com_survivalwiki_core_llm_LlamaCppEngine_nativeNextToken(
        JNIEnv* env, jobject, jlong handle) {
    auto* state = reinterpret_cast<LlamaState*>(handle);
    if (state == nullptr || state->eog) return env->NewStringUTF("");

    llama_token id = llama_sampler_sample(state->sampler, state->ctx, -1);
    if (llama_vocab_is_eog(state->vocab, id)) {
        state->eog = true;
        return env->NewStringUTF("");
    }

    const std::string piece = tokenToPiece(state->vocab, id);

    llama_batch batch = llama_batch_get_one(&id, 1);
    if (llama_decode(state->ctx, batch) != 0) {
        state->eog = true;
        return env->NewStringUTF("");
    }
    state->n_past += 1;
    return env->NewStringUTF(piece.c_str());
}

JNIEXPORT void JNICALL
Java_com_survivalwiki_core_llm_LlamaCppEngine_nativeFree(JNIEnv*, jobject, jlong handle) {
    auto* state = reinterpret_cast<LlamaState*>(handle);
    if (state == nullptr) return;
    if (state->sampler) llama_sampler_free(state->sampler);
    if (state->ctx) llama_free(state->ctx);
    if (state->model) llama_model_free(state->model);
    delete state;
}

} // extern "C"
