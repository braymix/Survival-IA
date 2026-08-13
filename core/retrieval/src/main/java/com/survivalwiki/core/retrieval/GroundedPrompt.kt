package com.survivalwiki.core.retrieval

/** Prompt costruito per la generazione vincolata + la mappatura marcatore→passaggio. */
data class GroundedPrompt(
    val text: String,
    /** Passaggi nell'ordine di citazione: l'indice 0 corrisponde a [F1]. */
    val passages: List<RetrievedChunk>,
)

/**
 * Costruisce il prompt della generazione vincolata: system prompt + PASSAGGI numerati [F1], [F2]…
 * + la domanda. I passaggi sono quelli selezionati dal retrieval (già sopra soglia).
 */
class PromptBuilder(private val systemPrompt: String) {

    fun build(question: String, passages: List<ScoredChunk>): GroundedPrompt {
        val chunks = passages.map { it.chunk }
        val sb = StringBuilder()
        sb.append(systemPrompt.trim()).append("\n\n")
        sb.append("PASSAGGI:\n")
        chunks.forEachIndexed { i, c ->
            val marker = "F${i + 1}"
            val src = buildString {
                append(c.docTitle)
                c.section?.let { append(" — $it") }
                c.pageStart?.let { append(" (p. $it)") }
            }
            sb.append("[$marker] ($src)\n").append(c.text.trim()).append("\n\n")
        }
        sb.append("DOMANDA: ").append(question.trim()).append("\n")
        sb.append("RISPOSTA:")
        return GroundedPrompt(text = sb.toString(), passages = chunks)
    }
}
