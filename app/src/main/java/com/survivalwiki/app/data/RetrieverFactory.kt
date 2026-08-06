package com.survivalwiki.app.data

import com.survivalwiki.core.embedding.Embedder
import com.survivalwiki.core.embedding.OnnxEmbedder
import com.survivalwiki.core.embedding.SpmUnigramTokenizer
import com.survivalwiki.core.retrieval.ConfidenceGate
import com.survivalwiki.core.retrieval.CorpusReader
import com.survivalwiki.core.retrieval.HybridRetriever
import com.survivalwiki.core.retrieval.QueryRewriter
import com.survivalwiki.core.retrieval.SafetyFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Costruisce l'[HybridRetriever] corrente tenendo conto dello stato a runtime:
 *  - presenza del modello di embedding (altrimenti retrieval solo-lessicale);
 *  - soglia di confidenza dalle impostazioni.
 *
 * Così l'app resta utile prima del download del modello e recepisce i cambi di impostazioni
 * senza riavvio.
 */
@Singleton
class RetrieverFactory @Inject constructor(
    private val corpus: CorpusReader,
    private val tokenizer: SpmUnigramTokenizer,
    private val synonyms: Synonyms,
    private val models: ModelRepository,
    private val settings: SettingsStore,
) {
    private val rewriter = QueryRewriter(synonyms.map)
    private val safety = SafetyFilter()

    // L'embedder ONNX è costoso: creato una sola volta quando il modello è disponibile.
    @Volatile
    private var embedder: Embedder? = null

    fun current(): HybridRetriever {
        val emb = if (models.hasEmbeddingModel()) {
            embedder ?: OnnxEmbedder(models.embeddingModel.absolutePath, tokenizer).also { embedder = it }
        } else {
            null
        }
        val gate = ConfidenceGate(threshold = settings.confidenceThreshold.value.toDouble())
        return HybridRetriever(
            embedder = emb,
            corpus = corpus,
            rewriter = rewriter,
            safety = safety,
            gate = gate,
        )
    }
}
