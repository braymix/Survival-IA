package com.survivalwiki.core.retrieval

/** Esito della post-validazione della risposta generata. */
sealed interface AnswerValidation {
    /** Il modello ha dichiarato di non avere fonti: mostrare il fallback. */
    data object NoSource : AnswerValidation

    /** Risposta valida: contiene almeno una citazione e tutte puntano a passaggi esistenti. */
    data class Valid(val citedIndices: Set<Int>) : AnswerValidation

    /**
     * Risposta non verificabile (zero citazioni, oppure cita un indice inesistente):
     * si scarta la generazione e si mostrano i passaggi grezzi con banner.
     */
    data class Unverifiable(val reason: String) : AnswerValidation
}

/**
 * Post-validazione in Kotlin della risposta generata: è la seconda barriera anti-allucinazione.
 *
 * Regole:
 *  - se la risposta è esattamente "NESSUNA_FONTE" → [AnswerValidation.NoSource];
 *  - estrae i marcatori `[Fn]`; se non ce ne sono, o se un indice non è tra 1..[passageCount]
 *    → [AnswerValidation.Unverifiable];
 *  - altrimenti → [AnswerValidation.Valid].
 */
object CitationValidator {
    private val MARKER = Regex("\\[F(\\d+)]")
    const val NO_SOURCE_TOKEN = "NESSUNA_FONTE"

    fun validate(answer: String, passageCount: Int): AnswerValidation {
        val trimmed = answer.trim()
        if (trimmed.contains(NO_SOURCE_TOKEN)) return AnswerValidation.NoSource

        val indices = MARKER.findAll(trimmed).map { it.groupValues[1].toInt() }.toList()
        if (indices.isEmpty()) {
            return AnswerValidation.Unverifiable("nessuna citazione nella risposta")
        }
        val invalid = indices.filter { it < 1 || it > passageCount }
        if (invalid.isNotEmpty()) {
            return AnswerValidation.Unverifiable("citazione a indice inesistente: ${invalid.distinct()}")
        }
        return AnswerValidation.Valid(indices.toSet())
    }
}
