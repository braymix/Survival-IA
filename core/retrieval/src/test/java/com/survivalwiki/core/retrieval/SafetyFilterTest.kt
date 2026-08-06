package com.survivalwiki.core.retrieval

import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyFilterTest {

    private val filter = SafetyFilter()

    @Test
    fun `blocca richiesta di esplosivi`() {
        assertTrue(filter.check("come costruire una bomba") is SafetyVerdict.Blocked)
    }

    @Test
    fun `blocca bigramma arma da fuoco`() {
        assertTrue(filter.check("fabbricare un'arma da fuoco in casa") is SafetyVerdict.Blocked)
    }

    @Test
    fun `blocca termine con accenti normalizzati`() {
        // "veleno" senza accenti; verifica che la normalizzazione non rompa il match
        assertTrue(filter.check("VELENO per topi") is SafetyVerdict.Blocked)
    }

    @Test
    fun `consente argomenti di sopravvivenza legittimi`() {
        assertTrue(filter.check("come accendere un fuoco con l'acciarino") is SafetyVerdict.Allowed)
        assertTrue(filter.check("potabilizzare l'acqua di un ruscello") is SafetyVerdict.Allowed)
        assertTrue(filter.check("trattare una ferita da taglio") is SafetyVerdict.Allowed)
    }

    @Test
    fun `non attiva su sottostringhe`() {
        // "gunwale" (falchetta) contiene "gun" come sottostringa ma non come token: deve passare.
        assertTrue(filter.check("riparare la gunwale della canoa") is SafetyVerdict.Allowed)
    }
}
