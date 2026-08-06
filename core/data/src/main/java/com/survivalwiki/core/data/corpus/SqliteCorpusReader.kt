package com.survivalwiki.core.data.corpus

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.use
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.survivalwiki.core.retrieval.Category
import com.survivalwiki.core.retrieval.CorpusDocument
import com.survivalwiki.core.retrieval.CorpusManifest
import com.survivalwiki.core.retrieval.CorpusReader
import com.survivalwiki.core.retrieval.RetrievedChunk
import com.survivalwiki.core.retrieval.ScoredChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lettore del corpus su SQLite (bundled driver → FTS5 sempre disponibile).
 *
 * Dense retrieval: **brute-force cosine** sui vettori float32 letti da `chunk_vectors`.
 * Scelta documentata in docs/ADR-0002: sqlite-vec resta il percorso primario "su carta", ma non è
 * verificabile in questo ambiente; il brute-force è corretto e sufficiente al di sotto dei ~50k chunk
 * (i vettori sono già L2-normalizzati, quindi la similarità coseno è un prodotto scalare).
 *
 * Lexical retrieval: FTS5 con `bm25()`.
 */
class SqliteCorpusReader(private val dbPath: String) : CorpusReader {

    private val driver = BundledSQLiteDriver()
    private val vectorMutex = Mutex()
    // Cache dei vettori: caricata pigramente al primo searchDense.
    private var vectorCache: List<VectorRow>? = null

    private data class VectorRow(val chunkId: String, val vector: FloatArray)

    private inline fun <T> withConnection(block: (SQLiteConnection) -> T): T {
        val conn = driver.open(dbPath)
        try {
            return block(conn)
        } finally {
            conn.close()
        }
    }

    override suspend fun manifest(): CorpusManifest = withContext(Dispatchers.IO) {
        withConnection { conn ->
            val schema = readMeta(conn, "schema_version")?.toIntOrNull() ?: 1
            val builtAt = readMeta(conn, "built_at") ?: ""
            val docCount = scalarLong(conn, "SELECT count(*) FROM documents").toInt()
            val chunkCount = scalarLong(conn, "SELECT count(*) FROM chunks").toInt()
            CorpusManifest(
                schemaVersion = schema,
                documentCount = docCount,
                chunkCount = chunkCount,
                builtAtIso = builtAt,
                sha256 = readMeta(conn, "sha256") ?: "",
            )
        }
    }

    override suspend fun categories(): List<Pair<Category, Int>> = withContext(Dispatchers.IO) {
        withConnection { conn ->
            val out = ArrayList<Pair<Category, Int>>()
            conn.prepare(
                "SELECT categoria, count(*) FROM chunks GROUP BY categoria ORDER BY categoria",
            ).use { st ->
                while (st.step()) {
                    val cat = Category.fromId(st.getText(0)) ?: continue
                    out.add(cat to st.getLong(1).toInt())
                }
            }
            out
        }
    }

    override suspend fun documents(category: Category): List<CorpusDocument> =
        withContext(Dispatchers.IO) {
            withConnection { conn ->
                val out = ArrayList<CorpusDocument>()
                conn.prepare(
                    "SELECT DISTINCT d.doc_id,d.title,d.author,d.year,d.license,d.category " +
                        "FROM documents d JOIN chunks c ON c.doc_id=d.doc_id " +
                        "WHERE c.categoria = ? ORDER BY d.title",
                ).use { st ->
                    st.bindText(1, category.id)
                    while (st.step()) {
                        out.add(
                            CorpusDocument(
                                docId = st.getText(0),
                                title = st.getText(1),
                                author = if (st.isNull(2)) null else st.getText(2),
                                year = if (st.isNull(3)) null else st.getLong(3).toInt(),
                                license = st.getText(4),
                                category = if (st.isNull(5)) null else Category.fromId(st.getText(5)),
                            ),
                        )
                    }
                }
                out
            }
        }

    override suspend fun searchDense(queryEmbedding: FloatArray, topK: Int): List<ScoredChunk> =
        withContext(Dispatchers.Default) {
            val vectors = loadVectors()
            // Top-K per prodotto scalare (vettori L2-normalizzati → coseno).
            val scored = vectors.map { it.chunkId to dot(queryEmbedding, it.vector) }
                .sortedByDescending { it.second }
                .take(topK)
            val byId = hydrate(scored.map { it.first }).associateBy { it.chunkId }
            scored.mapNotNull { (id, cos) -> byId[id]?.let { ScoredChunk(it, cos.toDouble()) } }
        }

    override suspend fun searchLexical(query: String, topK: Int): List<RetrievedChunk> =
        withContext(Dispatchers.IO) {
            val match = buildMatchQuery(query)
            if (match.isBlank()) return@withContext emptyList()
            withConnection { conn ->
                val ids = ArrayList<String>()
                conn.prepare(
                    "SELECT chunk_id FROM chunks_fts WHERE chunks_fts MATCH ? " +
                        "ORDER BY bm25(chunks_fts) LIMIT ?",
                ).use { st ->
                    st.bindText(1, match)
                    st.bindLong(2, topK.toLong())
                    while (st.step()) ids.add(st.getText(0))
                }
                hydrate(ids)
            }
        }

