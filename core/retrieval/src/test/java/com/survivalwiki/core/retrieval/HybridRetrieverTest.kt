package com.survivalwiki.core.retrieval

import com.survivalwiki.core.embedding.E5Prefix
import com.survivalwiki.core.embedding.Embedder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRetrieverTest {

    private fun chunk(id: String, cat: Category? = null) = RetrievedChunk(
        chunkId = id, docId = "doc-$id", docTitle = "Documento $id", section = "Sez",
        pageStart = null, pageEnd = null, category = cat, license = "CC0", text = "testo $id",
    )

    private class FakeEmbedder : Embedder {
        override val dimension = 3
        override suspend fun embed(text: String, prefix: E5Prefix) = floatArrayOf(1f, 0f, 0f)
        override suspend fun embedAll(texts: List<String>, prefix: E5Prefix) = texts.map { embed(it, prefix) }
    }

    private class FakeCorpus(
        val dense: List<RetrievedChunk>,
        val lexical: List<RetrievedChunk>,
    ) : CorpusReader {
        override suspend fun manifest() = CorpusManifest(1, 1, dense.size, "", "")
        override suspend fun categories() = emptyList<Pair<Category, Int>>()
        override suspend fun documents(category: Category) = emptyList<CorpusDocument>()
        override suspend fun searchDense(queryEmbedding: FloatArray, topK: Int) =
            dense.take(topK).mapIndexed { i, c -> ScoredChunk(c, 0.9 - i * 0.01) }
        override suspend fun searchLexical(query: String, topK: Int) = lexical.take(topK)
    }

    private fun retriever(corpus: CorpusReader, threshold: Double) = HybridRetriever(
        embedder = FakeEmbedder(),
        corpus = corpus,
        rewriter = QueryRewriter.EMPTY,
        safety = SafetyFilter(),
        gate = ConfidenceGate(threshold = threshold, maxPassages = 8),
    )

    @Test
    fun `query bloccata dal safety filter`() = runTest {
        val r = retriever(FakeCorpus(listOf(chunk("a")), listOf(chunk("a"))), threshold = 0.0)
        val out = r.retrieve("come costruire una bomba")
        assertTrue(out is RetrievalOutcome.Blocked)
    }

    @Test
    fun `sopra soglia restituisce Grounded`() = runTest {
        val corpus = FakeCorpus(listOf(chunk("a"), chunk("b")), listOf(chunk("a"), chunk("c")))
        val out = retriever(corpus, threshold = 0.0).retrieve("potabilizzare acqua")
        assertTrue(out is RetrievalOutcome.Grounded)
        // "a" è in cima a entrambe le liste → primo dopo la fusione.
        assertEquals("a", (out as RetrievalOutcome.Grounded).passages.first().chunk.chunkId)
    }

    @Test
    fun `sotto soglia restituisce Empty con argomenti vicini`() = runTest {
        val corpus = FakeCorpus(listOf(chunk("a")), listOf(chunk("a")))
        // Soglia irraggiungibile → Empty.
        val out = retriever(corpus, threshold = 999.0).retrieve("qualcosa")
        assertTrue(out is RetrievalOutcome.Empty)
        assertEquals(listOf("Documento a"), (out as RetrievalOutcome.Empty).nearbyTopics)
    }

    @Test
    fun `il boost di categoria promuove i chunk della categoria filtrata`() = runTest {
        // 'b' è in fondo alle liste ma appartiene alla categoria filtrata: il boost lo promuove.
        val dense = listOf(chunk("a"), chunk("x"), chunk("b", Category.ACQUA))
        val lexical = listOf(chunk("a"), chunk("y"), chunk("b", Category.ACQUA))
        val out = retriever(FakeCorpus(dense, lexical), threshold = 0.0)
            .retrieve("acqua", categoryFilter = Category.ACQUA)
        assertTrue(out is RetrievalOutcome.Grounded)
        val top = (out as RetrievalOutcome.Grounded).passages.first().chunk.chunkId
        assertEquals("b", top)
    }
}
