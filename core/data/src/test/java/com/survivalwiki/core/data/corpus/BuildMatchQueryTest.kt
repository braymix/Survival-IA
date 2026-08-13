package com.survivalwiki.core.data.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildMatchQueryTest {

    private fun build(q: String) = SqliteCorpusReader.buildMatchQuery(q)

    @Test
    fun `token di contenuto in match esatto`() {
        val m = build("come potabilizzare l'acqua")
        // "come" e "l" sono scartati; restano "potabilizzare" e "acqua", match esatto (niente *).
        assertTrue(m.contains("\"potabilizzare\""))
        assertTrue(m.contains("\"acqua\""))
        assertFalse("nessun prefix match", m.contains("*"))
    }

    @Test
    fun `query fuori dominio priva di token utili produce match vuoto o innocuo`() {
        // "dell", "qual" sono stopword/elisioni: non devono generare match.
        assertFalse(build("qual è la capitale").contains("\"qual\""))
        assertFalse(build("trama dell'ultimo film").contains("\"dell\""))
    }

    @Test
    fun `stopword rimosse`() {
        assertEquals("", build("come si fa a"))
    }
}
