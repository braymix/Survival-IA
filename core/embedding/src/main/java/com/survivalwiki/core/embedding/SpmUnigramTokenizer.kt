package com.survivalwiki.core.embedding

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/**
 * Tokenizer SentencePiece **Unigram** (modello XLM-RoBERTa usato da e5) in puro Kotlin.
 *
 * Riproduce la pipeline del tokenizer HuggingFace:
 *  1. Normalizzazione: NFKC + rimozione caratteri di controllo + collasso spazi multipli
 *     (approssima il `Precompiled` charsmap "nmt_nfkc" + `Replace(" {2,}", " ")`).
 *  2. Pre-tokenizzazione Metaspace: prefisso spazio (`add_prefix_space`) e sostituzione
 *     di ogni spazio con `▁` (U+2581).
 *  3. Segmentazione Viterbi sul lattice Unigram (massimizza la somma dei log-score).
 *  4. Post-processing: `<s>` (0) … `</s>` (2).
 *
 * La parità con il tokenizer Python è verificata da SpmUnigramTokenizerTest su fixture reali.
 */
class SpmUnigramTokenizer(
    private val pieceToId: HashMap<String, Int>,
    private val scores: FloatArray,
    private val unkId: Int,
    private val maxPieceLen: Int,
) {
    private val unkScore: Float = (scores.minOrNull() ?: -20f) - 10f
    private val metaspace = '▁'

    companion object {
        const val BOS_ID = 0
        const val EOS_ID = 2

        /** Carica da un TSV gzip: prima riga `#unk_id\tN`, poi `piece\tscore` per riga. */
        fun fromGzip(input: InputStream): SpmUnigramTokenizer =
            fromReader(BufferedReader(InputStreamReader(GZIPInputStream(input), Charsets.UTF_8)))

        fun fromReader(reader: BufferedReader): SpmUnigramTokenizer {
            val pieceToId = HashMap<String, Int>(300_000)
            val scoreList = ArrayList<Float>(300_000)
            var unkId = 3
            var maxLen = 1
            reader.useLines { lines ->
                var id = 0
                for (line in lines) {
                    if (line.startsWith("#unk_id\t")) {
                        unkId = line.substringAfter('\t').trim().toInt()
                        continue
                    }
                    val tab = line.lastIndexOf('\t')
                    if (tab <= 0) continue
                    val piece = line.substring(0, tab)
                    val score = line.substring(tab + 1).toFloat()
                    pieceToId[piece] = id
                    scoreList.add(score)
                    val cpLen = piece.codePointCount(0, piece.length)
                    if (cpLen > maxLen) maxLen = cpLen
                    id++
                }
            }
            return SpmUnigramTokenizer(pieceToId, scoreList.toFloatArray(), unkId, maxLen)
        }
    }

    /** Testo → id di token, con `<s>`/`</s>`. */
    fun encode(text: String): IntArray {
        val pieces = segment(preTokenize(normalize(text)))
        val out = IntArray(pieces.size + 2)
        out[0] = BOS_ID
        for (i in pieces.indices) out[i + 1] = pieces[i]
        out[out.size - 1] = EOS_ID
        return out
    }

    /** Solo per debug/test: le stringhe dei pezzi (senza speciali). */
    fun encodePieces(text: String): List<String> {
        val idToPiece = HashMap<Int, String>(pieceToId.size)
        for ((p, i) in pieceToId) idToPiece[i] = p
        return segment(preTokenize(normalize(text))).map { idToPiece[it] ?: "<unk>" }
    }

    // --- 1. Normalizzazione ---
    private fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        var lastWasSpace = false
        for (ch in nfkc) {
            val type = Character.getType(ch)
            // Rimuove caratteri di controllo/format (Cc, Cf) tranne il tab/newline che diventano spazio.
            if (ch == '\t' || ch == '\n' || ch == '\r') {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true }
                continue
            }
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()) continue
            if (ch == ' ') {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true }
            } else {
                sb.append(ch); lastWasSpace = false
            }
        }
        return sb.toString()
    }

    // --- 2. Pre-tokenizzazione Metaspace ---
    private fun preTokenize(text: String): String {
        val prefixed = if (text.startsWith(" ")) text else " $text"
        return buildString(prefixed.length) {
            for (ch in prefixed) append(if (ch == ' ') metaspace else ch)
        }
    }

    // --- 3. Viterbi sul lattice Unigram (su code point) ---
    private fun segment(s: String): IntArray {
        if (s.isEmpty()) return IntArray(0)
        // Indici dei confini di code point.
        val cpStarts = ArrayList<Int>(s.length + 1)
        var i = 0
        while (i < s.length) {
            cpStarts.add(i)
            i += Character.charCount(s.codePointAt(i))
        }
        cpStarts.add(s.length)
        val n = cpStarts.size - 1 // numero di code point

        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val backPiece = IntArray(n + 1) { -1 }
        val backStart = IntArray(n + 1) { -1 }
        best[0] = 0.0

        for (start in 0 until n) {
            if (best[start] == Double.NEGATIVE_INFINITY) continue
            val maxEnd = minOf(n, start + maxPieceLen)
            var matchedAny = false
            for (end in start + 1..maxEnd) {
                val sub = s.substring(cpStarts[start], cpStarts[end])
                val id = pieceToId[sub] ?: continue
                matchedAny = true
                val cand = best[start] + scores[id]
                if (cand > best[end]) { best[end] = cand; backPiece[end] = id; backStart[end] = start }
            }
            // Fallback unk: un singolo code point non coperto da alcun pezzo.
            if (!matchedAny) {
                val end = start + 1
                val cand = best[start] + unkScore
                if (cand > best[end]) { best[end] = cand; backPiece[end] = unkId; backStart[end] = start }
            }
        }

        // Ricostruzione a ritroso.
        val rev = ArrayList<Int>()
        var pos = n
        while (pos > 0) {
            val piece = backPiece[pos]
            val prev = backStart[pos]
            if (piece < 0 || prev < 0) { // sicurezza: nessun cammino (non dovrebbe accadere)
                rev.add(unkId); pos -= 1; continue
            }
            rev.add(piece); pos = prev
        }
        rev.reverse()
        return rev.toIntArray()
    }
}
