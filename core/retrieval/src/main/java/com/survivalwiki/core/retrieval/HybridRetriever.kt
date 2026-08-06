package com.survivalwiki.core.retrieval

import com.survivalwiki.core.embedding.E5Prefix
import com.survivalwiki.core.embedding.Embedder

/** Esito completo del retrieval, consumato dalla UI/ViewModel. */
sealed interface RetrievalOutcome {
    /** Query bloccata dal filtro di sicurezza. */
    data class Blocked(val reason: String) : RetrievalOutcome

    /** Passaggi affidabili trovati: pronti per la generazione vincolata o per la modalità "solo estratti". */
    data class Grounded(val passages: List<ScoredChunk>) : RetrievalOutcome

    /** Nessuna fonte sopra soglia: l'LLM NON viene invocato. */
    data class Empty(val nearbyTopics: List<String>) : RetrievalOutcome
}

/**
 * Pipeline di retrieval ibrida (dense + lexical → RRF → soglia), con SafetyFilter a monte.
 *
 * È il cuore anti-allucinazione: sotto la soglia di confidenza restituisce [RetrievalOutcome.Empty]
 * e il chiamante NON deve invocare l'LLM.
 */
class HybridRetriever(
    // Nullable: se il modello di embedding non è ancora stato scaricato, il retrieval degrada
    // elegantemente a sola ricerca lessicale (FTS5). La modalità Browse/ricerca resta utile offline.
    private val embedder: Embedder?,
    private val corpus: CorpusReader,
    private val rewriter: QueryRewriter,
    private val safety: SafetyFilter,
    private val gate: ConfidenceGate,
    private val fusion: ReciprocalRankFusion = ReciprocalRankFusion(),
    private val denseTopK: Int = 20,
    private val lexicalTopK: Int = 20,
    private val categoryBoost: Double = 0.5,
    // Coseno minimo del miglior risultato denso perché una query sia considerata "in dominio".
    // Calibrato sul golden set (Fase 6): in-domain min=0.843, OOD max=0.821 → 0.835 separa.
    private val denseFloor: Float = 0.835f,
) {
    suspend fun retrieve(query: String, categoryFilter: Category? = null): RetrievalOutcome {
        when (val verdict = safety.check(query)) {
            is SafetyVerdict.Blocked -> return RetrievalOutcome.Blocked(verdict.reason)
            SafetyVerdict.Allowed -> Unit
        }

        val rewritten = rewriter.rewrite(query)
        if (rewritten.denseText.isBlank()) {
            return RetrievalOutcome.Empty(nearbyTopics = emptyList())
        }

        val denseScored = if (embedder != null) {
            val queryVector = embedder.embed(rewritten.denseText, E5Prefix.QUERY)
            corpus.searchDense(queryVector, denseTopK)
        } else {
            emptyList()
        }
        val dense = denseScored.map { it.chunk }
        val topDenseCosine = denseScored.firstOrNull()?.score?.toFloat() ?: 0f
        val lexical = corpus.searchLexical(rewritten.lexicalText, lexicalTopK)

        var fused = fusion.fuse(listOf(dense, lexical))
        if (categoryFilter != null) {
            // Boost per categoria: privilegia i chunk della categoria filtrata senza escludere gli altri.
            fused = fused
                .map { if (it.chunk.category == categoryFilter) it.copy(score = it.score * (1 + categoryBoost)) else it }
                .sortedByDescending { it.score }
        }

        val decision = gate.decide(
            fused = fused,
            hasLexicalSupport = lexical.isNotEmpty(),
            topDenseCosine = topDenseCosine,
            denseFloor = denseFloor,
            nearbyTopicsProvider = { nearbyTopics(fused) },
        )
        return when (decision) {
            is RetrievalDecision.Proceed -> RetrievalOutcome.Grounded(decision.passages)
            is RetrievalDecision.NoReliableSource -> RetrievalOutcome.Empty(decision.nearbyTopics)
        }
    }

    /** Argomenti "vicini" da suggerire quando non c'è nulla sopra soglia: titoli dei doc più in alto. */
    private fun nearbyTopics(fused: List<ScoredChunk>): List<String> =
        fused.take(5).map { it.chunk.docTitle }.distinct().take(3)
}
