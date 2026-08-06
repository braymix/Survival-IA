package com.survivalwiki.core.embedding

/**
 * Prefissi obbligatori del modello e5: i testi del corpus usano "passage: ",
 * le query usano "query: ". Ometterli degrada gravemente il retrieval.
 */
enum class E5Prefix(val value: String) {
    PASSAGE("passage: "),
    QUERY("query: "),
}

/**
 * Astrazione dell'embedder semantico.
 *
 * Implementazione primaria (Fase 3): OnnxEmbedder su multilingual-e5-small INT8 via ONNX Runtime,
 * con tokenizer SentencePiece/XLM-R in Kotlin e mean-pooling + normalizzazione L2.
 */
interface Embedder {

    /** Dimensione del vettore prodotto (e5-small = 384). */
    val dimension: Int

    /** Calcola l'embedding L2-normalizzato di un singolo testo, applicando [prefix]. */
    suspend fun embed(text: String, prefix: E5Prefix): FloatArray

    /** Versione batch (più efficiente in ingest/ricerca). */
    suspend fun embedAll(texts: List<String>, prefix: E5Prefix): List<FloatArray>
}
