package com.survivalwiki.core.llm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * [LlmEngine] basato su llama.cpp via JNI (libreria nativa `survivalllm`).
 *
 * La libreria nativa è compilata solo con il flag Gradle `-PwithLlama` (richiede NDK + submodule).
 * Se la libreria non è presente, [load] fallisce con [LlmException] e l'app resta in modalità
 * "solo estratti" (nessuna generazione), che è un percorso supportato di prima classe.
 *
 * Tutte le chiamate native per una sessione avvengono su un singolo thread dedicato: il contesto
 * llama.cpp non è thread-safe.
 */
class LlamaCppEngine : LlmEngine {

    @Volatile
    private var handle: Long = 0L

    override val isReady: Boolean get() = handle != 0L

    // Un solo thread per tutte le operazioni native della stessa sessione.
    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "llama-cpp") }.asCoroutineDispatcher()

    override suspend fun load(modelPath: String) = withContext(dispatcher) {
        ensureLibraryLoaded()
        if (handle != 0L) return@withContext
        val h = nativeInit(
            modelPath = modelPath,
            nCtx = DEFAULT_CONTEXT_TOKENS,
            temperature = DEFAULT_TEMPERATURE,
            topP = DEFAULT_TOP_P,
            repeatPenalty = DEFAULT_REPEAT_PENALTY,
        )
        if (h == 0L) throw LlmException("Inizializzazione del modello fallita: $modelPath")
        handle = h
    }

    override suspend fun unload() = withContext(dispatcher) {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    override fun generate(prompt: String, params: GenerationParams): Flow<String> = flow {
        val h = handle
        if (h == 0L) throw LlmException("Engine non caricato")
        if (!nativeStart(h, prompt)) {
            throw LlmException("Prompt non valido o troppo lungo per il contesto")
        }
        var produced = 0
        while (produced < params.maxOutputTokens) {
            val piece = nativeNextToken(h)
            if (piece.isEmpty()) break // EOG o errore di decode
            emit(piece)
            produced++
        }
    }.flowOn(dispatcher)

    // --- JNI ---
    private external fun nativeInit(
        modelPath: String, nCtx: Int, temperature: Float, topP: Float, repeatPenalty: Float,
    ): Long
    private external fun nativeStart(handle: Long, prompt: String): Boolean
    private external fun nativeNextToken(handle: Long): String
    private external fun nativeFree(handle: Long)

    companion object {
        private const val DEFAULT_CONTEXT_TOKENS = 4096
        private const val DEFAULT_TEMPERATURE = 0.2f
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_REPEAT_PENALTY = 1.1f

        @Volatile
        private var libraryLoaded = false

        private fun ensureLibraryLoaded() {
            if (libraryLoaded) return
            try {
                System.loadLibrary("survivalllm")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                throw LlmException("Libreria nativa llama.cpp non disponibile (build senza -PwithLlama)", e)
            }
        }
    }
}
