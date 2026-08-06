package com.survivalwiki.core.retrieval

/**
 * Decisione della "regola d'oro": se il miglior punteggio fuso non supera la soglia,
 * l'LLM NON viene invocato e si mostra il fallback "nessuna fonte affidabile".
 */
sealed interface RetrievalDecision {
    /** Ci sono passaggi sufficientemente rilevanti: procedere alla generazione vincolata. */
    data class Proceed(val passages: List<ScoredChunk>) : RetrievalDecision

    /** Nessuna fonte affidabile: mostrare il fallback, con eventuali argomenti vicini. */
    data class NoReliableSource(val nearbyTopics: List<String>) : RetrievalDecision
}

/**
 * Applica la soglia di confidenza sul risultato fuso.
 *
 * @param threshold punteggio RRF minimo del miglior passaggio per procedere. Tarabile da Settings.
 * @param maxPassages numero massimo di passaggi passati all'LLM (top-N dopo la fusione).
 */
class ConfidenceGate(
    private val threshold: Double,
    private val maxPassages: Int = 8,
) {
    fun decide(
        fused: List<ScoredChunk>,
        nearbyTopicsProvider: () -> List<String> = { emptyList() },
    ): RetrievalDecision {
        val best = fused.firstOrNull()
        return if (best != null && best.score >= threshold) {
            RetrievalDecision.Proceed(fused.take(maxPassages))
        } else {
            RetrievalDecision.NoReliableSource(nearbyTopicsProvider())
        }
    }
}
