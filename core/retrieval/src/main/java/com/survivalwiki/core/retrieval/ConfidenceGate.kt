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
    /**
     * @param hasLexicalSupport true se la ricerca lessicale (BM25 su token di contenuto) ha
     *        prodotto almeno un risultato. È il segnale anti-allucinazione più forte: una query
     *        fuori dominio non condivide vocabolario con il corpus.
     * @param topDenseCosine coseno del miglior risultato denso (0 se il modello non è disponibile).
     * @param denseFloor soglia assoluta di coseno oltre la quale il denso è considerato "in tema"
     *        anche senza supporto lessicale.
     *
     * Regola: si procede solo se il punteggio fuso supera la soglia **e** la query è "in dominio"
     * (supporto lessicale **oppure** coseno denso sopra il floor). Altrimenti nessuna fonte affidabile.
     */
    fun decide(
        fused: List<ScoredChunk>,
        hasLexicalSupport: Boolean = true,
        topDenseCosine: Float = 1f,
        denseFloor: Float = 0f,
        nearbyTopicsProvider: () -> List<String> = { emptyList() },
    ): RetrievalDecision {
        val best = fused.firstOrNull()
        val passThreshold = best != null && best.score >= threshold
        val inDomain = hasLexicalSupport || topDenseCosine >= denseFloor
        return if (passThreshold && inDomain) {
            RetrievalDecision.Proceed(fused.take(maxPassages))
        } else {
            RetrievalDecision.NoReliableSource(nearbyTopicsProvider())
        }
    }
}
