package com.survivalwiki.core.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciprocalRankFusionTest {

    private fun chunk(id: String) = RetrievedChunk(
        chunkId = id, docId = "d", docTitle = "t", section = null,
        pageStart = null, pageEnd = null, category = null, license = "PD", text = id,
    )

    @Test
    fun `chunk in cima a entrambe le liste vince`() {
        val dense = listOf(chunk("a"), chunk("b"), chunk("c"))
        val lexical = listOf(chunk("a"), chunk("c"), chunk("b"))

        val fused = ReciprocalRankFusion(k = 60).fuse(listOf(dense, lexical))

        assertEquals("a", fused.first().chunk.chunkId)
    }

    @Test
    fun `deduplica i chunk presenti in piu liste`() {
        val l1 = listOf(chunk("a"), chunk("b"))
        val l2 = listOf(chunk("a"), chunk("b"))

        val fused = ReciprocalRankFusion().fuse(listOf(l1, l2))

        assertEquals(2, fused.size)
    }

    @Test
    fun `il punteggio somma i contributi delle liste`() {
        val onlyDense = listOf(chunk("x"))
        val fused = ReciprocalRankFusion(k = 60).fuse(listOf(onlyDense))
        // Unico contributo: 1 / (60 + 0 + 1)
        assertEquals(1.0 / 61.0, fused.first().score, 1e-9)
    }

    @Test
    fun `lista vuota produce risultato vuoto`() {
        assertTrue(ReciprocalRankFusion().fuse(emptyList()).isEmpty())
    }
}
