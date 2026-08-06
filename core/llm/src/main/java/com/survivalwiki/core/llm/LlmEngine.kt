package com.survivalwiki.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * Parametri di campionamento per la generazione vincolata.
 * Default conservativi: bassa temperatura, output corto, contesto ampio abbastanza per i passaggi.
 */
data class GenerationParams(
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val contextTokens: Int = 4096,
    val maxOutputTokens: Int = 400,
)

/**
 * Astrazione dell'inferenza LLM on-device.
 *
 * Implementazione primaria (Fase 4): [com.survivalwiki.core.llm] LlamaCppEngine via JNI su GGUF.
 * Fallback documentato in ADR: MediaPipe LLM Inference API.
 *
 * Contratto: l'engine NON conosce il retrieval. Riceve un prompt già costruito (system + passaggi
 * numerati + domanda) e restituisce token in streaming. Ogni vincolo anti-allucinazione è imposto
 * a monte (soglia di retrieval) e a valle (post-validazione citazioni), non qui.
 */
interface LlmEngine {

    /** True se un modello è caricato e pronto all'inferenza. */
    val isReady: Boolean

    /**
     * Carica un modello GGUF dal percorso locale (storage app-private). Idempotente.
     * @throws LlmException se il file è assente/corrotto o il caricamento nativo fallisce.
     */
    suspend fun load(modelPath: String)

    /** Rilascia le risorse native. Sicuro da chiamare più volte. */
    suspend fun unload()

    /**
     * Genera la risposta come flusso di token (stringhe parziali) per lo streaming in UI.
     * Il flusso completa a fine generazione o quando si raggiunge [GenerationParams.maxOutputTokens].
     */
    fun generate(prompt: String, params: GenerationParams = GenerationParams()): Flow<String>
}

/** Errore di dominio dell'engine LLM. */
class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
