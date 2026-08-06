package com.survivalwiki.core.retrieval

/**
 * Reciprocal Rank Fusion (RRF).
 *
 * Fonde più liste di ranking (es. dense + lexical) senza dover normalizzare punteggi eterogenei:
 * ogni lista contribuisce 1 / (k + rank) al punteggio di ciascun documento.
 *
 * @param k costante di smorzamento (paper originale: 60). Valori più alti appiattiscono il peso
 *          delle prime posizioni.
 */
class ReciprocalRankFusion(private val k: Int = 60) {

    init {
        require(k > 0) { "k deve essere positivo" }
    }

    /**
     * @param rankedLists liste di ranking, ciascuna ordinata dalla più rilevante alla meno.
     *                    Il rank effettivo è la posizione nell'elenco (0-based) → +1 internamente.
     * @return chunk unici ordinati per punteggio RRF decrescente.
     */
    fun fuse(rankedLists: List<List<RetrievedChunk>>): List<ScoredChunk> {
        val scoreById = HashMap<String, Double>()
        val chunkById = HashMap<String, RetrievedChunk>()

        for (list in rankedLists) {
            list.forEachIndexed { index, chunk ->
                val contribution = 1.0 / (k + index + 1)
                scoreById.merge(chunk.chunkId, contribution, Double::plus)
                chunkById.putIfAbsent(chunk.chunkId, chunk)
            }
        }

        return scoreById.entries
            .map { (id, score) -> ScoredChunk(chunkById.getValue(id), score) }
            .sortedByDescending { it.score }
    }
}
