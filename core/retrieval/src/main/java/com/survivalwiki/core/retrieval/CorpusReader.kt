package com.survivalwiki.core.retrieval

/** Metadati globali del corpus (da `meta` + `corpus_manifest.json`). */
data class CorpusManifest(
    val schemaVersion: Int,
    val documentCount: Int,
    val chunkCount: Int,
    val builtAtIso: String,
    val sha256: String,
)

/** Un documento del corpus (nodo intermedio della modalità Browse). */
data class CorpusDocument(
    val docId: String,
    val title: String,
    val author: String?,
    val year: Int?,
    val license: String,
    val category: Category?,
)

/**
 * Accesso in sola lettura al corpus. L'interfaccia vive nel modulo di dominio (core:retrieval) così
 * che [HybridRetriever] non dipenda dall'implementazione SQLite (che sta in core:data), evitando cicli.
 */
interface CorpusReader {
    suspend fun manifest(): CorpusManifest

    /** Modalità Browse: categorie con conteggio chunk. */
    suspend fun categories(): List<Pair<Category, Int>>

    /** Documenti che contengono chunk della categoria data. */
    suspend fun documents(category: Category): List<CorpusDocument>

    /** Chunk (sezioni) di un documento, in ordine di lettura. Per la modalità wiki. */
    suspend fun documentChunks(docId: String): List<RetrievedChunk>

    /** Dense retrieval: top-K con punteggio coseno (vettori L2-normalizzati). Ordinati decrescenti. */
    suspend fun searchDense(queryEmbedding: FloatArray, topK: Int): List<ScoredChunk>

    /** Lexical retrieval: top-K BM25 via FTS5. */
    suspend fun searchLexical(query: String, topK: Int): List<RetrievedChunk>
}