    // --- helper ---

    private suspend fun loadVectors(): List<VectorRow> {
        vectorCache?.let { return it }
        return vectorMutex.withLock {
            vectorCache?.let { return it }
            val loaded = withContext(Dispatchers.IO) {
                withConnection { conn ->
                    val rows = ArrayList<VectorRow>()
                    conn.prepare("SELECT chunk_id,dim,vector FROM chunk_vectors").use { st ->
                        while (st.step()) {
                            val id = st.getText(0)
                            val dim = st.getLong(1).toInt()
                            val blob = st.getBlob(2)
                            rows.add(VectorRow(id, blobToFloats(blob, dim)))
                        }
                    }
                    rows
                }
            }
            vectorCache = loaded
            loaded
        }
    }

    /** Carica i chunk (metadati + testo) preservando l'ordine degli id passati. */
    private fun hydrate(orderedIds: List<String>): List<RetrievedChunk> {
        if (orderedIds.isEmpty()) return emptyList()
        val byId = HashMap<String, RetrievedChunk>(orderedIds.size)
        val placeholders = orderedIds.joinToString(",") { "?" }
        withConnection { conn ->
            conn.prepare(
                "SELECT chunk_id,doc_id,titolo_doc,sezione,pagina_inizio,pagina_fine,categoria," +
                    "licenza,testo FROM chunks WHERE chunk_id IN ($placeholders)",
            ).use { st ->
                orderedIds.forEachIndexed { i, id -> st.bindText(i + 1, id) }
                while (st.step()) {
                    val chunk = RetrievedChunk(
                        chunkId = st.getText(0),
                        docId = st.getText(1),
                        docTitle = st.getText(2),
                        section = if (st.isNull(3)) null else st.getText(3),
                        pageStart = if (st.isNull(4)) null else st.getLong(4).toInt(),
                        pageEnd = if (st.isNull(5)) null else st.getLong(5).toInt(),
                        category = if (st.isNull(6)) null else Category.fromId(st.getText(6)),
                        license = st.getText(7),
                        text = st.getText(8),
                    )
                    byId[chunk.chunkId] = chunk
                }
            }
        }
        return orderedIds.mapNotNull { byId[it] }
    }

    private fun readMeta(conn: SQLiteConnection, key: String): String? =
        conn.prepare("SELECT value FROM meta WHERE key = ?").use { st ->
            st.bindText(1, key)
            if (st.step()) st.getText(0) else null
        }

    private fun scalarLong(conn: SQLiteConnection, sql: String): Long =
        conn.prepare(sql).use { st -> if (st.step()) st.getLong(0) else 0L }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    companion object {
        /**
         * Costruisce una query FTS5: token di CONTENUTO in OR, con **match esatto** (niente prefix).
         * Le stopword IT/EN — incluse le ELISIONI (dell', qual, all'…) — sono rimosse: così una
         * query fuori dominio non produce alcun match lessicale, e il prefix non causa collisioni
         * come "ripara" → "riparo". Calibrato sul golden set (vedi tools/eval, ADR-0002/0003).
         */
        internal fun buildMatchQuery(query: String): String =
            Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase())
                .map { it.value }
                .filter { it.length >= 3 && it !in STOPWORDS }
                .map { "\"$it\"" }
                .joinToString(" OR ")

        private val STOPWORDS: Set<String> = setOf(
            // IT (incluse elisioni)
            "il", "lo", "la", "le", "gli", "un", "uno", "una", "di", "da", "del", "dei", "della",
            "delle", "degli", "dell", "al", "allo", "alla", "all", "con", "su", "sul", "sull",
            "per", "tra", "fra", "come", "che", "chi", "cosa", "quando", "dove", "perche", "e",
            "ed", "o", "ma", "se", "si", "no", "non", "mi", "ti", "ci", "vi", "ne", "nel", "nell",
            "in", "a", "ha", "ho", "hai", "sono", "essere", "fare", "posso", "devo", "vorrei",
            "qual", "quale", "quali", "quest", "questo", "questa", "quell", "dall", "coi", "col",
            "ai", "agli", "dai",
            // EN
            "the", "an", "of", "to", "on", "for", "and", "or", "how", "what", "who",
            "when", "where", "why", "is", "are", "do", "does", "can", "should", "with",
        )

        internal fun blobToFloats(blob: ByteArray, dim: Int): FloatArray {
            val out = FloatArray(dim)
            val bb = java.nio.ByteBuffer.wrap(blob).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            var i = 0
            while (i < dim && bb.remaining() >= 4) { out[i] = bb.float; i++ }
            return out
        }
    }
}
