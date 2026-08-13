package com.survivalwiki.core.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceGateTest {

    private fun scored(score: Double) = ScoredChunk(
        RetrievedChunk("c", "d", "t", null, null, null, null, "PD", "x"), score,
    )

    @Test
    fun `sopra soglia procede con al piu maxPassages`() {
        val gate = ConfidenceGate(threshold = 0.01, maxPassages = 2)
        val decision = gate.decide(listOf(scored(0.5), scored(0.4), scored(0.3)))
        assertTrue(decision is RetrievalDecision.Proceed)
        assertEquals(2, (decision as RetrievalDecision.Proceed).passages.size)
    }

    @Test
    fun `sotto soglia non procede e non invoca LLM`() {
        val gate = ConfidenceGate(threshold = 0.9)
        val decision = gate.decide(listOf(scored(0.1)), nearbyTopicsProvider = { listOf("acqua") })
        assertTrue(decision is RetrievalDecision.NoReliableSource)
        assertEquals(listOf("acqua"), (decision as RetrievalDecision.NoReliableSource).nearbyTopics)
    }

    @Test
    fun `lista vuota produce NoReliableSource`() {
        val gate = ConfidenceGate(threshold = 0.01)
        assertTrue(gate.decide(emptyList()) is RetrievalDecision.NoReliableSource)
    }
}
