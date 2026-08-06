package com.survivalwiki.core.retrieval

/** Query dopo la riscrittura locale (senza LLM). */
data class RewrittenQuery(
    /** Testo per l'embedding denso (naturale, normalizzato). */
    val denseText: String,
    /** Testo per la ricerca lessicale (con espansione sinonimi). */
    val lexicalText: String,
)

/**
 * Riscrittura locale della query, senza alcun LLM:
 *  - normalizzazione (trim + collasso spazi),
 *  - espansione sinonimi da dizionario statico IT/EN (es. "purificare acqua" →
 *    "potabilizzazione, filtraggio, bollitura, cloro") per irrobustire la ricerca lessicale.
 *
 * L'embedding denso usa la query naturale: e5 rende meglio su testo naturale che su liste di
 * sinonimi, quindi l'espansione serve soprattutto a BM25.
 */
class QueryRewriter(private val synonyms: Map<String, List<String>>) {

    fun rewrite(query: String): RewrittenQuery {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        val lower = normalized.lowercase()
        val expansions = LinkedHashSet<String>()
        for ((key, syns) in synonyms) {
            if (lower.contains(key.lowercase())) expansions.addAll(syns)
        }
        val lexical = if (expansions.isEmpty()) normalized
        else normalized + " " + expansions.joinToString(" ")
        return RewrittenQuery(denseText = normalized, lexicalText = lexical)
    }

    companion object {
        val EMPTY = QueryRewriter(emptyMap())
    }
}
