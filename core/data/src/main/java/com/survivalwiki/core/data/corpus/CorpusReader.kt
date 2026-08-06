package com.survivalwiki.core.data.corpus

import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.RetrievedChunk

/** Metadati globali del corpus, letti da `corpus_manifest.json`. */
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
 * Accesso in sola lettura al corpus SQLite (tabelle documents/chunks/chunks_fts/chunk_vectors),
 * caricato da `assets/` o da storage app-private. Implementazione (Fase 3) userà il
 * BundledSQLiteDriver con estensione sqlite-vec; fallback brute-force documentato in ADR.
 */
interface CorpusReader {

    suspend fun manifest(): CorpusManifest

    /** Modalità Browse: elenco categorie con conteggio documenti. */
    suspend fun categories(): List<Pair<Category, Int>>

    /** Documenti di una categoria. */
    suspend fun documents(category: Category): List<CorpusDocument>

    /** Dense retrieval: top-K per similarità coseno rispetto all'embedding della query. */
    suspend fun searchDense(queryEmbedding: FloatArray, topK: Int): List<RetrievedChunk>

    /** Lexical retrieval: top-K BM25 via FTS5. */
    suspend fun searchLexical(query: String, topK: Int): List<RetrievedChunk>
}
