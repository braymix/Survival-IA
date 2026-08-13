package com.survivalwiki.core.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationValidatorTest {

    @Test
    fun `NESSUNA_FONTE viene riconosciuto`() {
        assertTrue(CitationValidator.validate("NESSUNA_FONTE", 3) is AnswerValidation.NoSource)
    }

    @Test
    fun `risposta con citazioni valide`() {
        val out = CitationValidator.validate("Bolli l'acqua un minuto [F1]. Filtra prima [F2].", 3)
        assertTrue(out is AnswerValidation.Valid)
        assertEquals(setOf(1, 2), (out as AnswerValidation.Valid).citedIndices)
    }

    @Test
    fun `zero citazioni e non verificabile`() {
        val out = CitationValidator.validate("Bolli l'acqua per un minuto.", 3)
        assertTrue(out is AnswerValidation.Unverifiable)
    }

    @Test
    fun `citazione a indice inesistente e non verificabile`() {
        val out = CitationValidator.validate("Vedi [F5].", 3)
        assertTrue(out is AnswerValidation.Unverifiable)
    }

    @Test
    fun `il prompt numera i passaggi come F1 F2`() {
        val builder = PromptBuilder("Sistema.")
        fun c(id: String) = ScoredChunk(
            RetrievedChunk(id, "d", "Doc $id", "Sez", 1, 1, Category.ACQUA, "CC0", "testo $id"), 0.5,
        )
        val prompt = builder.build("come faccio?", listOf(c("a"), c("b")))
        assertTrue(prompt.text.contains("[F1]"))
        assertTrue(prompt.text.contains("[F2]"))
        assertTrue(prompt.text.contains("DOMANDA: come faccio?"))
        assertEquals(2, prompt.passages.size)
    }
}
