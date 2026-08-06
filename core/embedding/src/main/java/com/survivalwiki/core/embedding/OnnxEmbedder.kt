package com.survivalwiki.core.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.LongBuffer

/**
 * Embedder e5-small su ONNX Runtime. Deve produrre gli STESSI vettori della pipeline di ingest
 * (stesso modello INT8, stesso tokenizer, stesso mean-pooling + L2), condizione necessaria perché
 * il retrieval denso funzioni.
 *
 * @param modelPath percorso del file .onnx in storage app-private (scaricato al primo avvio).
 * @param tokenizer tokenizer SentencePiece Unigram (parità verificata con il Python).
 */
class OnnxEmbedder(
    modelPath: String,
    private val tokenizer: SpmUnigramTokenizer,
) : Embedder, Closeable {

    override val dimension: Int = EMBED_DIM

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelPath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        },
    )
    private val inputNames: Set<String> = session.inputNames.toSet()

    override suspend fun embed(text: String, prefix: E5Prefix): FloatArray =
        embedAll(listOf(text), prefix).first()

    override suspend fun embedAll(texts: List<String>, prefix: E5Prefix): List<FloatArray> =
        withContext(Dispatchers.Default) {
            texts.map { runSingle(prefix.value + it) }
        }

    private fun runSingle(text: String): FloatArray {
        val ids: IntArray = tokenizer.encode(text)
        val seqLen = ids.size
        val longIds = LongArray(seqLen) { ids[it].toLong() }
        val mask = LongArray(seqLen) { 1L }

        val shape = longArrayOf(1, seqLen.toLong())
        val tensors = HashMap<String, OnnxTensor>()
        try {
            tensors["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longIds), shape)
            tensors["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
            if ("token_type_ids" in inputNames) {
                tensors["token_type_ids"] =
                    OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(seqLen)), shape)
            }
            session.run(tensors).use { result ->
                val out = result[0] as OnnxTensor
                // last_hidden_state: [1, seqLen, dim]
                @Suppress("UNCHECKED_CAST")
                val data = out.value as Array<Array<FloatArray>>
                return meanPoolL2(data[0], mask)
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    /** Mean pooling mascherato + normalizzazione L2 (identico all'embedder Python). */
    private fun meanPoolL2(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim = hidden.first().size
        val pooled = FloatArray(dim)
        var count = 0f
        for (t in hidden.indices) {
            if (mask[t] == 0L) continue
            val row = hidden[t]
            for (d in 0 until dim) pooled[d] += row[d]
            count += 1f
        }
        if (count > 0f) for (d in 0 until dim) pooled[d] /= count
        var norm = 0f
        for (d in 0 until dim) norm += pooled[d] * pooled[d]
        norm = kotlin.math.sqrt(norm)
        if (norm > 1e-12f) for (d in 0 until dim) pooled[d] /= norm
        return pooled
    }

    override fun close() {
        session.close()
    }
}
