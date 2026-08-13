package com.survivalwiki.core.retrieval

/** Categorie chiuse del corpus (devono combaciare con `tools/ingest/categories.yaml`). */
enum class Category(val id: String) {
    ACQUA("acqua"),
    FUOCO("fuoco"),
    RIFUGIO("rifugio"),
    CIBO("cibo"),
    MEDICO("medico"),
    NAVIGAZIONE("navigazione"),
    SEGNALAZIONE("segnalazione"),
    ATTREZZATURA("attrezzatura"),
    CLIMA("clima"),
    SICUREZZA("sicurezza");

    companion object {
        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}

/** Un chunk recuperato dal corpus, con i metadati necessari alla citazione. */
data class RetrievedChunk(
    val chunkId: String,
    val docId: String,
    val docTitle: String,
    val section: String?,
    val pageStart: Int?,
    val pageEnd: Int?,
    val category: Category?,
    val license: String,
    val text: String,
)

/** Elemento di ranking prima della fusione: un chunk e la sua posizione (rank) nella lista sorgente. */
data class RankedItem(val chunk: RetrievedChunk, val rank: Int)

/** Risultato fuso con punteggio RRF. */
data class ScoredChunk(val chunk: RetrievedChunk, val score: Double)
